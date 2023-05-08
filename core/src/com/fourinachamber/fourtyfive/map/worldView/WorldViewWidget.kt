package com.fourinachamber.fourtyfive.map.worldView

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.map.MapManager
import com.fourinachamber.fourtyfive.screen.ResourceHandle
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import onj.value.OnjArray
import onj.value.OnjObject
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2

class WorldViewWidget(config: OnjObject, screen: OnjScreen) : CustomImageActor(
    config.get<OnjObject>("worldView").get<String>("backgroundHandle"),
    screen
) {

    private val locationIndicatorDrawableHandle: ResourceHandle = config
        .get<OnjObject>("worldView")
        .get<String>("locationIndicatorHandle")

    private val locationIndicatorWidth = config
        .get<OnjObject>("worldView")
        .get<Double>("locationIndicatorWidth")
        .toFloat()

    private val locationIndicatorHeight = config
        .get<OnjObject>("worldView")
        .get<Double>("locationIndicatorHeight")
        .toFloat()

    private val locations: Map<String, Vector2> = config
        .get<OnjObject>("worldView")
        .get<OnjArray>("locations")
        .value
        .map { it as OnjObject }
        .associate {
            it.get<String>("name") to Vector2(it.get<Long>("x").toFloat(), it.get<Long>("y").toFloat())
        }

    private val locationIndicator: Drawable by lazy {
        ResourceManager.get(screen, locationIndicatorDrawableHandle)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        val curMap = MapManager.currentDetail.name
        val (x, y) = locations[curMap] ?: return
        locationIndicator.draw(batch, x, y, locationIndicatorWidth, locationIndicatorHeight)
    }
}
