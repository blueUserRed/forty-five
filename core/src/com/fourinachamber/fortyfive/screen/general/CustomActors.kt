package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE0
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE1
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.fourinachamber.fortyfive.keyInput.InputActor
import com.fourinachamber.fortyfive.keyInput.InputActorImpl
import com.fourinachamber.fortyfive.keyInput.KeyboardFocusable
import com.fourinachamber.fortyfive.screen.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.utils.*
import kotlin.math.max

open class CustomLabel(
    val screen: OnjScreen,
    text: String,
    labelStyle: LabelStyle,
    private val isDistanceField: Boolean,
    private val backgroundHints: Array<String> = arrayOf(),
) : Label(text, labelStyle), ZIndexActor, DisableActor, OnLayoutActor, DropShadowActor,
    DebugBoundsActor by DebugBoundsActorImpl(), InputActor by InputActorImpl(),
    KotlinStyledActor, OffSettable {

    override var dropShadow: DropShadow? = null

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

    var underline: Boolean = false

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)

    override var marginTop: Float = 0f
    override var marginBottom: Float = 0f
    override var marginLeft: Float = 0f
    override var marginRight: Float = 0f
    override var positionType: PositionType = PositionType.RELATIV

    override var drawOffsetX: Float = 0f
    override var drawOffsetY: Float = 0f
    override var logicalOffsetX: Float = 0f
    override var logicalOffsetY: Float = 0f

    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        screen.addDisposable(renderer)
        renderer
    }

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    init {
        initInput(this, screen)
        initDebugBounds(this)
        touchable = Touchable.disabled
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun setFontScale(fontScale: Float) {
        super.setFontScale(fontScale)
        if (fontScale !in (0.7f..1.3f)) {
            badTexture(name ?: text.toString(), comment = "font scale is $fontScale; Choose different font instead")
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) {
            super.draw(null, parentAlpha)
            return
        }
        drawBackground(batch, parentAlpha)
        if (!isDistanceField) {
            super.draw(batch, parentAlpha)
            return
        }
        val prevShader = batch.shader
        batch.shader = fontShader
        x += drawOffsetX
        y += drawOffsetY
        super.draw(batch, parentAlpha)
        x -= drawOffsetX
        y -= drawOffsetY
        batch.shader = prevShader
        if (underline) {
            batch.end()
            val shapeRenderer = shapeRenderer
            screen.stage.viewport.apply()
            shapeRenderer.projectionMatrix = screen.stage.viewport.camera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = style.fontColor
            val (x, y) = localToStageCoordinates(Vector2(drawOffsetX, drawOffsetY))
            val width = glyphLayout.width
            shapeRenderer.rect(x, y - 1f, width, 3f)
            shapeRenderer.end()
            batch.begin()
        }
    }

    override fun layout() {
        onLayout.forEach { it() }
        super.layout()
    }

    protected fun drawBackground(batch: Batch, parentAlpha: Float) {
        val background = background ?: return
        dropShadow?.doDropShadow(batch, screen, background, this)
        batch.flush()
        val old = batch.color.cpy()
        batch.setColor(old.r, old.g, old.b, parentAlpha * alpha)
        background.draw(batch, x, y, width, height)
        batch.flush()
        batch.setColor(old.r, old.g, old.b, old.a)
    }

    companion object {

        val fontShader: ShaderProgram by lazy {
            val shader = ShaderProgram(
                Gdx.files.internal("shaders/font/font.vert"),
                Gdx.files.internal("shaders/font/font.frag")
            )
            if (!shader.isCompiled) {
                throw RuntimeException(shader.log)
            }
            shader
        }

    }

}

open class TemplateStringLabel(
    screen: OnjScreen,
    var templateString: TemplateString,
    labelStyle: LabelStyle,
    isDistanceField: Boolean,
    backgroundHints: Array<String> = arrayOf(),
) : CustomLabel(
    screen,
    templateString.string,
    labelStyle,
    isDistanceField,
    backgroundHints,
) {

    var skipTextCheck = false

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val newString = templateString.string
        if (!textEquals(newString) && !skipTextCheck) {
            setText(newString)
        }
        super.draw(batch, parentAlpha)
    }
}

/**
 * custom Image that implements functionality for z-indices and masking
 */
open class CustomImageActor(
    drawableHandle: ResourceHandle?,
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf(),
) : Image(), Maskable, ZIndexActor, DisableActor, OnLayoutActor, AnimatedActor,
    OffSettable, InputActor by InputActorImpl(), DropShadowActor,
    KotlinStyledActor, DebugBoundsActor by DebugBoundsActorImpl() {

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

    override var dropShadow: DropShadow? = null

    override val animationsNeedingUpdate: MutableList<AnimatedActor.NeedsUpdate> = mutableListOf()

    override var marginTop: Float = 0f
    override var marginBottom: Float = 0f
    override var marginLeft: Float = 0f
    override var marginRight: Float = 0f
    override var positionType: PositionType = PositionType.RELATIV

    override var mask: Texture? = null
    override var invert: Boolean = false
    override var maskScaleX: Float = 1f
    override var maskScaleY: Float = 1f
    override var maskOffsetX: Float = 0f
    override var maskOffsetY: Float = 0f

    override var drawOffsetX: Float = 0F
    override var drawOffsetY: Float = 0F
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    private val backgroundHandleObserver = SubscribeableObserver(drawableHandle)
    var backgroundHandle: String? by backgroundHandleObserver

    val loadedDrawableResourceGetter = automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)
    val loadedDrawable: Drawable? by loadedDrawableResourceGetter

    /**
     * if set to true, the preferred-, min-, and max-dimension functions will return the dimensions with the scaling
     * already applied
     */
    var reportDimensionsWithScaling: Boolean = false
        set(value) {
            field = value
            invalidateHierarchy()
        }

    /**
     * if set to true, the scale of the image will be ignored when drawing
     */
    var ignoreScalingWhenDrawing: Boolean = false

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    init {
        initInput(this, screen)
        initDebugBounds(this)
        touchable = Touchable.disabled
    }

    override fun drawDebugBounds(shapes: ShapeRenderer?) {
        drawCustomDebugBounds(shapes)
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateAnimations()
        val mask = mask

        drawable = loadedDrawable

        if (batch == null || drawable == null) {
            return
        }

        validate()
        x += drawOffsetX
        y += drawOffsetY
        val width = if (ignoreScalingWhenDrawing) width else width * scaleX
        val height = if (ignoreScalingWhenDrawing) height else height * scaleY

        if (mask == null) {
            val c = batch.color.cpy()
            batch.setColor(c.r, c.g, c.b, parentAlpha * alpha)
            if (rotation != 0f) {
                val drawable = drawable
                if (drawable !is TransformDrawable) throw RuntimeException(
                    "attempted to rotate an image, but the " +
                            "drawable does not implement TransformDrawable"
                )
                dropShadow?.doDropShadowRotated(batch, screen, drawable, this)
                drawable.draw(batch, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
            } else {
                dropShadow?.doDropShadow(batch, screen, drawable, this)
                drawable.draw(batch, x, y, width, height)
            }
            batch.color = c

            x -= drawOffsetX
            y -= drawOffsetY
            return
        }

        val prevShader = batch.shader
        batch.shader = maskingShader

        maskingShader.bind()
        maskingShader.setUniformi("u_texture2", 1)
        maskingShader.setUniformf("u_offset", Vector2(maskOffsetX / width, maskOffsetY / height))
        maskingShader.setUniformf("u_scale", Vector2(maskScaleX, maskScaleY))

        Gdx.gl.glActiveTexture(GL_TEXTURE1)
        mask.bind()
        Gdx.gl.glActiveTexture(GL_TEXTURE0)

        drawable.draw(batch, x, y, width, height)
        batch.flush()

        batch.shader = prevShader

        x -= drawOffsetX
        y -= drawOffsetY
    }

    override fun layout() {
        onLayout.forEach { it() }
        super.layout()
    }

    override fun toString(): String {
        return "CustomImageActor($backgroundHandle)"
    }

    companion object {

        val maskingShader: ShaderProgram by lazy {
            val program = ShaderProgram(
                Gdx.files.internal("shaders/masking/masking.vert"),
                Gdx.files.internal("shaders/masking/masking.frag")
            )
            if (!program.isCompiled) {
                throw RuntimeException(program.log)
            }
            program
        }
    }
}

/**
 * custom h-group, that implements [ZIndexActor] and [ZIndexGroup]
 */
open class CustomHorizontalGroup(
    val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf(),
) : HorizontalGroup(), ZIndexGroup, ZIndexActor, OffSettable, OnLayoutActor,
    KotlinStyledActor {

    override var drawOffsetX: Float = 0f
    override var drawOffsetY: Float = 0f
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    override var fixedZIndex: Int = 0

    override var marginTop: Float = 0F
    override var marginBottom: Float = 0F
    override var marginLeft: Float = 0F
    override var marginRight: Float = 0F
    override var positionType: PositionType = PositionType.RELATIV

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        this.x += drawOffsetX
        this.y += drawOffsetY
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
        this.x -= drawOffsetX
        this.y -= drawOffsetY
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun layout() {
        onLayout.forEach { it() }
        (0 until children.size).forEach { (children[it] as? Layout)?.validate() }
        super.layout()
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

}

/**
 * custom v-group, that implements [ZIndexActor] and [ZIndexGroup]
 */
open class CustomVerticalGroup(
    val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf()
) : VerticalGroup(), ZIndexGroup, ZIndexActor, OnLayoutActor {

    override var fixedZIndex: Int = 0

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun layout() {
        onLayout.forEach { it() }
        // These cant use normal iterators because .validate may use an iterator over children as well,
        // and LibGDX doesn't like that
        (0 until children.size).forEach { (children[it] as? Layout)?.validate() }
        super.layout()
    }

    fun invalidateChildren() {
        children.forEach { (it as? Layout)?.invalidate() }
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }
}

open class CustomGroup(
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf()
) : WidgetGroup(), ZIndexGroup, ZIndexActor, OffSettable, OnLayoutActor, KotlinStyledActor,
    DropShadowActor, AnimatedActor, InputActor by InputActorImpl(), DebugBoundsActor by DebugBoundsActorImpl() {

    override val animationsNeedingUpdate: MutableList<AnimatedActor.NeedsUpdate> = mutableListOf()

    override var drawOffsetX: Float = 0f
    override var drawOffsetY: Float = 0f
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    override var marginTop: Float = 0f
    override var marginBottom: Float = 0f
    override var marginLeft: Float = 0f
    override var marginRight: Float = 0f
    override var positionType: PositionType = PositionType.RELATIV

    override var fixedZIndex: Int = 0

    override var keyboardFocusable: KeyboardFocusable = KeyboardFocusable.GROUP

    /**
     * the children in the original order as they were added
     */
    protected val _originalChildren: MutableList<Actor> = mutableListOf()
    val originalChildren: List<Actor>
        get() = _originalChildren

    private var sortedChildrenDirty: Boolean = false

    var forcedPrefWidth: Float? = null
    var forcedPrefHeight: Float? = null

    protected var layoutPrefWidth = 0F
    protected var layoutPrefHeight = 0F

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    var backgroundHandle: String? by backgroundHandleObserver
    protected val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)
    override var dropShadow: DropShadow? = null

    var manualBackground: Drawable? = null

    private val onUpdateCallbacks: MutableList<() -> Unit> = mutableListOf()

    init {
        initInput(this, screen)
        initDebugBounds(this)
    }

    override fun drawDebugBounds(shapes: ShapeRenderer?) {
        drawCustomDebugBounds(shapes)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateAnimations()
        validate()
        batch ?: return
        val oldX = x
        val oldY = y
        if (isDragged) {
            x = dragX
            y = dragY
        }
        this.x += drawOffsetX
        this.y += drawOffsetY
        if (batch.color != color || parentAlpha != 1f) {
            val batchColor = batch.color.cpy()
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
            drawBackground(batch)
            batch.color = batchColor
        } else {
            drawBackground(batch)
        }

        if (sortedChildrenDirty) {
            resortZIndices()
            sortedChildrenDirty = false
        }
        super.draw(batch, parentAlpha)
        x = oldX
        y = oldY
    }

    override fun act(delta: Float) {
        onUpdateCallbacks.forEach { it() }
        super.act(delta)
    }

    fun onUpdate(callback: () -> Unit) {
        onUpdateCallbacks.add(callback)
    }

    private fun drawBackground(batch: Batch?) {
        val background = manualBackground ?: background
        background?.let {
            dropShadow?.doDropShadow(batch, screen, it, this)
            if (it is TransformDrawable) {
                it.draw(batch, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
            } else {
                it.draw(batch, x, y, width, height)
            }
        }
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun layout() {
        onLayout.forEach { it() }
        (0 until children.size).forEach { (children[it] as? Layout)?.validate() }
        super.layout()
    }

    fun invalidateChildren() {
        children.forEach { (it as? Layout)?.invalidate() }
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun addActor(actor: Actor) {
        sortedChildrenDirty = true
        _originalChildren.add(actor)
        invalidate()
        super.addActor(actor)
    }

    override fun addActorAt(index: Int, actor: Actor) {
        sortedChildrenDirty = true
        _originalChildren.add(index, actor)
        invalidate()
        super.addActorAt(index, actor)
    }

    override fun removeActor(actor: Actor, unfocus: Boolean): Boolean {
        sortedChildrenDirty = true
        val index = children.indexOf(actor, true)
        if (index == -1) return false
        invalidate()
        removeActorAt(_originalChildren.indexOf(actor), unfocus)
        return true
    }

    override fun removeActorAt(index: Int, unfocus: Boolean): Actor {
        sortedChildrenDirty = true
        val actor = _originalChildren.removeAt(index)
        invalidate()
        return super.removeActorAt(children.indexOf(actor), unfocus)
    }

    override fun clearChildren(unfocus: Boolean) {
        sortedChildrenDirty = true
        invalidate()
        super.clearChildren(unfocus)
    }

    override fun clear() {
        _originalChildren.clear()
        super.clear()
        invalidate()
    }

    override fun clearChildren() {
        _originalChildren.clear()
        invalidate()
        super.clearChildren()
    }

    fun walk(): Sequence<Actor> = sequence {
        childrenInCorrectOrderOrOriginal().forEach { child ->
            yield(child)
            if (child is CustomGroup) yieldAll(child.walk())
        }
    }
}

class Spacer(
    var definedWidth: Float = 0f,
    var definedHeight: Float = 0f,
) : Widget(), OnLayoutActor {

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun layout() {
        onLayout.forEach { it() }
        super.layout()
    }

    override fun drawDebug(renderer: ShapeRenderer?) {
        renderer ?: return
        renderer.flush()
        renderer.color = Color.Red
        val width = max(definedWidth, 10f)
        val height = max(definedHeight, 10f)
        renderer.rect(x, y, width, height)
    }

    override fun setWidth(width: Float) {
    }

    override fun setHeight(height: Float) {
    }

    override fun setSize(width: Float, height: Float) {
    }

    override fun getWidth(): Float = definedWidth
    override fun getMinWidth(): Float = definedWidth
    override fun getMaxWidth(): Float = definedWidth
    override fun getPrefWidth(): Float = definedWidth

    override fun getHeight(): Float = definedHeight
    override fun getMinHeight(): Float = definedHeight
    override fun getMaxHeight(): Float = definedHeight
    override fun getPrefHeight(): Float = definedHeight
}
