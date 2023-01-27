package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.screen.general.styles.StyleTarget
import com.fourinachamber.fourtyfive.keyInput.KeyInputMap

class StyleableOnjScreen(
    drawables: MutableMap<String, Drawable>,
    cursors: Map<String, Cursor>,
    fonts: Map<String, BitmapFont>,
    particles: Map<String, ParticleEffect>,
    postProcessors: Map<String, PostProcessor>,
    children: List<Actor>,
    viewport: Viewport,
    batch: Batch,
    toDispose: List<Disposable>,
    earlyRenderTasks: List<OnjScreen.() -> Unit>,
    lateRenderTasks: List<OnjScreen.() -> Unit>,
    private val styleTargets: List<StyleTarget>,
    namedActors: Map<String, Actor>,
    behaviours: List<Behaviour>,
    printFrameRate: Boolean,
    keyInputMap: KeyInputMap?
) : OnjScreen(
    drawables,
    cursors,
    fonts,
    particles,
    postProcessors,
    children,
    viewport,
    batch,
    toDispose,
    earlyRenderTasks,
    lateRenderTasks,
    mapOf(),
    namedActors,
    behaviours,
    printFrameRate,
    keyInputMap
) {

    override fun show() {
        super.show()
        for (target in styleTargets) target.init(this)
    }

    override fun render(delta: Float) {
        for (styleTarget in styleTargets) styleTarget.update()
        super.render(delta)
    }

}
