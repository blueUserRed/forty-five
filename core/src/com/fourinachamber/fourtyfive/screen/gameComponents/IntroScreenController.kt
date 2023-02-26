package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import com.fourinachamber.fourtyfive.screen.general.ScreenController
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.plus
import onj.value.OnjNamedObject
import kotlin.math.pow

/**
 * controls the intro-screen
 */
class IntroScreenController(val onj: OnjNamedObject) : ScreenController() {

    private val appearActorName = onj.get<String>("appearActor")
    private val nextScreen = onj.get<String>("nextScreen")

    private lateinit var onjScreen: OnjScreen
    private lateinit var textures: List<TextureRegion>
    private lateinit var appearActor: Actor
    private var startTime: Long = 0
    private val sprites: MutableList<CardSprite> = mutableListOf()

    private val renderTask: (Batch) -> Unit = { batch ->
        for (sprite in sprites) sprite.draw(batch)
    }

    private var timeline: Timeline = Timeline(mutableListOf())

    override fun init(onjScreen: OnjScreen, context: Any?) {
        //TODO: put these magic numbers in an onj file somewhere
        this.onjScreen = onjScreen

        Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)

        appearActor = onjScreen.namedActorOrError(appearActorName)

        textures = ResourceManager
            .resources
            .filter {
                it is ResourceManager.AtlasRegionResource &&
                        it.atlasResourceHandle == ResourceManager.cardAtlasResourceHandle
            }
            .map { ResourceManager.get(onjScreen, it.handle) }

        onjScreen.addLateRenderTask(renderTask)

        timeline = Timeline.timeline {
            delay(1000)
            action { startTime = TimeUtils.millis() }
            delayUntil {
                val timeSinceStart = TimeUtils.timeSinceMillis(startTime)

                val percent = timeSinceStart / 2000.0
                val amount = ((percent * 10).pow(3) / 128).toInt().coerceAtLeast(1)

                repeat(amount) { spawnRandomSprite() }
                timeSinceStart > 2000
            }
            delay(500)
            action { appearActor.isVisible = true }
            delay(2000)
            action {
                FourtyFive.changeToScreen(ScreenBuilder(Gdx.files.internal(nextScreen)).build())
            }
        }

        timeline.start()
    }

    override fun update() {
        val iterator = sprites.iterator()
        while (iterator.hasNext()) {
            val sprite = iterator.next()
            sprite.velocity += Vector2(0f, -2.5f)
            sprite.update()
            if (sprite.y < -sprite.height) iterator.remove()
        }
        timeline.update()
    }

    private fun spawnRandomSprite() {
        val worldWidth = onjScreen.stage.viewport.worldWidth
        val worldHeight = onjScreen.stage.viewport.worldHeight

        val scale = Math.random().toFloat() * 0.04f + 0.03f
        val texture = textures.random()
        val sprite = CardSprite(texture, 1.0f)
        sprite.setSize(texture.regionWidth * scale, texture.regionHeight * scale)
        sprite.setPosition(worldWidth * Math.random().toFloat(), worldHeight + Math.random().toFloat() * 5)
        sprite.rotationalVelocity = (Math.random().toFloat() - 0.5f) * 1.5f
        sprites.add(sprite)
    }

    override fun end() {
        onjScreen.removeLateRenderTask(renderTask)
    }

    /**
     * sprite with basic physics for animation the cards
     */
    class CardSprite(texture: TextureRegion, initialScale: Float) : Sprite(texture) {

        var velocity: Vector2 = Vector2()
        var rotationalVelocity: Float = 0f

        init {
            setScale(initialScale)
        }

        fun update() {
            val deltaTime = Gdx.graphics.deltaTime
            setPosition(x + velocity.x * deltaTime, y + velocity.y * deltaTime)
            setOrigin(width / 2, height / 2)
            rotate(rotationalVelocity)
            rotation %= 360
        }

    }


}
