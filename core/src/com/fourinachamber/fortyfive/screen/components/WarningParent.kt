package com.fourinachamber.fortyfive.screen.components

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.epsilonEquals
import com.fourinachamber.fortyfive.utils.minMagnitude
import kotlin.math.abs

class WarningParent(val creator: ScreenCreator, val screen: OnjScreen) {

    private var actor: CustomGroup? = null

    private val displayedWarnings: MutableList<Warning> = mutableListOf()

    fun getActor(): CustomGroup {
        actor?.let { return it }
        with(creator) {
            val created = createActorWithReceiver()
            actor = created
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

    fun update() {
        displayedWarnings.forEach { it.update() }
    }

    private fun ScreenCreator.createActorWithReceiver() = newGroup {
        x = 0f
        y = 0f
        onLayout { height = parent.height }
        width = 100f
    }

    fun showTemporaryWarning(text: String, level: Level, time: Int = defaultDisplayTime): Warning {
        val warning = Warning(text, level)
        showTemporaryWarning(warning, time)
        return warning
    }

    fun showTemporaryWarning(warning: Warning, time: Int = defaultDisplayTime) {
        warning.show()
        screen.afterMs(time) { warning.hide() }
    }

    fun show(warning: Warning) {
        val actor = warning.getActor()
        val parent = this.actor ?: return
        parent.addActor(actor)
        displayedWarnings.add(warning)
        actor.x = -500f
        actor.y = 0f
        updatePositionsOfWarnings()
    }

    fun hide(warning: Warning) {
        if (warning !in displayedWarnings) return
        warning.targetX = -400f
        screen.afterMs(400) {
            val removed = displayedWarnings.remove(warning)
            if (!removed) return@afterMs
            actor?.removeActor(warning.getActor())
            updatePositionsOfWarnings()
        }
    }

    inner class Warning(
        val text: String,
        val level: Level
    ) {

        var targetX = 0f
        var targetY = 0f

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
                label("red_wing", level.symbol, level.fontColor)
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

                // TODO: add smaller variant of roadgeek
                label("roadgeek", text, level.fontColor) {
                    setFontScale(0.5f)
                    relativeWidth(100f)
                    relativeHeight(100f)
                    wrap = true
                }
            }
        }

        fun show() {
            this@WarningParent.show(this)
        }

        fun hide() {
            this@WarningParent.hide(this)
        }

    }

    companion object {
        const val movementSpeed = 5_000f
        const val defaultDisplayTime = 8_000
    }

    enum class Level(val symbol: String, val background: String, val fontColor: com.badlogic.gdx.graphics.Color) {

        INFO("i", "warning_label_background_grey", Color.Black),
        MID("!", "warning_label_background_red", Color.FortyWhite),
        HIGH("!!!", "warning_label_background_red", Color.FortyWhite),
    }

}
