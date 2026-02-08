package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.between

class Slider(
    val sliderBackground: ResourceHandle,
    val handleRadius: Float,
    val handleColor: Color,
    val sliderHeight: Float,
    val min: Float,
    val max: Float,
    bind: String?,
    val screen: CustomScreen
) : Widget(), ResourceBorrower {

    var cursorPos: Float = 0.5f
        private set

    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        screen.addDisposable(renderer)
        renderer
    }

    private val sliderDrawable: Promise<Drawable> = FortyFive.resourceManager.request(this, screen.lifetime, sliderBackground)

    private val sliderShader: Promise<BetterShader> = FortyFive.resourceManager.request(this, screen.lifetime, "slider_shader")

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
        addListener(inputListener)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null) return
        bindTarget?.let { cursorPos = (it.getter() - min) / (max - min) }
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
        r.projectionMatrix = batch.projectionMatrix
        r.transformMatrix = batch.transformMatrix
        r.color = handleColor
        r.circle(x + width * cursorPos, y + height / 2, handleRadius)
        r.end()
        batch.begin()
    }

    fun updatePos(mouseX: Float) {
        cursorPos = (mouseX / width).between(0f, 1f)
        bindTarget?.setter?.let { it(min + cursorPos * (max - min)) }
    }

    fun move(by: Float) {
        cursorPos = (cursorPos + by).between(0f, 1f)
        bindTarget?.setter?.let { it(min + cursorPos * (max - min)) }
    }

}
