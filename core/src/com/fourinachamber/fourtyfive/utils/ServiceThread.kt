package com.fourinachamber.fourtyfive.utils

import com.fourinachamber.fourtyfive.screen.Resource
import com.fourinachamber.fourtyfive.screen.ResourceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.trySendBlocking

class ServiceThread : Thread("ServiceThread") {

    private val channel: Channel<ServiceThreadMessage> = Channel(Channel.Factory.UNLIMITED)

    override fun run(): Unit = runBlocking {
        FourtyFiveLogger.debug(logTag, "starting up")
        launchChannelListener()
    }

    private fun CoroutineScope.launchChannelListener() = launch {
        for (message in channel) {
            FourtyFiveLogger.debug(logTag, "received message $message")
            when (message) {

                is ServiceThreadMessage.PrepareResources -> prepareResources()

                else -> throw RuntimeException("unknown message: $message")
            }
        }
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
        FourtyFiveLogger.debug(logTag, "closing channel")
    }

    companion object {
        const val logTag = "serviceThread"
    }

}

sealed class ServiceThreadMessage {

    object PrepareResources : ServiceThreadMessage()

    override fun toString(): String = this::class.simpleName ?: ""

}