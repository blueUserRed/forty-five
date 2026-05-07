package com.microwavestudios.fortyfive.screen.commonComponents

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent.Warning
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.epsilonEquals
import com.microwavestudios.fortyfive.utils.minMagnitude
import kotlin.math.abs

interface IWarningParent {

    fun showTemporaryWarning(text: String, level: Level, time: Int = defaultDisplayTime): IWarning
    fun showTemporaryWarning(warning: IWarning, time: Int = defaultDisplayTime)
    fun show(warning: IWarning)
    fun hide(warning: IWarning)

    fun warning(text: String, level: Level): IWarning

    enum class Level(val symbol: String, val background: String, val fontColor: com.badlogic.gdx.graphics.Color) {
        INFO("i", "warning_label_background_grey", Color.Black),
        MID("!", "warning_label_background_red", Color.FortyWhite),
        HIGH("!!!", "warning_label_background_red", Color.FortyWhite),
    }

    interface IWarning {

        val isActive: Boolean

        fun show()
        fun hide()
    }

    companion object {
        const val movementSpeed = 5_000f
        const val defaultDisplayTime = 8_000
    }

    class ShowWarningEvent(val level: Level, val text: String, val displayTime: Int = defaultDisplayTime)
}

class WarningParent(
    val creator: ScreenCreator,
    val screen: RenderableScreen,
    private val events: EventPipeline? = null
) : IWarningParent {

    private var actor: CustomGroup? = null

    private val displayedWarnings: MutableList<Warning> = mutableListOf()

    init {
        events?.watchFor<IWarningParent.ShowWarningEvent> { event ->
            showTemporaryWarning(Warning(event.text, event.level), event.displayTime)
        }
    }

    fun getActor(): CustomGroup {
        actor?.let { return it }
        with(creator) {
            val created = createActorWithReceiver()
            actor = created
            created.onUpdate { this@WarningParent.update() }
            return created
        }
    }

    private fun updatePositionsOfWarnings() {
        val actor = actor ?: return
        var y = actor.height - 220f
        displayedWarnings.forEach { warning ->
            warning.targetY = y
            y -= warning.getActor().height + 5f
        }
    }

    private fun update() {
        displayedWarnings.forEach { it.update() }
    }

    private fun ScreenCreator.createActorWithReceiver() = newGroup {
        x = 0f
        y = 0f
        touchable = Touchable.disabled
        onLayout { height = parent.height }
        width = 100f
    }

    override fun showTemporaryWarning(text: String, level: IWarningParent.Level, time: Int): IWarningParent.IWarning {
        val warning = Warning(text, level)
        showTemporaryWarning(warning, time)
        return warning
    }

    override fun showTemporaryWarning(warning: IWarningParent.IWarning, time: Int) {
        warning.show()
        screen.afterMs(time) { warning.hide() }
    }

    override fun show(warning: IWarningParent.IWarning) {
        require(warning is Warning) { "Mock Warnings cant be used with real WarningParent" }
        if (warning.isActive) return
        val actor = warning.getActor()
        val parent = this.actor ?: return
        warning.isActive = true
        parent.addActor(actor)
        displayedWarnings.add(warning)
        actor.x = -500f
        actor.y = 0f
        warning.targetX = 0f
        updatePositionsOfWarnings()
    }

    override fun hide(warning: IWarningParent.IWarning) {
        require(warning is Warning) { "Mock Warnings cant be used with real WarningParent" }
        if (!warning.isActive) return
        warning.isActive = false
        warning.targetX = -500f
        screen.afterMs(400) {
            if (warning.isActive) return@afterMs
            val removed = displayedWarnings.remove(warning)
            if (!removed) return@afterMs
            actor?.removeActor(warning.getActor())
            updatePositionsOfWarnings()
        }
    }

    override fun warning(text: String, level: IWarningParent.Level): IWarningParent.IWarning = Warning(text, level)

    inner class Warning(
        val text: String,
        val level: IWarningParent.Level
    ) : IWarningParent.IWarning {

        var targetX = 0f
        var targetY = 0f

        override var isActive: Boolean = false

        private var actor: CustomBox? = null

        fun setTargetCoords(targetX: Float, targetY: Float) {
            this.targetX = targetX
            this.targetY = targetY
        }

        fun getActor(): CustomBox {
            actor?.let { return it }
            with(creator) {
                val created = createWithReceiver()
                actor = created
                return created
            }
        }

        fun update() {
            val actor = actor ?: return
            val movementSpeed = IWarningParent.movementSpeed
            val totalHeight = (this@WarningParent.actor?.height ?: return).coerceAtLeast(1f)
            val x = actor.x
            val y = actor.y
            val xDiff = abs(x - targetX)
            val yDiff = abs(y - targetY)
            // dividing by totalHeight doesn't really make sense for x
            val xSpeedMultiplier = (xDiff / totalHeight)
            val ySpeedMultiplier = (yDiff / totalHeight)
            val xSpeed = (movementSpeed * xSpeedMultiplier * Gdx.graphics.deltaTime).minMagnitude(4f)
            val ySpeed = (movementSpeed * ySpeedMultiplier * Gdx.graphics.deltaTime).minMagnitude(4f)

            when {
                x.epsilonEquals(targetX, epsilon = 4f) -> actor.x = targetX
                x > targetX -> actor.x -= xSpeed
                x < targetX -> actor.x += xSpeed
            }
            when {
                y.epsilonEquals(targetY, epsilon = 4f) -> actor.y = targetY
                y > targetY -> actor.y -= ySpeed
                y < targetY -> actor.y += ySpeed
            }
        }

        private fun ScreenCreator.createWithReceiver() = newBox {
            flexDirection = FlexDirection.ROW
            width = 340f
            height = 100f
            backgroundHandle = level.background
            verticalAlign = CustomAlign.CENTER
            box {
                relativeWidth(20f)
                relativeHeight(100f)
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER
                label("red wing", level.symbol, level.fontColor, 32)
            }
            box {
                relativeHeight(90f)
                width = 2f
                backgroundHandle = "forty_white_texture"
            }
            box {
                relativeHeight(100f)
                relativeWidth(72f)
                marginLeft = 8f

                label("roadgeek", text, level.fontColor, 16) {
                    relativeWidth(100f)
                    relativeHeight(100f)
                    wrap = true
                }
            }
        }

        override fun show() {
            this@WarningParent.show(this)
        }

        override fun hide() {
            this@WarningParent.hide(this)
        }

    }
}
