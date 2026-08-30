package com.hitster.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitster.mobile.net.ConnState
import com.hitster.mobile.ui.components.ConnectionBanner
import com.hitster.mobile.ui.screens.GameActions
import com.hitster.mobile.ui.screens.GameScreen
import com.hitster.mobile.ui.screens.GameUi
import com.hitster.mobile.ui.screens.HomeScreen
import com.hitster.mobile.ui.screens.LobbyScreen
import com.hitster.mobile.ui.theme.Danger
import com.hitster.mobile.ui.theme.HitsterTheme
import com.hitster.mobile.ui.theme.Ink
import com.hitster.mobile.ui.theme.NeonPink
import com.hitster.mobile.ui.theme.NeonYellow
import com.hitster.mobile.ui.theme.Surface2
import com.hitster.mobile.ui.theme.Surface3
import com.hitster.mobile.ui.theme.TextPrimary

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            HitsterTheme {
                val room by vm.room.collectAsStateWithLifecycle()
                val connection by vm.connection.collectAsStateWithLifecycle()
                val isHost by vm.isHost.collectAsStateWithLifecycle()
                val busy by vm.busy.collectAsStateWithLifecycle()
                val playback by vm.playback.collectAsStateWithLifecycle()
                val previewReady by vm.previewReady.collectAsStateWithLifecycle()
                val selectedSlot by vm.selectedSlot.collectAsStateWithLifecycle()
                val claimsTitle by vm.claimsTitle.collectAsStateWithLifecycle()
                val challengeMode by vm.challengeMode.collectAsStateWithLifecycle()
                val challengeSlot by vm.challengeSlot.collectAsStateWithLifecycle()
                val viewingTimelineOf by vm.viewingTimelineOf.collectAsStateWithLifecycle()
                val sessions by vm.sessions.collectAsStateWithLifecycle()
                val hostAddress by vm.hostAddress.collectAsStateWithLifecycle()

                val snackbar = remember { SnackbarHostState() }
                var confirmLeave by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    vm.toasts.collect { snackbar.showSnackbar(message = it.text, withDismissAction = true) }
                }

                val actions = remember {
                    GameActions(
                        selectSlot = vm::selectSlot,
                        confirmPlacement = vm::confirmPlacement,
                        toggleClaim = vm::toggleClaim,
                        skip = vm::skipSong,
                        buyCard = vm::buyCard,
                        play = vm::playPreview,
                        pause = vm::pausePreview,
                        replay = vm::replay,
                        pass = vm::pass,
                        startChallenge = vm::startChallenge,
                        cancelChallenge = vm::cancelChallenge,
                        pickChallengeSlot = vm::pickChallengeSlot,
                        confirmChallenge = vm::confirmChallenge,
                        vote = vm::vote,
                        continueGame = vm::continueGame,
                        openTimeline = vm::openTimeline,
                        restart = vm::restart,
                        leave = { confirmLeave = true },
                    )
                }

                Box(Modifier.fillMaxSize().background(Ink)) {
                    val r = room
                    val screen = when {
                        r == null -> "home"
                        r.game == null || r.phase == "lobby" -> "lobby"
                        else -> "game"
                    }
                    BackHandler(enabled = screen != "home") {
                        if (viewingTimelineOf != null) vm.openTimeline(null)
                        else if (challengeMode) vm.cancelChallenge()
                        else confirmLeave = true
                    }
                    AnimatedContent(targetState = screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
                        // The outgoing screen keeps composing during the fade‑out while `room` may already be
                        // null (left session) or `game` null (back to lobby) – guard instead of `!!`.
                        val rr = r
                        val game = rr?.game
                        when {
                            s == "home" -> HomeScreen(
                                prefs = vm.prefs, busy = busy, sessions = sessions,
                                onStartDiscovery = vm::startDiscovery, onStopDiscovery = vm::stopDiscovery,
                                onCreate = vm::createSession, onJoin = vm::joinSession,
                            )
                            s == "lobby" && rr != null -> LobbyScreen(
                                room = rr, myId = vm.myId, isHost = isHost, connection = connection, hostAddress = hostAddress,
                                onSetDecks = vm::setDecks, onSetOptions = vm::setOptions, onKick = vm::kick,
                                onStart = vm::startGame, onLeave = { confirmLeave = true },
                            )
                            s == "game" && rr != null && game != null -> GameScreen(
                                ui = GameUi(
                                    room = rr, game = game, myId = vm.myId, isHost = isHost, clockOffset = vm.clockOffset,
                                    playback = playback, previewReady = previewReady != null, selectedSlot = selectedSlot,
                                    claimsTitle = claimsTitle, challengeMode = challengeMode, challengeSlot = challengeSlot,
                                    viewingTimelineOf = viewingTimelineOf,
                                ),
                                a = actions,
                            )
                            else -> Box(Modifier.fillMaxSize())
                        }
                    }

                    // Connection lost mid‑session (Wi‑Fi drop, host phone asleep): say so instead of a frozen screen.
                    ConnectionBanner(visible = screen != "home" && connection != ConnState.CONNECTED, onLeave = { confirmLeave = true })

                    if (confirmLeave) {
                        AlertDialog(
                            onDismissRequest = { confirmLeave = false },
                            containerColor = Surface2,
                            title = { Text("Sair da sessão?", color = TextPrimary) },
                            text = { Text("Você sairá da partida. Se sair sem querer, entre de novo com o mesmo código para retomar.", color = TextPrimary) },
                            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveSession() }) { Text("Sair", color = Danger) } },
                            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Ficar", color = NeonYellow) } },
                        )
                    }

                    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)) { data ->
                        Snackbar(snackbarData = data, containerColor = Surface3, contentColor = TextPrimary, actionColor = NeonPink, dismissActionContentColor = NeonPink, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}
