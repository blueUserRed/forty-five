package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Circle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.BoundedActor
import com.fourinachamber.fortyfive.utils.Promise
import java.lang.Float.max

class TutorialInfoActor(
    private val maskedBackgroundTextureName: ResourceHandle,
    private val circleRadiusMultiplier: Float,
    private val circleRadiusExtension: Float,
    screen: OnjScreen
) : CustomFlexBox(screen), ResourceBorrower {

    private val loadedBackground: Promise<Drawable> = ResourceManager.request(this, screen, maskedBackgroundTextureName)

    private var positionProvider: (() -> Circle)? = null

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch ?: return
        val focus = positionProvider?.let { it() }
        if (focus == null) {
            loadedBackground.getOrNull()?.draw(batch, x, y, width, height)
            super.draw(batch, parentAlpha)
            return
        }
        batch.flush()
        batch.shader = shader.shader
        shader.prepare(screen)
        val center = Vector2(focus.x, focus.y)
        val radius = focus.radius
        shader.shader.setUniformf("u_center", center)
        shader.shader.setUniformf("u_radius", radius)
        loadedBackground.getOrNull()?.draw(batch, x, y, width, height)
        batch.flush()
        batch.shader = null
        super.draw(batch, parentAlpha)
    }

    fun focusActor(name: String) {
        val actor = screen.namedActorOrError(name)
        if (actor !is BoundedActor) {
            throw RuntimeException("Actor '$name' must implement BoundedActor to be focused by TutorialInfoActor")
        }
        positionProvider = {
            val bounds = actor.getScreenSpaceBounds(screen)
            val center = Vector2(0f, 0f)
            bounds.getCenter(center)
            val radius = max(bounds.width, bounds.height) * 0.5f * circleRadiusMultiplier + circleRadiusExtension
            Circle(center, radius)
        }
    }

    fun focusByLambda(positionProvider: () -> Circle) {
        this.positionProvider = positionProvider
    }

    fun removeFocus() {
        positionProvider = null
    }

    companion object {

        private const val shaderPath: String = "shaders/tutorial_actor_shader.glsl"

        private val shader: BetterShader by lazy {
            BetterShader.load(shaderPath)
        }

    }

}