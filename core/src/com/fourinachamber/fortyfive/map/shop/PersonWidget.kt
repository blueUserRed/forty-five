package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import onj.value.OnjObject
import java.nio.channels.FileLock
import com.fourinachamber.fortyfive.utils.plus
import onj.value.OnjNamedObject

class PersonWidget(
    private val offsetX: Float,
    private val offsetY: Float,
    val scale: Float,
    val dropBehaviour:OnjNamedObject,
    val screen: OnjScreen,
) : Widget(), ResourceBorrower,StyledActor {

    private lateinit var personDrawable: Drawable

    private val defOffset: HashMap<String, Float> = HashMap()

    init {
        bindHoverStateListeners(this)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (this::personDrawable.isInitialized) {
            val xPos = offsetX + defOffset["offsetX"]
            val yPos = offsetY + defOffset["offsetY"]
            val w = personDrawable.minWidth * scale * defOffset["scale"]!!
            val h = personDrawable.minHeight * scale * defOffset["scale"]!!
            personDrawable.draw(batch, xPos, yPos, w, h)
        }
    }

    fun setDrawable(imgData: OnjObject) {
        val textureName = imgData.get<String>("textureName")
        defOffset["offsetX"] = imgData.get<Double>("offsetX").toFloat()
        defOffset["offsetY"] = imgData.get<Double>("offsetY").toFloat()
        defOffset["scale"] = imgData.get<Double>("scale").toFloat()
        ResourceManager.borrow(this, textureName)
        personDrawable = ResourceManager.get(this, textureName)
        ResourceManager.giveBack(this, textureName)
    }
    
    fun addDropTarget(dragAndDrop: DragAndDrop) {
        val behaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
            dropBehaviour.name,
            dragAndDrop,
            this,
            dropBehaviour
        )
        dragAndDrop.addTarget(behaviour)
    }

    override var styleManager: StyleManager?=null

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    override var isHoveredOver: Boolean=false
}
