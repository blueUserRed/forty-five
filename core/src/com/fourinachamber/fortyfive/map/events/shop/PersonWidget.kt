package com.fourinachamber.fortyfive.map.events.shop

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.DragAndDropBehaviourFactory
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import onj.value.OnjNamedObject
import onj.value.OnjObject

class PersonWidget(
    private val offsetX: Float,
    private val offsetY: Float,
    val scale: Float,
    val screen: OnjScreen,
) : Widget(), ResourceBorrower, StyledActor {

    private lateinit var personDrawable: Drawable

    private var defaultOffsetX: Float = 0F
    private var defaultOffsetY: Float = 0F
    private var defaultScale: Float = 0F
    private lateinit var textureName: String
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean=false

    init {
        bindHoverStateListeners(this)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (this::personDrawable.isInitialized) {
            val xPos = offsetX + defaultOffsetX
            val yPos = offsetY + defaultOffsetY
            val w = personDrawable.minWidth * scale * defaultScale
            val h = personDrawable.minHeight * scale * defaultScale
            personDrawable.draw(batch, xPos, yPos, w, h)
        }
    }

    fun setDrawable(imgData: OnjObject) {
        textureName = imgData.get<String>("textureName")
        defaultOffsetX = imgData.get<Double>("offsetX").toFloat()
        defaultOffsetY = imgData.get<Double>("offsetY").toFloat()
        defaultScale = imgData.get<Double>("scale").toFloat()
        ResourceManager.borrow(this, textureName)
        personDrawable = ResourceManager.get(this, textureName)
    }



    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    fun giveResourcesBack() {
//        ResourceManager.giveBack(this, textureName)
    }

}
