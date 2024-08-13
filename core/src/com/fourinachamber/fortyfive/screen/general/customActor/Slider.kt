package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.game.UserPrefs
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.between

class Slider(
    val sliderBackground: ResourceHandle,
    val handleRadius: Float,
    val handleColor: Color,
    val sliderHeight: Float,
    val min: Float,
    val max: Float,
    bind: String?,
    val screen: OnjScreen
) : Widget(), StyledActor, ResourceBorrower {

    override var styleManager: StyleManager? = null

    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    private var cursorPos: Float = 0.5f

    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        screen.addDisposable(renderer)
        renderer
    }

    private val sliderDrawable: Promise<Drawable> = ResourceManager.request(this, screen, sliderBackground)

    private val sliderShader: Promise<BetterShader> = ResourceManager.request(this, screen, "slider_shader")

    private val inputListener = object : DragListener() {

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            updatePos(x)
            return super.touchDown(event, x, y, pointer, button)
        }

        override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            updatePos(x)
            super.drag(event, x, y, pointer)
        }
    }

    private var bindTarget: BindTarget<Float>? = bind?.let { BindTargetFactory.get<Float>(it) }

    init {
        bindHoverStateListeners(this)
        addListener(inputListener)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null) return
        bindTarget?.let { cursorPos = 1 - (it.getter() - min) / (max - min) }
        batch.flush()
        val shader = sliderShader.getOrNull() ?: return
        val sliderDrawable = sliderDrawable.getOrNull() ?: return
        shader.shader.bind()
        shader.shader.setUniformf("u_pos", cursorPos)
        shader.prepare(screen)
        batch.shader = shader.shader
        sliderDrawable.draw(
            batch,
            x,
            y + height / 2 - sliderHeight / 2,
            width, sliderHeight
        )
        batch.flush()
        batch.shader = null
        batch.end()
        val r = shapeRenderer
        r.begin(ShapeRenderer.ShapeType.Filled)
        screen.viewport.apply()
        r.projectionMatrix = screen.viewport.camera.combined
        r.color = handleColor
        r.circle(x + width * cursorPos, y + height / 2, handleRadius)
        r.end()
        batch.begin()
    }

    private fun updatePos(mouseX: Float) {
        cursorPos = (mouseX / width).between(0f, 1f)
        bindTarget?.let { it.setter(min + (1f - cursorPos) * (max - min)) }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

}
