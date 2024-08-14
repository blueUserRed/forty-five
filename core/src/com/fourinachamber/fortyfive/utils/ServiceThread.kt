package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.trySendBlocking

class ServiceThread : Thread("ServiceThread") {

    private val channel: Channel<ServiceThreadMessage> = Channel(Channel.Factory.UNLIMITED)

    @OptIn(DelicateCoroutinesApi::class)
    private val cardDrawingDispatcher = newSingleThreadContext("card-drawer")

    override fun run(): Unit = runBlocking {
        FortyFiveLogger.debug(logTag, "starting up")
        launchChannelListener()
    }

    private fun CoroutineScope.launchChannelListener() = launch {
        for (message in channel) {
//            FortyFiveLogger.debug(logTag, "received message $message")
            try {
                handleMessage(message)
            } catch (e: Exception) {
                FortyFiveLogger.severe(logTag, "encountered exception during processing of message $message")
                FortyFiveLogger.stackTrace(e)
                // Retry
                try {
                    handleMessage(message)
                    FortyFiveLogger.debug(logTag, "Retry of message $message worked")
                } catch (_: Exception) {
                    FortyFiveLogger.debug(logTag, "Retry failed as well")
                }
            }
        }
    }

    private fun CoroutineScope.handleMessage(message: ServiceThreadMessage) {
        when (message) {

            is ServiceThreadMessage.DrawCardPixmap -> drawCardPixmap(message)
            is ServiceThreadMessage.LoadCardPixmap -> loadCardPixmap(message)
            is ServiceThreadMessage.PrepareResource -> prepareResource(message)

        }
    }

    private fun CoroutineScope.prepareResource(message: ServiceThreadMessage.PrepareResource) = launch(Dispatchers.IO) {
        message.resource.prepare()
        message.promise.resolve(message.resource)
    }

    private fun CoroutineScope.loadCardPixmap(message: ServiceThreadMessage.LoadCardPixmap) = launch {
        val pixmap = Pixmap(Gdx.files.internal("blobs/cards/${message.name}.png"))
        message.promise.resolve(pixmap)
    }

    private fun CoroutineScope.drawCardPixmap(message: ServiceThreadMessage.DrawCardPixmap) = launch(cardDrawingDispatcher) {
        synchronized(message.pixmap) { synchronized(message.card) {
            val pixmap = message.pixmap
            val cardTexturePixmap = message.cardTexturePixmap
            val card = message.card
            val damageValue = message.damageValue
            val costValue = message.costValue
            val baseDamage = card.baseDamage
            val isDark = card.actor.isDark
            val fontScale = card.actor.fontScale
            val savedSymbol = message.savedPixmap
            pixmap.drawPixmap(cardTexturePixmap, 0, 0)
            val situation = when {
                damageValue > baseDamage -> "increase"
                damageValue < baseDamage -> "decrease"
                else -> "normal"
            }
            val damageFontColor = GraphicsConfig.cardFontColor(isDark, situation)
            val reserveFontColor = GraphicsConfig.cardFontColor(isDark, "normal")
            message.font.write(pixmap, damageValue.toString(), 35, 480, fontScale, damageFontColor)
            message.font.write(pixmap, costValue.toString(), 490, 28, fontScale, reserveFontColor)
            if (savedSymbol != null) {
                pixmap.drawPixmap(
                    savedSymbol,
                    0, 0,
                    savedSymbol.width, savedSymbol.height,
                    450, 440,
                    100, 100
                )
            }
            message.promise.resolve(pixmap)
        } }
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

    class DrawCardPixmap(
        val pixmap: Pixmap,
        val cardTexturePixmap: Pixmap,
        val card: Card,
        val damageValue: Int,
        val costValue: Int,
        val savedPixmap: Pixmap?,
        val font: PixmapFont,
        val promise: Promise<Pixmap> = Promise()
    ) : ServiceThreadMessage()

    class LoadCardPixmap(
        val name: String,
        val promise: Promise<Pixmap> = Promise()
    ) : ServiceThreadMessage()

    class PrepareResource(
        val resource: Resource,
        val promise: Promise<Resource> = Promise()
    ) : ServiceThreadMessage()

    override fun toString(): String = this::class.simpleName ?: ""

}
