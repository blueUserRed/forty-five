package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles

class Selector(
    private val font: BitmapFont,
    private val fontScale: Float,
    private val screen: OnjScreen,
) : Widget(), StyledActor {

    override var styleManager: StyleManager? = null

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    private val options: List<String> = listOf("1", "2", "3")
    private var curOptionIndex: Int = 0

    private val arrowTexture: Texture by lazy {
        ResourceManager.get(screen, "common_symbol_arrow")
    }

    private val glyphLayout: GlyphLayout = GlyphLayout(font.apply { data.setScale(fontScale) }, options[curOptionIndex])

    private val arrowWidth = 20f
    private val arrowHeight = 20f

    private val clickListener = object : ClickListener() {

        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            onClick(x)
            super.clicked(event, x, y)
        }
    }

    init {
        addListener(clickListener)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null) return
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

    private fun switch(amount: Int) {
        curOptionIndex = (options.size + curOptionIndex + amount) % options.size
        font.data.setScale(fontScale)
        glyphLayout.setText(font, options[curOptionIndex])
    }

    private fun onClick(x: Float): Unit = when {
        x > width / 2 -> switch(1)
        else -> switch(-1)
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }
}
