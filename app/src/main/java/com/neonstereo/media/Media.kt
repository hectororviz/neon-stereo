package com.neonstereo.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MediaState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val cover: Bitmap? = null,
    val packageName: String = "",
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canNext: Boolean = false,
    val canPrev: Boolean = false,
    val canSeek: Boolean = false,
    val stale: Boolean = true, // última canción conocida, no sesión activa
)

/** Listener mínimo para descubrir sesiones vía notificaciones (vía oficial). */
class MediaNotificationListener : NotificationListenerService()

class MediaMonitor(private val ctx: Context) {
    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state
    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null

    private val sessionMgr by lazy { ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager }
    private val comp = ComponentName(ctx, MediaNotificationListener::class.java)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pickBest(controllers)
    }

    fun start() {
        try {
            sessionMgr.addOnActiveSessionsChangedListener(sessionsListener, comp)
            pickBest(sessionMgr.getActiveSessions(comp))
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(active = false, stale = true)
        }
    }

    fun stop() {
        try { sessionMgr.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        unregister()
    }

    /** Priorizar sesión en reproducción; foco estable (§7). */
    private fun pickBest(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            // Conservar última conocida marcada stale.
            _state.value = _state.value.copy(active = false, stale = true)
            unregister()
            return
        }
        val playing = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PAUSED
        } ?: controllers.first()
        // Evitar saltos: si ya seguimos una que sigue activa y reproduce, mantenerla.
        val current = controller
        if (current != null && current.packageName == playing.packageName &&
            _state.value.playing && playing.playbackState?.state == PlaybackState.STATE_PLAYING
        ) return
        attach(playing)
    }

    private fun attach(c: MediaController) {
        unregister()
        controller = c
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = push()
            override fun onPlaybackStateChanged(state: PlaybackState?) = push()
            override fun onSessionDestroyed() {
                _state.value = _state.value.copy(active = false, stale = true)
            }
        }
        callback = cb
        c.registerCallback(cb, null)
        push()
    }

    private fun unregister() {
        try { val cb = callback; if (cb != null) controller?.unregisterCallback(cb) } catch (_: Exception) {}
        controller = null; callback = null
    }

    private fun push() {
        val c = controller ?: return
        val md = c.metadata
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        val stateCode = ps?.state ?: PlaybackState.STATE_NONE
        _state.value = MediaState(
            active = true,
            playing = stateCode == PlaybackState.STATE_PLAYING,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = ps?.position ?: 0L,
            cover = try { md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) } catch (_: Exception) { null },
            packageName = c.packageName,
            canPlay = (actions and PlaybackState.ACTION_PLAY) != 0L,
            canPause = (actions and PlaybackState.ACTION_PAUSE) != 0L,
            canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
            canPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
            canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
            stale = false,
        )
    }

    fun toggle() {
        val c = controller ?: return
        val playing = _state.value.playing
        try {
            if (playing && _state.value.canPause) c.transportControls.pause()
            else if (!playing && _state.value.canPlay) c.transportControls.play()
            else if (playing) c.transportControls.pause() else c.transportControls.play()
        } catch (_: Exception) {}
    }
    fun next() {
        try { if (_state.value.canNext) controller?.transportControls?.skipToNext() } catch (_: Exception) {}
    }
    fun prev() {
        try { if (_state.value.canPrev) controller?.transportControls?.skipToPrevious() } catch (_: Exception) {}
    }
    fun seekTo(ms: Long) {
        try { if (_state.value.canSeek) controller?.transportControls?.seekTo(ms) } catch (_: Exception) {}
    }
    fun openPlayerIntent(): android.app.PendingIntent? = try { controller?.sessionActivity } catch (_: Exception) { null }
}
