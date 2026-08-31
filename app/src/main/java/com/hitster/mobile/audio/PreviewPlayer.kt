package com.hitster.mobile.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaybackState(
    val url: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 30_000,
    val ended: Boolean = false,
    val error: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** ExoPlayer wrapper that streams the 30s preview clip. Only the active player's phone ever calls play(). */
class PreviewPlayer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startTicker() else stopTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED,
                    durationMs = duration.takeIf { it > 0 } ?: 30_000,
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(error = error.errorCodeName, isPlaying = false)
            }
        })
    }

    /** Start (or resume) the clip. Resuming after a pause continues where it stopped; only a finished clip restarts. */
    fun play(url: String) {
        if (_state.value.url == url && player.playbackState != Player.STATE_IDLE) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play(); return
        }
        _state.value = PlaybackState(url = url, isBuffering = true)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Restart from 0. In `STATE_IDLE` (after a playback error, or after `stop()`) the media item is gone:
     * a bare seek is ignored and `play()` only flips playWhenReady, so the clip has to be prepared again.
     */
    fun replay() {
        val url = _state.value.url ?: return
        if (player.playbackState == Player.STATE_IDLE) play(url)
        else { player.seekTo(0); player.play() }
    }

    fun pause() = player.pause()

    fun stop() {
        player.stop()
        player.clearMediaItems()
        stopTicker()
        _state.value = PlaybackState()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                _state.value = _state.value.copy(positionMs = player.currentPosition, durationMs = player.duration.takeIf { it > 0 } ?: 30_000)
                delay(100)
            }
        }
    }

    private fun stopTicker() { ticker?.cancel(); ticker = null }

    fun release() { stop(); player.release() }
}
