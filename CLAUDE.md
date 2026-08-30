# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Android replica of the HITSTER music board game, played **phone‑to‑phone**: the phone that creates a
session runs the game host *inside the app* (embedded WebSocket server on the LAN / hotspot); other
phones discover it via mDNS or type `ip:port`. There is no online backend. Music is the 30 s Spotify
preview, streamed inside the app (ExoPlayer), only on the active player's phone. UI copy is pt‑BR;
code identifiers/comments are English.

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK  → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # signed only if keystore.properties exists (see app/build.gradle.kts)
./gradlew :app:testDebugUnitTest      # all JVM tests (rules engine + full WebSocket session vs. the in-app host)
./gradlew :app:testDebugUnitTest --tests "*GameEngineTest*"        # one class
./gradlew :app:testDebugUnitTest --tests "*GameEngineTest.skip"    # one test
cd server && npm install && npm test  # optional Node host (same protocol); node --test test/*.test.js
cd tools && python3 build_catalog.py --db gameset_database.json --out ../catalog   # rebuild decks (needs the official DB, see README)
cd tools && python3 reconcile.py aaaq0001 --cache ../catalog/_cache.json           # curated vs. automatic years
cd design && python3 make_artboards.py                                             # regenerate mockup artboards
```

Toolchain: JDK 17, Android SDK 34 (`local.properties` → `sdk.dir`), Gradle wrapper 8.9, AGP 8.5.2,
Kotlin 2.0.20 + Compose compiler plugin, Compose BOM 2024.09. Use `--no-daemon -q` in CI‑like runs.
No emulator/KVM is assumed: validate with the build and the JVM tests; `testOptions.unitTests.isReturnDefaultValues`
is on so `android.util.Log` in host code works under plain JUnit.

## Architecture (app/src/main/java/com/hitster/mobile)

Everything is one process; the roles are separated by package:

- **`net/`** – the wire contract. `Models.kt` holds the `@Serializable` snapshot types (`Room`, `GameView`,
  `Turn`, `Card`, `ClientMessage`, `ServerMessage`, `Action`) and the shared `json` config
  (`ignoreUnknownKeys`, `explicitNulls=false`). **These classes are used by both the client and the host**,
  so a change here changes the protocol; `server/src/game.js` mirrors the same shapes.
  `GameClient.kt` is the OkHttp WebSocket client: keeps the last `Room` in a `StateFlow`, emits
  `events`/`errors`/`kicked`/`ended`, reconnects with backoff and re‑sends a `join` carrying the persisted
  `playerId` so a player resumes with the same cards/tokens. `clockOffset` (server `now` − local) drives
  the countdowns so every phone agrees on deadlines.
- **`host/`** – the authoritative game, run only on the creator's phone.
  `GameEngine.kt` is a pure rules engine (no Android deps): deck/discard, turn phases
  `listen → challenge → vote → result`, token actions (`skip`, `challenge`, `buyCard`, `claimTitle`),
  `tick()` for deadlines, `view(forPlayerId)` which **hides the current card** (only `id`+`preview` go to
  the active player, nothing to opponents) until the reveal. `LocalHost.kt` wraps it in a Java‑WebSocket
  `WebSocketServer`: one room, 4‑letter code, host = first player, reconnect by `playerId`, a scheduler
  for phase deadlines, `broadcast()` sends a personalised snapshot to each connection. All state mutation
  is under one lock (`synchronized(lock)`) because callbacks arrive on socket threads. `Discovery.kt`
  advertises/browses `_hitster._tcp` via `NsdManager` (resolves are queued – NSD allows one at a time)
  and finds the LAN IPv4 for manual entry. `Catalog.kt` loads the decks bundled as assets.
- **`GameViewModel.kt`** – glue. `createSession()` starts `LocalHost`, waits for the bound port,
  advertises, then connects `GameClient` to `ws://127.0.0.1:<port>` (the host is also a normal client).
  `onRoom()` detects a new turn by the key `round:playerId:skips` and, if it is my turn, resolves the
  preview and autoplays; local‑only UI state (selected slot, challenge mode) lives here as `StateFlow`s
  and is reset per turn.
- **`audio/`** – `PreviewResolver` scrapes `open.spotify.com/embed/track/<id>` for `audioPreview.url`
  (no API key; the catalog's stored URL is the fallback); `PreviewPlayer` is the ExoPlayer wrapper.
- **`ui/`** – Compose. `MainActivity` picks `home | lobby | game` from the room snapshot (no nav library),
  shows toasts and the connection‑lost banner. `screens/GameScreen.kt` switches on the turn phase and on
  `BoxWithConstraints` (portrait: stacked; landscape: panel + players column on top, full‑width timeline
  below). `components/Cards.kt` has the timeline (`LazyRow` of alternating slot/card items keyed by card id,
  `animateItem`, auto‑scroll centring the target) and the decade‑coloured `YearCard`. Design tokens live in
  `ui/theme/Theme.kt` (Righteous display face, Poppins body; `TextTertiary` was raised to #8A8AA0 for
  contrast). Keep tap targets ≥ 44 dp and prefer transform/alpha animations.

Rules reference: the manual in `/root/hitster_manual_com_detalhes_nao_ofi_342510.pdf` (pages are images).
Implemented rules are listed in README.md; tests in `app/src/test` encode them – update both together.

## Catalog (`catalog/`, `tools/`)

`catalog/aaaq000{1,2,3}.json` are the official Brazilian decks (Hitster, Lado B = "Guilty Pleasures",
Summer Party; 308 cards each) built from Jumbo's public gameset database. Years are **hand‑curated**:
`tools/overrides/<sku>.json` (per‑deck review with reasons) and `tools/year_overrides.json` (final
adjudications, wins over the per‑deck files). Convention: the year the song was released/became a hit,
matching official HITSTER cards of other editions; remasters/compilations use the original year; a live
recording that *is* the famous version uses the live release year. Never take the Spotify album date at
face value. The deck JSONs are bundled into the APK via `sourceSets["main"].assets.srcDirs("../catalog")`
(`_cache.json`/`review.csv` are excluded by `ignoreAssetsPattern`).

## Gotchas

- Kotlin nests block comments: `/*` inside a KDoc (e.g. `catalog/*.json`) breaks compilation.
- `LocalHost` clamps options (`challengeSeconds` 5–60, `cardsToWin` 5–20); tests must respect the clamps.
- The host phone must keep the app open; `shutdown()` sends `ended` so guests return home instead of
  reconnecting forever.
- Two cards in Lado B have no pre‑recorded preview URL; the resolver fetches at play time.
- Release signing reads `keystore.properties` (git‑ignored) – keep the keystore; updates must use the same key.
