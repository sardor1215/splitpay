# SplitPay — Complete Test Plan
**Version:** 2.0 | **Date:** 2026-05-21 | **Stack:** Node.js/Express/PostgreSQL · Android/Jetpack Compose

---

## Table of Contents
1. [Authentication](#1-authentication)
2. [Profile & Account](#2-profile--account)
3. [Groups](#3-groups)
4. [Expenses (Spaces)](#4-expenses-spaces)
5. [Split Modes](#5-split-modes)
6. [Settlement & Quorum](#6-settlement--quorum)
7. [Money Transfer & AML](#7-money-transfer--aml)
8. [Notifications](#8-notifications)
9. [KYC](#9-kyc)
10. [Admin Dashboard](#10-admin-dashboard)
11. [AML Alerts](#11-aml-alerts)
12. [GDPR Config](#12-gdpr-config)
13. [Edge Cases & Security](#13-edge-cases--security)

---

## 1. Authentication

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 1.1 | Register with valid data | POST /auth/register with name, email, password | 200, user created | ⬜ |
| 1.2 | Register with duplicate email | POST /auth/register with existing email | 409 Conflict | ⬜ |
| 1.3 | Register with weak password | Password < 8 chars or no uppercase/digit | 400 validation error | ⬜ |
| 1.4 | Login with correct credentials | POST /auth/login with valid email+password | 200, accessToken + refreshToken returned | ⬜ |
| 1.5 | Login with wrong password | POST /auth/login with wrong password | 401, error message shown in UI (not login loop) | ⬜ |
| 1.6 | Login with unknown email | POST /auth/login with non-existent email | 401 | ⬜ |
| 1.7 | Token refresh | Access token expires → app auto-refreshes via /auth/refresh | Seamless, user not logged out | ⬜ |
| 1.8 | Refresh token expired | Both tokens expired | User redirected to Login screen | ⬜ |
| 1.9 | Logout | Tap logout on Profile screen | Tokens cleared, navigated to Login, back-stack cleared | ⬜ |
| 1.10 | Auth interceptor skips /auth/* | POST /auth/login returns 401 | No notifyExpired() called, error message shown in UI | ⬜ |
| 1.11 | Suspended account | Suspended user attempts any protected action | 403, AlertDialog shown with suspension message, redirect to Login | ⬜ |
| 1.12 | Frozen account (AML) | Frozen user makes API call | Request blocked at backend level | ⬜ |

---

## 2. Profile & Account

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 2.1 | View profile | Navigate to Profile screen | Name, email, KYC status, account balance displayed | ⬜ |
| 2.2 | Account balance positive | User has received payments | Balance shown in blue, positive amount | ⬜ |
| 2.3 | Account balance negative | User has sent more than received | Balance shown in red, negative amount | ⬜ |
| 2.4 | Payment history — sent | User sent a payment for a settled expense | Row shows ↑ (red), "To [name]", expense name, date | ⬜ |
| 2.5 | Payment history — received | User received payment as launcher | Row shows ↓ (green), "From [name]", expense name | ⬜ |
| 2.6 | Payment history empty | User has no payments | "PAYMENT HISTORY" section not shown | ⬜ |
| 2.7 | Update profile name | PATCH /me with new name | Profile updated, UI refreshed | ⬜ |
| 2.8 | Export data JSON | Tap export JSON | JSON file with user data returned | ⬜ |
| 2.9 | Delete account | DELETE /me | Account soft-deleted, tokens cleared, logged out | ⬜ |
| 2.10 | Require consent toggle | Toggle switch in profile | PATCH /me/require-consent called, setting persisted | ⬜ |

---

## 3. Groups

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 3.1 | Create group — no members | Tap +, fill name + emoji only | Group created, navigated to GroupDetail | ⬜ |
| 3.2 | Create group — with members | Add contacts during creation | Members invited, group pre-populated in cache | ⬜ |
| 3.3 | Group list | Navigate to Home / Groups | All non-archived groups shown | ⬜ |
| 3.4 | Group detail — empty | Open new group | "No expenses yet — Tap + to create" empty state shown | ⬜ |
| 3.5 | Group detail — with expenses | Group has spaces | Expense cards listed below "EXPENSES" header | ⬜ |
| 3.6 | Edit group name/emoji | Settings → Edit Group → save | Group updated | ⬜ |
| 3.7 | Delete group (admin) | Admin: Settings → Delete Group → confirm | Group deleted, all members notified | ⬜ |
| 3.8 | Delete group (non-admin) | Non-admin opens Settings | Delete option not shown | ⬜ |
| 3.9 | Add member — on SplitPay | Admin: Add → select app contact | 200 → member added; 202 → invitation sent | ⬜ |
| 3.10 | Add member — not on SplitPay | Admin: Add → invitable contact | Share sheet opens to invite via SMS | ⬜ |
| 3.11 | Remove member | Admin taps Remove next to member | Member removed from group | ⬜ |
| 3.12 | Remove member — cannot remove admin | Admin tries to remove another admin | Remove button not shown for admin role | ⬜ |
| 3.13 | Accept group invitation | User taps Accept in Notifications | User added to group, group list refreshed, inviter notified | ⬜ |
| 3.14 | Decline group invitation | User taps Decline | Invitation removed, inviter notified | ⬜ |
| 3.15 | Invalid group ID | GET /groups/not-a-uuid | 400 "Invalid group ID format" | ⬜ |
| 3.16 | Group not found | GET /groups/00000000-… | 404, no server crash | ⬜ |
| 3.17 | Archive / Unarchive group | PATCH /groups/:id/archive then /unarchive | Group disappears/reappears from active list | ⬜ |

---

## 4. Expenses (Spaces)

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 4.1 | Create expense — equally | All participants, divisible amount | Created, all participants invited, status = pending_acceptance | ⬜ |
| 4.2 | Create expense — equally blocked | Amount not divisible by participant count | "equally" chip disabled, error message shown | ⬜ |
| 4.3 | Create expense — exact amounts | Select exact mode, enter per-participant amounts | Created with correct shares | ⬜ |
| 4.4 | Create expense — exact, invalid sum | Shares do not add up to total | Submit button disabled, remaining shown in red | ⬜ |
| 4.5 | Create expense — percentage | Percentages summing to 100% | Created, amounts computed from percentages | ⬜ |
| 4.6 | Create expense — percentage invalid | Percentages don't sum to 100 | Submit blocked, remaining % shown in red | ⬜ |
| 4.7 | Date picker — PLAN mode | Select Deferred (PLAN), tap calendar button | DatePickerDialog opens, date selected and displayed (YYYY-MM-DD) | ⬜ |
| 4.8 | Create expense — PLAN without date | Select PLAN, skip date | Backend returns 400 "Due date required" | ⬜ |
| 4.9 | Create expense — AML blocked | Unverified user (R18) | 403, AML alert created, expense not created | ⬜ |
| 4.10 | Expense card — normal | No action required | Name, category, status chip, amount, member count shown | ⬜ |
| 4.11 | Expense card — response needed | User invited, status = pending_acceptance | Orange accent bar, "🔔 Your response is needed" banner | ⬜ |
| 4.12 | Expense card — quorum required | Settling phase, user in quorum | Blue accent bar, "⚠️ Confirm quorum required" banner | ⬜ |
| 4.13 | Accept expense invitation | SpaceDetail → Accept | Status → accepted, notification sent to creator | ⬜ |
| 4.14 | Decline expense invitation | SpaceDetail → Decline | Status → declined, may trigger auto-cancel | ⬜ |
| 4.15 | Edit expense — name/category | Creator taps pencil icon | Bottom sheet opens pre-filled, save updates expense | ⬜ |
| 4.16 | Edit expense — amount (pending_acceptance) | Edit amount while still in acceptance phase | Amount and shares updated, participants notified | ⬜ |
| 4.17 | Edit expense — amount (active) | Creator tries to change amount on active expense | Amount field not shown (canEditAmountMode = false) | ⬜ |
| 4.18 | Edit expense — settled | Creator calls PATCH on settled space | 409 "Cannot edit a settled or cancelled expense" | ⬜ |
| 4.19 | Delete expense — creator | Tap trash icon → confirm | Expense deleted, all members notified "Expense deleted by [name]" | ⬜ |
| 4.20 | Delete expense — non-creator | Non-creator views expense detail | No trash icon visible | ⬜ |
| 4.21 | Audit log — opens without crash | Tap "AUDIT LOG" on expense detail | Log section expands, no app crash | ⬜ |
| 4.22 | Audit log — entries shown | Expense has event history | Events shown with type, user name, timestamp | ⬜ |
| 4.23 | Audit log — snake_case mapping | Backend returns event_type, created_at | Correctly displayed via @SerializedName (no NPE) | ⬜ |

---

## 5. Split Modes

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 5.1 | Equally valid — €100 / 4 | Amount=100, 4 participants | "equally" enabled | ⬜ |
| 5.2 | Equally invalid — €100 / 3 | Amount=100, 3 participants | "equally" grayed out, error message, auto-switches to "exact" | ⬜ |
| 5.3 | Equally valid — €90 / 3 | Amount=90, 3 participants | "equally" enabled, each share = €30.00 | ⬜ |
| 5.4 | Equally — no amount | Amount field empty | "equally" disabled | ⬜ |
| 5.5 | Equally — auto-switch on participant change | Switch from 4 to 3 participants, amount=100 | "equally" auto-disabled, mode resets to "exact" | ⬜ |
| 5.6 | Equally — 1 participant | Any amount, 1 participant selected | "equally" always valid | ⬜ |
| 5.7 | Exact — valid sum | A: €40, B: €60, total: €100 | Submit enabled, shares stored correctly | ⬜ |
| 5.8 | Exact — remaining indicator | Enter €40 for A of 2, total €100 | "Remaining: €60.00" shown | ⬜ |
| 5.9 | Exact — fully filled (green) | All shares = total | Remaining shows "€0.00" in green | ⬜ |
| 5.10 | Percentage — valid 100% | 50% + 50% | Submit enabled | ⬜ |
| 5.11 | Percentage — over 100% | 60% + 60% | Remaining shows "-20.00%" in red, submit blocked | ⬜ |
| 5.12 | Percentage — backend computes share | €200, participant gets 30% | Share stored as €60.00 | ⬜ |
| 5.13 | Equally constraint — edit sheet | Open edit sheet, change amount to indivisible value | "equally" option disabled in edit sheet | ⬜ |

---

## 6. Settlement & Quorum

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 6.1 | All accept → active | All participants accept | Status → "active" | ⬜ |
| 6.2 | One declines → auto-cancel (FR-053) | Any participant declines | Status → "cancelled", all notified "Expense cancelled" | ⬜ |
| 6.3 | Force launch — creator (FR-054) | Creator force-launches after mixed responses | Only accepted members, shares recalculated | ⬜ |
| 6.4 | Force launch button visibility | Some accepted + some declined | Button "Launch with accepting members only" shown to creator only | ⬜ |
| 6.5 | Trigger settlement | Creator/launcher taps "Trigger settlement" | Status → "settling", quorum designated, participants notified | ⬜ |
| 6.6 | Trigger settlement — non-creator | Non-creator views active expense | Button not shown | ⬜ |
| 6.7 | Quorum size — 2 participants | requiredQuorum(2) | 1 confirmation needed | ⬜ |
| 6.8 | Quorum size — 5 participants | requiredQuorum(5) | 2 confirmations needed | ⬜ |
| 6.9 | Quorum size — 10 participants | requiredQuorum(10) | 4 confirmations needed | ⬜ |
| 6.10 | Confirm quorum | Quorum member taps Confirm | Confirmation recorded; space settles if threshold met | ⬜ |
| 6.11 | Reject quorum | Quorum member taps Reject | Member replaced by another, replacement notified | ⬜ |
| 6.12 | Quorum complete → settled | All required confirmations received | Status → "settled", payments processed | ⬜ |
| 6.13 | Early settle request — PLAN (FR-051) | Launcher taps "Request early settlement" | All accepted members notified to vote | ⬜ |
| 6.14 | Early settle — all vote yes | All members accept | Settlement triggered immediately | ⬜ |
| 6.15 | Early settle — any votes no | Member declines | Early settle cancelled, PLAN continues | ⬜ |
| 6.16 | Assist member (FR-056) | Creator taps "Assist" on a participant during settling | Assist recorded in audit log | ⬜ |
| 6.17 | Transfer launcher role | Creator transfers launcher to another member | New launcher can trigger settlement | ⬜ |

---

## 7. Money Transfer & AML

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 7.1 | Settlement triggers payment | Quorum reached on 2-person expense (€100, equally) | Non-launcher pays €50 to launcher; both balances updated | ⬜ |
| 7.2 | Multiple participants pay | 4-person expense, 3 pay launcher | 3 payments recorded, launcher receives total of 3 shares | ⬜ |
| 7.3 | Launcher skipped | Launcher's own share not transferred | No payment from launcher to themselves in payments table | ⬜ |
| 7.4 | Payment in history | User views Profile after settlement | Payment in "PAYMENT HISTORY" with amount + expense name | ⬜ |
| 7.5 | Participant balance decreases | After settled expense | GET /me returns lower accountBalance | ⬜ |
| 7.6 | Launcher balance increases | After settled expense | Launcher's accountBalance = previous + sum of participant shares | ⬜ |
| 7.7 | Payment recorded in DB | Check payments table after settlement | Row with space_id, from_user, to_user, amount, method='in_app' | ⬜ |
| 7.8 | AML R18 — unverified blocks payment | Participant is not email-verified | Payment blocked, AML alert created, participant notified | ⬜ |
| 7.9 | AML R17 — sanctions + no KYC | User on sanctions, no KYC | BLOCK + freeze + SAR | ⬜ |
| 7.10 | AML R4 — sanctions (verified) | User on OFAC/EU sanctions | BLOCK + freeze + SAR + 72h notify | ⬜ |
| 7.11 | AML R8 — sanctions + high-risk country | Both flags set | BLOCK + freeze + SAR | ⬜ |
| 7.12 | AML R6 — KYC incomplete, amount > €150 | No KYC, create expense > €150 | BLOCK "KYC incomplete — CDD required" | ⬜ |
| 7.13 | AML R5 — KYC incomplete, amount < €150 | No KYC, amount below SDD threshold | ALLOW with active monitoring | ⬜ |
| 7.14 | AML R1 — low risk verified | Verified + KYC + low amount | ALLOW "SDD — verified low-risk profile" | ⬜ |
| 7.15 | AML R2 — verified, CDD range €150–€10k | Verified user, mid-range amount | ALLOW "Standard CDD" | ⬜ |
| 7.16 | AML EDD — amount > €10,000 | Create expense > €10k | BLOCK "exceeds EDD threshold — CTR required" | ⬜ |
| 7.17 | AML R9 — high frequency | ≥10 payments in 24h same user/group | ALERT "Unusual transaction pattern detected" | ⬜ |
| 7.18 | AML R11 — smurfing + unusual | ≥5 payments < €150 in 2h | BLOCK "Smurfing detected + unusual pattern" | ⬜ |
| 7.19 | AML R9 — round amounts | ≥3 payments of round amounts (100, 200…) in window | ALERT triggered | ⬜ |
| 7.20 | AML monthly total via payments | getMonthlyTotal reads payments table | Cumulative monthly spend computed from real payments | ⬜ |
| 7.21 | AML auto-suspend (R-escalation) | User accumulates ≥3 pending AML alerts | aml_status → 'suspended', account frozen, admins notified | ⬜ |
| 7.22 | Admin notified on AML payment block | AML blocks payment during settlement | Admin receives "AML — Payments blocked" FCM notification | ⬜ |
| 7.23 | Atomic payment transaction | DB error mid-transfer | ROLLBACK, no partial balance change, failure logged in audit | ⬜ |
| 7.24 | AML R12 — PEP + smurfing + unusual | All three flags | BLOCK "PEP + smurfing + unusual pattern — maximum risk" + freeze + SAR | ⬜ |
| 7.25 | AML R3 — PEP with KYC | PEP user with approved KYC | EDD "PEP status detected — Enhanced Due Diligence required" | ⬜ |
| 7.26 | AML R7 — high-risk country | KYC-approved user in high-risk country | ALERT "High-risk country — enhanced CDD + source of funds verification" | ⬜ |

---

## 8. Notifications

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 8.1 | FCM token registered | App launches, user logged in | POST /users/fcm-token called | ⬜ |
| 8.2 | Group invitation push | User invited to group | FCM push received, appears in Notifications screen | ⬜ |
| 8.3 | Invitation accepted → inviter notified | Invitee accepts | Inviter gets "accepted your invitation and joined [group]" | ⬜ |
| 8.4 | Invitation declined → inviter notified | Invitee declines | Inviter gets "declined your invitation to join [group]" | ⬜ |
| 8.5 | New member broadcast | User joins group | All group members notified "[name] joined [group]" | ⬜ |
| 8.6 | Expense invitation push | User invited to expense | FCM push, "Response needed" badge in Notifications | ⬜ |
| 8.7 | Expense cancelled notification | Participant declines → auto-cancel | All members notified "Expense cancelled" (in English) | ⬜ |
| 8.8 | Expense deleted notification | Creator deletes expense | All participants notified "has been deleted by [name]" | ⬜ |
| 8.9 | Expense updated notification | Creator edits expense | All participants notified "has been updated by [name]" | ⬜ |
| 8.10 | Settlement triggered notification | Launcher triggers settlement | All participants notified "Settlement in progress" (in English) | ⬜ |
| 8.11 | Quorum required notification | User designated for quorum | Notified "Confirm quorum required" | ⬜ |
| 8.12 | Expense settled notification | Quorum complete | All participants notified "Expense settled!" (in English) | ⬜ |
| 8.13 | Payment processed notification | User's share deducted | Notified "€X.XX has been deducted from your balance" | ⬜ |
| 8.14 | Payment received notification | Launcher receives money | Notified "You received €X.XX for [expense]" | ⬜ |
| 8.15 | Payment blocked notification | AML blocks participant payment | Participant notified "Payment blocked by compliance" | ⬜ |
| 8.16 | Account frozen notification | AML freezes account | Admins notified "Account frozen — AML" | ⬜ |
| 8.17 | Notification badge count | Pending group invitations + pending space actions | Badge = sum of both on Notifications tab icon | ⬜ |
| 8.18 | Stale FCM token cleanup | Token returns 404 from FCM | Token deleted from fcm_tokens table | ⬜ |
| 8.19 | No notifications in French | Any notification fired | All notification titles + bodies in English | ⬜ |

---

## 9. KYC

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 9.1 | View KYC status | Profile → KYC screen | Status shown: none / pending_review / approved / rejected | ⬜ |
| 9.2 | Upload KYC document | Submit base64 document | Document stored, status → pending_review | ⬜ |
| 9.3 | Submit for review | User taps Submit | kyc_status → 'pending_review' | ⬜ |
| 9.4 | Admin sees pending KYC | Admin → KYC tab | Users with status = 'pending_review' listed | ⬜ |
| 9.5 | Admin — view document | Tap eye icon | Document image loads via authenticated URL | ⬜ |
| 9.6 | Admin — approve all | Tap "Approve All" | All user docs approved, kyc_status → 'approved' | ⬜ |
| 9.7 | Admin — reject single | Tap ✕ → enter reason | Document rejected, reason saved | ⬜ |
| 9.8 | Approved user → KYC flag set | KYC-approved user creates expense | A2_kycCompleted = true, CDD rules accessible | ⬜ |
| 9.9 | Unapproved user blocked CDD range | No KYC + amount > €150 | R6 BLOCK triggered | ⬜ |

---

## 10. Admin Dashboard

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 10.1 | Admin access | Admin navigates to Admin screen | Dashboard shown | ⬜ |
| 10.2 | Non-admin access | Non-admin calls /admin/* | 403 Forbidden | ⬜ |
| 10.3 | Stats overview | View dashboard | Total users, groups, expenses, total amount shown | ⬜ |
| 10.4 | Users tab | View Users tab | All users with name, email, KYC, AML status | ⬜ |
| 10.5 | Groups tab | View Groups tab | All groups with member count, total amount | ⬜ |
| 10.6 | Lift suspension | Tap "Lift Suspension" on suspended user | aml_status → 'clear', account_frozen = false | ⬜ |
| 10.7 | AML alert badge | Pending alerts exist | Red badge with count on "Alerts" tab | ⬜ |
| 10.8 | KYC pending badge | Pending KYC docs exist | Red badge with count on "KYC" tab | ⬜ |

---

## 11. AML Alerts

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 11.1 | View pending alerts | Admin → Alerts tab | Pending alerts listed | ⬜ |
| 11.2 | All descriptions in English | View any alert | No French text in description field | ⬜ |
| 11.3 | Alert type chip | Large transaction alert | Chip shows "LARGE TRANSACTION" in amber | ⬜ |
| 11.4 | Clear alert | Admin taps "Clear" | Status → 'cleared' | ⬜ |
| 11.5 | Suspend user from alert | Admin taps "Suspend User" | aml_status → 'suspended', account frozen | ⬜ |
| 11.6 | Filter alerts by status | Toggle pending / cleared / suspended / all | Correct alerts shown per filter | ⬜ |
| 11.7 | SAR created | R4 / R8 / R17 triggered | Row inserted in sar_reports table | ⬜ |
| 11.8 | Auto-suspend on 3 alerts | User reaches autoSuspendAfterAlerts threshold | Auto-suspended, admins notified "AML Auto-suspension" | ⬜ |

---

## 12. GDPR Config

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 12.1 | Edit archive threshold | Admin changes "Archive after N months" | Saved via PUT /admin/gdpr/config | ⬜ |
| 12.2 | Edit large transaction threshold | Change threshold value | preCheck uses updated value | ⬜ |
| 12.3 | Edit high frequency count | Change transaction frequency limit | detectUnusualPattern uses new count | ⬜ |
| 12.4 | Edit auto-suspend threshold | Change N alerts threshold | checkEscalation uses new value | ⬜ |
| 12.5 | Run GDPR cleanup | Admin taps "Run GDPR Cleanup Now" | Cleanup job executed | ⬜ |

---

## 13. Edge Cases & Security

| # | Test Case | Steps | Expected Result | Status |
|---|-----------|-------|-----------------|--------|
| 13.1 | UUID validation — group | GET /groups/not-a-uuid | 400 "Invalid group ID format" | ⬜ |
| 13.2 | UUID validation — space | GET .../spaces/notauuid | 400 "Invalid space ID" | ⬜ |
| 13.3 | Non-creator edits expense | Non-creator calls PATCH /spaces/:id | 403 Forbidden | ⬜ |
| 13.4 | Non-creator deletes expense | Non-creator calls DELETE /spaces/:id | 403 Forbidden | ⬜ |
| 13.5 | Edit settled expense | Creator PATCH on settled space | 409 "Cannot edit a settled or cancelled expense" | ⬜ |
| 13.6 | Wrong invitation ID | User accepts another user's invitation | 403 "Not your invitation" | ⬜ |
| 13.7 | Double-accept invitation | Accept already-accepted invitation | 409 "Invitation already accepted" | ⬜ |
| 13.8 | XSS in expense name | Name = `<script>alert(1)</script>` | Stored and displayed as plain text, no execution | ⬜ |
| 13.9 | Negative amount | Create expense with amount = -100 | 400 validation error | ⬜ |
| 13.10 | Zero participants | Create expense with empty participant list | 400 "At least one participant required" | ⬜ |
| 13.11 | Unauthenticated request | Call any protected endpoint without Bearer token | 401 Unauthorized | ⬜ |
| 13.12 | Atomic payment — DB failure | Simulate error mid-transfer | ROLLBACK, balances unchanged, failure logged in audit | ⬜ |
| 13.13 | Equally — 1 participant | Any amount, 1 person selected | "equally" always enabled (N % 1 = 0) | ⬜ |
| 13.14 | Equally — large divisible: €999 / 3 | 3 participants | "equally" enabled, share = €333.00 | ⬜ |
| 13.15 | Equally — indivisible cents: €10.01 / 3 | 3 participants | "equally" disabled | ⬜ |
| 13.16 | Auto-switch from equally | Select equally → add 3rd participant (€100 indivisible) | Mode auto-switches to "exact" | ⬜ |
| 13.17 | Offline — cached groups | Open Home screen without network | Previously cached groups shown from SharedPreferences | ⬜ |
| 13.18 | Session expiry mid-action | Access token expires during expense creation | Auto-refresh via Authenticator, action retried | ⬜ |
| 13.19 | PLAN expense without due date | POST /spaces without dueDate + settlementMode=PLAN | 400 "Due date required" | ⬜ |
| 13.20 | Cache invalidated on group delete | Creator deletes group | AppCache cleared, group not shown on Home/Groups | ⬜ |
| 13.21 | Payments table — space_id FK | Check payments table after settlement | space_id set correctly, expense_id = null | ⬜ |
| 13.22 | Payment history — max 50 rows | User with > 50 payments | Only latest 50 returned by GET /me/payments | ⬜ |

---

## Test Accounts

| User | Email | Password | Role | KYC | Notes |
|------|-------|----------|------|-----|-------|
| test1 | test1@test.com | Password1 | member | approved | Standard user |
| test2 | test2@test.com | Password1 | member | none | No KYC |
| test3 | test3@test.com | Password1 | member | pending_review | Awaiting review |
| admin | admin@splitpay.com | *(ask team)* | admin | approved | Full admin access |

---

## Backend — Automated Test Suite

| Module | File | Tests |
|--------|------|-------|
| Pure functions (shares, balances, quorum) | `tests/unit/pure-functions.test.js` | 29 |
| AML rules R1–R18 | `tests/unit/aml-rules.test.js` | 34 |
| Auth endpoints | `tests/integration/auth.test.js` | 16 |
| Expense creation & validation | `tests/integration/expenses.test.js` | 20 |
| Space lifecycle | `tests/integration/spaces.test.js` | 26 |

**Run all tests:**
```bash
cd backend-node && node --test tests/**/*.test.js
```

---

## Status Legend

| Symbol | Meaning |
|--------|---------|
| ⬜ | Not tested |
| ✅ | Passed |
| ❌ | Failed |
| ⬬ | Partially passed |
| ⏭️ | Skipped / N/A |

---

*Total manual test cases: **181** across 13 sections*  
*Last updated: 2026-05-21*
