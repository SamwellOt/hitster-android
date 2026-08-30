import test from 'node:test';
import assert from 'node:assert/strict';
import { createGame, apply, tick, viewFor, fits, insertSorted, PHASE, GameError } from '../src/game.js';

// deterministic deck: years 1960, 1962, ... so we always know what is drawn
const cards = Array.from({ length: 40 }, (_, i) => ({ id: 'id' + i, title: 'T' + i, artist: 'A' + i, year: 1960 + i * 2, preview: null }));
const players = [{ id: 'p1', name: 'Ana', color: '#f00' }, { id: 'p2', name: 'Bia', color: '#0f0' }, { id: 'p3', name: 'Caio', color: '#00f' }];
const noShuffle = () => 0; // Math.floor(0 * (i+1)) === 0 → swaps a[i] with a[0]; still deterministic
let clock = 1_000_000;
const now = () => clock;

function fresh(opts = {}) {
  clock = 1_000_000;
  return createGame({ players, cards, options: { challengeSeconds: 10, voteSeconds: 10, resultSeconds: 5, ...opts }, rng: () => 0.999999, now });
}
// rng≈1 keeps the original order (j === i every time)

test('setup: 2 tokens and one starting card each, first player active', () => {
  const g = fresh();
  assert.equal(g.players.length, 3);
  for (const p of g.players) { assert.equal(p.tokens, 2); assert.equal(p.timeline.length, 1); }
  assert.equal(g.turn.playerId, 'p1');
  assert.equal(g.turn.phase, PHASE.LISTEN);
  assert.equal(g.players[0].timeline[0].year, 1960);
  assert.equal(g.players[1].timeline[0].year, 1962);
  assert.equal(g.players[2].timeline[0].year, 1964);
  assert.equal(g.turn.card.year, 1966);
});

test('fits / insertSorted', () => {
  const tl = [{ year: 1970 }, { year: 1990 }];
  assert.equal(fits(tl, 0, 1960), true);
  assert.equal(fits(tl, 0, 1975), false);
  assert.equal(fits(tl, 1, 1975), true);
  assert.equal(fits(tl, 2, 1995), true);
  assert.equal(fits(tl, 1, 1990), true, 'same year is fine on either side');
  assert.equal(fits(tl, 2, 1990), true);
  const t2 = [{ year: 1970 }, { year: 1990 }];
  assert.equal(insertSorted(t2, { year: 1980 }), 1);
  assert.deepEqual(t2.map(c => c.year), [1970, 1980, 1990]);
});

test('hidden card: opponents see nothing, active player sees only id', () => {
  const g = fresh();
  assert.equal(viewFor(g, 'p2').turn.card, null);
  assert.deepEqual(Object.keys(viewFor(g, 'p1').turn.card).sort(), ['id', 'preview']);
});

test('correct placement keeps the card; wrong placement discards it', () => {
  const g = fresh();
  // p1 has 1960, card is 1966 → slot 1 is right
  apply(g, 'p1', { type: 'place', slot: 1 }, now);
  assert.equal(g.turn.phase, PHASE.CHALLENGE);
  apply(g, 'p2', { type: 'pass' }, now);
  apply(g, 'p3', { type: 'pass' }, now);
  assert.equal(g.turn.phase, PHASE.RESULT);
  assert.equal(g.turn.result.correct, true);
  assert.equal(g.players[0].timeline.length, 2);
  apply(g, 'p1', { type: 'continue' }, now);
  assert.equal(g.turn.playerId, 'p2');
  // p2 has 1962, card is 1968 → slot 0 is wrong
  apply(g, 'p2', { type: 'place', slot: 0 }, now);
  clock += 11_000; tick(g, now); // nobody answers → deadline reveals
  assert.equal(g.turn.phase, PHASE.RESULT);
  assert.equal(g.turn.result.correct, false);
  assert.equal(g.players[1].timeline.length, 1);
  assert.equal(g.discard.length, 1);
});

test('skip costs a token and puts the card at the bottom', () => {
  const g = fresh();
  const first = g.turn.card;
  apply(g, 'p1', { type: 'skip' }, now);
  assert.equal(g.players[0].tokens, 1);
  assert.notEqual(g.turn.card.id, first.id);
  assert.equal(g.deck[g.deck.length - 1].id, first.id);
  apply(g, 'p1', { type: 'skip' }, now);
  assert.throws(() => apply(g, 'p1', { type: 'skip' }, now), GameError);
});

test('HITSTER challenge steals the card when the owner is wrong and the challenger is right', () => {
  const g = fresh();
  apply(g, 'p1', { type: 'place', slot: 0 }, now); // wrong (1966 after 1960)
  assert.throws(() => apply(g, 'p1', { type: 'challenge' }, now), /si mesmo/);
  apply(g, 'p2', { type: 'challenge' }, now);       // p2 shouts first
  assert.equal(g.players[1].tokens, 1);
  assert.throws(() => apply(g, 'p2', { type: 'challenge' }, now), /já desafiou/i);
  apply(g, 'p3', { type: 'challenge' }, now);       // p3 also bets – spends a token, but p2 was first
  assert.equal(g.players[2].tokens, 1);
  assert.equal(g.turn.phase, PHASE.RESULT);
  assert.equal(g.turn.result.correct, false);
  assert.equal(g.turn.result.stolenBy, 'p2');
  assert.ok(g.turn.result.challenges.every(c => c.correct === true));
  assert.deepEqual(g.players[1].timeline.map(c => c.year), [1962, 1966]);
  assert.equal(g.players[0].timeline.length, 1);
  assert.equal(g.players[2].timeline.length, 1);
});

test('challenge token is lost when the owner was right', () => {
  const g = fresh();
  apply(g, 'p1', { type: 'place', slot: 1 }, now);
  apply(g, 'p2', { type: 'challenge' }, now);
  apply(g, 'p3', { type: 'pass' }, now);
  assert.equal(g.turn.result.correct, true);
  assert.equal(g.turn.result.stolenBy, null);
  assert.equal(g.turn.result.challenges[0].correct, false);
  assert.equal(g.players[1].tokens, 1);
  assert.equal(g.players[0].timeline.length, 2);
});

test('naming title+artist earns a token via opponents vote (max 5)', () => {
  const g = fresh();
  apply(g, 'p1', { type: 'place', slot: 0, claimsTitle: true }, now); // wrong placement, still may earn
  apply(g, 'p2', { type: 'pass' }, now); apply(g, 'p3', { type: 'pass' }, now);
  assert.equal(g.turn.phase, PHASE.VOTE);
  assert.ok(viewFor(g, 'p2').turn.card.title, 'card is revealed during the vote');
  apply(g, 'p2', { type: 'vote', value: true }, now);
  apply(g, 'p3', { type: 'vote', value: false }, now);
  assert.equal(g.turn.phase, PHASE.RESULT);
  assert.equal(g.turn.result.tokenEarned, true, 'tie favours the player');
  assert.equal(g.players[0].tokens, 3);
});

test('3 tokens buy the top card, placed correctly, at any time', () => {
  const g = fresh();
  g.players[2].tokens = 5;
  const top = g.deck[0];
  apply(g, 'p3', { type: 'buyCard' }, now); // p1's turn, p3 buys
  assert.equal(g.players[2].tokens, 2);
  assert.equal(g.players[2].timeline.length, 2);
  assert.ok(g.players[2].timeline.some(c => c.id === top.id));
  assert.throws(() => apply(g, 'p3', { type: 'buyCard' }, now), /3 fichas/);
});

test('first to 10 cards wins', () => {
  const g = fresh({ cardsToWin: 3 });
  apply(g, 'p1', { type: 'place', slot: 1 }, now);
  apply(g, 'p2', { type: 'pass' }, now); apply(g, 'p3', { type: 'pass' }, now);
  apply(g, 'p1', { type: 'continue' }, now);
  apply(g, 'p2', { type: 'place', slot: 1 }, now); apply(g, 'p1', { type: 'pass' }, now); apply(g, 'p3', { type: 'pass' }, now);
  apply(g, 'p2', { type: 'continue' }, now);
  apply(g, 'p3', { type: 'place', slot: 1 }, now); apply(g, 'p1', { type: 'pass' }, now); apply(g, 'p2', { type: 'pass' }, now);
  apply(g, 'p3', { type: 'continue' }, now);
  assert.equal(g.round, 2);
  apply(g, 'p1', { type: 'place', slot: 2 }, now); apply(g, 'p2', { type: 'pass' }, now); apply(g, 'p3', { type: 'pass' }, now);
  assert.equal(g.finished, true);
  assert.equal(g.winnerId, 'p1');
  assert.throws(() => apply(g, 'p1', { type: 'continue' }, now), /terminou/);
});

test('result phase auto-advances on tick', () => {
  const g = fresh();
  apply(g, 'p1', { type: 'place', slot: 1 }, now);
  apply(g, 'p2', { type: 'pass' }, now); apply(g, 'p3', { type: 'pass' }, now);
  clock += 6_000;
  const ev = tick(g, now);
  assert.equal(g.turn.playerId, 'p2');
  assert.ok(ev.some(e => e.kind === 'turn'));
});
