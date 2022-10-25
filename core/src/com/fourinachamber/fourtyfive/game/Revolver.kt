package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.screen.CustomLabel
import com.fourinachamber.fourtyfive.screen.InitialiseableActor
import com.fourinachamber.fourtyfive.screen.ScreenDataProvider
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import javax.swing.GroupLayout.Alignment

class Revolver : Widget(), ZIndexActor, InitialiseableActor {

    override var fixedZIndex: Int = 0
    private lateinit var screenDataProvider: ScreenDataProvider
    var slotTexture: TextureRegion? = null
    var slotFont: BitmapFont? = null
    var fontColor: Color? = null
    var fontScale: Float? = null
    var slotSize: Float = 10f
    private var wasInitialised: Boolean = false
    private lateinit var slots: Array<RevolverSlot>
    private var prefWidth: Float = 0f
    private var prefHeight: Float = 0f
    private lateinit var slotOffsets: Array<Vector2>

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (!wasInitialised) {
            calcOffsets()
            slots = Array(5) {
                val slot = RevolverSlot(it + 1, slotTexture, slotFont!!, fontColor!!, slotSize, slotSize)
                slot.setPosition(slotOffsets[it].x + x + prefWidth / 2, slotOffsets[it].y + y + prefHeight / 2)
                screenDataProvider.addActorToRoot(slot)
                slot.setFontScale(fontScale!!)
                slot
            }
            wasInitialised = true
        }
    }

    private fun calcOffsets() {
        prefHeight = slotSize * 3.5f
        prefWidth = slotSize * 4f
        slotOffsets = arrayOf(
            Vector2(-slotSize / 2, slotSize),
            Vector2(slotSize, 0f),
            Vector2(slotSize / 2, -slotSize * 1.5f),
            Vector2(-slotSize * 1.5f, -slotSize * 1.5f),
            Vector2(-slotSize * 2f, 0f)
        )
    }

    override fun getMinWidth(): Float = prefWidth
    override fun getMinHeight(): Float = prefHeight
    override fun getPrefWidth(): Float = prefWidth
    override fun getPrefHeight(): Float = prefHeight

}

class RevolverSlot(
    val num: Int,
    texture: TextureRegion?,
    font: BitmapFont,
    fontColor: Color,
    private val slotWidth: Float,
    private val slotHeight: Float
) : CustomLabel(num.toString(), LabelStyle(font, fontColor), TextureRegionDrawable(texture)) {

    init {
        setAlignment(Align.center)
    }

    override fun getWidth(): Float = slotWidth
    override fun getHeight(): Float = slotHeight
}
