package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputActorImpl
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.utils.Promise

class Selector(
    private val font: BitmapFont,
    var fontScale: Float = 1f,
    var fontColor: Color,
    private val arrowTextureHandle: ResourceHandle,
    private val arrowWidth: Float = 20f,
    private val arrowHeight: Float = 20f,
    private val bindTarget: BindTarget<*>,
    private val screen: RenderableScreen,
    private val settingChangedCallback: (() -> Unit)? = null,
) : Widget(), InputActor by InputActorImpl(), ResourceBorrower {

    private val options: List<Pair<String, Any>>
    private var curOptionIndex: Int = 0

    private val arrowTexture: Promise<Texture> = FortyFive.resourceManager.request(this, screen.lifetime, arrowTextureHandle)

    private val glyphLayout: GlyphLayout = GlyphLayout()

    private val clickListener = object : ClickListener() {

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            onClick(x)
            super.clicked(event, x, y)
        }
    }

    private var lastValue: Any = Unit

    init {
        initInput(this, screen)
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
        font.data.setScale(fontScale)
        font.color = fontColor
        font.draw(batch, glyphLayout, x + width / 2 - glyphLayout.width / 2, y + height / 2 + glyphLayout.height / 2)
    }

    private fun checkValue() {
        val curValue = bindTarget.getter()
        if (curValue == lastValue) return
        lastValue = curValue
        curOptionIndex = options.indexOfFirst { it.second == curValue }
        font.data.setScale(fontScale)
        glyphLayout.setText(font, options[curOptionIndex].first)
    }

    fun switch(amount: Int) {
        curOptionIndex = (options.size + curOptionIndex + amount) % options.size
        @Suppress("UNCHECKED_CAST") // I hate generics
        (bindTarget.setter as (Any) -> Unit)(options[curOptionIndex].second)
        settingChangedCallback?.invoke()
    }

    fun onClick(x: Float): Unit = when {
        x > width / 2 -> switch(1)
        else -> switch(-1)
    }

}
