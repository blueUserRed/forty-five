package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.screen.general.styles.StyleTarget
import com.fourinachamber.fourtyfive.keyInput.KeyInputMap
import com.fourinachamber.fourtyfive.utils.MainThreadOnly

class StyleableOnjScreen( // TODO: having this as a separate class is useless, merge it with OnjScreen
    viewport: Viewport,
    batch: Batch,
    background: String?,
    controllerContext: Any?,
    useAssets: List<String>,
    earlyRenderTasks: List<OnjScreen.() -> Unit>,
    lateRenderTasks: List<OnjScreen.() -> Unit>,
    private val styleTargets: List<StyleTarget>,
    namedActors: Map<String, Actor>,
    printFrameRate: Boolean,
) : OnjScreen(
    viewport,
    batch,
    background,
    controllerContext,
    useAssets,
    earlyRenderTasks,
    lateRenderTasks,
    mapOf(),
    namedActors,
    printFrameRate,
) {

    @MainThreadOnly
    override fun show() {
        super.show()
        for (target in styleTargets) target.init(this)
    }

    @MainThreadOnly
    override fun render(delta: Float) {
        for (styleTarget in styleTargets) styleTarget.update()
        super.render(delta)
    }

}
