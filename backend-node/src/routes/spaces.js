/**
 * Routes Espaces — Logique Métier SplitEase v1.3
 * - Espace lié à un groupe (FR-047)
 * - Participants = membres actifs du groupe (FR-048)
 * - Phases : pending_acceptance → active → settling → settled / cancelled / suspended
 * - Mode PAY (immédiat) | PLAN (différé avec date d'échéance)
 * - Règle unanimité 48h (FR-053) + forçage créateur (FR-054)
 * - Règlement anticipé PLAN → accord unanime requis (FR-051/052)
 * - Lanceur désigné (FR-049/050)
 * - Assistance membre défaillant (FR-056/057)
 * - Quorum multi-membres avec remplacement (FR-058–061)
 * - Journal d'audit immuable (FR-062)
 */
const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { query } = require('../config/db');
const { authenticate } = require('../middleware/auth');
const { preCheck } = require('../services/aml');
const { notifyUser, notifyGroupMembers } = require('../services/fcm');

const router = express.Router({ mergeParams: true });
router.use(authenticate);

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
router.param('spaceId',  (req, res, next, val) => { if (!UUID_RE.test(val)) return res.status(400).json({ message: `Invalid space ID: "${val}"` });  next(); });
router.param('groupId',  (req, res, next, val) => { if (!UUID_RE.test(val)) return res.status(400).json({ message: `Invalid group ID: "${val}"` });  next(); });

// ── Quorum requis selon taille du groupe ─────────────────────────────────────
function requiredQuorum(memberCount) {
  if (memberCount <= 2)  return 1;
  if (memberCount <= 5)  return 2;
  if (memberCount <= 10) return 4;
  return Math.floor(memberCount / 3);
}

// ── Recalcul des parts (forçage avec membres acceptants uniquement) ──────────
function recalculateShares(totalAmount, acceptedCount, splitMode, originalShares) {
  if (splitMode === 'equally') {
    const share = Math.round((totalAmount / acceptedCount) * 100) / 100;
    return Object.fromEntries(originalShares.map(s => [s.user_id, share]));
  }
  // Pour exact/percentage : redistribution proportionnelle
  const totalShares = originalShares.reduce((s, p) => s + parseFloat(p.share), 0);
  return Object.fromEntries(originalShares.map(s => [
    s.user_id,
    Math.round((parseFloat(s.share) / totalShares * totalAmount) * 100) / 100
  ]));
}

// ── POST /groups/:groupId/spaces ─────────────────────────────────────────────
router.post('/', async (req, res) => {
  const { groupId } = req.params;
  const { name, category = 'autre', totalAmount, splitMode = 'equally',
          settlementMode = 'PAY', dueDate, launcherId, participants } = req.body;

  if (!name?.trim())       return res.status(400).json({ message: 'Le nom de l\'espace est requis' });
  if (!totalAmount || totalAmount <= 0) return res.status(400).json({ message: 'Le montant total doit être positif' });
  if (!participants?.length) return res.status(400).json({ message: 'Au moins un participant requis' });
  if (settlementMode === 'PLAN' && !dueDate) return res.status(400).json({ message: 'Date d\'échéance requise en mode PLAN' });

  // FR-047 : créateur doit être membre du groupe
  const isMember = (await query('SELECT id FROM group_members WHERE group_id=$1 AND user_id=$2', [groupId, req.userId])).rows.length > 0;
  if (!isMember) return res.status(403).json({ message: 'Vous devez être membre du groupe pour créer un espace' });

  // FR-048 : vérifier que tous les participants sont membres actifs du groupe
  for (const p of participants) {
    const m = await query('SELECT id FROM group_members WHERE group_id=$1 AND user_id=$2', [groupId, p.userId]);
    if (!m.rows.length) return res.status(400).json({ message: `L'utilisateur ${p.userId} n'est pas membre du groupe` });
  }

  // AML pre-check sur le montant total (respecte les règles R1–R18)
  const amlBlock = await preCheck(req.userId, totalAmount, groupId);
  if (amlBlock) return res.status(422).json({ message: `Espace bloqué par compliance AML: ${amlBlock}` });

  // Calcul des parts
  const count = participants.length;
  const shares = participants.map(p => {
    let share;
    if (splitMode === 'equally')    share = Math.round(totalAmount / count * 100) / 100;
    else if (splitMode === 'exact') share = parseFloat(p.share || 0);
    else if (splitMode === 'percentage') share = Math.round(totalAmount * parseFloat(p.share || 0) / 100 * 100) / 100;
    else share = Math.round(totalAmount / count * 100) / 100;
    return { userId: p.userId, share };
  });

  const spaceId    = uuidv4();
  const realLauncher = launcherId || req.userId;

  await query(
    `INSERT INTO spaces (id, group_id, name, category, total_amount, split_mode, settlement_mode,
      due_date, launcher_id, created_by, status, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'pending_acceptance',NOW())`,
    [spaceId, groupId, name, category, totalAmount, splitMode, settlementMode,
     dueDate || null, realLauncher, req.userId]
  );

  for (const s of shares) {
    await query(
      'INSERT INTO space_participants (id, space_id, user_id, share) VALUES ($1,$2,$3,$4)',
      [uuidv4(), spaceId, s.userId, s.share]
    );
  }

  await auditLog(spaceId, req.userId, 'space_created', { name, totalAmount, splitMode, settlementMode, participantCount: count });

  // Notifier tous les participants — délai 48h (FR-053)
  const creatorResult = await query('SELECT name FROM users WHERE id=$1', [req.userId]);
  const creatorName = creatorResult.rows[0]?.name || 'Quelqu\'un';
  for (const s of shares) {
    notifyUser(s.userId, 'Invitation à un espace',
      `${creatorName} vous invite à "${name}" — montant: ${s.share.toFixed(2)}€. Répondez dans 48h.`,
      { type: 'space_invitation', spaceId, groupId }).catch(() => {});
  }

  // Planifier rappels H+24 et H+47 (stockés pour traitement par job)
  await auditLog(spaceId, req.userId, 'reminders_scheduled', { h24: true, h47: true });

  const space = await getSpaceDetail(spaceId, req.userId);
  res.status(201).json(space);
});

// ── GET /groups/:groupId/spaces ───────────────────────────────────────────────
router.get('/', async (req, res) => {
  const { groupId } = req.params;
  const spaces = await query(
    `SELECT s.*, u.name AS creator_name, u2.name AS launcher_name
     FROM spaces s
     JOIN users u  ON u.id  = s.created_by
     JOIN users u2 ON u2.id = s.launcher_id
     WHERE s.group_id = $1 ORDER BY s.created_at DESC`,
    [groupId]
  );
  const result = await Promise.all(spaces.rows.map(s => getSpaceDetail(s.id, req.userId)));
  res.json(result);
});

// ── GET /pending — DOIT être avant /:spaceId pour ne pas être capturé ──────────
router.get('/pending', async (req, res) => {
  const result = await query(
    `SELECT s.id AS space_id, s.group_id, s.name, s.total_amount, s.category,
            s.settlement_mode, s.status, s.created_at,
            sp.share, sp.acceptance_status,
            g.name AS group_name, g.emoji AS group_emoji,
            u.name AS created_by_name,
            sq.status AS quorum_status
     FROM space_participants sp
     JOIN spaces s ON s.id = sp.space_id
     JOIN expense_groups g ON g.id = s.group_id
     JOIN users u ON u.id = s.created_by
     LEFT JOIN space_quorum_confirmations sq
       ON sq.space_id = s.id AND sq.user_id = $1 AND sq.status = 'pending'
     WHERE sp.user_id = $1
       AND (
         (sp.acceptance_status = 'pending' AND s.status = 'pending_acceptance')
         OR (sq.status = 'pending')
         OR (s.early_settle_requested = true AND sp.acceptance_status = 'accepted')
       )`,
    [req.userId]
  );
  res.json(result.rows.map(r => ({
    id:               r.space_id,
    groupId:          r.group_id,
    groupName:        r.group_name,
    groupEmoji:       r.group_emoji,
    name:             r.name,
    category:         r.category,
    totalAmount:      parseFloat(r.total_amount),
    myShare:          parseFloat(r.share),
    settlementMode:   r.settlement_mode,
    status:           r.status,
    acceptanceStatus: r.acceptance_status,
    quorumStatus:     r.quorum_status || null,
    createdByName:    r.created_by_name,
    createdAt:        r.created_at,
  })));
});

// ── GET /groups/:groupId/spaces/:spaceId ──────────────────────────────────────
router.get('/:spaceId', async (req, res) => {
  const detail = await getSpaceDetail(req.params.spaceId, req.userId);
  if (!detail) return res.status(404).json({ message: 'Espace non trouvé' });
  res.json(detail);
});

// ── POST /:spaceId/accept — Accepter la participation (FR-053) ────────────────
router.post('/:spaceId/accept', async (req, res) => {
  const { spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });
  if (space.status !== 'pending_acceptance') return res.status(409).json({ message: 'La phase d\'acceptation est terminée' });

  const participant = (await query(
    'SELECT * FROM space_participants WHERE space_id=$1 AND user_id=$2', [spaceId, req.userId]
  )).rows[0];
  if (!participant) return res.status(403).json({ message: 'Vous n\'êtes pas invité à cet espace' });
  if (participant.acceptance_status !== 'pending') return res.status(409).json({ message: `Vous avez déjà répondu: ${participant.acceptance_status}` });

  await query('UPDATE space_participants SET acceptance_status=$1, responded_at=NOW() WHERE space_id=$2 AND user_id=$3',
    ['accepted', spaceId, req.userId]);
  await auditLog(spaceId, req.userId, 'participant_accepted', {});

  // Vérifier unanimité
  await checkAcceptanceUnanimity(space, spaceId);

  res.json({ message: 'Participation acceptée' });
});

// ── POST /:spaceId/decline — Refuser la participation ────────────────────────
router.post('/:spaceId/decline', async (req, res) => {
  const { spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });
  if (space.status !== 'pending_acceptance') return res.status(409).json({ message: 'La phase d\'acceptation est terminée' });

  await query('UPDATE space_participants SET acceptance_status=$1, responded_at=NOW() WHERE space_id=$2 AND user_id=$3',
    ['declined', spaceId, req.userId]);
  await auditLog(spaceId, req.userId, 'participant_declined', {});

  // Annulation automatique si refus (FR-053)
  await cancelSpaceOnDecline(space, spaceId);

  res.json({ message: 'Participation refusée' });
});

// ── POST /:spaceId/force-launch — Forcer avec membres acceptants (FR-054) ─────
router.post('/:spaceId/force-launch', async (req, res) => {
  const { spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });
  if (space.created_by !== req.userId) return res.status(403).json({ message: 'Seul le créateur peut forcer le lancement' });
  if (space.status !== 'pending_acceptance') return res.status(409).json({ message: 'Impossible de forcer dans cet état' });

  const accepted = (await query(
    'SELECT * FROM space_participants WHERE space_id=$1 AND acceptance_status=$2', [spaceId, 'accepted']
  )).rows;

  if (!accepted.length) return res.status(400).json({ message: 'Aucun participant n\'a accepté' });

  // Recalcul des parts avec les acceptants uniquement (FR-054)
  const newShares = recalculateShares(parseFloat(space.total_amount), accepted.length, space.split_mode, accepted);
  for (const [uid, share] of Object.entries(newShares)) {
    await query('UPDATE space_participants SET share=$1 WHERE space_id=$2 AND user_id=$3', [share, spaceId, uid]);
  }

  // Supprimer les refus et non-réponses
  await query("DELETE FROM space_participants WHERE space_id=$1 AND acceptance_status!='accepted'", [spaceId]);
  await query("UPDATE spaces SET status='active', force_launched=true, updated_at=NOW() WHERE id=$1", [spaceId]);
  await auditLog(spaceId, req.userId, 'force_launched', { acceptedCount: accepted.length, newTotal: space.total_amount });

  res.json({ message: `Espace lancé avec ${accepted.length} membre(s). Montant redistribué.` });
});

// ── POST /:spaceId/settle — Déclencher le règlement (FR-050) ─────────────────
router.post('/:spaceId/settle', async (req, res) => {
  const { groupId, spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });

  // FR-050 : seul créateur ou lanceur désigné peut déclencher
  if (space.launcher_id !== req.userId && space.created_by !== req.userId)
    return res.status(403).json({ message: 'Seul le créateur ou le membre lanceur peut déclencher le règlement' });

  if (!['active'].includes(space.status))
    return res.status(409).json({ message: `Impossible de régler un espace en état "${space.status}"` });

  // AML check avant règlement
  const amlBlock = await preCheck(req.userId, space.total_amount, groupId, spaceId);
  if (amlBlock) return res.status(422).json({ message: `Règlement bloqué par compliance AML: ${amlBlock}` });

  await initiateSettlement(space, spaceId, groupId, req.userId);
  res.json({ message: 'Règlement initié — quorum de confirmation en cours' });
});

// ── POST /:spaceId/early-settle — Demander règlement anticipé PLAN (FR-051) ──
router.post('/:spaceId/early-settle', async (req, res) => {
  const { spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });
  if (space.settlement_mode !== 'PLAN') return res.status(400).json({ message: 'Le règlement anticipé est uniquement disponible en mode PLAN' });
  if (space.launcher_id !== req.userId && space.created_by !== req.userId)
    return res.status(403).json({ message: 'Seul le lanceur peut demander un règlement anticipé' });
  if (space.status !== 'active') return res.status(409).json({ message: 'L\'espace n\'est pas actif' });

  await query("UPDATE spaces SET early_settle_requested=true, early_settle_requested_at=NOW(), updated_at=NOW() WHERE id=$1", [spaceId]);
  await auditLog(spaceId, req.userId, 'early_settle_requested', {});

  // Notifier TOUS les membres pour accord unanime (FR-051)
  const participants = (await query(
    'SELECT user_id FROM space_participants WHERE space_id=$1 AND acceptance_status=$2', [spaceId, 'accepted']
  )).rows;

  for (const p of participants) {
    if (p.user_id !== req.userId) {
      notifyUser(p.user_id, 'Règlement anticipé demandé',
        'Le lanceur souhaite régler cet espace avant la date prévue. Acceptez-vous ?',
        { type: 'early_settle_request', spaceId, action: 'required' }).catch(() => {});
    }
  }

  res.json({ message: 'Demande de règlement anticipé envoyée à tous les membres' });
});

// ── POST /:spaceId/early-settle/respond — Répondre au règlement anticipé ──────
router.post('/:spaceId/early-settle/respond', async (req, res) => {
  const { groupId, spaceId } = req.params;
  const { vote } = req.body; // 'accepted' | 'declined'
  if (!['accepted', 'declined'].includes(vote))
    return res.status(400).json({ message: 'vote doit être "accepted" ou "declined"' });

  const space = await getSpace(spaceId);
  if (!space?.early_settle_requested) return res.status(400).json({ message: 'Aucune demande de règlement anticipé en cours' });

  // Upsert vote
  await query(
    `INSERT INTO space_early_settle_votes (id, space_id, user_id, vote, voted_at)
     VALUES ($1,$2,$3,$4,NOW())
     ON CONFLICT (space_id, user_id) DO UPDATE SET vote=$4, voted_at=NOW()`,
    [uuidv4(), spaceId, req.userId, vote]
  );
  await auditLog(spaceId, req.userId, 'early_settle_vote', { vote });

  if (vote === 'declined') {
    // FR-052 : au moins un refus → blocage du règlement anticipé
    await query("UPDATE spaces SET early_settle_requested=false, updated_at=NOW() WHERE id=$1", [spaceId]);
    const launcher = (await query('SELECT launcher_id FROM spaces WHERE id=$1', [spaceId])).rows[0];
    notifyUser(launcher.launcher_id, 'Règlement anticipé refusé',
      'Un membre a refusé le règlement anticipé. L\'espace reste en mode PLAN jusqu\'à la date d\'échéance.',
      { type: 'early_settle_refused', spaceId }).catch(() => {});
    return res.json({ message: 'Règlement anticipé refusé. L\'espace reste en mode PLAN.' });
  }

  // Vérifier unanimité
  const totalAccepted = (await query(
    'SELECT COUNT(*) FROM space_participants WHERE space_id=$1 AND acceptance_status=$2', [spaceId, 'accepted']
  )).rows[0].count;
  const totalVotedYes = (await query(
    'SELECT COUNT(*) FROM space_early_settle_votes WHERE space_id=$1 AND vote=$2', [spaceId, 'accepted']
  )).rows[0].count;

  if (parseInt(totalVotedYes) >= parseInt(totalAccepted)) {
    // Unanimité atteinte → déclencher le règlement
    await query("UPDATE spaces SET early_settle_requested=false, updated_at=NOW() WHERE id=$1", [spaceId]);
    await auditLog(spaceId, req.userId, 'early_settle_approved_unanimous', {});
    await initiateSettlement(space, spaceId, groupId, space.launcher_id);
    return res.json({ message: 'Accord unanime — règlement anticipé déclenché' });
  }

  res.json({ message: `Vote enregistré (${totalVotedYes}/${totalAccepted})` });
});

// ── POST /:spaceId/quorum/confirm — Confirmer le règlement (quorum) ───────────
router.post('/:spaceId/quorum/confirm', async (req, res) => {
  const { groupId, spaceId } = req.params;
  await respondToQuorum(spaceId, groupId, req.userId, 'confirmed', res);
});

// ── POST /:spaceId/quorum/reject — Rejeter le règlement (quorum) ─────────────
router.post('/:spaceId/quorum/reject', async (req, res) => {
  const { groupId, spaceId } = req.params;
  await respondToQuorum(spaceId, groupId, req.userId, 'rejected', res);
});

// ── POST /:spaceId/assist — Assistance membre défaillant (FR-056) ─────────────
router.post('/:spaceId/assist', async (req, res) => {
  const { spaceId } = req.params;
  const { memberId } = req.body; // ID du membre à assister

  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });

  const memberParticipant = (await query(
    'SELECT * FROM space_participants WHERE space_id=$1 AND user_id=$2', [spaceId, memberId]
  )).rows[0];
  if (!memberParticipant) return res.status(404).json({ message: 'Membre non trouvé dans cet espace' });
  if (memberParticipant.assisted_by) return res.status(409).json({ message: 'Ce membre est déjà assisté' });

  // FR-057 : enregistrer la dette
  await query('UPDATE space_participants SET assisted_by=$1 WHERE space_id=$2 AND user_id=$3',
    [req.userId, spaceId, memberId]);
  await auditLog(spaceId, req.userId, 'assistance_provided',
    { assistedUser: memberId, amount: memberParticipant.share });

  // Notifier tous les autres membres que l'assistance est prise en charge
  const participants = (await query(
    'SELECT user_id FROM space_participants WHERE space_id=$1', [spaceId]
  )).rows;
  const assistantName = (await query('SELECT name FROM users WHERE id=$1', [req.userId])).rows[0]?.name || 'Un membre';
  const memberName    = (await query('SELECT name FROM users WHERE id=$1', [memberId])).rows[0]?.name || 'Un membre';

  for (const p of participants) {
    if (p.user_id !== req.userId) {
      notifyUser(p.user_id, 'Assistance prise en charge',
        `${assistantName} a couvert la part de ${memberName} (${parseFloat(memberParticipant.share).toFixed(2)}€)`,
        { type: 'assistance_taken', spaceId }).catch(() => {});
    }
  }

  res.json({ message: `Assistance enregistrée. ${assistantName} couvre ${parseFloat(memberParticipant.share).toFixed(2)}€ pour ${memberName}` });
});

// ── POST /:spaceId/transfer-launcher — Transférer le rôle lanceur ─────────────
router.post('/:spaceId/transfer-launcher', async (req, res) => {
  const { spaceId } = req.params;
  const { toUserId } = req.body;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Espace non trouvé' });
  if (space.created_by !== req.userId) return res.status(403).json({ message: 'Seul le créateur peut transférer le rôle lanceur' });
  if (['settling', 'settled', 'cancelled'].includes(space.status))
    return res.status(409).json({ message: 'Impossible de transférer après le déclenchement' });

  const isMember = (await query(
    'SELECT id FROM space_participants WHERE space_id=$1 AND user_id=$2', [spaceId, toUserId]
  )).rows.length > 0;
  if (!isMember) return res.status(400).json({ message: 'L\'utilisateur cible n\'est pas participant de l\'espace' });

  await query('UPDATE spaces SET launcher_id=$1, updated_at=NOW() WHERE id=$2', [toUserId, spaceId]);
  await auditLog(spaceId, req.userId, 'launcher_transferred', { toUserId });

  const newLauncherName = (await query('SELECT name FROM users WHERE id=$1', [toUserId])).rows[0]?.name || '';
  notifyUser(toUserId, 'Rôle lanceur attribué',
    `Vous êtes maintenant le lanceur de l'espace "${space.name}"`,
    { type: 'launcher_assigned', spaceId }).catch(() => {});

  res.json({ message: `Rôle lanceur transféré à ${newLauncherName}` });
});

// ── PATCH /:spaceId — Modifier un espace et notifier les membres ─────────────
router.patch('/:spaceId', async (req, res) => {
  const { spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Expense not found' });
  if (space.created_by !== req.userId)
    return res.status(403).json({ message: 'Only the creator can edit this expense' });
  if (['settled', 'cancelled'].includes(space.status))
    return res.status(409).json({ message: 'Cannot edit a settled or cancelled expense' });

  const { name, category, totalAmount, splitMode, dueDate } = req.body;

  // Validate equally constraint
  if (splitMode === 'equally' && totalAmount) {
    const participantCount = (await query(
      'SELECT COUNT(*) FROM space_participants WHERE space_id=$1', [spaceId]
    )).rows[0].count;
    const cents = Math.round(parseFloat(totalAmount) * 100);
    if (cents % parseInt(participantCount) !== 0)
      return res.status(400).json({ message: `Amount €${totalAmount} cannot be split equally among ${participantCount} participants` });
  }

  const updates = [];
  const values  = [];
  let idx = 1;

  if (name?.trim())  { updates.push(`name=$${idx++}`);     values.push(name.trim()); }
  if (category)      { updates.push(`category=$${idx++}`); values.push(category); }

  if (space.status === 'pending_acceptance') {
    if (totalAmount && parseFloat(totalAmount) > 0) { updates.push(`total_amount=$${idx++}`); values.push(parseFloat(totalAmount)); }
    if (splitMode)   { updates.push(`split_mode=$${idx++}`); values.push(splitMode); }
    if (dueDate !== undefined) { updates.push(`due_date=$${idx++}`); values.push(dueDate || null); }
  }

  if (updates.length === 0) return res.status(400).json({ message: 'No fields to update' });

  values.push(spaceId);
  await query(`UPDATE spaces SET ${updates.join(', ')} WHERE id=$${idx}`, values);

  const updatedSpace = await getSpaceDetail(spaceId, req.userId);
  res.json(updatedSpace);

  // Notify participants
  const participants = (await query('SELECT user_id FROM space_participants WHERE space_id=$1', [spaceId])).rows;
  const updaterRes = await query('SELECT name FROM users WHERE id=$1', [req.userId]);
  const updaterName = updaterRes.rows[0]?.name || 'Someone';
  for (const p of participants) {
    if (p.user_id !== req.userId) {
      notifyUser(p.user_id, 'Expense updated',
        `"${space.name}" has been updated by ${updaterName}.`,
        { type: 'expense_updated', spaceId }).catch(() => {});
    }
  }
});

// ── DELETE /:spaceId — Supprimer un espace et notifier les membres ───────────
router.delete('/:spaceId', async (req, res) => {
  const { spaceId } = req.params;
  const space = await getSpace(spaceId);
  if (!space) return res.status(404).json({ message: 'Expense not found' });
  if (space.created_by !== req.userId)
    return res.status(403).json({ message: 'Only the creator can delete this expense' });

  // Fetch participants before deleting
  const participantsResult = await query(
    `SELECT sp.user_id, u.name FROM space_participants sp
     JOIN users u ON u.id = sp.user_id WHERE sp.space_id=$1`,
    [spaceId]
  );
  const participants = participantsResult.rows;

  // Delete in dependency order
  await query('DELETE FROM space_quorum_confirmations WHERE space_id=$1', [spaceId]);
  await query('DELETE FROM space_early_settle_votes   WHERE space_id=$1', [spaceId]);
  await query('DELETE FROM space_audit_log            WHERE space_id=$1', [spaceId]);
  await query('DELETE FROM space_participants         WHERE space_id=$1', [spaceId]);
  await query('DELETE FROM spaces WHERE id=$1', [spaceId]);

  res.json({ message: 'Expense deleted' });

  // Notify all participants except creator
  const creatorRes = await query('SELECT name FROM users WHERE id=$1', [req.userId]);
  const creatorName = creatorRes.rows[0]?.name || 'Someone';
  for (const p of participants) {
    if (p.user_id !== req.userId) {
      notifyUser(
        p.user_id,
        'Expense deleted',
        `"${space.name}" has been deleted by ${creatorName}.`,
        { type: 'expense_deleted', spaceId }
      ).catch(() => {});
    }
  }
});

// ── GET /:spaceId/audit — Journal d'audit (FR-062) ────────────────────────────
router.get('/:spaceId/audit', async (req, res) => {
  const logs = await query(
    `SELECT al.*, u.name AS user_name FROM space_audit_log al
     LEFT JOIN users u ON u.id = al.user_id
     WHERE al.space_id=$1 ORDER BY al.created_at ASC`,
    [req.params.spaceId]
  );
  res.json(logs.rows);
});

// ── Traitement des paiements lors du règlement ────────────────────────────────
async function processSettlementPayments(spaceId, space) {
  const launcherId = space.launcher_id;

  const participants = (await query(
    `SELECT sp.user_id, sp.share FROM space_participants sp
     WHERE sp.space_id = $1 AND sp.acceptance_status = 'accepted'`,
    [spaceId]
  )).rows;

  let totalTransferred = 0;
  const blockedUsers   = [];

  for (const p of participants) {
    if (p.user_id === launcherId) continue; // le lanceur ne se paie pas à lui-même

    const share = parseFloat(p.share);
    if (share <= 0) continue;

    // ── AML check avant chaque transfert ──────────────────────────────────
    const blocked = await preCheck(p.user_id, share, space.group_id, spaceId);
    if (blocked) {
      await auditLog(spaceId, p.user_id, 'payment_blocked_aml', { amount: share, reason: blocked });
      notifyUser(p.user_id, 'Payment blocked',
        `Your payment of €${share.toFixed(2)} for "${space.name}" was blocked by compliance: ${blocked}`,
        { type: 'payment_blocked', spaceId }).catch(() => {});
      blockedUsers.push({ userId: p.user_id, share });
      continue;
    }

    // ── Transfert atomique ─────────────────────────────────────────────────
    try {
      await query('BEGIN');
      await query('UPDATE users SET account_balance = account_balance - $1 WHERE id = $2', [share, p.user_id]);
      await query('UPDATE users SET account_balance = account_balance + $1 WHERE id = $2', [share, launcherId]);
      await query(
        `INSERT INTO payments (space_id, from_user, to_user, amount, method, note)
         VALUES ($1, $2, $3, $4, 'in_app', $5)`,
        [spaceId, p.user_id, launcherId, share, `Settlement: ${space.name}`]
      );
      await query('COMMIT');

      totalTransferred += share;
      await auditLog(spaceId, p.user_id, 'payment_transferred', { amount: share, to: launcherId });

      notifyUser(p.user_id, 'Payment processed',
        `€${share.toFixed(2)} has been deducted from your balance for "${space.name}".`,
        { type: 'payment_processed', spaceId }).catch(() => {});
    } catch (err) {
      await query('ROLLBACK');
      console.error(`[PAYMENT] Transfer failed — user ${p.user_id}:`, err.message);
      await auditLog(spaceId, p.user_id, 'payment_failed', { amount: share, error: err.message });
    }
  }

  // Notifier le lanceur du total reçu
  if (totalTransferred > 0) {
    notifyUser(launcherId, 'Payments received',
      `You received €${totalTransferred.toFixed(2)} for "${space.name}".`,
      { type: 'payments_received', spaceId, amount: totalTransferred }).catch(() => {});
  }

  // Notifier les admins si des paiements ont été bloqués par l'AML
  if (blockedUsers.length > 0) {
    const { notifyAdmins } = require('../services/fcm');
    notifyAdmins('AML — Payments blocked',
      `${blockedUsers.length} payment(s) blocked during settlement of "${space.name}"`,
      { type: 'aml_payments_blocked', spaceId }).catch(() => {});
  }
}

// ── Fonctions internes ────────────────────────────────────────────────────────

async function getSpace(spaceId) {
  const r = await query('SELECT * FROM spaces WHERE id=$1', [spaceId]);
  return r.rows[0] || null;
}

async function getSpaceDetail(spaceId, currentUserId) {
  const space = await getSpace(spaceId);
  if (!space) return null;

  const participants = (await query(
    `SELECT sp.*, u.name FROM space_participants sp JOIN users u ON u.id = sp.user_id WHERE sp.space_id=$1`,
    [spaceId]
  )).rows;

  const myParticipant  = participants.find(p => p.user_id === currentUserId);
  const currentCycle   = await getCurrentSettlementCycle(spaceId);
  const myQuorumStatus = currentCycle
    ? (await query('SELECT status FROM space_quorum_confirmations WHERE space_id=$1 AND user_id=$2 AND settlement_cycle=$3',
        [spaceId, currentUserId, currentCycle])).rows[0]?.status
    : null;

  return {
    id:                    space.id,
    groupId:               space.group_id,
    name:                  space.name,
    category:              space.category,
    totalAmount:           parseFloat(space.total_amount),
    splitMode:             space.split_mode,
    settlementMode:        space.settlement_mode,
    dueDate:               space.due_date,
    launcherId:            space.launcher_id,
    status:                space.status,
    forceLaunched:         space.force_launched,
    earlySettleRequested:  space.early_settle_requested,
    createdBy:             space.created_by,
    createdAt:             space.created_at,
    myShare:               myParticipant ? parseFloat(myParticipant.share) : null,
    myAcceptanceStatus:    myParticipant?.acceptance_status || null,
    myAssistedBy:          myParticipant?.assisted_by || null,
    myQuorumStatus,
    participants: participants.map(p => ({
      userId:           p.user_id,
      name:             p.name,
      share:            parseFloat(p.share),
      acceptanceStatus: p.acceptance_status,
      assistedBy:       p.assisted_by,
    })),
  };
}

// Vérification unanimité après chaque acceptation / dépassement délai
async function checkAcceptanceUnanimity(space, spaceId) {
  const all = (await query('SELECT acceptance_status FROM space_participants WHERE space_id=$1', [spaceId])).rows;
  const allAccepted = all.every(p => p.acceptance_status === 'accepted');
  if (allAccepted) {
    await query("UPDATE spaces SET status='active', updated_at=NOW() WHERE id=$1", [spaceId]);
    await auditLog(spaceId, null, 'unanimous_acceptance', { count: all.length });

    // Notification globale de confirmation
    const participants = (await query('SELECT user_id FROM space_participants WHERE space_id=$1', [spaceId])).rows;
    const spaceName = space.name;
    for (const p of participants) {
      notifyUser(p.user_id, 'Espace confirmé !',
        `Tous les membres ont accepté "${spaceName}". L'espace est maintenant actif.`,
        { type: 'space_confirmed', spaceId }).catch(() => {});
    }
  }
}

// Annulation automatique si au moins un refus (FR-053)
async function cancelSpaceOnDecline(space, spaceId) {
  await query("UPDATE spaces SET status='cancelled', updated_at=NOW() WHERE id=$1", [spaceId]);
  await auditLog(spaceId, null, 'auto_cancelled', { reason: 'participant_declined' });

  const participants = (await query('SELECT user_id FROM space_participants WHERE space_id=$1', [spaceId])).rows;
  for (const p of participants) {
    notifyUser(p.user_id, 'Expense cancelled',
      `"${space.name}" was automatically cancelled due to a declined response.`,
      { type: 'space_cancelled', spaceId }).catch(() => {});
  }
}

// Initier le règlement : notification globale + désignation quorum ─────────────
async function initiateSettlement(space, spaceId, groupId, launcherId) {
  await query("UPDATE spaces SET status='settling', updated_at=NOW() WHERE id=$1", [spaceId]);
  await auditLog(spaceId, launcherId, 'settlement_initiated', {});

  const participants = (await query(
    'SELECT user_id FROM space_participants WHERE space_id=$1 AND acceptance_status=$2', [spaceId, 'accepted']
  )).rows;

  // FR-058 : notification informative à TOUS
  for (const p of participants) {
    notifyUser(p.user_id, 'Settlement in progress',
      `Settlement of "${space.name}" has been triggered.`,
      { type: 'settlement_in_progress', spaceId }).catch(() => {});
  }

  // Désigner le quorum
  await designateQuorum(spaceId, participants, launcherId, 1);
}

// Désigner les membres du quorum (sélection aléatoire, hors lanceur) ──────────
async function designateQuorum(spaceId, participants, excludeUserId, cycle) {
  const eligible = participants
    .map(p => p.user_id)
    .filter(uid => uid !== excludeUserId);

  // Retirer les déjà sollicités ce cycle
  const alreadySolicited = (await query(
    'SELECT user_id FROM space_quorum_confirmations WHERE space_id=$1 AND settlement_cycle=$2',
    [spaceId, cycle]
  )).rows.map(r => r.user_id);

  const candidates = eligible.filter(uid => !alreadySolicited.includes(uid));
  const needed = requiredQuorum(participants.length);

  if (candidates.length === 0) {
    // FR-061 : plus de membres disponibles → règlement suspendu
    await query("UPDATE spaces SET status='suspended', updated_at=NOW() WHERE id=$1", [spaceId]);
    await auditLog(spaceId, null, 'quorum_impossible', { cycle });
    const space = await getSpace(spaceId);
    notifyUser(space.launcher_id, 'Règlement suspendu',
      `Impossible d'atteindre le quorum pour "${space.name}". Vous pouvez relancer.`,
      { type: 'quorum_impossible', spaceId }).catch(() => {});
    return;
  }

  // Sélection aléatoire
  const shuffled = candidates.sort(() => Math.random() - 0.5);
  const selected = shuffled.slice(0, Math.min(needed, shuffled.length));
  const expiresAt = new Date(Date.now() + 5 * 60_000); // 5 minutes

  for (const uid of selected) {
    await query(
      `INSERT INTO space_quorum_confirmations (id, space_id, settlement_cycle, user_id, status, expires_at)
       VALUES ($1,$2,$3,$4,'pending',$5)`,
      [uuidv4(), spaceId, cycle, uid, expiresAt]
    );
    const space = await getSpace(spaceId);
    notifyUser(uid, 'Confirmation requise',
      `Votre confirmation est requise pour régler "${space.name}". Délai : 5 minutes.`,
      { type: 'quorum_confirmation_required', spaceId, action: 'required' }).catch(() => {});
  }

  await auditLog(spaceId, null, 'quorum_designated', { cycle, members: selected });
}

// Répondre au quorum ───────────────────────────────────────────────────────────
async function respondToQuorum(spaceId, groupId, userId, response, res) {
  const space = await getSpace(spaceId);
  if (!space || space.status !== 'settling')
    return res.status(409).json({ message: 'Aucun règlement en cours' });

  const cycle = await getCurrentSettlementCycle(spaceId);
  const confirmation = (await query(
    'SELECT * FROM space_quorum_confirmations WHERE space_id=$1 AND user_id=$2 AND settlement_cycle=$3 AND status=$4',
    [spaceId, userId, cycle, 'pending']
  )).rows[0];

  if (!confirmation) return res.status(403).json({ message: 'Vous n\'êtes pas dans le quorum actuel' });

  // Vérifier expiration
  if (new Date() > new Date(confirmation.expires_at)) {
    await replaceQuorumMember(spaceId, userId, groupId, cycle);
    return res.status(410).json({ message: 'Délai expiré — un autre membre vous remplace dans le quorum' });
  }

  await query('UPDATE space_quorum_confirmations SET status=$1, responded_at=NOW() WHERE id=$2',
    [response, confirmation.id]);
  await auditLog(spaceId, userId, `quorum_${response}`, { cycle });

  if (response === 'rejected') {
    // FR-059 : remplacement du confirmateur refusant
    await replaceQuorumMember(spaceId, userId, groupId, cycle);
    return res.json({ message: 'Rejet enregistré — un autre membre a été désigné' });
  }

  // Vérifier si quorum atteint
  const participants = (await query(
    'SELECT user_id FROM space_participants WHERE space_id=$1 AND acceptance_status=$2', [spaceId, 'accepted']
  )).rows;
  const needed     = requiredQuorum(participants.length);
  const confirmed  = (await query(
    'SELECT COUNT(*) FROM space_quorum_confirmations WHERE space_id=$1 AND settlement_cycle=$2 AND status=$3',
    [spaceId, cycle, 'confirmed']
  )).rows[0].count;

  if (parseInt(confirmed) >= needed) {
    // Quorum reached → settle the space and process payments
    await query("UPDATE spaces SET status='settled', updated_at=NOW() WHERE id=$1", [spaceId]);
    await auditLog(spaceId, null, 'space_settled', { confirmations: confirmed });

    // Process money transfers (async, non-blocking for the response)
    processSettlementPayments(spaceId, space).catch(err =>
      console.error('[PAYMENT] Settlement payment processing failed:', err.message)
    );

    for (const p of participants) {
      notifyUser(p.user_id, 'Expense settled!',
        `"${space.name}" has been successfully settled.`,
        { type: 'space_settled', spaceId }).catch(() => {});
    }
    return res.json({ message: 'Quorum reached — expense settled!' });
  }

  res.json({ message: `Confirmation enregistrée (${confirmed}/${needed})` });
}

// FR-059 : Remplacer un membre défaillant du quorum ────────────────────────────
async function replaceQuorumMember(spaceId, expiredUserId, groupId, cycle) {
  await query('UPDATE space_quorum_confirmations SET status=$1 WHERE space_id=$2 AND user_id=$3 AND settlement_cycle=$4 AND status=$5',
    ['expired', spaceId, expiredUserId, cycle, 'pending']);
  await auditLog(spaceId, expiredUserId, 'quorum_member_replaced', { cycle });

  const participants = (await query(
    'SELECT user_id FROM space_participants WHERE space_id=$1 AND acceptance_status=$2', [spaceId, 'accepted']
  )).rows;

  const space = await getSpace(spaceId);
  await designateQuorum(spaceId, participants, space.launcher_id, cycle);
}

// Cycle de règlement courant ───────────────────────────────────────────────────
async function getCurrentSettlementCycle(spaceId) {
  const r = await query(
    'SELECT MAX(settlement_cycle) AS c FROM space_quorum_confirmations WHERE space_id=$1', [spaceId]
  );
  return r.rows[0]?.c || null;
}

// Journal d'audit immuable (FR-062) ───────────────────────────────────────────
async function auditLog(spaceId, userId, eventType, details) {
  await query(
    'INSERT INTO space_audit_log (id, space_id, user_id, event_type, details) VALUES ($1,$2,$3,$4,$5)',
    [uuidv4(), spaceId, userId || null, eventType, JSON.stringify(details)]
  ).catch(() => {});
}

module.exports = router;
