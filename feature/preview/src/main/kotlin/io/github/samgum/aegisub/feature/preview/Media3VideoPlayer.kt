package io.github.samgum.aegisub.feature.preview

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

class Media3VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlayer {

    // ضبط استهلاك الذاكرة المؤقتة ليكون خفيفاً وسلساً جداً على الهواتف الاقتصادية
    private val lightweightLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            2_000,  // الحد الأدنى للمخزن المؤقت (2 ثانية)
            5_000,  // الحد الأقصى للمخزن المؤقت (5 ثوانٍ فقط لمنع امتلاء الرام)
            1_000,  // سرعة بدء التشغيل
            1_500,  // إعادة التشغيل بعد الـ Seek
        )
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(lightweightLoadControl)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPolling() else stopPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val duration = exoPlayer.duration.coerceAtLeast(0L)
                    _state.value = _state.value.copy(
                        isReady = true,
                        durationMs = duration,
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    )
                    readFps()
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _state.value = _state.value.copy(speed = playbackParameters.speed)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                readFps()
            }
        })
    }

    private fun readFps() {
        val formatFps = exoPlayer.videoFormat?.frameRate ?: Format.NO_VALUE.toFloat()
        val fps = if (formatFps > 0f) formatFps else 0f
        if (_state.value.fps != fps) {
            _state.value = _state.value.copy(fps = fps)
        }
    }

    override fun setMedia(uri: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    override fun play() = exoPlayer.play()

    override fun pause() = exoPlayer.pause()

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updatePosition()
    }

    override fun setSpeed(rate: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(rate)
    }

    override fun seekNextFrame() {
        if (!_state.value.isReady) return
        val fps = _state.value.fps.let { if (it > 0f) it else 30f }
        val frameMs = (1000.0f / fps).toLong().coerceAtLeast(1L)
        val maxPos = exoPlayer.duration.coerceAtLeast(0L)
        val target = (exoPlayer.currentPosition + frameMs).coerceAtMost(maxPos)
        exoPlayer.seekTo(target)
        updatePosition()
    }

    override fun seekPreviousFrame() {
        if (!_state.value.isReady) return
        val fps = _state.value.fps.let { if (it > 0f) it else 30f }
        val frameMs = (1000.0f / fps).toLong().coerceAtLeast(1L)
        val target = (exoPlayer.currentPosition - frameMs).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
        updatePosition()
    }

    override fun release() {
        stopPolling()
        scope.cancel()
        exoPlayer.release()
    }

    private fun updatePosition() {
        _state.value = _state.value.copy(positionMs = exoPlayer.currentPosition.coerceAtLeast(0L))
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                updatePosition()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
    }
}
