package com.fourinachamber.fortyfive.map.worldView

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import onj.value.OnjArray
import onj.value.OnjObject
import com.fourinachamber.fortyfive.utils.component1
import com.fourinachamber.fortyfive.utils.component2

class WorldViewWidget(config: OnjObject, val screen: OnjScreen) : Widget(), StyledActor {

    private val backgroundHandle: ResourceHandle = config.get<OnjObject>("worldView").get<String>("backgroundHandle")

    private val background: Drawable by lazy {
        ResourceManager.get(screen, backgroundHandle)
    }

    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false

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

    init {
        bindHoverStateListeners(this)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        background.draw(batch, x, y, width, height)
        val curMap = MapManager.currentDetail.name
        val (x, y) = locations[curMap] ?: return
        val adjX = (width / background.minWidth) * x
        val adjY = height - (height / background.minHeight) * y
        locationIndicator.draw(batch, adjX, adjY, locationIndicatorWidth, locationIndicatorHeight)
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }
}
