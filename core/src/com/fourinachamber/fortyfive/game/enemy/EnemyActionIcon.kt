package com.fourinachamber.fortyfive.game.enemy

import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.DetailWidget
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.TemplateString

class EnemyActionIcon(
    screen: OnjScreen,
    private val hiddenActionIconHandle: ResourceHandle
) : CustomImageActor(null, screen) {

    fun setupForAction(action: NextEnemyAction) = when (action) {

        is NextEnemyAction.None -> {
            detailWidget=null
            backgroundHandle = null
        }

        is NextEnemyAction.HiddenEnemyAction -> {
            detailWidget = DetailWidget.SimpleBigDetailActor(screen){
                "The enemy is going to perform an unknown action"
            }

            backgroundHandle = hiddenActionIconHandle
        }

        is NextEnemyAction.ShownEnemyAction -> {

            detailWidget = DetailWidget.SimpleBigDetailActor(screen){
                TemplateString(
                    action.action.prototype.descriptionTemplate,
                    action.action.descriptionParams
                ).string
            }
            backgroundHandle = action.action.prototype.iconHandle
        }

    }

}
