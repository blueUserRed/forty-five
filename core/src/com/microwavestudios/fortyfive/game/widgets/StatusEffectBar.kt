package com.microwavestudios.fortyfive.game.widgets

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.game.StatusEffect
import com.microwavestudios.fortyfive.game.card.DetailDescriptionHandler
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.CustomBox
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.NewLabel
import com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.screen.screens.EncounterScreen
import com.microwavestudios.fortyfive.utils.Colors
import com.microwavestudios.fortyfive.utils.EventPipeline
import kotlin.math.roundToInt

object StatusEffectBarCreator {

    fun createStatusBar(
        creator: ScreenCreator,
        target: StatusEffectBarTarget,
        iconStepDown: Float = 0f,
    ): CustomGroup {

        val statusEffects: MutableList<Triple<StatusEffect, CustomBox, NewLabel>> = mutableListOf()

        val group = with(creator) { newGroup {

            onLayout {
                val statusEffectAmount = statusEffects.size
                val scale = findCorrectScale(statusEffectAmount, width, height, iconStepDown)
                val effectWidth = statusEffectWidth * scale
                val effectHeight = statusEffectHeight * scale
                val distance = (width % effectWidth).coerceIn(10f, 20f)

                var x = 0f
                var y = height - effectHeight

                var lines = 1
                children.forEach { child ->
                    child.setBounds(x, y, effectWidth, effectHeight)
                    x += effectWidth
                    x += distance
                    y -= iconStepDown
                    if (effectWidth <= width - effectWidth) return@forEach
                    x = 0f
                    lines++
                    y = height - lines * effectHeight
                }
            }
        } }

        target.onStatusEffectApplied { statusEffect ->
            lateinit var label: NewLabel
            val box = with(creator) { newBox {
                flexDirection = FlexDirection.COLUMN
                horizontalAlign = CustomAlign.CENTER
                image {
                    relativeHeight(50f)
                    widthByAspectRatio(1.0)
                    backgroundHandle = statusEffect.iconHandle
                }
                label = label("red wing", statusEffect.getDisplayText(), Colors.FortyWhite, 20) {
                    setAlignment(Align.center)
                    relativeHeight(50f)
                    relativeWidth(100f)
                    onLayout { fontSize = (width * 0.5f).roundToInt() }
                }
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                bindDetailToInputState(GameInputs.States.focused)
                val text = {
                    listOf(DetailDescriptionHandler.descriptions[statusEffect.name]?.second ?: "")
                }
                detailWidget = DetailWidget.ComplexBigDetailActor(
                    screen,
                    text = text,
                    effects = DetailDescriptionHandler.allTextEffects,
                    subtexts = { DetailDescriptionHandler.extractAllExtraDescriptions(text()) }
                )
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

    private fun findCorrectScale(
        statusEffectAmount: Int,
        width: Float, height: Float,
        iconStepDown: Float
    ): Float {
        scales.forEach { scale ->
            val effectWidth = statusEffectWidth * scale
            val distance = (width % effectWidth).coerceIn(10f, 20f)
            val stepDown = iconStepDown * scale
            val effectsPerLine = (width / (effectWidth + distance)).toInt()
            val effectHeight = statusEffectHeight * scale
            val lines = ((height - stepDown * effectsPerLine) / effectHeight).toInt()
            val possibleEffects = lines * effectsPerLine
            if (possibleEffects >= statusEffectAmount) return scale
        }
        return scales.last()
    }


    abstract class StatusEffectBarTarget {

        abstract fun onStatusEffectApplied(callback: (StatusEffect) -> Unit)

        abstract fun onStatusEffectRemoved(callback: (StatusEffect) -> Unit)

        abstract fun onUpdate(callback: () -> Unit)


        class EnemyTarget(
            val enemy: Enemy,
            val gameEvents: EventPipeline
        ) : StatusEffectBarTarget() {

            override fun onStatusEffectApplied(callback: (StatusEffect) -> Unit) {
                enemy.enemyEvents.watchFor<Enemy.StatusEffectAdded> { (effect) ->
                    callback(effect)
                }
            }

            override fun onStatusEffectRemoved(callback: (StatusEffect) -> Unit) {
                enemy.enemyEvents.watchFor<Enemy.StatusEffectRemoved> { (effect) ->
                    callback(effect)
                }
            }

            override fun onUpdate(callback: () -> Unit) {
                gameEvents.watchFor<EncounterScreen.UpdateUiEvent> { callback() }
            }

        }

        class PlayerTarget(val gameEvents: EventPipeline) : StatusEffectBarTarget() {

            override fun onStatusEffectApplied(callback: (StatusEffect) -> Unit) {
                gameEvents.watchFor<GameControllerImpl.Events.AddedPlayerStatusEffect> { (effect) ->
                    callback(effect)
                }
            }

            override fun onStatusEffectRemoved(callback: (StatusEffect) -> Unit) {
                gameEvents.watchFor<GameControllerImpl.Events.RemovedPlayerStatusEffect> { (effect) ->
                    callback(effect)
                }
            }

            override fun onUpdate(callback: () -> Unit) {
                gameEvents.watchFor<EncounterScreen.UpdateUiEvent> { callback() }
            }

        }
    }

    private const val statusEffectHeight = 80f
    private const val statusEffectWidth = 40f
    private val scales = arrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f)

}
