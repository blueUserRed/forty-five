package com.microwavestudios.fortyfive.screen.actors

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputActorImpl
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.resources.ResourceManager
import com.microwavestudios.fortyfive.screen.DropShadow
import com.microwavestudios.fortyfive.screen.DropShadowActor
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.utils.Color
import com.microwavestudios.fortyfive.utils.Promise
import com.microwavestudios.fortyfive.utils.SubscribeableObserver
import com.microwavestudios.fortyfive.utils.TemplateString
import com.microwavestudios.fortyfive.utils.alpha
import com.microwavestudios.fortyfive.utils.automaticResourceGetter
import com.microwavestudios.fortyfive.utils.component1
import com.microwavestudios.fortyfive.utils.component2
import kotlin.math.absoluteValue

open class NewLabel(
    val screen: OnjScreen,
    text: String,
    private val backgroundHints: Array<String> = arrayOf(),
) : Widget(), ZIndexActor, DisableActor, OnLayoutActor, DropShadowActor,
    DebugActor by DebugActorImpl(), InputActor by InputActorImpl(),
    KotlinStyledActor, OffSettable {

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

    override var dropShadow: DropShadow? = null
    override var marginTop: Float = 0f
    override var marginBottom: Float = 0f
    override var marginLeft: Float = 0f
    override var marginRight: Float = 0f
    override var positionType: PositionType = PositionType.RELATIVE
    override var drawOffsetX: Float = 0f
    override var drawOffsetY: Float = 0f
    override var logicalOffsetX: Float = 0f
    override var logicalOffsetY: Float = 0f

    val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var backgroundHandle: String? by backgroundHandleObserver
    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen.lifetime, backgroundHints)

    private val fontHandleObserver = SubscribeableObserver<String?>(null)
    private var fontHandle: String? by fontHandleObserver
    private val fontGetter = automaticResourceGetter<BitmapFont>(fontHandleObserver, screen.lifetime, arrayOf())
    private val bitmapFont: BitmapFont? by fontGetter

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    var fontGroup: String? = null
        set(value) {
            if (field == value) return
            field = value
            paramsChanged()
        }

    var overrideFontHandle: ResourceHandle? = null
        set(value) {
            if (field == value) return
            field = value
            paramsChanged()
        }

    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            paramsChanged()
        }

    var fontSize: Int = 40
        set(value) {
            if (field == value) return
            field = value
            paramsChanged()
        }

    var wrap: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            paramsChanged()
        }

    var underline: Boolean = false

    var fontColor: com.badlogic.gdx.graphics.Color = Color.Black

    private var reuseLayout: Boolean = false
    private val layout: GlyphLayout = GlyphLayout()

    private var labelAlign: Int = Align.left

    private var prefWidth: Float = 0f
    private var prefHeight: Float = 0f

    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        screen.addDisposable(renderer)
        renderer
    }

    var useShader: Boolean = true

    var template: TemplateString? = null
    var skipTemplateTextCheck: Boolean = false

    val isTemplate
        get() = template != null

    init {
        initInput(this, screen)
        initDebugBounds(this, screen)
        screen.screenEvents.watchFor<OnjScreen.ScreenResizedEvent> { paramsChanged() }
    }

    fun setFontScale(scale: Float) {
        throw RuntimeException("use fontSize")
    }

    fun setAlignment(align: Int) {
        labelAlign = align
        reuseLayout = false
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateTemplate()
        super.draw(batch, parentAlpha)
        if (batch == null) return
        validate()
        drawBackground(batch, parentAlpha)
        drawText(batch, parentAlpha)
        if (underline) drawUnderline(batch)
    }

    private fun updateTemplate() {
        val template = template ?: return
        val newString = template.string
        if (text != newString && !skipTemplateTextCheck) {
            setText(newString)
        }
    }

    private fun drawUnderline(batch: Batch) {
        batch.end()
        val shapeRenderer = shapeRenderer
        screen.stage.viewport.apply()
        shapeRenderer.projectionMatrix = screen.stage.viewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = fontColor
        val (x, y) = localToStageCoordinates(Vector2(drawOffsetX, drawOffsetY))
        val width = layout.width
        shapeRenderer.rect(x, y - 1f, width, 3f)
        shapeRenderer.end()
        batch.begin()
    }

    private fun drawText(batch: Batch, parentAlpha: Float) {
        val font = bitmapFont ?: return
        val currentFontHandle = fontGetter.currentResourceHandle ?: return
        val variant = FortyFive
            .resourceManager
            .fonts
            .flatMap { it.variants }
            .find { it.resourceHandle == currentFontHandle }
        requireNotNull(variant) { "no variant for font handle: '$currentFontHandle'" }
        val fontSize = variant.size
        val target = this.fontSize
        val scale = target.toFloat() / fontSize.toFloat()
        font.data.setScale(scale)
        val color = com.badlogic.gdx.graphics.Color(fontColor.r, fontColor.g, fontColor.b, fontColor.a * color.a * parentAlpha)
        font.cache.tint(color)

        // TODO: figure out how to reuse the Layout again
        // doesnt work because of color, the layout for whatever reason determines the color the font is rendered with,
        // and if it isn't set everytime the color changes, the label will render with the wrong color
        // BUT you can't just set reuseLayout to false everytime the color changes, because, due to the fact that
        // the LibGDX Color class is mutable, it is impossible to know when the color changes

//        if (!reuseLayout) {
            layout.setText(font, text, color, width, labelAlign, wrap)
            prefWidth = layout.width
            prefHeight = layout.height + (-font.descent + font.ascent) * scale * 2
            invalidateHierarchy()
            reuseLayout = true
//        }
        val lHeight = layout.height
        val textY = if (Align.isTop(labelAlign)) {
            y + height
        } else if (Align.isCenterVertical(labelAlign)) {
            y + height / 2 + lHeight / 2
        } else {
            y + lHeight * 2
        }
        if (!useShader) {
            font.draw(batch, layout, x, textY)
            return
        }
        val shader = shaderPromise.getOrNull() ?: return
        batch.flush()
        batch.shader = shader.shader
        font.draw(batch, layout, x, textY)
        batch.flush()
        batch.shader = null
    }

    private fun paramsChanged() {
        invalidateHierarchy()
        reuseLayout = false
        val fontGroup = fontGroup ?: return
        val group = FortyFive.resourceManager.fonts.find { it.name == fontGroup }
        requireNotNull(group) { "unknown font group: '$fontGroup'" }
        require(group.variants.isNotEmpty()) { "font '$fontGroup' has no variants" }
        fontHandle = overrideFontHandle ?: findBestVariant(group).resourceHandle
    }

    private fun findBestVariant(group: ResourceManager.FontGroup): ResourceManager.FontVariant {
        val fontSize = fontSize
        val pixelRatio = screen.viewport.screenWidth / screen.viewport.worldWidth
        val pixelSize = fontSize * pixelRatio
        var bestCandidate: ResourceManager.FontVariant = group.variants.first()
        val biggerFontBias = 0
        val best = group
            .variants
            .filter { it.size > pixelSize + biggerFontBias }
            .minByOrNull { it.size }
        if (best != null) return best
        group.variants.forEach { variant ->
            val curSizeDiff = (bestCandidate.size - pixelSize).absoluteValue
            val checkSizeDiff = (variant.size - pixelSize).absoluteValue
            if (checkSizeDiff < curSizeDiff) bestCandidate = variant
        }
        return bestCandidate
    }

    private fun drawBackground(batch: Batch, parentAlpha: Float) {
        val background = background ?: return
        dropShadow?.doDropShadow(batch, screen, background, this)
        batch.flush()
        val old = batch.color.cpy()
        batch.setColor(old.r, old.g, old.b, parentAlpha * alpha)
        background.draw(batch, x, y, width, height)
        batch.flush()
        batch.setColor(old.r, old.g, old.b, old.a)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        reuseLayout = false
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun layout() {
        onLayout.forEach { it() }
        super.layout()
    }


    override fun getPrefWidth(): Float = prefWidth
    override fun getPrefHeight(): Float = prefHeight


    companion object : ResourceBorrower {

        val shaderPromise: Promise<BetterShader> by lazy {
            FortyFive.resourceManager.request(this, FortyFive.gameLifetime, "text_shader")
        }

    }

}

fun NewLabel.setText(text: String?) {
    this.text = text ?: "null"
}
