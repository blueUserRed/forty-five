// contains currently unused actors, but they may be important in the future
@file:Suppress("unused")

package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE0
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE1
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.selection.SelectionGroup
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import io.github.orioncraftmc.meditate.enums.YogaUnit
import ktx.actors.alpha
import ktx.actors.onTouchEvent
import onj.value.*
import kotlin.math.abs
import kotlin.math.max

open class CustomLabel(
    override val screen: OnjScreen,
    text: String,
    labelStyle: LabelStyle,
    private val isDistanceField: Boolean,
    private val backgroundHints: Array<String> = arrayOf(),
    override var detailWidget: DetailWidget? = null,
    override val partOfHierarchy: Boolean = false
) : Label(text, labelStyle), ZIndexActor, DisableActor, KeySelectableActor, OnLayoutActor, DropShadowActor,
    StyledActor, BackgroundActor, HasOnjScreen, DisplayDetailActor, KotlinStyledActor {

    override var dropShadow: DropShadow? = null

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

    override var group: SelectionGroup? = null
    override var isFocusable: Boolean = false
    override var isFocused: Boolean = false
    override var isSelectable: Boolean = false
    override var isSelected: Boolean = false

    //    override var isSelected: Boolean = false
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false
    override var styleManager: StyleManager? = null

    var underline: Boolean = false

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)


    override var marginTop: Float = 0f
    override var marginBottom: Float = 0f
    override var marginLeft: Float = 0f
    override var marginRight: Float = 0f
    override var positionType: PositionType = PositionType.RELATIV


    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        screen.addDisposable(renderer)
        renderer
    }

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    var forcedPrefHeight: Float? = null
    var forcedPrefWidth: Float? = null

    init {
        bindDefaultListeners(this, screen)
        registerOnFocusDetailActor(this, screen)
    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    @MainThreadOnly
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
        super.draw(batch, parentAlpha)
        batch.shader = prevShader
        if (underline) {
            batch.end()
            val shapeRenderer = shapeRenderer
            screen.stage.viewport.apply()
            shapeRenderer.projectionMatrix = screen.stage.viewport.camera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = style.fontColor
            val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
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

    override fun getPrefWidth(): Float = forcedPrefWidth ?: super.getPrefWidth()

    override fun getPrefHeight(): Float = forcedPrefHeight ?: super.getPrefHeight()

    override fun setWidth(width: Float) {
        super.setWidth(width)
    }

    override fun initStyles(screen: OnjScreen) {
        addLabelStyles(screen)
        addBackgroundStyles(screen)
        addDisableStyles(screen)
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
    detailWidget: DetailWidget? = null,
    partOfHierarchy: Boolean = false
) : CustomLabel(
    screen,
    templateString.string,
    labelStyle,
    isDistanceField,
    backgroundHints,
    detailWidget,
    partOfHierarchy
), BackgroundActor {

    var skipTextCheck = false

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        val newString = templateString.string
        if (!textEquals(newString) && !skipTextCheck) {
            setText(newString)
        }
        super.draw(batch, parentAlpha)
    }

    override fun initStyles(screen: OnjScreen) {
        super.initStyles(screen)
        addTemplateLabelStyles(screen)
    }
}

/**
 * custom Image that implements functionality for z-indices and masking
 */
open class CustomImageActor(
    drawableHandle: ResourceHandle?,
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf(),
    override val partOfHierarchy: Boolean = false,
) : Image(), Maskable, ZIndexActor, DisableActor, OnLayoutActor, AnimatedActor,
    KeySelectableActor, StyledActor, BackgroundActor, OffSettable, DisplayDetailActor, HasOnjScreen,
    KotlinStyledActor, DragAndDroppableActor {

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

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
    var tintColor: Color? = null

    override var drawOffsetX: Float = 0F
    override var drawOffsetY: Float = 0F
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    var forcedPrefWidth: Float? = null
    var forcedPrefHeight: Float? = null

    override var detailWidget: DetailWidget? = null

    private val backgroundHandleObserver = SubscribeableObserver(drawableHandle)
    override var backgroundHandle: String? by backgroundHandleObserver

    val loadedDrawableResourceGetter = automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)
    val loadedDrawable: Drawable? by loadedDrawableResourceGetter

    override var isSelected: Boolean = false
    override var isSelectable: Boolean = false

    override var isDraggable: Boolean = false
    override var inDragPreview: Boolean = false
    override var targetGroups: List<String> = listOf()
    override var resetCondition: ((Actor?) -> Boolean)? = null
    override val onDragAndDrop: MutableList<(Actor, Actor) -> Unit> = mutableListOf()

    override var group: SelectionGroup? = null
    override var isFocusable: Boolean = false
        set(value) {
            if (this.isFocused) screen.focusedActor = null
            field = value
        }
    override var isFocused: Boolean = false
    override var isClicked: Boolean = false

    override var isHoveredOver: Boolean = false

    override var styleManager: StyleManager? = null

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
        bindDefaultListeners(this, screen)
        registerOnFocusDetailActor(this, screen)
    }

//    override fun generateDetailActor(): Actor? = mutableMapOf<String, OnjValue>(
//        "hoverText" to OnjString(hoverText)
//    ).also {
//        it.putAll(additionalHoverData)
//    }

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    @MainThreadOnly
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
                drawable.draw(batch, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
            } else {
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

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return if (reportDimensionsWithScaling) {
            Rectangle(x, y, width, height)
        } else {
            Rectangle(x, y, width * scaleX, height * scaleY)
        }
    }

    override fun getPrefWidth(): Float = forcedPrefWidth ?: super.getPrefWidth()
    override fun getPrefHeight(): Float = forcedPrefHeight ?: super.getPrefHeight()

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
        addBackgroundStyles(screen)
        addDisableStyles(screen)
        addOffsetableStyles(screen)
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

open class CustomFlexBox(
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf(),
    private val hasHoverDetail: Boolean = false,
    private val hoverText: String = ""
) : FlexBox(), ZIndexActor, ZIndexGroup, StyledActor, BackgroundActor, AnimatedActor,
    Detachable, OffSettable, HasOnjScreen, DisableActor, BoundedActor, DisplayDetailActor,
    InOutAnimationActor, ResourceBorrower {

    override var fixedZIndex: Int = 0

    override val animationsNeedingUpdate: MutableList<AnimatedActor.NeedsUpdate> = mutableListOf()

    private val dropShadowShader: Promise<BetterShader> by lazy {
//        ResourceManager.request<BetterShader>(this, screen, "gaussian_blur_shader")
        ResourceManager.request<BetterShader>(this, screen, "drop_shadow_shader")
    }

    override var isDisabled: Boolean = false

    override var isHoveredOver: Boolean = false

    override var styleManager: StyleManager? = null
    override var isClicked: Boolean = false

    override var drawOffsetX: Float = 0F
    override var drawOffsetY: Float = 0F
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    override var detailWidget: DetailWidget? = null

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)

    private var reattachTo: Group? = null

    override val attached: Boolean
        get() = reattachTo == null


    var onDisplay: () -> Timeline = {
        Timeline.timeline {
            action {
                this@CustomFlexBox.isVisible = true;
            }
        }
    }

    var onHide: () -> Timeline = {
        Timeline.timeline {
            action {
                this@CustomFlexBox.isVisible = false;
            }
        }
    }

    init {
        bindHoverStateListeners(this)
        registerOnFocusDetailActor(this, screen)
    }

//    override fun generateDetailActor(): Actor? = mutableMapOf<String, OnjValue>(
//        "hoverText" to OnjString(hoverText)
//    ).also {
//        it.putAll(additionalHoverData)
//    }

    @Suppress("UNCHECKED_CAST")
    fun getAllChildren(): List<Pair<YogaNode, Actor>> {
        val field = FlexBox::class.java.getDeclaredField("nodes")
        field.isAccessible = true
        val result: com.badlogic.gdx.utils.Array<Any> = field.get(this) as com.badlogic.gdx.utils.Array<Any>
        val innerClass = FlexBox::class.java.declaredClasses.find { it.simpleName == "YogaActor" }!!
        val getNode = innerClass.getDeclaredMethod("getNode")
        val getActor = innerClass.getDeclaredMethod("getActor")
        getNode.isAccessible = true
        getActor.isAccessible = true
        return result.map {
            getNode.invoke(it) as YogaNode to getActor.invoke(it) as Actor
        }
    }

    override fun clear() {
        getAllChildren().forEach { remove(it.first) }
    }

    override fun detach() {
        val parent = parent
        reattachTo = parent
        if (parent is CustomFlexBox) {
            parent.remove(styleManager?.node)
        } else {
            parent.removeActor(this)
        }
    }

    override fun reattach() {
        val target = reattachTo ?: run {
            FortyFiveLogger.warn("scene", "attempted to reattach, but no target is defined")
            return
        }
        reattachTo = null
        if (target is CustomFlexBox) {
            val node = target.add(this)
            val oldManager = styleManager
            styleManager = oldManager!!.copyWithNode(node)
            screen.swapStyleManager(oldManager, styleManager!!)
        } else {
            target.addActor(this)
        }
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        updateAnimations()
        x += drawOffsetX
        y += drawOffsetY
        if (batch != null && background != null) {
            val old = batch.color.a
            if (parentAlpha * alpha < 1f) batch.flush()
            batch.setColor(batch.color.r, batch.color.g, batch.color.b, parentAlpha * alpha)
            val background = background
            if (background is TransformDrawable) {
                background.draw(batch, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
            } else {
                background?.draw(batch, x, y, width, height)
            }
            if (parentAlpha * alpha < 1f) batch.flush()

            batch.setColor(batch.color.r, batch.color.g, batch.color.b, old)
        }
        super.draw(batch, parentAlpha)
        x -= drawOffsetX
        y -= drawOffsetY
    }


    override fun initStyles(screen: OnjScreen) {
        addFlexBoxStyles(screen)
        addBackgroundStyles(screen)
        addDetachableStyles(screen)
        addOffsetableStyles(screen)
        addDisableStyles(screen)
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    protected fun getTotalOffset(): Vector2 {
        val res = Vector2()
        var cur: Group = this
        while (cur.parent != null) {
            val parent = cur.parent
            if (parent is CustomFlexBox) {
                res.x += parent.drawOffsetX
                res.y += parent.drawOffsetY
            }
            cur = parent
        }
        return res
    }

    override fun display(): Timeline = onDisplay()

    override fun hide(): Timeline = onHide()

}

class CustomScrollableFlexBox(
    screen: OnjScreen,
    private val isScrollDirectionVertical: Boolean,
    private val scrollDistance: Float,
    private val isBackgroundStretched: Boolean,
    private val scrollbarBackgroundName: String?,
    private val scrollbarName: String?,
    private val scrollbarSide: String?,
    backgroundHints: Array<String> = arrayOf(),
) : CustomFlexBox(screen, backgroundHints, false) { //TODO fix bug with children with fixed size

    private val scrollListener = object : InputListener() {
        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            stage.scrollFocus = this@CustomScrollableFlexBox
        }

        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            if (x > width || x < 0 || y > height || y < 0) stage?.scrollFocus = null
        }

        override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
            this@CustomScrollableFlexBox.scroll(amountY)
            return super.scrolled(event, x, y, amountX, amountY)
        }
    }

    var needsScrollbar: Boolean = true
        private set

    private val dragListener = object : InputListener() {
        var startPos = 0f
        private var touchDownX: Float = -1f
        private var touchDownY: Float = -1f
        private var stageTouchDownX: Float = -1f
        private var stageTouchDownY: Float = -1f
        private var dragStartX = 0f
        private var dragStartY: Float = 0f
        private var dragLastX: Float = 0f
        private var dragLastY: Float = 0f
        private var dragX: Float = 0f
        private var dragY: Float = 0f
        var pressedPointer = -1
        var dragging = false

        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (pressedPointer != -1) return false
//            if (pointer == 0 && this.button != -1 && button != this.button) return false
            pressedPointer = pointer
            touchDownX = x
            touchDownY = y
            stageTouchDownX = event.stageX
            stageTouchDownY = event.stageY
            return true
        }

        override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            if (pointer != pressedPointer) return
            if (!dragging) {
                dragging = true
                dragStartX = x
                dragStartY = y
                dragStart()
                dragX = x
                dragY = y
            }
            if (dragging) {
                dragLastX = dragX
                dragLastY = dragY
                dragX = x
                dragY = y
                drag(x, y)
            }
        }

        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            if (pointer == pressedPointer) {
                cancel()
            }
        }

        fun cancel() {
            dragging = false
            pressedPointer = -1
        }

        fun dragStart() {
            val parentPos = this@CustomScrollableFlexBox.localToStageCoordinates(Vector2(0, 0))
            if (scrollbarHandle == null) {
                startPos = Float.NaN
                return
            }
            startPos = if (isScrollDirectionVertical) {
                val relX = (if (scrollbarSide == "left") 0F else width - scrollbarHandle!!.width)
                val relY = scrollbarHandle!!.y - parentPos.y
                if (touchDownX < relX + scrollbarHandle!!.width && touchDownX > relX
                    && touchDownY < relY + scrollbarHandle!!.height && touchDownY > relY
                ) scrollbarHandle!!.y
                else Float.NaN
            } else {
                val relX = scrollbarHandle!!.x - parentPos.x
                val relY = (if (scrollbarSide == "top") height - scrollbarHandle!!.height else 0F)
                if (touchDownX < relX + scrollbarHandle!!.width && touchDownX > relX
                    && touchDownY < relY + scrollbarHandle!!.height && touchDownY > relY
                ) scrollbarHandle!!.x
                else Float.NaN
            }
        }

        fun drag(x: Float, y: Float) {
            if (startPos.isNaN()) return
            val parentPos = this@CustomScrollableFlexBox.localToStageCoordinates(Vector2(0, 0))
            if (isScrollDirectionVertical) {
                val curHeight =
                    (if (scrollbarLength.unit == YogaUnit.PERCENT) height * scrollbarLength.value / 100F else scrollbarLength.value)
                val max = lastMax + cutBottom
                val curSize = curHeight * curHeight / (max + curHeight)
                scrollbarHandle!!.y = startPos + (y - touchDownY)
                offset =
                    -(scrollbarHandle!!.y - parentPos.y - curHeight - (height - curHeight) / 2 + curSize) * max / (curHeight - curSize)
            } else {
                val curWidth =
                    (if (scrollbarLength.unit == YogaUnit.PERCENT) width * scrollbarLength.value / 100F else scrollbarLength.value)

                scrollbarHandle!!.x = (startPos + x - touchDownX)
                val max = lastMax
                val curSize = curWidth * curWidth / (max + curWidth)
                offset =
                    (scrollbarHandle!!.x - parentPos.x - (width - curWidth) / 2) * max / (curWidth - curSize) + cutLeft
            }
            offset = if (isReversed) lastMax + cutRight - offset else offset
            invalidate()
        }
    }

    var currentlyDraggedChild: Actor? = null

    init {
        addListener(scrollListener)
        addListener(dragListener)
    }

    private fun scroll(offset: Float) {
        if (!isReversed == isScrollDirectionVertical) this.offset += offset * scrollDistance
        else this.offset -= offset * scrollDistance
        invalidate()
    }

    private var offset: Float = 0F
    var cutLeft: Float = 0F
    var cutRight: Float = 0F
    var cutTop: Float = 0F
    var cutBottom: Float = 0F
    private var lastMax: Float = 0F
    private var scrollbarBackground: CustomImageActor? = null
    private var scrollbarHandle: CustomImageActor? = null
    var scrollbarWidth: Float = 0F
    var scrollbarLength: YogaValue = YogaValue(100F, YogaUnit.PERCENT)
    private var isInitialLayout = true
    private var isReversed = false

    override fun layout() {
//        layoutScrollBar()
//        if (maxSizeInScrollDirection == -1F) {
//            while (children.size > 0) {
//                val childAt =
//                    root.getChildAt(0); tempChildren.add(children[0]); remove(childAt); children.removeIndex(0)
//            }
//            super.layout()
//            maxSizeInScrollDirection = 1F
////            tempChildren.forEach{ add(it) }
//        }
//        if (height == 54.0F) {
//            maxSizeInScrollDirection=54F
//            tempChildren.forEach { add(it) }
//        }
        super.layout()
        checkInitialLayout()
        layoutChildren()
        layoutScrollBar()
    }

    private fun checkInitialLayout() {
        if (isInitialLayout) {
            styleManager?.node?.let {
                isInitialLayout = false
                if (root.flexDirection == YogaFlexDirection.COLUMN_REVERSE || root.flexDirection == YogaFlexDirection.ROW_REVERSE) {
                    isReversed = true
                }
            }
        }
    }

    private fun layoutScrollBar() {
        if (scrollbarBackgroundName != null) {
            if (scrollbarBackground == null) {
                scrollbarBackground = screen.namedActorOrError(scrollbarBackgroundName) as CustomImageActor
                remove(scrollbarBackground?.styleManager?.node)
            }
            if (needsScrollbar) layoutScrollbarBackground()
        }
        if (scrollbarName != null) {
            if (scrollbarHandle == null) {
                scrollbarHandle = screen.namedActorOrError(scrollbarName) as CustomImageActor
                remove(scrollbarHandle?.styleManager?.node)
            }
            if (needsScrollbar) layoutScrollbarHandle()
        }
    }

    private fun layoutChildren() {
        if (isScrollDirectionVertical) {
            lastMax = children.map { -it.y }.maxOrNull() ?: -cutBottom

            if (0F < lastMax + cutBottom) {
                needsScrollbar = true
                offset = offset.between(0F, lastMax + cutBottom)
                children.forEach { it.y += offset }
            } else {
                needsScrollbar = false
            }
        } else {
            val negDist: Float = (children.minOf { it.x })
            lastMax = ((children.map { it.x + it.width }.maxOrNull() ?: 0F) - width) - negDist
            if (lastMax + cutRight > cutLeft / scrollDistance) {
                needsScrollbar = true
                offset = offset.between(cutLeft / scrollDistance, lastMax + cutRight)
                val localOff = if (isReversed) lastMax + cutRight - offset else offset
                children.forEach { it.x -= localOff + negDist }
            } else {
                needsScrollbar = false
            }
        }
    }

    private fun layoutScrollbarBackground() {
        if (isScrollDirectionVertical) {
            if (scrollbarSide != null && scrollbarSide == "left") {
                scrollbarBackground?.x = x
            } else {
                scrollbarBackground?.x = x + width - scrollbarWidth
            }
            scrollbarBackground?.height =
                (if (scrollbarLength.unit == YogaUnit.PERCENT) height * scrollbarLength.value / 100F else scrollbarLength.value)
            scrollbarBackground?.y = y + (height - scrollbarBackground!!.height) / 2
            scrollbarBackground?.width = scrollbarWidth
        } else {
            if (scrollbarSide != null && scrollbarSide == "top") {
                scrollbarBackground?.y = y + height - scrollbarWidth
            } else {
                scrollbarBackground?.y = y
            }
            scrollbarBackground?.width =
                (if (scrollbarLength.unit == YogaUnit.PERCENT) width * scrollbarLength.value / 100F else scrollbarLength.value)
            scrollbarBackground?.x = x + (width - scrollbarBackground!!.width) / 2
            scrollbarBackground?.height = scrollbarWidth
        }
    }

    private fun layoutScrollbarHandle() {
        val localOff = if (isReversed) lastMax + cutRight - offset else offset
        if (isScrollDirectionVertical) {
            val max = lastMax + cutBottom
            val maxSize =
                (if (scrollbarLength.unit == YogaUnit.PERCENT) height * scrollbarLength.value / 100F else scrollbarLength.value)
            val curSize = maxSize * maxSize / (max + maxSize)
            val curPos = localOff / max * (maxSize - curSize)
            if (scrollbarSide != null && scrollbarSide == "left") {
                scrollbarHandle!!.x = x
            } else {
                scrollbarHandle!!.x = x + width - scrollbarWidth
            }
            scrollbarHandle!!.width = scrollbarWidth
            scrollbarHandle!!.height = curSize
            scrollbarHandle!!.y = y + (height + maxSize) / 2 - curPos - curSize
        } else {
            val offset = localOff - cutLeft
            val max = lastMax
            val curWidth =
                (if (scrollbarLength.unit == YogaUnit.PERCENT) width * scrollbarLength.value / 100F else scrollbarLength.value)
            val curSize = curWidth * curWidth / (max + curWidth)
            val curPos = offset / max * (curWidth - curSize)
            if (scrollbarSide != null && scrollbarSide == "top") {
                scrollbarHandle!!.y = y + height - scrollbarWidth
            } else {
                scrollbarHandle!!.y = y
            }
            scrollbarHandle!!.height = scrollbarWidth
            scrollbarHandle!!.width = curSize
            scrollbarHandle!!.x = x + curPos + (width - curWidth) / 2
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch ?: return
        batch.flush()
        val viewport = screen.stage.viewport
        val xPixel =
            (Gdx.graphics.width - viewport.leftGutterWidth - viewport.rightGutterWidth) / viewport.worldWidth
        val yPixel =
            (Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / viewport.worldHeight
        if (isBackgroundStretched) {
            if (drawBackgroundStretched(batch, xPixel, viewport, yPixel, parentAlpha)) return
        } else {
            background?.draw(batch, x, y, width, height)
            if (drawItemsWithScissor(xPixel, viewport, yPixel, batch, parentAlpha)) return
        }
        if (needsScrollbar) {
            layoutScrollBar() //TODO ugly, but i don't know a better solution
            val off = getTotalOffset()
            scrollbarBackground?.let {
                it.x += off.x
                it.y += off.y
                it.draw(batch, parentAlpha)
                it.x -= off.x
                it.y -= off.y
            }
            scrollbarHandle?.let {
                it.x += off.x
                it.y += off.y
                it.draw(batch, parentAlpha)
                it.x -= off.x
                it.y -= off.y
            }
        }
        val curChild = this.currentlyDraggedChild
        if (curChild != null) {
            val coordinates = curChild.parent.localToStageCoordinates(Vector2())
            curChild.x += coordinates.x
            curChild.y += coordinates.y
            curChild.draw(batch, alpha)
            curChild.x -= coordinates.x
            curChild.y -= coordinates.y
        }
    }

    private fun drawItemsWithScissor(
        xPixel: Float,
        viewport: Viewport,
        yPixel: Float,
        batch: Batch,
        parentAlpha: Float
    ): Boolean {
        val scissor = Rectangle(
            xPixel * (x + cutLeft) + viewport.leftGutterWidth,
            yPixel * (y + cutBottom) + viewport.topGutterHeight,
            xPixel * (width - cutLeft - cutRight),
            yPixel * (height - cutTop - cutBottom)
        )
        batch.flush()
        if (!ScissorStack.pushScissors(scissor)) return true
        currentlyDraggedChild?.isVisible = false
        super.draw(batch, parentAlpha)
        currentlyDraggedChild?.isVisible = true
        batch.flush()
        ScissorStack.popScissors()
        batch.flush()
        return false
    }

    private fun drawBackgroundStretched(
        batch: Batch,
        xPixel: Float,
        viewport: Viewport,
        yPixel: Float,
        parentAlpha: Float
    ): Boolean {
        val scissorBack = Rectangle(
            xPixel * x + viewport.leftGutterWidth,
            yPixel * y + viewport.topGutterHeight,
            xPixel * width,
            yPixel * height
        )
        if (!ScissorStack.pushScissors(scissorBack)) return true
        validate()
        if (background != null) {
            if (isScrollDirectionVertical) background?.draw(
                batch,
                x,
                y - lastMax + max(0F, offset) - cutTop,
                width,
                height + lastMax
            ) else {
                background?.draw(batch, x + offset + cutLeft, y, width + lastMax, height)
            }
        }
        batch.flush()
        ScissorStack.popScissors()
        batch.flush()
        val saveBackground = backgroundHandle //TODO push vor drawn,
        this.backgroundHandle = null
        if (drawItemsWithScissor(xPixel, viewport, yPixel, batch, parentAlpha)) {
            this.backgroundHandle = saveBackground
            return true
        }
        this.backgroundHandle = saveBackground
        return false
    }

    override fun initStyles(screen: OnjScreen) {
        addScrollFlexBoxStyles(screen)
        addBackgroundStyles(screen)
        addDetachableStyles(screen)
    }


    companion object {
        fun isInsideScrollableParents(actor: Actor, x: Float, y: Float): Boolean {
            var cur: Actor? = actor
            while (cur != null) {
                cur = cur.parent
                if (cur is CustomScrollableFlexBox && cur.needsScrollbar) {
                    val coordinates = actor.localToActorCoordinates(cur, Vector2(x, y))
                    if (coordinates.x < 0 || coordinates.y < 0 || coordinates.x > cur.width || coordinates.y > cur.height)
                        return false
                }
            }
            return true
        }
    }
}

/**
 * an image that can be turned with the cursor and supports snapping
 * @param textureRegion the region that is rendered
 * @param viewport the viewport of the screen
 * @param onj the onj-object containing the configuration
 */
class RotatableImageActor(
    textureRegion: TextureRegion,
    private val viewport: Viewport,
    onj: OnjNamedObject
) : Widget(), ZIndexActor {

    override var fixedZIndex: Int = 0

    private val sprite: Sprite = Sprite()

    private var rotationalVelocity: Float = onj.getOr("startVelocity", 0.0).toFloat()

    private val snapRotations: Array<Float>?
    private val deactivateSnapAt: Float?
    private val snapStrength: Float?
    private val deactivateSnapWhileDragging: Boolean?

    private var lastPos: Vector2? = null
    private val slowdownRate = onj.get<Double>("slowdownRate").toFloat()
    private val velocityMultiplier = onj.get<Double>("velocityMultiplier").toFloat()

    init {

        if (!onj["snap"]!!.isNull()) {
            val snapOnj = onj.get<OnjObject>("snap")
            val rotations = snapOnj.get<OnjArray>("rotations").value
            snapRotations = Array(rotations.size) { (rotations[it] as OnjFloat).value.toFloat() }
            deactivateSnapAt = snapOnj.get<Double>("deactivateAt").toFloat()
            snapStrength = snapOnj.get<Double>("strengthMultiplier").toFloat()
            deactivateSnapWhileDragging = snapOnj.get<Boolean>("deactivateWhileDragging")
        } else {
            snapRotations = null
            deactivateSnapAt = null
            snapStrength = null
            deactivateSnapWhileDragging = null
        }

        sprite.setRegion(textureRegion)
        sprite.setPosition(x, y)
        sprite.setSize(width, height)
        onTouchEvent { event ->
            when (event.type) {
                InputEvent.Type.touchDown -> {
                    lastPos =
                        viewport.camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).xy
                }

                InputEvent.Type.touchUp -> lastPos = null
                else -> {}
            }
        }
        touchable = Touchable.enabled //TODO remove i guess, why is this here, this probably shouldn't be here i think
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val lastPos = lastPos // for implicit casting

        rotationalVelocity /= slowdownRate
        if (lastPos != null) {
            val curPos = Utils.getCursorPos(viewport)
            val diff = lastPos - curPos
            this.lastPos = curPos
            val centerOffset = curPos - Vector2(sprite.x + sprite.width / 2, sprite.y + sprite.height / 2)
            val vel = -(centerOffset.x * diff.y) + centerOffset.y * diff.x
            rotationalVelocity = vel * velocityMultiplier
        }

        if (
            snapRotations != null &&
            rotationalVelocity < deactivateSnapAt!! &&
            (deactivateSnapWhileDragging!! && lastPos == null)
        ) {
            var nearestSnapPos = 0
            var nearestSnapPosDistance = Float.MAX_VALUE
            for (i in snapRotations.indices) {
                if (abs(sprite.rotation - snapRotations[i]) < nearestSnapPosDistance) {
                    nearestSnapPos = i
                    nearestSnapPosDistance = abs(sprite.rotation - snapRotations[i])
                }
            }
            val snapDistance = snapRotations[nearestSnapPos] - sprite.rotation
            rotationalVelocity += snapDistance * 0.001f * snapStrength!!
        }

        if (batch == null) return

        sprite.setPosition(x, y)
        sprite.setSize(width, height)
        sprite.rotate(rotationalVelocity)
        sprite.rotation = if (sprite.rotation > 0) sprite.rotation % 360 else 360 - (sprite.rotation % 360)

        val originX = width / 2
        val originY = height / 2
        if (sprite.originX != originX && sprite.originY != originY) sprite.setOrigin(originX, originY)

        sprite.draw(batch)
    }
}

class CustomSplitPane(
    firstActor: Actor,
    secondActor: Actor,
    vertical: Boolean,
    screen: OnjScreen
) : SplitPane(firstActor, secondActor, vertical, SplitPaneStyle()) {


    companion object {

//        fun createScrollableSplitPane(first: Actor, second: Actor): Triple<
//                CustomSplitPane,
//                CustomFlexBox /*=parent of first actor*/,
//                CustomFlexBox /*=parent of second actor*/
//        > {
//            val first = ScrollPane(first)
//            val second = ScrollPane(first)
//        }

    }
}

/**
 * custom table, that implements [ZIndexActor] and [ZIndexGroup]
 */
class CustomTable : Table(), ZIndexGroup, ZIndexActor {

    override var fixedZIndex: Int = 0

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

}

/**
 * custom h-group, that implements [ZIndexActor] and [ZIndexGroup]
 */
open class CustomHorizontalGroup(
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf(),
) : HorizontalGroup(), ZIndexGroup, ZIndexActor, BackgroundActor, HasOnjScreen, OffSettable, OnLayoutActor,
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
    override var group: SelectionGroup? = null
    override var isFocusable: Boolean = false
    override var isFocused: Boolean = false
    override var isSelectable: Boolean = false
    override var isSelected: Boolean = false
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    var forcedPrefWidth: Float? = null
    var forcedPrefHeight: Float? = null

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        this.x += drawOffsetX
        this.y += drawOffsetY
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
        this.x -= drawOffsetX
        this.y -= drawOffsetY
    }

    fun invalidateChildren() {
        children.forEach { (it as? Layout)?.invalidate() }
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

    override fun getPrefWidth(): Float = forcedPrefWidth ?: super.getPrefWidth()
    override fun getPrefHeight(): Float = forcedPrefHeight ?: super.getPrefHeight()
}

/**
 * custom v-group, that implements [ZIndexActor] and [ZIndexGroup]
 */
open class CustomVerticalGroup(
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf()
) : VerticalGroup(), ZIndexGroup, ZIndexActor, StyledActor, BackgroundActor, HasOnjScreen, OnLayoutActor {

    override var fixedZIndex: Int = 0
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    var forcedPrefWidth: Float? = null
    var forcedPrefHeight: Float? = null

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

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

    override fun getPrefWidth(): Float = forcedPrefWidth ?: super.getPrefWidth()
    override fun getPrefHeight(): Float = forcedPrefHeight ?: super.getPrefHeight()

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }
}

open class CustomGroup(
    override val screen: OnjScreen,
    private val backgroundHints: Array<String> = arrayOf()
) : WidgetGroup(), ZIndexGroup, ZIndexActor, BackgroundActor, HasOnjScreen, OffSettable, OnLayoutActor, KotlinStyledActor,
    DropShadowActor, AnimatedActor {

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
    override var group: SelectionGroup? = null
    override var isFocusable: Boolean = false
    override var isFocused: Boolean = false
    override var isSelectable: Boolean = false
    override var isSelected: Boolean = false
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    override var fixedZIndex: Int = 0

    /**
     * the children in the original order as they were added
     */
    protected val originalChildren: MutableList<Actor> = mutableListOf()
    private var sortedChildrenDirty: Boolean = false

    var forcedPrefWidth: Float? = null
    var forcedPrefHeight: Float? = null

    protected var layoutPrefWidth = 0F
    protected var layoutPrefHeight = 0F

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver
    protected val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen, backgroundHints)
    override var dropShadow: DropShadow? = null

    init {
        bindDefaultListeners(this, screen)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateAnimations()
        validate()
        batch ?: return
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
        this.x -= drawOffsetX
        this.y -= drawOffsetY
    }

    private fun drawBackground(batch: Batch?) {
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
        originalChildren.add(actor)
        super.addActor(actor)
    }

    override fun addActorAt(index: Int, actor: Actor) {
        sortedChildrenDirty = true
        originalChildren.add(index, actor)
        super.addActorAt(index, actor)
    }

    override fun removeActor(actor: Actor, unfocus: Boolean): Boolean {
        sortedChildrenDirty = true
        val index = children.indexOf(actor, true)
        if (index == -1) return false
        removeActorAt(originalChildren.indexOf(actor), unfocus)
        return true
    }

    override fun removeActorAt(index: Int, unfocus: Boolean): Actor {
        sortedChildrenDirty = true
        val actor = originalChildren.removeAt(index)
        return super.removeActorAt(children.indexOf(actor), unfocus)
    }

    override fun clearChildren(unfocus: Boolean) {
        sortedChildrenDirty = true
        super.clearChildren(unfocus)
    }

    override fun getPrefWidth(): Float = forcedPrefWidth ?: layoutPrefWidth
    override fun getPrefHeight(): Float = forcedPrefHeight ?: layoutPrefHeight

    override fun clear() {
        originalChildren.clear()
        super.clear()
        invalidate()
    }

}

class CustomParticleActor(
    particle: ParticleEffect,
    resetOnStart: Boolean = true
) : ParticleEffectActor(particle, resetOnStart), ZIndexActor {
    override var fixedZIndex: Int = 0
}

class Spacer(
    var definedWidth: Float = 0f,
    var definedHeight: Float = 0f,
    val growProportionHeight: Float? = null,
    val growProportionWidth: Float? = null,
) : Widget(), OnLayoutActor {

    private val onLayout: MutableList<() -> Unit> = mutableListOf()

    override fun onLayout(callback: () -> Unit) {
        onLayout.add(callback)
    }

    override fun layout() {
        onLayout.forEach { it() }
        updateGrowthHeight()
        updateGrowthWidth()
        super.layout()
    }

    private fun updateGrowthHeight() {
        if (growProportionHeight == null) return
        val parentHeight = parent.height
        val siblingsHeight = parent
            .children
            .filter { it !== this }
            .filter { !(it is Spacer && it.growProportionHeight != null) }
            .map { if (it is Layout) it.prefHeight else it.height }
            .sum()
        definedHeight = max((parentHeight - siblingsHeight) * growProportionHeight, 0f)
    }

    private fun updateGrowthWidth() {
        if (growProportionWidth == null) return
        val parentWidth = parent.width
        val siblingsWidth = parent
            .children
            .filter { it !== this }
            .filter { !(it is Spacer && it.growProportionWidth != null) }
            .map { if (it is Layout) it.prefWidth else it.width }
            .sum()
        definedWidth = max((parentWidth - siblingsWidth) * growProportionWidth, 0f)
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
