package com.microwavestudios.fortyfive.audio

import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.EndableLifetime
import com.microwavestudios.fortyfive.utils.Lifetime
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.between
import com.microwavestudios.fortyfive.utils.epsilonEquals
import com.microwavestudios.fortyfive.utils.iterateRemoving
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.asinh
import kotlin.time.Duration.Companion.milliseconds

class AudioThread(
    private val audioInterface: AudioInterface
) : Thread("AudioThread") {

    private val channel: Channel<AudioThreadMessage> = Channel(Channel.UNLIMITED)

    private val streamOnce: MutableList<Pair<StreamableAudio, Float>> = mutableListOf()
    private val playOnce: MutableList<Pair<SoundInstance, Float>> = mutableListOf()

    private var run: Boolean = true

    private val soundsCache: MutableMap<String, CachedSound> = mutableMapOf()

    private var masterVolume = 1f
    private var musicVolume = 1f
    private var soundVolume = 1f

    private val activeThemes: MutableList<Theme> = mutableListOf()

    // may be null during transitions
    private var currentTheme: Theme? = SilentTheme()

    private val themeTimeline: Timeline = Timeline()

    init {
        activeThemes.add(currentTheme!!)
        themeTimeline.startTimeline()
    }

    override fun run() = runBlocking {
        audioInterface.createContext()

        launchUpdater()
        launchChannelListener()

        Unit
    }

    private fun CoroutineScope.launchChannelListener() = launch {
        for (message in channel) handleMessage(message)
        FortyFive.logger.debug(logTag, "shutting down")
        cleanupThread()
    }

    private fun cleanupThread() {
        run = false
        playOnce.forEach { (sound, _) ->
            sound.stop()
            sound.dispose()
        }
        streamOnce.forEach { (streamable, _) ->
            streamable.stop()
            streamable.dispose()
        }
        soundsCache.values.forEach {
            it.soundData.dispose()
        }
        soundsCache.clear()
        themeTimeline.stopTimeline()
        activeThemes.forEach {
            it.end()
            it.dispose()
        }
    }

    private fun handleMessage(message: AudioThreadMessage) {
        when (message) {
            is AudioThreadMessage.StreamAudioOnce -> streamAudioOnce(message)
            is AudioThreadMessage.PlaySoundOnce -> playSoundOnce(message)
            is AudioThreadMessage.VolumeChanged -> volumeChanged(message)
            is AudioThreadMessage.ChangeTheme -> changeTheme(message)
        }
    }

    private fun changeTheme(message: AudioThreadMessage.ChangeTheme) {
        val timeline = Timeline.later {
            val duration = 3_000
            var startTime = 0L
            val oldTheme = currentTheme!!
            currentTheme = null
            val newTheme = message.theme

            action {
                newTheme.init(audioInterface)
                activeThemes.add(newTheme)
                startTime = TimeUtils.millis()
                newTheme.setVolume(0f)
                newTheme.start()
            }
            delayUntil {
                val now = TimeUtils.millis()
                val timePassed = now - startTime
                val fractionPassed = (timePassed.toFloat() / duration.toFloat()).between(0f, 1f)
                oldTheme.setVolume((1f - fractionPassed) * musicVolume * masterVolume)
                newTheme.setVolume(fractionPassed * musicVolume * masterVolume)
                oldTheme.update()
                newTheme.update()
                fractionPassed.epsilonEquals(1f)
            }
            action {
                oldTheme.setVolume(0f)
                newTheme.setVolume(musicVolume * masterVolume)
                oldTheme.end()
                oldTheme.dispose()
                activeThemes.remove(oldTheme)
                currentTheme = newTheme
            }
        }
        themeTimeline.appendAction(timeline.asAction())
    }

    private fun volumeChanged(message: AudioThreadMessage.VolumeChanged) {
        masterVolume = message.masterVolume
        musicVolume = message.musicVolume
        soundVolume = message.soundVolume

        streamOnce.forEach { (streamable, volume) ->
            streamable.setVolume(masterVolume * musicVolume * volume)
        }
        playOnce.forEach { (sound, volume) ->
            sound.setVolume(masterVolume * soundVolume * volume)
        }
        currentTheme?.setVolume(masterVolume * musicVolume)
    }

    private fun playSoundOnce(message: AudioThreadMessage.PlaySoundOnce) {
        val playLifetime = EndableLifetime()
        val dataLifetime = playLifetime.longer(message.keepSoundAlive)
        val data = getSoundData(message.handle, message.file, dataLifetime)
        val instance = data.createInstance()
        instance.playingLifetime.onEnd { playLifetime.die() }
        instance.setVolume(masterVolume * soundVolume * message.inherentVolume)
        instance.setPan(message.pan)
        instance.play()
        playOnce.add(instance to message.inherentVolume)
    }

    private fun streamAudioOnce(message: AudioThreadMessage.StreamAudioOnce) {
        val streamable = audioInterface.createStreamableAudio(message.file)
        streamable.setVolume(masterVolume * musicVolume * message.inherentVolume)
        streamable.setPan(message.pan)
        streamable.play()
        streamOnce.add(streamable to message.inherentVolume)
    }

    private fun getSoundData(handle: String, file: File, lifetime: Lifetime): SoundData {
        val cachedSound = soundsCache[handle]
        if (cachedSound != null) {
            cachedSound.lifetime = cachedSound.lifetime.longer(lifetime)
            return cachedSound.soundData
        }
        val soundData = audioInterface.createSoundData(file)
        soundsCache[handle] = CachedSound(soundData, lifetime)
        return soundData
    }

    private fun update() {
        currentTheme?.update()
        themeTimeline.updateTimeline()
        streamOnce.iterateRemoving { (streamable, _), remover ->
            streamable.update()
            if (!streamable.isPlaying()) {
                streamable.dispose()
                remover()
            }
        }
        soundsCache.iterateRemoving { (_, cachedSound), remover ->
            if (cachedSound.lifetime.alive) return@iterateRemoving
            cachedSound.soundData.dispose()
            remover()
        }
        playOnce.iterateRemoving { (soundInstance, _), remover ->
            soundInstance.update()
            if (soundInstance.isPlaying()) return
            soundInstance.dispose()
            remover()
        }
    }

    private fun CoroutineScope.launchUpdater() = launch {
        while (run) {
            update()
            delay(10.milliseconds)
        }
    }

    private data class CachedSound(val soundData: SoundData, var lifetime: Lifetime)




    fun sendMessage(message: AudioThreadMessage): ChannelResult<Unit> = channel.trySendBlocking(message)

    fun close() {
        channel.close()
    }

    companion object {
        private const val logTag = "AudioThread"
    }

}

sealed class AudioThreadMessage {

    class StreamAudioOnce(
        val handle: ResourceHandle,
        val inherentVolume: Float = 1f,
        val pan: Float = 0f,
    ) : AudioThreadMessage() {
        // forceGet is fine here, because no heavy loading is done, just a path resolution
        val file = FortyFive.resourceManager.forceGet<File>(audioFileBorrower, FortyFive.gameLifetime, handle)
    }

    class PlaySoundOnce(
        val handle: ResourceHandle,
        val keepSoundAlive: Lifetime,
        val inherentVolume: Float = 1f,
        val pan: Float = 0f,
    ) : AudioThreadMessage() {
        val file = FortyFive.resourceManager.forceGet<File>(audioFileBorrower, FortyFive.gameLifetime, handle)
    }

    class VolumeChanged(
        val masterVolume: Float,
        val musicVolume: Float,
        val soundVolume: Float,
    ) : AudioThreadMessage()

    class ChangeTheme(val theme: Theme) : AudioThreadMessage()

    companion object {
        private val audioFileBorrower = object : ResourceBorrower {}
    }

}
