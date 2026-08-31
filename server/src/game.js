// Pure, deterministic Hitster game logic. No I/O here – index.js wires this to WebSockets and timers.
//
// Rules implemented (manual pages 1‑2):
//  • Each player starts with 2 HITSTER tokens and 1 face‑up card in their timeline.
//  • On your turn: listen to the song, place the card in your timeline, then reveal.
//    Correct → the card stays. Wrong → discarded (unless an opponent challenged correctly).
//  • Token use 1 – on your turn: pay 1 token to skip the song (card goes to the bottom of the pile).
//  • Token use 2 – on an opponent's turn: shout HITSTER *before the reveal*, pay 1 token, pick another
//    position in the opponent's timeline. If the opponent is wrong and you are right, you steal the card.
//    Only one token per position; the first to shout places first.
//  • Token use 3 – any time: trade 3 tokens for the top card of the pile, placed correctly in your timeline.
//  • Earn a token on your turn by naming title + artist correctly (even if the placement was wrong). Max 5.
//  • First player with 10 correctly placed cards wins.

const MAX_TOKENS = 5;
const CARDS_TO_WIN = 10;
const START_TOKENS = 2;

export const PHASE = {
  LISTEN: 'listen',       // current player listens & chooses a slot
  CHALLENGE: 'challenge', // slot locked; opponents may shout HITSTER until the deadline / all pass
  VOTE: 'vote',           // card revealed; opponents vote whether title+artist were named correctly
  RESULT: 'result',       // round summary; anyone may continue (or auto‑advance)
};

export class GameError extends Error {}

export function shuffle(arr, rng = Math.random) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/** True when `year` can sit at `slot` (0..timeline.length). Equal years may sit on either side. */
export function fits(timeline, slot, year) {
  const left = slot > 0 ? timeline[slot - 1].year : -Infinity;
  const right = slot < timeline.length ? timeline[slot].year : Infinity;
  return left <= year && year <= right;
}

/** Insert `card` in chronological order (used for stolen and bought cards). */
export function insertSorted(timeline, card) {
  let i = 0;
  while (i < timeline.length && timeline[i].year <= card.year) i++;
  timeline.splice(i, 0, card);
  return i;
}

export function createGame({ players, cards, options = {}, rng = Math.random, now = Date.now }) {
  if (players.length < 2) throw new GameError('São necessários pelo menos 2 jogadores.');
  const deck = shuffle(cards, rng);
  const needed = players.length; // one starting card each
  if (deck.length < needed + 10) throw new GameError('Baralho pequeno demais.');
  const state = {
    options: {
      challengeSeconds: options.challengeSeconds ?? 12,
      voteSeconds: options.voteSeconds ?? 25,
      resultSeconds: options.resultSeconds ?? 15,
      cardsToWin: options.cardsToWin ?? CARDS_TO_WIN,
      startTokens: options.startTokens ?? START_TOKENS,
    },
    deck,
    discard: [],
    players: players.map(p => ({
      id: p.id,
      name: p.name,
      color: p.color,
      tokens: options.startTokens ?? START_TOKENS,
      timeline: [deck.shift()],
    })),
    order: players.map(p => p.id),
    turnIndex: 0,
    round: 1,
    turn: null,
    winnerId: null,
    finished: false,
    log: [],
  };
  startTurn(state, now);
  return state;
}

function player(state, id) {
  const p = state.players.find(p => p.id === id);
  if (!p) throw new GameError('Jogador desconhecido.');
  return p;
}

function drawCard(state) {
  if (state.deck.length === 0) {
    if (state.discard.length === 0) return null;
    state.deck = shuffle(state.discard);
    state.discard = [];
  }
  return state.deck.shift();
}

function log(state, entry) {
  state.log.push({ round: state.round, ...entry });
  if (state.log.length > 60) state.log.shift();
}

function startTurn(state, now) {
  const card = drawCard(state);
  // Deck (and discard) exhausted: whoever has the longest timeline wins, like finishByCount().
  if (!card) return finish(state, state.players.reduce((a, b) => (b.timeline.length > a.timeline.length ? b : a), state.players[0])?.id ?? null);
  state.turn = {
    playerId: state.order[state.turnIndex],
    card,
    phase: PHASE.LISTEN,
    slot: null,
    challenges: [],   // [{playerId, slot}] in shout order
    passed: [],       // opponents who declined to challenge
    votes: {},        // playerId -> boolean
    deadline: null,   // epoch ms for the current phase (null = no timer)
    result: null,
    skips: 0,
  };
}

function finish(state, winnerId) {
  state.finished = true;
  state.winnerId = winnerId;
  state.turn = state.turn ? { ...state.turn, phase: PHASE.RESULT, deadline: null } : null;
}

function opponents(state) {
  return state.players.filter(p => p.id !== state.turn.playerId);
}

/**
 * Apply a player action. Mutates `state`; returns a list of events for the UI.
 * Throws GameError with a user‑facing (pt‑BR) message on illegal moves.
 */
export function apply(state, playerId, action, now = Date.now) {
  if (state.finished) throw new GameError('O jogo já terminou.');
  const t = state.turn;
  const me = player(state, playerId);
  const isCurrent = t.playerId === playerId;
  const events = [];

  switch (action.type) {
    case 'skip': {
      if (!isCurrent || t.phase !== PHASE.LISTEN) throw new GameError('Só é possível pular na sua vez, antes de posicionar.');
      if (me.tokens < 1) throw new GameError('Você precisa de 1 ficha HITSTER para pular a música.');
      me.tokens -= 1;
      state.deck.push(t.card); // unused card goes to the bottom of the pile
      const next = drawCard(state);
      if (!next) return finishByCount(state, events);
      t.card = next;
      t.skips += 1;
      log(state, { kind: 'skip', playerId });
      events.push({ kind: 'skip', playerId });
      return events;
    }

    case 'place': {
      if (!isCurrent || t.phase !== PHASE.LISTEN) throw new GameError('Não é sua vez de posicionar.');
      const slot = Number(action.slot);
      if (!Number.isInteger(slot) || slot < 0 || slot > me.timeline.length) throw new GameError('Posição inválida.');
      t.slot = slot;
      t.phase = PHASE.CHALLENGE;
      t.deadline = now() + state.options.challengeSeconds * 1000;
      log(state, { kind: 'place', playerId, slot });
      events.push({ kind: 'placed', playerId, slot });
      return events;
    }

    case 'challenge': {
      if (isCurrent) throw new GameError('Você não pode desafiar a si mesmo.');
      if (t.phase !== PHASE.CHALLENGE) throw new GameError('O desafio só vale antes da carta ser revelada.');
      if (me.tokens < 1) throw new GameError('Você precisa de 1 ficha HITSTER para desafiar.');
      if (t.challenges.some(c => c.playerId === playerId)) throw new GameError('Você já desafiou nesta rodada.');
      // A bet that the active player is wrong; the token is spent either way.
      me.tokens -= 1;
      t.challenges.push({ playerId });
      t.passed = t.passed.filter(id => id !== playerId);
      log(state, { kind: 'challenge', playerId });
      events.push({ kind: 'challenge', playerId });
      if (everyoneDecided(state)) reveal(state, events, now);
      return events;
    }

    case 'pass': {
      if (isCurrent || t.phase !== PHASE.CHALLENGE) throw new GameError('Ação indisponível.');
      if (!t.passed.includes(playerId) && !t.challenges.some(c => c.playerId === playerId)) t.passed.push(playerId);
      if (everyoneDecided(state)) reveal(state, events, now);
      return events;
    }

    case 'vote': {
      if (isCurrent || t.phase !== PHASE.VOTE) throw new GameError('Ação indisponível.');
      t.votes[playerId] = !!action.value;
      if (opponents(state).every(p => p.id in t.votes)) resolveVote(state, events, now);
      return events;
    }

    case 'buyCard': {
      // The bought card is inserted in the buyer's timeline, so it must not move under a placement
      // that is already locked in and about to be judged against t.slot.
      if (isCurrent && t.phase === PHASE.CHALLENGE) throw new GameError('Espere a revelação da sua carta para trocar fichas.');
      if (me.tokens < 3) throw new GameError('Você precisa de 3 fichas HITSTER.');
      const card = drawCard(state);
      if (!card) throw new GameError('O baralho acabou.');
      me.tokens -= 3;
      const idx = insertSorted(me.timeline, card);
      log(state, { kind: 'buy', playerId, card });
      events.push({ kind: 'bought', playerId, card, index: idx });
      if (checkWin(state)) events.push({ kind: 'finished', winnerId: state.winnerId });
      return events;
    }

    case 'continue': {
      if (t.phase !== PHASE.RESULT) throw new GameError('Ação indisponível.');
      nextTurn(state, events, now);
      return events;
    }

    default:
      throw new GameError('Ação desconhecida: ' + action.type);
  }
}

/** Called by the host loop when `state.turn.deadline` has passed. */
export function tick(state, now = Date.now) {
  const events = [];
  const t = state.turn;
  if (!t || state.finished || !t.deadline || now() < t.deadline) return events;
  if (t.phase === PHASE.CHALLENGE) reveal(state, events, now);
  else if (t.phase === PHASE.VOTE) resolveVote(state, events, now);
  else if (t.phase === PHASE.RESULT) nextTurn(state, events, now);
  return events;
}

function everyoneDecided(state) {
  const t = state.turn;
  return opponents(state).every(p => t.passed.includes(p.id) || t.challenges.some(c => c.playerId === p.id));
}

function reveal(state, events, now) {
  const t = state.turn;
  const owner = player(state, t.playerId);
  const card = t.card;
  const correct = fits(owner.timeline, t.slot, card.year);
  let stolenBy = null;
  // every challenger bet on a mistake: the bet pays off iff the owner was wrong
  const challengeResults = t.challenges.map(c => ({ ...c, correct: !correct }));
  if (correct) {
    owner.timeline.splice(t.slot, 0, card);
  } else {
    const winner = t.challenges[0]; // first to shout takes the card
    if (winner) {
      stolenBy = winner.playerId;
      insertSorted(player(state, stolenBy).timeline, card);
    } else {
      state.discard.push(card);
    }
  }
  t.result = { correct, stolenBy, challenges: challengeResults, tokenEarned: null };
  log(state, { kind: 'reveal', playerId: t.playerId, card, correct, stolenBy });
  events.push({ kind: 'reveal', playerId: t.playerId, card, correct, stolenBy });

  // The opponents always confirm whether the player named title + artist (nothing to press beforehand).
  if (owner.tokens < MAX_TOKENS && opponents(state).length > 0) {
    t.phase = PHASE.VOTE;
    t.deadline = now() + state.options.voteSeconds * 1000;
  } else {
    t.result.tokenEarned = false;
    toResult(state, events, now);
  }
}

function resolveVote(state, events, now) {
  const t = state.turn;
  const owner = player(state, t.playerId);
  const votes = Object.values(t.votes);
  const yes = votes.filter(Boolean).length;
  const no = votes.length - yes;
  const earned = votes.length > 0 && yes >= no; // majority of the votes cast; ties favour the player
  if (earned && owner.tokens < MAX_TOKENS) owner.tokens += 1;
  t.result.tokenEarned = earned;
  events.push({ kind: 'vote', playerId: t.playerId, earned });
  toResult(state, events, now);
}

function toResult(state, events, now) {
  const t = state.turn;
  t.phase = PHASE.RESULT;
  if (checkWin(state)) {
    t.deadline = null;
    events.push({ kind: 'finished', winnerId: state.winnerId });
  } else {
    t.deadline = now() + state.options.resultSeconds * 1000;
  }
}

function checkWin(state) {
  const goal = state.options.cardsToWin;
  const reached = state.players.filter(p => p.timeline.length >= goal);
  if (reached.length === 0) return false;
  // Ties inside the same round: most cards, then the active player.
  reached.sort((a, b) => b.timeline.length - a.timeline.length || (a.id === state.turn.playerId ? -1 : 1));
  finish(state, reached[0].id);
  return true;
}

function finishByCount(state, events) {
  const best = [...state.players].sort((a, b) => b.timeline.length - a.timeline.length)[0];
  finish(state, best.id);
  events.push({ kind: 'finished', winnerId: state.winnerId, reason: 'deck' });
  return events;
}

function nextTurn(state, events, now) {
  state.turnIndex = (state.turnIndex + 1) % state.order.length;
  if (state.turnIndex === 0) state.round += 1;
  startTurn(state, now);
  if (state.finished) events.push({ kind: 'finished', winnerId: state.winnerId, reason: 'deck' });
  else events.push({ kind: 'turn', playerId: state.turn.playerId });
}

/** Remove a player who left for good (lobby leaves are handled elsewhere). */
export function removePlayer(state, playerId, now = Date.now) {
  const events = [];
  const idx = state.order.indexOf(playerId);
  if (idx < 0) return events;
  const wasCurrent = state.turn && state.turn.playerId === playerId;
  state.order.splice(idx, 1);
  state.players = state.players.filter(p => p.id !== playerId);
  if (state.order.length < 2) { finish(state, state.order[0] ?? null); return events; }
  if (idx < state.turnIndex) state.turnIndex -= 1;
  if (wasCurrent) {
    if (state.turn.card) state.discard.push(state.turn.card);
    state.turnIndex = state.turnIndex % state.order.length;
    startTurn(state, Date.now);
  } else if (state.turn) {
    state.turn.challenges = state.turn.challenges.filter(c => c.playerId !== playerId);
    state.turn.passed = state.turn.passed.filter(id => id !== playerId);
    delete state.turn.votes[playerId];
    // The one who left may have been the last player everyone was waiting for.
    const t = state.turn;
    if (t.phase === PHASE.CHALLENGE && everyoneDecided(state)) reveal(state, events, now);
    else if (t.phase === PHASE.VOTE && opponents(state).every(p => p.id in t.votes)) resolveVote(state, events, now);
  }
  return events;
}

/**
 * Snapshot for one client. The current card is hidden until the reveal:
 * the active player only receives the Spotify id (needed to play the preview).
 */
export function viewFor(state, playerId) {
  const t = state.turn;
  let turn = null;
  if (t) {
    const revealed = t.phase === PHASE.VOTE || t.phase === PHASE.RESULT;
    let card = null;
    if (revealed) card = t.card;
    else if (t.playerId === playerId) card = { id: t.card.id, preview: t.card.preview };
    turn = { ...t, card };
  }
  return {
    options: state.options,
    deckCount: state.deck.length,
    players: state.players,
    order: state.order,
    turnIndex: state.turnIndex,
    round: state.round,
    turn,
    winnerId: state.winnerId,
    finished: state.finished,
    log: state.log.slice(-12),
    now: Date.now(),
  };
}
