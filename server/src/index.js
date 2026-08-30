// Hitster Mobile – sync server.
// One WebSocket room per game session. The server is the single source of truth for game state;
// phones only send intents ("place", "challenge", ...) and render the snapshots they receive.
//
//   npm start            -> ws://0.0.0.0:8080
//   PORT=9000 npm start
//
// Protocol (JSON text frames)
//   client → server
//     {type:'create', name, color, playerId?}                → room created, you are host
//     {type:'join',   code, name, color, playerId?}          → join lobby (or rejoin a running game)
//     {type:'setDecks', decks:['aaaq0001', ...]}            → host only
//     {type:'setOptions', options:{challengeSeconds,...}}    → host only
//     {type:'start'}                                         → host only
//     {type:'action', action:{type:'place', slot:2, claimsTitle:true}} ... (see game.js)
//     {type:'leave'} / {type:'ping'}
//   server → client
//     {type:'joined', roomCode, playerId, isHost}
//     {type:'room', room:{code, hostId, phase:'lobby'|'playing'|'finished', players:[...], decks, options, game?}}
//     {type:'events', events:[...]}                          → transient toasts/animations
//     {type:'error', message}
//     {type:'pong'}

import { WebSocketServer } from 'ws';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import crypto from 'node:crypto';
import { createGame, apply, tick, viewFor, removePlayer, GameError } from './game.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8080);
const CATALOG_DIR = process.env.CATALOG_DIR || path.join(__dirname, '..', '..', 'catalog');

// ---------------------------------------------------------------- catalog
const decks = new Map();
for (const f of fs.readdirSync(CATALOG_DIR)) {
  if (!f.endsWith('.json') || f.startsWith('_')) continue;
  const d = JSON.parse(fs.readFileSync(path.join(CATALOG_DIR, f), 'utf8'));
  if (!d.sku || !Array.isArray(d.cards)) continue;
  decks.set(d.sku, d);
  console.log(`deck ${d.sku} "${d.name}" – ${d.cards.length} cards`);
}
const deckSummaries = () => [...decks.values()].map(d => ({ sku: d.sku, name: d.name, subtitle: d.subtitle, count: d.cards.length }));

// ---------------------------------------------------------------- rooms
const rooms = new Map();
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function newCode() {
  let code;
  do { code = Array.from({ length: 4 }, () => ALPHABET[crypto.randomInt(ALPHABET.length)]).join(''); } while (rooms.has(code));
  return code;
}

class Room {
  constructor(code) {
    this.code = code;
    this.hostId = null;
    this.phase = 'lobby';
    this.players = new Map(); // playerId -> {id, name, color, ws, connected}
    this.decks = decks.has('aaaq0001') ? ['aaaq0001'] : [deckSummaries()[0]?.sku].filter(Boolean);
    this.options = { challengeSeconds: 12, voteSeconds: 25, resultSeconds: 15, cardsToWin: 10 };
    this.game = null;
    this.timer = null;
    this.lastActivity = Date.now();
  }

  publicPlayers() {
    return [...this.players.values()].map(p => ({ id: p.id, name: p.name, color: p.color, connected: p.connected }));
  }

  snapshot(forPlayerId) {
    return {
      code: this.code,
      hostId: this.hostId,
      phase: this.phase,
      players: this.publicPlayers(),
      decks: this.decks,
      availableDecks: deckSummaries(),
      options: this.options,
      game: this.game ? viewFor(this.game, forPlayerId) : null,
    };
  }

  broadcast(events = []) {
    for (const p of this.players.values()) {
      if (!p.connected) continue;
      send(p.ws, { type: 'room', room: this.snapshot(p.id) });
      if (events.length) send(p.ws, { type: 'events', events });
    }
    this.armTimer();
  }

  armTimer() {
    clearTimeout(this.timer);
    this.timer = null;
    const t = this.game?.turn;
    if (!this.game || this.game.finished || !t?.deadline) return;
    const delay = Math.max(0, t.deadline - Date.now()) + 50;
    this.timer = setTimeout(() => {
      const events = tick(this.game);
      if (this.game.finished) this.phase = 'finished';
      this.broadcast(events);
    }, delay);
  }

  start(byId) {
    if (byId !== this.hostId) throw new GameError('Só o anfitrião pode iniciar.');
    if (this.phase === 'playing') throw new GameError('O jogo já começou.');
    const cards = [];
    const seen = new Set();
    for (const sku of this.decks) {
      for (const c of decks.get(sku)?.cards ?? []) {
        if (!c.year || seen.has(c.id)) continue;
        seen.add(c.id);
        cards.push({ ...c, deck: sku });
      }
    }
    if (cards.length === 0) throw new GameError('Nenhum baralho selecionado.');
    const players = [...this.players.values()].map(p => ({ id: p.id, name: p.name, color: p.color }));
    this.game = createGame({ players, cards, options: this.options });
    this.phase = 'playing';
    this.broadcast([{ kind: 'started' }, { kind: 'turn', playerId: this.game.turn.playerId }]);
  }

  action(playerId, action) {
    if (this.phase !== 'playing') throw new GameError('O jogo não está em andamento.');
    const events = apply(this.game, playerId, action);
    if (this.game.finished) this.phase = 'finished';
    this.broadcast(events);
  }

  leave(playerId) {
    const p = this.players.get(playerId);
    if (!p) return;
    this.players.delete(playerId);
    if (this.game && !this.game.finished) {
      removePlayer(this.game, playerId);
      if (this.game.finished) this.phase = 'finished';
    }
    if (this.hostId === playerId) this.hostId = this.players.keys().next().value ?? null;
    if (this.players.size === 0) { clearTimeout(this.timer); rooms.delete(this.code); return; }
    this.broadcast([{ kind: 'left', playerId, name: p.name }]);
  }
}

// ---------------------------------------------------------------- transport
function send(ws, msg) {
  if (ws && ws.readyState === ws.OPEN) ws.send(JSON.stringify(msg));
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: true, rooms: rooms.size, decks: deckSummaries() }));
    return;
  }
  res.writeHead(200, { 'content-type': 'text/plain' });
  res.end('Hitster Mobile sync server. Connect with a WebSocket client.');
});

const wss = new WebSocketServer({ server });

wss.on('connection', ws => {
  let room = null;
  let playerId = null;
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', raw => {
    let msg;
    try { msg = JSON.parse(raw); } catch { return send(ws, { type: 'error', message: 'JSON inválido.' }); }
    try {
      switch (msg.type) {
        case 'ping': return send(ws, { type: 'pong', now: Date.now() });

        case 'create': {
          const r = new Room(newCode());
          rooms.set(r.code, r);
          room = r;
          playerId = msg.playerId || crypto.randomUUID();
          r.hostId = playerId;
          r.players.set(playerId, { id: playerId, name: cleanName(msg.name), color: msg.color || '#ff2d8f', ws, connected: true });
          send(ws, { type: 'joined', roomCode: r.code, playerId, isHost: true });
          r.broadcast();
          return;
        }

        case 'join': {
          const r = rooms.get(String(msg.code || '').toUpperCase().trim());
          if (!r) throw new GameError('Sessão não encontrada. Confira o código.');
          const existing = msg.playerId && r.players.get(msg.playerId);
          if (existing) {
            // reconnect
            if (existing.ws && existing.ws !== ws && existing.ws.readyState === ws.OPEN) existing.ws.close();
            existing.ws = ws; existing.connected = true;
            if (msg.name) existing.name = cleanName(msg.name);
            room = r; playerId = existing.id;
            send(ws, { type: 'joined', roomCode: r.code, playerId, isHost: r.hostId === playerId });
            r.broadcast([{ kind: 'reconnected', playerId, name: existing.name }]);
            return;
          }
          if (r.phase !== 'lobby') throw new GameError('Essa partida já começou.');
          if (r.players.size >= 10) throw new GameError('A sessão está cheia (máx. 10).');
          room = r;
          playerId = msg.playerId || crypto.randomUUID();
          r.players.set(playerId, { id: playerId, name: cleanName(msg.name), color: msg.color || '#00e5ff', ws, connected: true });
          send(ws, { type: 'joined', roomCode: r.code, playerId, isHost: false });
          r.broadcast([{ kind: 'joined', playerId, name: cleanName(msg.name) }]);
          return;
        }

        case 'setDecks': {
          requireHost(room, playerId);
          const chosen = (msg.decks || []).filter(s => decks.has(s));
          if (chosen.length === 0) throw new GameError('Escolha pelo menos um baralho.');
          room.decks = chosen;
          return room.broadcast();
        }

        case 'setOptions': {
          requireHost(room, playerId);
          const o = msg.options || {};
          const clamp = (v, lo, hi, d) => (Number.isFinite(+v) ? Math.min(hi, Math.max(lo, +v)) : d);
          room.options = {
            challengeSeconds: clamp(o.challengeSeconds, 5, 60, room.options.challengeSeconds),
            voteSeconds: clamp(o.voteSeconds, 10, 60, room.options.voteSeconds),
            resultSeconds: clamp(o.resultSeconds, 5, 60, room.options.resultSeconds),
            cardsToWin: clamp(o.cardsToWin, 5, 20, room.options.cardsToWin),
          };
          return room.broadcast();
        }

        case 'kick': {
          requireHost(room, playerId);
          if (msg.playerId && msg.playerId !== playerId) {
            const target = room.players.get(msg.playerId);
            if (target) { send(target.ws, { type: 'kicked' }); room.leave(msg.playerId); }
          }
          return;
        }

        case 'start': return requireRoom(room).start(playerId);

        case 'restart': {
          requireHost(room, playerId);
          room.game = null; room.phase = 'lobby';
          return room.broadcast([{ kind: 'lobby' }]);
        }

        case 'action': return requireRoom(room).action(playerId, msg.action || {});

        case 'leave': {
          if (room) room.leave(playerId);
          room = null; playerId = null;
          return;
        }

        default: throw new GameError('Mensagem desconhecida: ' + msg.type);
      }
    } catch (e) {
      if (e instanceof GameError) return send(ws, { type: 'error', message: e.message });
      console.error(e);
      send(ws, { type: 'error', message: 'Erro interno do servidor.' });
    }
  });

  ws.on('close', () => {
    if (!room || !playerId) return;
    const p = room.players.get(playerId);
    if (p && p.ws === ws) {
      p.connected = false;
      if (room.phase === 'lobby' && room.hostId !== playerId) room.leave(playerId);
      else room.broadcast([{ kind: 'disconnected', playerId, name: p.name }]);
    }
  });
});

function requireRoom(room) { if (!room) throw new GameError('Você não está em uma sessão.'); return room; }
function requireHost(room, playerId) { requireRoom(room); if (room.hostId !== playerId) throw new GameError('Só o anfitrião pode fazer isso.'); }
function cleanName(n) { const s = String(n || '').trim().slice(0, 18); return s || 'Jogador'; }

// heartbeat + garbage collection of dead rooms
setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.isAlive) { ws.terminate(); continue; }
    ws.isAlive = false; ws.ping();
  }
  const cutoff = Date.now() - 6 * 3600 * 1000;
  for (const [code, r] of rooms) {
    const anyConnected = [...r.players.values()].some(p => p.connected);
    if (!anyConnected && r.lastActivity < cutoff) { clearTimeout(r.timer); rooms.delete(code); }
    if (anyConnected) r.lastActivity = Date.now();
  }
}, 30_000);

server.listen(PORT, () => console.log(`Hitster sync server listening on :${PORT} (catalog: ${CATALOG_DIR})`));
