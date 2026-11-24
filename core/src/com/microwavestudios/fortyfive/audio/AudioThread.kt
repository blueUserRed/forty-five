package com.microwavestudios.fortyfive.audio

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.iterateRemoving
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class AudioThread(
    private val audioInterface: AudioInterface
) : Thread("AudioThread") {

    private val channel: Channel<AudioThreadMessage> = Channel(Channel.UNLIMITED)

    private val streamOnce: MutableList<StreamableAudio> = mutableListOf()

    private var run: Boolean = true

    override fun run() = runBlocking {
        audioInterface.createContext()

        launchUpdater()
        launchChannelListener()

        Unit
    }

    private fun CoroutineScope.launchChannelListener() = launch {
        for (message in channel) handleMessage(message)
    }

    private fun CoroutineScope.handleMessage(message: AudioThreadMessage) {
        when (message) {
            is AudioThreadMessage.StreamAudioOnce -> streamAudioOnce(message)
        }
    }

    private fun streamAudioOnce(message: AudioThreadMessage.StreamAudioOnce) {
        val streamable = audioInterface.createStreamableAudio(message.file)
        streamable.play()
        streamOnce.add(streamable)
    }

    private fun CoroutineScope.launchUpdater() = launch {
        while (run) {
            update()
            delay(10.milliseconds)
        }
    }

    private fun update() {
        streamOnce.iterateRemoving { streamable, remover ->
            streamable.update()
            if (!streamable.isPlaying()) remover()
        }
    }




    fun sendMessage(message: AudioThreadMessage): ChannelResult<Unit> = channel.trySendBlocking(message)

}

sealed class AudioThreadMessage {

    class StreamAudioOnce(val handle: ResourceHandle) : AudioThreadMessage() {
        // forceGet is fine here, because no heavy loading is done, just a path resolution
        val file = FortyFive.resourceManager.forceGet<File>(audioFileBorrower, FortyFive.gameLifetime, handle)
    }

    companion object {
        private val audioFileBorrower = object : ResourceBorrower {}
    }

}
