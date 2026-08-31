package com.linkshare.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import com.linkshare.app.model.ConnectionPhase

class ConnectionSoundManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedSoundIds = mutableSetOf<Int>()
    private var successSoundId = 0
    private var failedSoundId = 0
    private var activeStreamId = 0
    private var lastPlayedPhase: ConnectionPhase? = null
    private var lastPlayedAtMillis = 0L

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (status == 0) loadedSoundIds += soundId
        }
        successSoundId = loadSound("connection_success")
        failedSoundId = loadSound("connection_failed")
    }

    fun onConnectionPhaseChanged(from: ConnectionPhase, to: ConnectionPhase) {
        if (from == to) return
        val soundId = when (to) {
            ConnectionPhase.Connected -> successSoundId
            ConnectionPhase.Failed -> failedSoundId
            else -> return
        }
        if (soundId == 0 || soundId !in loadedSoundIds || !canPlayUiSound()) return

        val now = SystemClock.elapsedRealtime()
        if (lastPlayedPhase == to && now - lastPlayedAtMillis < DEBOUNCE_MILLIS) return

        if (activeStreamId != 0) soundPool.stop(activeStreamId)
        activeStreamId = soundPool.play(soundId, PLAYBACK_VOLUME, PLAYBACK_VOLUME, 1, 0, 1f)
        lastPlayedPhase = to
        lastPlayedAtMillis = now
    }

    fun release() {
        soundPool.release()
        activeStreamId = 0
        loadedSoundIds.clear()
    }

    private fun loadSound(resourceName: String): Int {
        val resourceId = appContext.resources.getIdentifier(resourceName, "raw", appContext.packageName)
        return if (resourceId == 0) 0 else soundPool.load(appContext, resourceId, 1)
    }

    private fun canPlayUiSound(): Boolean {
        return audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 &&
            audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) > 0
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 500L
        const val PLAYBACK_VOLUME = 0.8f
    }
}
