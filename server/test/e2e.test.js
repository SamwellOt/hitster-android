// End‑to‑end: boots the real server with a fixture catalog and drives 3 WebSocket clients through a game.
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import WebSocket from 'ws';

const PORT = 18080 + Math.floor(Math.random() * 1000);

function fixtureCatalog() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'hitster-cat-'));
  const cards = Array.from({ length: 60 }, (_, i) => ({ n: i + 1, id: 'trk' + i, title: 'Song ' + i, artist: 'Artist ' + i, year: 1960 + i, preview: null }));
  fs.writeFileSync(path.join(dir, 'aaaq0001.json'), JSON.stringify({ sku: 'aaaq0001', name: 'Fixture', cards }));
  return dir;
}

class Client {
  constructor(name) { this.name = name; this.room = null; this.events = []; this.errors = []; this.playerId = null; }
  async connect() {
    this.ws = new WebSocket(`ws://127.0.0.1:${PORT}`);
    this.ws.on('message', raw => {
      const m = JSON.parse(raw);
      if (m.type === 'room') this.room = m.room;
      if (m.type === 'joined') this.playerId = m.playerId;
      if (m.type === 'events') this.events.push(...m.events);
      if (m.type === 'error') this.errors.push(m.message);
    });
    await new Promise((res, rej) => { this.ws.once('open', res); this.ws.once('error', rej); });
  }
  send(msg) { this.ws.send(JSON.stringify(msg)); }
  action(action) { this.send({ type: 'action', action }); }
  async until(pred, ms = 3000) {
    const t0 = Date.now();
    while (Date.now() - t0 < ms) { if (pred(this)) return; await new Promise(r => setTimeout(r, 20)); }
    throw new Error(`${this.name}: condition not met; errors=${JSON.stringify(this.errors)} phase=${this.room?.game?.turn?.phase}`);
  }
  get game() { return this.room.game; }
  get me() { return this.game.players.find(p => p.id === this.playerId); }
  close() { this.ws.close(); }
}

test('full session over websockets', async t => {
  const catalog = fixtureCatalog();
  const server = spawn(process.execPath, [path.join(import.meta.dirname, '..', 'src', 'index.js')], {
    env: { ...process.env, PORT: String(PORT), CATALOG_DIR: catalog }, stdio: ['ignore', 'pipe', 'inherit'],
  });
  await new Promise(res => server.stdout.on('data', d => { if (String(d).includes('listening')) res(); }));
  t.after(() => server.kill());

  const [a, b, c] = [new Client('A'), new Client('B'), new Client('C')];
  await Promise.all([a.connect(), b.connect(), c.connect()]);

  a.send({ type: 'create', name: 'Ana', color: '#f00', playerId: 'ana-1' });
  await a.until(x => x.room && x.room.code);
  const code = a.room.code;
  assert.equal(code.length, 4);

  b.send({ type: 'join', code, name: 'Bia', color: '#0f0', playerId: 'bia-1' });
  c.send({ type: 'join', code: code.toLowerCase(), name: 'Caio', color: '#00f', playerId: 'caio-1' });
  await a.until(x => x.room.players.length === 3);

  // non‑host cannot start / set decks
  b.send({ type: 'start' });
  await b.until(x => x.errors.length === 1);
  assert.match(b.errors[0], /anfitrião/);

  a.send({ type: 'setOptions', options: { challengeSeconds: 5, voteSeconds: 10, resultSeconds: 5, cardsToWin: 3 } });
  await a.until(x => x.room.options.challengeSeconds === 5);
  a.send({ type: 'start' });
  await Promise.all([a, b, c].map(x => x.until(y => y.room.phase === 'playing' && y.game)));

  // hidden card semantics
  const turnOf = a.game.turn.playerId;
  const current = [a, b, c].find(x => x.playerId === turnOf);
  const others = [a, b, c].filter(x => x !== current);
  assert.ok(current.game.turn.card.id, 'current player sees the track id');
  assert.equal(current.game.turn.card.title, undefined, 'title hidden from the current player');
  for (const o of others) assert.equal(o.game.turn.card, null, 'opponents see no card');

  // place correctly using knowledge of the deck (fixture years are unique and known: year = 1960 + idx)
  const year = 1960 + Number(current.game.turn.card.id.slice(3));
  const tl = current.me.timeline;
  let slot = 0; while (slot < tl.length && tl[slot].year <= year) slot++;
  current.action({ type: 'place', slot });
  await Promise.all(others.map(o => o.until(x => x.game.turn.phase === 'challenge')));

  // one opponent challenges a (necessarily wrong) different slot, the other passes
  const wrongSlot = slot === 0 ? 1 : 0;
  others[0].action({ type: 'challenge', slot: wrongSlot });
  others[1].action({ type: 'pass' });
  await current.until(x => x.game.turn.phase === 'vote');
  assert.equal(current.game.turn.card.title, 'Song ' + current.game.turn.card.id.slice(3), 'revealed during vote');
  assert.equal(others[0].me.tokens, 1, 'challenger paid a token');

  others[0].action({ type: 'vote', value: true });
  others[1].action({ type: 'vote', value: true });
  await current.until(x => x.game.turn.phase === 'result');
  assert.equal(current.game.turn.result.correct, true);
  assert.equal(current.game.turn.result.tokenEarned, true);
  assert.equal(current.me.tokens, 3);
  assert.equal(current.me.timeline.length, 2);

  // auto‑advance after resultSeconds
  await a.until(x => x.game.turn.phase === 'listen' && x.game.turn.playerId !== turnOf, 8000);

  // reconnect keeps identity and state
  const rc = others[1];
  rc.close();
  const rc2 = new Client('R'); await rc2.connect();
  rc2.send({ type: 'join', code, playerId: rc.playerId, name: 'Caio2' });
  await rc2.until(x => x.room && x.game);
  assert.equal(rc2.playerId, rc.playerId);
  assert.equal(rc2.room.players.length, 3);

  // 3 tokens → card at any time
  const buyer = [a, b, rc2].find(x => x.me.tokens >= 3) ?? null;
  if (buyer) {
    const before = buyer.me.timeline.length;
    buyer.action({ type: 'buyCard' });
    await buyer.until(x => x.me.timeline.length === before + 1);
    assert.equal(buyer.me.tokens, 0);
  }

  a.close(); b.close(); rc2.close();
  fs.rmSync(catalog, { recursive: true, force: true });
});
