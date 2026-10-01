package com.microwavestudios.fortyfive.game.widgets

import com.microwavestudios.fortyfive.game.StatusEffect
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.NewLabel
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.Colors

object StatusEffectBarCreator {

    fun createStatusBar(
        creator: ScreenCreator,
        target: StatusEffectBarTarget
    ): CustomGroup {

        val statusEffects: MutableList<Triple<StatusEffect, CustomBox, NewLabel>> = mutableListOf()

        val group = with(creator) { newGroup {

            onLayout {
                val statusEffectAmount = statusEffects.size
                val scale = findCorrectScale(statusEffectAmount, width, height)
                val effectWidth = statusEffectWidth * scale
                val effectHeight = statusEffectHeight * scale

                var x = 0f
                var y = height - effectHeight

                children.forEach { child ->
                    child.setBounds(x, y, effectWidth, effectHeight)
                    x += effectWidth
                    if (effectWidth <= width - effectWidth) return@forEach
                    x = 0f
                    y -= effectHeight
                }
            }
        } }

        target.onStatusEffectApplied { statusEffect ->
            lateinit var label: NewLabel
            val box = with(creator) { newBox {
                flexDirection = FlexDirection.COLUMN
                image {
                    relativeWidth(100f)
                    heightByAspectRatio(1.0)
                    backgroundHandle = statusEffect.iconHandle
                }
                label = label("red wing", statusEffect.getDisplayText(), Colors.FortyWhite, 10) {
                    syncDimensions()
                }
            } }
            group.addActor(box)
            statusEffects.add(Triple(statusEffect, box, label))
        }

        target.onStatusEffectRemoved { statusEffect ->
            val (_, box, _) = statusEffects.find { it.first === statusEffect } ?: return@onStatusEffectRemoved
            group.removeActor(box)
        }

        target.onUpdate {
            statusEffects.forEach { (statusEffect, _, label) ->
                label.text = statusEffect.getDisplayText()
            }
        }

        return group
    }

    private fun findCorrectScale(statusEffectAmount: Int, width: Float, height: Float): Float {
        scales.forEach { scale ->
            val effectWidth = statusEffectWidth * scale
            val effectHeight = statusEffectHeight * scale
            val lines = (height / effectHeight).toInt()
            val effectsPerLine = (width / effectWidth).toInt()
            val possibleEffects = lines * effectsPerLine
            if (possibleEffects >= statusEffectAmount) return scale
        }
        return scales.last()
    }


    abstract class StatusEffectBarTarget {

        abstract fun onStatusEffectApplied(callback: (StatusEffect) -> Unit)

        abstract fun onStatusEffectRemoved(callback: (StatusEffect) -> Unit)

        abstract fun onUpdate(callback: () -> Unit)
    }

    private const val statusEffectHeight = 70f
    private const val statusEffectWidth = 50f
    private val scales = arrayOf(1f, 0.8f, 0.5f, 0.4f)

}
