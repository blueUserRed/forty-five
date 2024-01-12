package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.graphics.Pixmap
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.Resource
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
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
                is ServiceThreadMessage.DrawCardPixmap -> drawCardPixmap(message)
                is ServiceThreadMessage.LoadSingleResource -> loadSingleResource(message)

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

    private fun CoroutineScope.drawCardPixmap(message: ServiceThreadMessage.DrawCardPixmap) = launch {
        synchronized(message.pixmap) {
            val pixmap = message.pixmap
            val cardTexturePixmap = message.cardTexturePixmap
            val card = message.card
            val damageValue = message.damageValue
            val baseDamage = card.baseDamage
            val font = card.actor.font
            val isDark = card.actor.isDark
            val fontScale = card.actor.fontScale
            pixmap.drawPixmap(cardTexturePixmap, 0, 0)
            val situation = when {
                damageValue > baseDamage -> "increase"
                damageValue < baseDamage -> "decrease"
                else -> "normal"
            }
            val damageFontColor = GraphicsConfig.cardFontColor(isDark, situation)
            val reserveFontColor = GraphicsConfig.cardFontColor(isDark, "normal")
            font.write(pixmap, damageValue.toString(), 35, 480, fontScale, damageFontColor)
            font.write(pixmap, card.cost.toString(), 490, 28, fontScale, reserveFontColor)
            message.isFinished = true
        }
    }

    private fun CoroutineScope.loadSingleResource(message: ServiceThreadMessage.LoadSingleResource) = launch {
        val resource = ResourceManager
            .resources
            .find { it.handle == message.handle }
            ?: throw RuntimeException("unknown resource: ${message.handle}")
        launch { resource.prepare() }.join()
        synchronized(message) {
            message.finished = true
            if (message.cancelled) resource.dispose()
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

    class DrawCardPixmap(
        val pixmap: Pixmap,
        val cardTexturePixmap: Pixmap,
        val card: Card,
        val damageValue: Int,
        var isFinished: Boolean = false
    ) : ServiceThreadMessage()

    class LoadSingleResource(
        val handle: ResourceHandle,
        var finished: Boolean = false,
        var cancelled: Boolean = false
    ) : ServiceThreadMessage()

    override fun toString(): String = this::class.simpleName ?: ""

}
