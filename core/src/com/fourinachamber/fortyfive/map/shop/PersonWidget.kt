package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import onj.value.OnjObject
import java.nio.channels.FileLock
import com.fourinachamber.fortyfive.utils.plus

class PersonWidget(
    private val offsetX: Float,
    private val offsetY: Float,
    val scale: Float,
    val screen: OnjScreen,
) : Widget(), ResourceBorrower {

    private lateinit var personDrawable: Drawable

    private val defOffset: HashMap<String, Float> = HashMap()

//    private lateinit var textureName: ResourceHandle

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (this::personDrawable.isInitialized) {
            val xPos = offsetX + defOffset["offsetX"]
            val yPos = offsetY + defOffset["offsetY"]
            val w = personDrawable.minWidth * scale * defOffset["scale"]!!
            val h = personDrawable.minHeight * scale * defOffset["scale"]!!

//            println(""+xPos+" "+yPos+" "+w+" "+h+" ")
            personDrawable.draw(batch, xPos, yPos, w, h)
        }
    }

    public fun setDrawable(imgData: OnjObject) {
//        if (this::textureName.isInitialized) {
        val textureName = imgData.get<String>("textureName")
        defOffset["offsetX"] = imgData.get<Double>("offsetX").toFloat()
        defOffset["offsetY"] = imgData.get<Double>("offsetY").toFloat()
        defOffset["scale"] = imgData.get<Double>("scale").toFloat()
        ResourceManager.borrow(this, textureName)
        personDrawable = ResourceManager.get(this, textureName)
        ResourceManager.giveBack(this, textureName)
//        }
    }
}
