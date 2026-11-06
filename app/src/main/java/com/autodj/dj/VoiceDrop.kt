package com.autodj.dj

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * Deep DJ voice drops using bundled audio samples played via [SoundPool]
 * on the media stream, pitch-shifted to a deep, bassy festival announcer voice.
 */
class VoiceDrop(context: Context) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .build()

    private var idAreYouReady = 0
    private var idFireTheDj = 0
    private var idLetsGo = 0
    private var idPullUp = 0

    init {
        try {
            val assets = context.assets
            idAreYouReady = pool.load(assets.openFd("sfx/voice_are_you_ready.mp3"), 1)
            idFireTheDj = pool.load(assets.openFd("sfx/voice_fire_the_dj.mp3"), 1)
            idLetsGo = pool.load(assets.openFd("sfx/voice_lets_go.mp3"), 1)
            idPullUp = pool.load(assets.openFd("sfx/voice_pull_up.mp3"), 1)
            Log.i("VoiceDrop", "Loaded DJ voice drops into SoundPool (ready=$idAreYouReady, fire=$idFireTheDj)")
        } catch (e: Exception) {
            Log.e("VoiceDrop", "Failed to load voice drops from assets", e)
        }
    }

    /**
     * Plays a voice drop with full volume and a pitch rate of 0.75x - 0.80x
     * to make the announcer voice deep, booming and resonant.
     */
    private fun play(soundId: Int, rate: Float = 0.76f) {
        if (soundId == 0) return
        pool.play(soundId, 1f, 1f, 1, 0, rate)
    }

    fun areYouReady() = play(idAreYouReady, 0.76f)
    fun fireTheDJ() = play(idFireTheDj, 0.75f)
    fun letsGo() = play(idLetsGo, 0.80f)
    fun pullUp() = play(idPullUp, 0.76f)

    fun release() {
        pool.release()
    }
}
