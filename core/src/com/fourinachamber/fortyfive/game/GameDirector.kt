package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyPrototype
import com.fourinachamber.fortyfive.utils.between
import com.fourinachamber.fortyfive.utils.templateParam
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.Double.max

class GameDirector(private val controller: GameController) {


    fun init() {
        val enemiesOnj = OnjParser.parseFile(Gdx.files.internal("config/enemies.onj").file())
        enemiesFileSchema.assertMatches(enemiesOnj)
        enemiesOnj as OnjObject
        val enemyPrototypes = Enemy.readEnemies(enemiesOnj.get<OnjArray>("enemies"))
        val chosen = chooseEnemy(enemyPrototypes)
        val enemy = scaleAndCreateEnemy(chosen)
        controller.initEnemyArea(enemy)
    }

    private fun chooseEnemy(prototypes: List<EnemyPrototype>): EnemyPrototype {
        return prototypes.random() // TODO: some more sophisticated logic
    }

    private fun scaleAndCreateEnemy(prototype: EnemyPrototype): Enemy {
        // TODO: some more sophisticated logic
        val defeated = SaveState.enemiesDefeated
        val health = (prototype.baseHealth * (1f + defeated / 10f)).toInt()
        val damage = (prototype.baseDamage * (1f + defeated / 10f)).toInt()
        return prototype.create(health, damage)
    }

    // TODO: remove, just for testing
    private var curEvaluation: Double by templateParam("game.curEvaluation", 0.0)

    fun evaluateState(): Double { // TODO: make private
        val enemy = controller.enemyArea.enemies[0]

        val turns = controller.turnCounter - 1

        // the ratio of how many reserves were spent to how the amount of turns, minus the baseReserves
        // a value higher than 0 indicates that the player gains reserves from different sources, a negative value
        // indicates suboptimal reserve usage
        val avgReserveGainOrLoss = if (turns == 0) {
            0.0
        } else {
            controller.reservesSpent.toDouble() / turns.toDouble()  - controller.baseReserves
        }
        // how much damage the bullets in the revolver would do when fired ignoring effects
        val damageInRevolver = controller.revolver.slots.sumOf { it.card?.baseDamage ?: 0 }
        val damagedPercent = 1.0 -
                (max(enemy.currentHealth.toDouble() - damageInRevolver, 0.0) / enemy.health.toDouble())

        val cardsDrawn = if (turns == 0) {
            0.0
        } else {
            (controller.cardsDrawn - controller.cardsToDrawInFirstRound) / turns.toDouble() - controller.cardsToDraw
        }

        val eval = if (damagedPercent > 0.8) {
            damagedPercent
        } else {
            val x = ((avgReserveGainOrLoss.between(-3.0, 3.0) + 3.0) / 6.0) - 0.5
            x * 0.4 + damagedPercent * 0.6 + (cardsDrawn / 10.0)
        }.between(0.0, 1.0)
        curEvaluation = eval
        return eval
    }

    companion object {

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
