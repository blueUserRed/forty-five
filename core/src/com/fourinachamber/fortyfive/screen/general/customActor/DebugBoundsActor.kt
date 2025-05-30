package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.utils.FortyFiveLogger

interface DebugBoundsActor {

    var debugColor: Color

    fun initDebugBounds(actor: Actor)

    fun badTexture(
        name: String,
        missingFocusTexture: Boolean = false,
        lowRes: Boolean = false,
        comment: String? = null
    )

    fun drawCustomDebugBounds(shapes: ShapeRenderer?)

    fun debug(color: Color)

}

class DebugBoundsActorImpl : DebugBoundsActor {

    override var debugColor: Color = Color(0f, 1f, 0f, 0.85f)

    private lateinit var actor: Actor

    private var badTexture: Boolean = false

    private val blinkOffset = (0..500).random()

    override fun initDebugBounds(actor: Actor) {
        this.actor = actor
    }

    override fun badTexture(
        name: String,
        missingFocusTexture: Boolean,
        lowRes: Boolean,
        comment: String?
    ) {
        val commentBuilder = StringBuilder()
        if (missingFocusTexture) {
            commentBuilder.append("No focus texture")
        }
        if (lowRes) {
            if (commentBuilder.isNotEmpty()) commentBuilder.append("; ")
            commentBuilder.append("Is low res")
        }
        if (comment != null) {
            if (commentBuilder.isNotEmpty()) commentBuilder.append("; ")
            commentBuilder.append(comment)
        }
        if (commentBuilder.isEmpty()) commentBuilder.append("no or wrong texture")

        actorsWithBadTextures[name] = commentBuilder.toString()

        if (!debugHighlightBadTextures) return
        actor.debug()
        badTexture = true
    }

    override fun drawCustomDebugBounds(shapes: ShapeRenderer?) {
        val color = when {
            badTexture -> com.fourinachamber.fortyfive.utils.Color.Red
            actor.debug -> debugColor
            else -> return
        }
        shapes ?: return
        shapes.color = color
        if (badTexture && makeBadTextureHighlightsExtryAnnoying && (TimeUtils.millis() + blinkOffset) % 500 > 250) return
        shapes.rect(
            actor.x, actor.y,
            actor.originX, actor.originY,
            actor.width, actor.height,
            actor.scaleX, actor.scaleY,
            actor.rotation
        )
    }

    override fun debug(color: Color) {
        debugColor = color
        actor.debug()
    }

    companion object {
        const val debugHighlightBadTextures: Boolean = false
        const val makeBadTextureHighlightsExtryAnnoying: Boolean = false

        val actorsWithBadTextures: MutableMap<String, String> = mutableMapOf()

        fun dumpActorsWithBadTextures() {
            if (actorsWithBadTextures.isEmpty()) return
            val builder = StringBuilder()
            actorsWithBadTextures.forEach { name, comment ->
                builder.append("$name: $comment\n")
            }
            FortyFive.logger.dump(
                FortyFiveLogger.LogLevel.MEDIUM,
                builder.toString(),
                "Actors with bad textures were shown when playing the game"
            )
        }

    }

}
