const jwt = require('jsonwebtoken');

function authenticate(req, res, next) {
  const header = req.headers.authorization;

  if (!header || !header.startsWith('Bearer ')) {
    console.log(`[AUTH 401] ${req.method} ${req.path} — header ${header ? `"${header.slice(0,20)}..."` : 'MISSING'}`);
    return res.status(401).json({ message: 'Missing or invalid Authorization header' });
  }

  const token = header.slice(7);
  try {
    const payload = jwt.verify(token, process.env.JWT_ACCESS_SECRET);
    req.userId = payload.sub;
    next();
  } catch (err) {
    console.log(`[AUTH 401] ${req.method} ${req.path} — JWT error: ${err.message}`);
    res.status(401).json({ message: 'Invalid or expired token' });
  }
}

module.exports = { authenticate };
