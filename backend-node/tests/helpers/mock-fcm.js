/** Silent FCM mock — records calls without sending real notifications. */

const calls = [];

function notifyUser(userId, title, body, data = {}) {
  calls.push({ target: 'user', userId, title, body, data });
  return Promise.resolve();
}

function notifyGroupMembers(groupId, excludeId, memberIds, title, body) {
  calls.push({ target: 'group', groupId, excludeId, memberIds, title, body });
  return Promise.resolve();
}

function notifyAdmins(title, body, data = {}) {
  calls.push({ target: 'admins', title, body, data });
  return Promise.resolve();
}

function getCalls() { return [...calls]; }
function clearCalls() { calls.length = 0; }

module.exports = { notifyUser, notifyGroupMembers, notifyAdmins, getCalls, clearCalls };
