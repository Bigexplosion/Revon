const express = require('express');
const cors = require('cors');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();
const app = express();
const port = 3000;
const JWT_SECRET = 'revon-super-secret-key';

app.use(cors());
app.use(express.json());

// Middleware: Verify JWT
const authenticate = (req, res, next) => {
  const authHeader = req.headers.authorization;
  if (!authHeader) return res.status(401).json({ error: 'No token provided' });

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.userId = decoded.userId;
    next();
  } catch (err) {
    res.status(401).json({ error: 'Invalid token' });
  }
};

// --- Auth Routes ---
app.post('/api/auth/register', async (req, res) => {
  const { email, nickname, password } = req.body;
  try {
    const hashedPassword = await bcrypt.hash(password, 10);
    const user = await prisma.user.create({
      data: { email, nickname, password: hashedPassword }
    });
    const token = jwt.sign({ userId: user.id }, JWT_SECRET);
    res.json({ token, user: { id: user.id, nickname: user.nickname, role: user.role } });
  } catch (e) {
    res.status(400).json({ error: 'User already exists or invalid data' });
  }
});

app.post('/api/auth/login', async (req, res) => {
  const { nickname, password } = req.body;
  const user = await prisma.user.findUnique({ where: { nickname } });
  if (user && await bcrypt.compare(password, user.password)) {
    const token = jwt.sign({ userId: user.id }, JWT_SECRET);
    res.json({ token, user: { id: user.id, nickname: user.nickname, role: user.role } });
  } else {
    res.status(401).json({ error: 'Invalid credentials' });
  }
});

app.get('/api/auth/me', authenticate, async (req, res) => {
  const user = await prisma.user.findUnique({
    where: { id: req.userId },
    include: { clubs: true }
  });
  res.json(user);
});

// --- Tracks & Results ---
app.get('/api/tracks', async (req, res) => {
  const tracks = await prisma.track.findMany();
  res.json(tracks);
});

app.post('/api/race-results', authenticate, async (req, res) => {
  const resultData = req.body;
  try {
    const result = await prisma.raceResult.create({
      data: {
        ...resultData,
        finishTimeMs: BigInt(resultData.finishTimeMs),
        userId: req.userId
      }
    });
    // Serialize BigInt
    res.json({ ...result, finishTimeMs: result.finishTimeMs.toString() });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

app.get('/api/race-results', async (req, res) => {
  const results = await prisma.raceResult.findMany({
    orderBy: { finishTimeMs: 'asc' },
    take: 50
  });
  res.json(results.map(r => ({ ...r, finishTimeMs: r.finishTimeMs.toString() })));
});

// --- Clubs ---
app.get('/api/clubs', async (req, res) => {
  const clubs = await prisma.club.findMany({ include: { members: true } });
  res.json(clubs);
});

app.listen(port, () => {
  console.log(`Revon Backend running at http://localhost:${port}`);
});
