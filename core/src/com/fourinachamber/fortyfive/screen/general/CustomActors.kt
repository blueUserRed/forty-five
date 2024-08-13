// contains currently unused actors, but they may be important in the future
@file:Suppress("unused")

package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
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
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import ktx.actors.*
import onj.value.*
import kotlin.math.abs
import kotlin.math.max

open class CustomLabel(
    override val screen: OnjScreen,
    text: String,
    labelStyle: LabelStyle,
    private val isDistanceField: Boolean,
    private val hasHoverDetail: Boolean = false,
    private val hoverText: String = "",
    override val partOfHierarchy: Boolean = false
) : Label(text, labelStyle), ZIndexActor, DisableActor, KeySelectableActor,
    StyledActor, BackgroundActor, ActorWithAnimationSpawners, HasOnjScreen, GeneralDisplayDetailOnHoverActor {

    override val actor: Actor = this

    private val _animationSpawners: MutableList<AnimationSpawner> = mutableListOf()

    override val animationSpawners: List<AnimationSpawner>
        get() = _animationSpawners

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false
    override var isSelected: Boolean = false
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false
    override var styleManager: StyleManager? = null

    var underline: Boolean = false

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen)

    override var detailActor: Actor? = null
    override var mainHoverDetailActor: String? = null
    override var isHoverDetailActive: Boolean = hasHoverDetail

    override val additionalHoverData: MutableMap<String, OnjValue> = mutableMapOf()

    private val shapeRenderer: ShapeRenderer by lazy {
        val renderer = ShapeRenderer()
        screen.addDisposable(renderer)
        renderer
    }

    init {
        bindHoverStateListeners(this)
        registerOnHoverDetailActor(this, screen)
    }

    override fun getHoverDetailData(): Map<String, OnjValue> = mutableMapOf<String, OnjValue>(
        "hoverText" to OnjString(hoverText)
    ).also {
        it.putAll(additionalHoverData)
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    override fun addAnimationSpawner(spawner: AnimationSpawner) {
        _animationSpawners.add(spawner)
        screen.addActorToRoot(spawner.actor)
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
        // Dont ask me why the -width is necessary
        layoutSpawners(x - width, y, width, height)
        super.layout()
    }

    protected fun drawBackground(batch: Batch, parentAlpha: Float) {
        val background = background ?: return
        batch.flush()
        val old = batch.color.cpy()
        batch.setColor(old.r, old.g, old.b, parentAlpha * alpha)
        background.draw(batch, x, y, width, height)
        batch.flush()
        batch.setColor(old.r, old.g, old.b, old.a)
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
    hasHoverDetail: Boolean = false,
    hoverText: String = "",
    partOfHierarchy: Boolean = false
) : CustomLabel(
    screen,
    templateString.string,
    labelStyle,
    isDistanceField,
    hasHoverDetail,
    hoverText,
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
    _screen: OnjScreen,
    override val partOfHierarchy: Boolean = false,
    var hoverText: String = "",
    var hasHoverDetail: Boolean = false
) : Image(), Maskable, ZIndexActor, DisableActor,
    KeySelectableActor, StyledActor, BackgroundActor, OffSettable, GeneralDisplayDetailOnHoverActor, HasOnjScreen {

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false
    override var isClicked: Boolean = false
    override var mainHoverDetailActor: String? = null

    override var isHoverDetailActive: Boolean
        get() = hasHoverDetail
        set(value) {
            hasHoverDetail = value
        }

    override val screen: OnjScreen = _screen

    override var mask: Texture? = null
    override var invert: Boolean = false
    override var maskScaleX: Float = 1f
    override var maskScaleY: Float = 1f
    override var maskOffsetX: Float = 0f
    override var maskOffsetY: Float = 0f
    var tintColor: Color? = null

    override var offsetX: Float = 0F
    override var offsetY: Float = 0F

    override val actor: Actor = this

    override val additionalHoverData: MutableMap<String, OnjValue> = mutableMapOf()

    private val backgroundHandleObserver = SubscribeableObserver(drawableHandle)
    override var backgroundHandle: String? by backgroundHandleObserver

    protected val loadedDrawable: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, _screen)

    /**
     * overrides and ignores the background handle and the loaded drawable
     */
    var programmedDrawable: Drawable? = null

    override var isSelected: Boolean = false

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

    override var detailActor: Actor? = null

    init {
        bindHoverStateListeners(this)
        registerOnHoverDetailActor(this, _screen)
    }

    override fun getHoverDetailData(): Map<String, OnjValue> = mutableMapOf<String, OnjValue>(
        "hoverText" to OnjString(hoverText)
    ).also {
        it.putAll(additionalHoverData)
    }

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        val mask = mask

        drawable = if (programmedDrawable != null) {
            programmedDrawable
        } else {
            loadedDrawable
        }

        if (batch == null || drawable == null) {
            return
        }

        validate()
        x += offsetX
        y += offsetY
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

        x -= offsetX
        y -= offsetY
    }

    override fun getMinWidth(): Float =
        if (reportDimensionsWithScaling) super.getPrefWidth() * scaleX else super.getPrefWidth()

    override fun getPrefWidth(): Float =
        if (reportDimensionsWithScaling) super.getPrefWidth() * scaleX else super.getPrefWidth()

    override fun getMaxWidth(): Float =
        if (reportDimensionsWithScaling) super.getMaxWidth() * scaleX else super.getMaxWidth()

    override fun getMinHeight(): Float =
        if (reportDimensionsWithScaling) super.getPrefHeight() * scaleY else super.getPrefHeight()

    override fun getPrefHeight(): Float =
        if (reportDimensionsWithScaling) super.getPrefHeight() * scaleY else super.getPrefHeight()

    override fun getMaxHeight(): Float =
        if (reportDimensionsWithScaling) super.getMaxHeight() * scaleY else super.getMaxHeight()

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? { // workaround
        if (!reportDimensionsWithScaling) return super.hit(x, y, touchable)
        if (touchable && this.touchable != Touchable.enabled) return null
        if (!isVisible) return null
        val didHit = x >= 0 && x < width / scaleX && y >= 0 && y < height / scaleY
        return if (didHit) this else null
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return if (reportDimensionsWithScaling) {
            Rectangle(x, y, width, height)
        } else {
            Rectangle(x, y, width * scaleX, height * scaleY)
        }
    }

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
    private val hasHoverDetail: Boolean = false,
    private val hoverText: String = ""
) : FlexBox(), ZIndexActor, ZIndexGroup, StyledActor, BackgroundActor,
    Detachable, OffSettable, HasOnjScreen, DisableActor, BoundedActor, GeneralDisplayDetailOnHoverActor,
    InOutAnimationActor, ResourceBorrower {

    override var fixedZIndex: Int = 0

    override val actor: Actor = this

    private val dropShadowShader: Promise<BetterShader> by lazy {
        ResourceManager.request<BetterShader>(this, screen, "gaussian_blur_shader")
    }

    override var isDisabled: Boolean = false

    override var isHoveredOver: Boolean = false

    override var styleManager: StyleManager? = null
    override var isClicked: Boolean = false

    override var offsetX: Float = 0F
    override var offsetY: Float = 0F

    override val additionalHoverData: MutableMap<String, OnjValue> = mutableMapOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen)

    private var reattachTo: Group? = null

    override val attached: Boolean
        get() = reattachTo == null

    override var detailActor: Actor? = null
    override var mainHoverDetailActor: String? = null

    override var isHoverDetailActive: Boolean = hasHoverDetail
        set(value) {}

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
        registerOnHoverDetailActor(this, screen)
    }

    override fun getHoverDetailData(): Map<String, OnjValue> = mutableMapOf<String, OnjValue>(
        "hoverText" to OnjString(hoverText)
    ).also {
        it.putAll(additionalHoverData)
    }

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
        x += offsetX
        y += offsetY
        if (batch != null && background != null) {
            val old = batch.color.a
            if (parentAlpha * alpha < 1f) batch.flush()
            batch.setColor(batch.color.r, batch.color.g, batch.color.b, parentAlpha * alpha)
            val background = background
            if(name=="drop_shadow_testing_name"){
                dropShadowShader.getOrNull()?.let {
                    batch.flush()
                    it.shader.bind()
                    it.prepare(screen)
                    val oldShader=batch.shader

//                    val glowDist = 0.05F                  //drop shadow config
////                    val glowDist = 0.015F
//                    val extraWidth = 1F/1.2F
//                    val extraHeight = 1F/1.2F
//                    val offset= Vector2(0.02F,0.1F)

//                    val glowDist = 0.05F                    //glow config
////                    val glowDist = 0.015F
//                    val extraWidth = 1F
//                    val extraHeight = 1F
//                    val offset= Vector2(0.0F,0.0F)

//                    batch.shader = it.shader
//                    it.shader.setUniformf("u_multiplier", glowDist)
//                    it.shader.setUniformf("u_color", Color.GREEN)
//                    it.shader.setUniformf("u_offset", offset)
//                    background?.draw(batch,
//                        x-width*glowDist*extraWidth,
//                        y-height*glowDist*extraHeight,
//                        width*(1+glowDist*2*extraWidth),
//                        height*(1+glowDist*2*extraHeight))


//                    it.shader.setUniformf("u_radius", 30F)
//                    it.shader.setUniformf("u_dir", Vector2(0F,0.001F))
//                    background?.draw(batch, x, y+height*2, width, height)
//                    batch.flush()
//                    it.shader.setUniformf("u_radius", 30F)
//                    it.shader.setUniformf("u_dir", Vector2(0.001F,0F))
//                    background?.draw(batch, x, y+height*2, width, height)

                    batch.flush()
                    batch.shader = oldShader
                }
            } //else //TODO remove this else
            if (background is TransformDrawable) {
                background.draw(batch, x, y, width / 2, height / 2, width, height, 1f, 1f, rotation)
            } else {
                background?.draw(batch, x, y, width, height)
            }
            if (parentAlpha * alpha < 1f) batch.flush()

            batch.setColor(batch.color.r, batch.color.g, batch.color.b, old)
        }
        super.draw(batch, parentAlpha)
        x -= offsetX
        y -= offsetY
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
                res.x += parent.offsetX
                res.y += parent.offsetY
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
) : CustomFlexBox(screen, false) { //TODO fix bug with children with fixed size

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
                    -(scrollbarHandle!!.x - parentPos.x - (width - curWidth) / 2) * max / (curWidth - curSize) - cutLeft
            }
            invalidate()
        }
    }

    var currentlyDraggedChild: Actor? = null

    init {
        addListener(scrollListener)
        addListener(dragListener)
    }

    private fun scroll(offset: Float) {
        this.offset += offset * scrollDistance
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
        layoutChildren()
        layoutScrollBar()
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
            lastMax = ((children.map { it.x + it.width }.maxOrNull() ?: 0F) - width)
            if (-lastMax - cutRight < -cutLeft / scrollDistance) {
                needsScrollbar = true
                offset = offset.between(-lastMax - cutRight, -cutLeft / scrollDistance)
                children.forEach { it.x += offset }
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
        if (isScrollDirectionVertical) {
            val max = lastMax + cutBottom
            val maxSize =
                (if (scrollbarLength.unit == YogaUnit.PERCENT) height * scrollbarLength.value / 100F else scrollbarLength.value)
            val curSize = maxSize * maxSize / (max + maxSize)
            val curPos = offset / max * (maxSize - curSize)
            if (scrollbarSide != null && scrollbarSide == "left") {
                scrollbarHandle!!.x = x
            } else {
                scrollbarHandle!!.x = x + width - scrollbarWidth
            }
            scrollbarHandle!!.width = scrollbarWidth
            scrollbarHandle!!.height = curSize
            scrollbarHandle!!.y = y + (height + maxSize) / 2 - curPos - curSize
        } else {
            val offset = offset + cutLeft
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
            scrollbarHandle!!.x = x - curPos + (width - curWidth) / 2
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
        touchable = Touchable.enabled
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
    override val screen: OnjScreen
) : HorizontalGroup(), ZIndexGroup, ZIndexActor, BackgroundActor, HasOnjScreen, OffSettable {

    override var offsetX: Float = 0f
    override var offsetY: Float = 0f

    override var fixedZIndex: Int = 0

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        this.x += offsetX
        this.y += offsetY
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
        this.x -= offsetX
        this.y -= offsetY
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
    override val screen: OnjScreen
) : VerticalGroup(), ZIndexGroup, ZIndexActor, StyledActor, BackgroundActor, HasOnjScreen {

    override var fixedZIndex: Int = 0
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: String? by backgroundHandleObserver

    private val background: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen)


    override fun draw(batch: Batch?, parentAlpha: Float) {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }
}

class CustomParticleActor(
    particle: ParticleEffect,
    resetOnStart: Boolean = true
) : ParticleEffectActor(particle, resetOnStart), ZIndexActor {
    override var fixedZIndex: Int = 0
}
