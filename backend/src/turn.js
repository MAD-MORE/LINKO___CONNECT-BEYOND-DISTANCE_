const { createHmac } = require('node:crypto');

const TURN_CREDENTIAL_TTL_SECONDS = 15 * 60;

function parseTurnUrls(value) {
  return (value || '').split(',').map(url => url.trim()).filter(Boolean);
}

function issueTurnCredentials({ sessionId, deviceId, sharedSecret, urls, now = Date.now }) {
  if (!sharedSecret || !urls?.length) return null;
  const expiresAtEpochSeconds = Math.floor(now() / 1000) + TURN_CREDENTIAL_TTL_SECONDS;
  const username = `${expiresAtEpochSeconds}:${deviceId}:${sessionId}`;
  const credential = createHmac('sha1', sharedSecret).update(username).digest('base64');
  return { urls, username, credential, credentialType: 'password', expiresAtEpochSeconds };
}

module.exports = { TURN_CREDENTIAL_TTL_SECONDS, parseTurnUrls, issueTurnCredentials };
