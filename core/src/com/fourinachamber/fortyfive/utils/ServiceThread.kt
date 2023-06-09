package com.fourinachamber.fortyfive.utils

import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.Resource
import com.fourinachamber.fortyfive.screen.ResourceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.trySendBlocking

class ServiceThread : Thread("ServiceThread") {

    private val channel: Channel<ServiceThreadMessage> = Channel(Channel.Factory.UNLIMITED)

    override fun run(): Unit = runBlocking {
        FortyFiveLogger.debug(logTag, "starting up")
        launchChannelListener()
    }

    private fun CoroutineScope.launchChannelListener() = launch {
        for (message in channel) {
            FortyFiveLogger.debug(logTag, "received message $message")
            when (message) {

                is ServiceThreadMessage.PrepareResources -> prepareResources()
                is ServiceThreadMessage.GenerateMaps -> generateMaps(message)

                else -> throw RuntimeException("unknown message: $message")
            }
        }
    }

    private fun CoroutineScope.generateMaps(message: ServiceThreadMessage.GenerateMaps) = launch(Dispatchers.Default) {
        MapManager.generateMaps(this)
        message.completed.complete(Unit)
    }

    private fun CoroutineScope.prepareResources() {
        ResourceManager
            .resources
            .filter { it.state == Resource.ResourceState.NOT_LOADED && it.borrowedBy.isNotEmpty() }
            .forEach {
                launch(Dispatchers.IO) { it.prepare() }
            }
    }

    fun sendMessage(message: ServiceThreadMessage): ChannelResult<Unit> = channel.trySendBlocking(message)

    fun close() {
        channel.close()
        FortyFiveLogger.debug(logTag, "closing channel")
    }

    companion object {
        const val logTag = "serviceThread"
    }

}

sealed class ServiceThreadMessage {

    object PrepareResources : ServiceThreadMessage()

    class GenerateMaps(val completed: CompletableDeferred<Unit> = CompletableDeferred()) : ServiceThreadMessage()

    override fun toString(): String = this::class.simpleName ?: ""

}
