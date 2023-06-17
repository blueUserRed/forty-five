package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import java.nio.channels.FileLock

class PersonWidget(
    override var fixedZIndex: Int,
    override var isHoveredOver: Boolean,
    override var backgroundHandle: ResourceHandle?,
    override var styleManager: StyleManager?,
    drawableHandle: ResourceHandle?,
    screen: OnjScreen,
    offsetX: Float,
    offsetY: Float,
    additionalScale: Float,
) : CustomImageActor(drawableHandle, screen) {



    override fun draw(batch: Batch?, parentAlpha: Float) {
//        super.draw(batch, parentAlpha)

    }


//    override fun SpriteBatch.draw()
}