package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.utils.FortyFiveLogger

interface DebugActor {

    var debugColor: Color

    fun initDebugBounds(actor: Actor, screen: RenderableScreen)

    fun badTexture(
        name: String,
        missingFocusTexture: Boolean = false,
        lowRes: Boolean = false,
        comment: String? = null
    )

    fun drawCustomDebugBounds(shapes: ShapeRenderer?)

    fun debug(color: Color)

    fun invalidateCalled()

    fun debugHierarchy()

    fun getDebugName(): String

}

class DebugActorImpl : DebugActor {

    override var debugColor: Color = Color(0f, 1f, 0f, 0.85f)

    private lateinit var actor: Actor
    private lateinit var screen: RenderableScreen

    private var badTexture: Boolean = false

    private val blinkOffset = (0..500).random()

    private var invalidateCalls: Int = 0
    private var lastInvalidateCheckTime: Long = TimeUtils.millis()

    override fun initDebugBounds(actor: Actor, screen: RenderableScreen) {
        this.actor = actor
        this.screen = screen
    }

    override fun invalidateCalled() {
        invalidateCalls++
        val now = TimeUtils.millis()
        if (now - lastInvalidateCheckTime < 1000) return
        if (invalidateCalls > 30) {
            val name = actor.name?.ifBlank { actor.toString() } ?: actor.toString()
//            FortyFive.logger.warn(
//                "debugActor",
//                "actor '$name': invalidate called $invalidateCalls times in the last second"
//            )
        }
        invalidateCalls = 0
        lastInvalidateCheckTime = now
    }

    override fun debugHierarchy() {
        fun rec(actor: Actor) {
            actor.debug()
            if (actor is Group) actor.children.forEach { rec(it) }
        }
        rec(actor)
    }

    override fun getDebugName(): String {
        val actor = actor
        if (actor.name != null) return actor.name
        if (actor is Label) return "Label(\"${actor.text}\")"
        if (actor is InputActor && actor.groups.isNotEmpty()) {
            return actor
                .groups
                .joinToString(
                    separator = ", ",
                    prefix = "${actor::class.simpleName}(",
                    postfix = ")"
                )
        }
        if (actor is CustomImageActor && actor.backgroundHandle != null) {
            return "Image(${actor.backgroundHandle})"
        }
        if (actor is CustomGroup && actor.backgroundHandle != null) {
            return "${actor::class.simpleName}(bg = ${actor.backgroundHandle})"
        }
        return actor::class.simpleName ?: ""
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

        actorsWithWarnings[name] = commentBuilder.toString()

        if (!debugHighlightBadTextures) return
        actor.debug()
        badTexture = true
    }

    override fun drawCustomDebugBounds(shapes: ShapeRenderer?) {
        val color = when {
            badTexture -> com.microwavestudios.fortyfive.utils.Color.Red
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

        val actorsWithWarnings: MutableMap<String, String> = mutableMapOf()

        fun dumpActorsWithDebugWarnings() {
            if (actorsWithWarnings.isEmpty()) return
            val builder = StringBuilder()
            actorsWithWarnings.forEach { (name, comment) ->
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
