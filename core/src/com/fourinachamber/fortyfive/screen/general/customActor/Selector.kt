package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.Promise

class Selector(
    private val font: BitmapFont,
    var fontScale: Float = 1f,
    private val arrowTextureHandle: ResourceHandle,
    private val arrowWidth: Float = 20f,
    private val arrowHeight: Float = 20f,
    bind: String,
    private val screen: OnjScreen,
) : Widget(), StyledActor, ResourceBorrower {

    override var styleManager: StyleManager? = null

    var forcedPrefHeight: Float? = null
    var forcedPrefWidth: Float? = null

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    private val options: List<Pair<String, Any>>
    private var curOptionIndex: Int = 0

    private val arrowTexture: Promise<Texture> = ResourceManager.request(this, screen, arrowTextureHandle)

    private val bindTarget: BindTarget<*> = BindTargetFactory.getAnyType(bind)

    private val glyphLayout: GlyphLayout = GlyphLayout()

    private val clickListener = object : ClickListener() {

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            onClick(x)
            super.clicked(event, x, y)
        }
    }

    private var lastValue: Any = Unit

    init {
        bindHoverStateListeners(this)
        addListener(clickListener)
        options = bindTarget
            .mappings
            .map { (key, value) -> value to key }
        checkValue()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null) return
        checkValue()
        val arrowTexture = arrowTexture.getOrNull() ?: return
        batch.draw(
            arrowTexture,
            x,
            y + height / 2 - arrowHeight / 2,
            arrowWidth, arrowHeight,
            0, 0,
            arrowTexture.width, arrowTexture.height,
            true, false
        )
        batch.flush()
        batch.draw(
            arrowTexture,
            x + width - arrowWidth,
            y + height / 2 - arrowHeight / 2,
            arrowWidth, arrowHeight,
            0, 0,
            arrowTexture.width, arrowTexture.height,
            false, false
        )
        val shader = CustomLabel.fontShader
        batch.flush()
        shader.bind()
        batch.shader = shader
        font.draw(batch, glyphLayout, x + width / 2 - glyphLayout.width / 2, y + height / 2 + glyphLayout.height / 2)
        batch.flush()
        batch.shader = null
    }

    private fun checkValue() {
        val curValue = bindTarget.getter()
        if (curValue == lastValue) return
        lastValue = curValue
        curOptionIndex = options.indexOfFirst { it.second == curValue }
        font.data.setScale(fontScale)
        glyphLayout.setText(font, options[curOptionIndex].first)
    }

    private fun switch(amount: Int) {
        curOptionIndex = (options.size + curOptionIndex + amount) % options.size
        @Suppress("UNCHECKED_CAST") // I hate generics
        (bindTarget.setter as (Any) -> Unit)(options[curOptionIndex].second)
    }

    private fun onClick(x: Float): Unit = when {
        x > width / 2 -> switch(1)
        else -> switch(-1)
    }

    override fun getPrefWidth(): Float = forcedPrefWidth ?: super.getPrefWidth()

    override fun getPrefHeight(): Float = forcedPrefHeight ?: super.getPrefHeight()

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }
}
