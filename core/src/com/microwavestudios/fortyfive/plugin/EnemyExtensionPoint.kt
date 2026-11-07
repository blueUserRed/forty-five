package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.screen.commonComponents.EnemyActorCreator

@PluginExtensionPoint
interface EnemyExtensionPoint {

    fun enemyActorSelector(): ((Enemy) -> EnemyActorCreator?)? = null

}
