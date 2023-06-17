package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.EnemyPrototype
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

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

    companion object {

        private val enemiesFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/enemies.onjschema").file())
        }

    }
}
