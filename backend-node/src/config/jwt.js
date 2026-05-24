const jwt = require('jsonwebtoken');

function generateAccessToken(userId, email) {
  return jwt.sign({ sub: userId, email }, process.env.JWT_ACCESS_SECRET, {
    expiresIn: process.env.JWT_ACCESS_EXPIRES || '15m',
  });
}

function generateRefreshToken(userId) {
  return jwt.sign({ sub: userId }, process.env.JWT_REFRESH_SECRET, {
    expiresIn: process.env.JWT_REFRESH_EXPIRES || '30d',
  });
}

function validateRefreshToken(token) {
  try {
    const payload = jwt.verify(token, process.env.JWT_REFRESH_SECRET);
    return payload.sub;
  } catch {
    return null;
  }
}

module.exports = { generateAccessToken, generateRefreshToken, validateRefreshToken };
