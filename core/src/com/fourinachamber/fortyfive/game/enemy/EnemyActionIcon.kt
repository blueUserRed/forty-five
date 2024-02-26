package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.TemplateString

class EnemyActionIcon(
    screen: OnjScreen,
    private val hiddenActionIconHandle: ResourceHandle
) : CustomImageActor(null, screen) {

    fun setupForAction(action: NextEnemyAction) = when (action) {

        is NextEnemyAction.None -> {
            hasHoverDetail = false
            backgroundHandle = null
        }

        is NextEnemyAction.HiddenEnemyAction -> {
            hasHoverDetail = true
            hoverText = "The enemy is going to perform an unknown action"
            backgroundHandle = hiddenActionIconHandle
        }

        is NextEnemyAction.ShownEnemyAction -> {
            hasHoverDetail = true
            hoverText = TemplateString(
                action.action.prototype.descriptionTemplate,
                action.action.descriptionParams
            ).string
            backgroundHandle = action.action.prototype.iconHandle
        }

    }

}
