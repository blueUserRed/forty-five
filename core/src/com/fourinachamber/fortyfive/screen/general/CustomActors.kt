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
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.YogaValue
import ktx.actors.*
import onj.value.OnjArray
import onj.value.OnjFloat
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.properties.Delegates


/**
 * an object which is rendered and to which a mask can be applied
 */
interface Maskable {

    /**
     * the mask to apply
     */
    var mask: Texture?

    /**
     * by default only parts where the mask is opaque will be rendered, but if invert is set to true, only parts where
     * the mask is not opaque are rendered
     */
    var invert: Boolean

    /**
     * scales the mask horizontally
     */
    var maskScaleX: Float

    /**
     * scales the mask vertically
     */
    var maskScaleY: Float

    /**
     * offsets the mask horizontally
     */
    var maskOffsetX: Float

    /**
     * offsets the mask vertically
     */
    var maskOffsetY: Float
}

/**
 * an actor that can be disabled
 */
interface DisableActor {

    /**
     * true if the actor is disabled
     */
    var isDisabled: Boolean
}

/**
 * The default implementation of z-indices in libgdx is really bad, so here is my own.
 * Actors that implement this interface can have z-indices applied.
 * Only works when the actor is in a [ZIndexGroup]
 */
interface ZIndexActor {

    /**
     * the actor with the higher z-index is rendered on top
     */
    var fixedZIndex: Int
}

/**
 * A group that supports [ZIndexActor]. [resortZIndices] must be called after an actor is added for the z-indices to
 * work correctly
 */
interface ZIndexGroup {

    /**
     * resorts the children according to their z-indices; has to be called after adding an actor
     */
    fun resortZIndices()
}

/**
 * an actor that can be in an animation
 */
interface AnimationActor {

    /**
     * true if the actor is in an animation. If so, it should be treated differently, e.g. by not setting its position
     */
    var inAnimation: Boolean
}

/**
 * an actor that can be selected using the keyboard
 */
interface KeySelectableActor {

    /**
     * true when the actor is currently selected
     */
    var isSelected: Boolean

    /**
     * true when the actor wants to be part of the hierarchy used to determine the next actor.
     * When this is false, the actor cannot be selected
     */
    val partOfHierarchy: Boolean

    /**
     * returns the area of the actor on the screen, which will be highlighted when the actor is selected
     */
    fun getHighlightArea(): Rectangle
}

/**
 * Actor that can keep track of whether it is hovered over or not
 */
interface HoverStateActor {

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean

    /**
     * binds listeners to [actor] that automatically assign [isHoveredOver]
     */
    fun bindHoverStateListeners(actor: Actor) {
        actor.onEnter { isHoveredOver = true }
        actor.onExit { isHoveredOver = false }
    }

}

/**
 * actor that has a background that can be changed
 */
interface BackgroundActor {

    /**
     * handle of the current background
     */
    var backgroundHandle: ResourceHandle?

}

/**
 * Actor that can be detached from the screen and then reattached
 */
interface Detachable {

    val attached: Boolean

    fun detach()
    fun reattach()
}

/**
 * Label that uses a custom shader to render distance-field fonts correctly
 * @param background If not set to null, it is drawn behind the text using the default-shader. Will be scaled to fit the
 *  label
 */
open class CustomLabel @AllThreadsAllowed constructor(
    val screen: OnjScreen,
    text: String,
    labelStyle: LabelStyle,
    override val partOfHierarchy: Boolean = false
) : Label(text, labelStyle), ZIndexActor, DisableActor, KeySelectableActor, StyledActor, BackgroundActor {

    override var fixedZIndex: Int = 0

    override var isDisabled: Boolean = false

    override var isSelected: Boolean = false

    override var isHoveredOver: Boolean = false

    override var styleManager: StyleManager? = null

    override var backgroundHandle: String? = null
        set(value) {
            field = value
            background = null
        }

    private var background: Drawable? = null

    init {
        bindHoverStateListeners(this)
    }

    private fun getBackground(): Drawable? {
        if (backgroundHandle == null) return null
        if (background == null) background = ResourceManager.get(screen, backgroundHandle!!)
        return background
    }

    override fun getHighlightArea(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) {
            super.draw(null, parentAlpha)
            return
        }
        val background = getBackground()
        background?.draw(batch, x, y, width, height)
        val prevShader = batch.shader
        batch.shader = fontShader
        super.draw(batch, parentAlpha)
        batch.shader = prevShader
    }

    override fun initStyles(screen: OnjScreen) {
        addLabelStyles(screen)
        addBackgroundStyles(screen)
        addDisableStyles(screen)
    }

    companion object {

        private val fontShader: ShaderProgram by lazy {
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

open class TemplateStringLabel @AllThreadsAllowed constructor(
    screen: OnjScreen,
    private val templateString: TemplateString,
    labelStyle: LabelStyle,
    partOfHierarchy: Boolean = false
) : CustomLabel(screen, templateString.string, labelStyle, partOfHierarchy), BackgroundActor {

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        val newString = templateString.string
        if (!textEquals(newString)) {
            setText(newString)
        }
        super.draw(batch, parentAlpha)
    }
}

/**
 * custom Image that implements functionality for z-indices and masking
 */
open class CustomImageActor @AllThreadsAllowed constructor(
    drawableHandle: ResourceHandle?,
    private val screen: OnjScreen,
    override val partOfHierarchy: Boolean = false
) : Image(), Maskable, ZIndexActor, DisableActor, KeySelectableActor, StyledActor, BackgroundActor {

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

    override var mask: Texture? = null
    override var invert: Boolean = false
    override var maskScaleX: Float = 1f
    override var maskScaleY: Float = 1f
    override var maskOffsetX: Float = 0f
    override var maskOffsetY: Float = 0f
    var tintColor: Color? = null

    override var backgroundHandle: String? = drawableHandle
        set(value) {
            if (field != value) loadedDrawable = null
            field = value
        }

    protected var loadedDrawable: Drawable? = null
        private set

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

    init {
        bindHoverStateListeners(this)
    }

    @MainThreadOnly
    override fun draw(batch: Batch?, parentAlpha: Float) {
        val mask = mask
        val backgroundHandle = backgroundHandle

        if (backgroundHandle != null && loadedDrawable == null) {
            loadedDrawable = ResourceManager.get(screen, backgroundHandle)
            drawable = loadedDrawable
            invalidateHierarchy()
        }

        if (batch == null || drawable == null) {
            super.draw(batch, parentAlpha)
            return
        }

        validate()

        val width = if (ignoreScalingWhenDrawing) width else width * scaleX
        val height = if (ignoreScalingWhenDrawing) height else height * scaleY

        if (mask == null) {
            val c = batch.color.cpy()
            batch.setColor(c.r, c.g, c.b, alpha)
            drawable.draw(batch, x, y, width, height)
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
    }

    fun forceLoadDrawable() {
        val backgroundHandle = backgroundHandle
        if (backgroundHandle == null || loadedDrawable != null) return
        loadedDrawable = ResourceManager.get(screen, backgroundHandle)
        drawable = loadedDrawable
        invalidateHierarchy()
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

    override fun getHighlightArea(): Rectangle {
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
    private val screen: OnjScreen
) : FlexBox(), ZIndexActor, ZIndexGroup, StyledActor, BackgroundActor, Detachable {

    override var fixedZIndex: Int = 0

    protected var background: Drawable? = null

    override var isHoveredOver: Boolean = false

    override var styleManager: StyleManager? = null

    override var backgroundHandle: String? = null
        set(value) {
            if (field != value) background = null
            field = value
        }

    private var reattachTo: Group? = null

    override val attached: Boolean
        get() = reattachTo == null

    init {
        bindHoverStateListeners(this)
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
        val backgroundHandle = backgroundHandle
        if (backgroundHandle != null && background == null) {
            background = ResourceManager.get(screen, backgroundHandle)
        }
        validate()
        if (batch != null && background != null) {
            background?.draw(batch, x, y, width, height)
        }
        super.draw(batch, parentAlpha)
    }

    override fun initStyles(screen: OnjScreen) {
        addFlexBoxStyles(screen)
        addBackgroundStyles(screen)
        addDetachableStyles(screen)
    }
}

class CustomScrollableFlexBox(
    private val screen: OnjScreen,
    private val isScrollDirectionVertical: Boolean,
    private val scrollDistance: Float,
    private val isBackgroundStretched: Boolean,
    private val scrollbarBackgroundName: String?,
    private val scrollbarName: String?,
    private val scrollbarSide: String?,
) : CustomFlexBox(screen) {

    private val scrollListener = object : InputListener() {
        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            stage.scrollFocus = this@CustomScrollableFlexBox
        }

        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            if (x > width || x < 0 || y > height || y < 0)
                stage.scrollFocus = null
        }

        override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
            this@CustomScrollableFlexBox.scroll(amountY)
            return super.scrolled(event, x, y, amountX, amountY)
        }
    }

    init {
        addListener(scrollListener)
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
    private lateinit var scrollbarBackground: CustomImageActor
    private lateinit var scrollbarHandle: CustomImageActor
    var scrollbarWidth: Float = 3F

    override fun layout() {
        super.layout()

        layoutChildren()
        layoutScrollBar()
    }

    private fun layoutScrollBar() {
        if (scrollbarBackgroundName != null) {
            if (!this::scrollbarBackground.isInitialized) {
                scrollbarBackground = screen.namedActorOrError(scrollbarBackgroundName) as CustomImageActor
                remove(scrollbarBackground.styleManager?.node)
            }
            layoutScrollbarBackground()
        }
        if (scrollbarName != null) {
            if (!this::scrollbarHandle.isInitialized) {
                scrollbarHandle = screen.namedActorOrError(scrollbarName) as CustomImageActor
                remove(scrollbarHandle.styleManager?.node)
                //get width form style manager
                scrollbarWidth = (scrollbarHandle.styleManager
                    ?.let {
                        scrollbarHandle.styleManager?.styleProperties
                            ?.filterIsInstance<WidthStyleProperty<*>>()
                            ?.first()
                            ?.get(it.node)
                    })?.value ?: scrollbarWidth
            }
            layoutScrollbarHandle()
        }
    }

    private fun layoutChildren() {
        if (isScrollDirectionVertical) {
            lastMax = children.map { -it.y }.max()
            if (0F < lastMax + cutBottom) {
                offset = offset.between(0F, lastMax + cutBottom)
                children.forEach { it.y += offset }
            }
        } else {
            lastMax = (children.map { it.x + it.width }.max() - width)
            if (-lastMax - cutRight < -cutLeft / scrollDistance) {
                offset = offset.between(-lastMax - cutRight, -cutLeft / scrollDistance)
                children.forEach { it.x += offset }
            }
        }
    }

    private fun layoutScrollbarBackground() {
        if (isScrollDirectionVertical) {
            if (scrollbarSide != null && scrollbarSide == "left") {
                scrollbarBackground.x = x
            } else {
                scrollbarBackground.x = x + width - scrollbarWidth
            }
            scrollbarBackground.y = y
            scrollbarBackground.width = scrollbarWidth
            scrollbarBackground.height = height
        } else {
            if (scrollbarSide != null && scrollbarSide == "top") {
                scrollbarBackground.y = y + height - scrollbarWidth
            } else {
                scrollbarBackground.y = y
            }
            scrollbarBackground.x = x
            scrollbarBackground.width = width
            scrollbarBackground.height = scrollbarWidth
        }
    }

    private fun layoutScrollbarHandle() {
        if (isScrollDirectionVertical) {
            val max = lastMax + cutBottom
            val curSize = height * height / (max + height)

            val curPos = offset / max * (height - curSize)
            println("$height  $curPos")
            if (scrollbarSide != null && scrollbarSide == "left") {
                scrollbarHandle.x = x
            } else {
                scrollbarHandle.x = x + width - scrollbarWidth
            }
            scrollbarHandle.width = scrollbarWidth
            scrollbarHandle.height = curSize
            scrollbarHandle.y = y + height - curPos - curSize
        } else {
            if (scrollbarSide != null && scrollbarSide == "top") {
                scrollbarHandle.y = y + height - scrollbarWidth
            } else {
                scrollbarHandle.y = y
            }
            scrollbarHandle.x = x
            scrollbarHandle.width = width
            scrollbarHandle.height = scrollbarWidth
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch ?: return
        batch.flush()
        val viewport = screen.stage.viewport
        val xPixel = (Gdx.graphics.width - viewport.leftGutterWidth - viewport.rightGutterWidth) / viewport.worldWidth
        val yPixel =
            (Gdx.graphics.height - viewport.topGutterHeight - viewport.bottomGutterHeight) / viewport.worldHeight
        if (isBackgroundStretched) {
            if (drawBackgroundStretched(batch, xPixel, viewport, yPixel, parentAlpha)) return
        } else {
            super.draw(batch, parentAlpha)
        }
        val scissor = Rectangle(
            xPixel * (x + cutLeft) + viewport.leftGutterWidth,
            yPixel * (y + cutBottom) + viewport.topGutterHeight,
            xPixel * (width - cutLeft - cutRight),
            yPixel * (height - cutTop - cutBottom)
        )
        if (!ScissorStack.pushScissors(scissor)) return
        batch.flush()
        ScissorStack.popScissors()

        batch.flush()
        if (this::scrollbarBackground.isInitialized) scrollbarBackground.draw(batch, alpha)
        if (this::scrollbarHandle.isInitialized) scrollbarHandle.draw(batch, alpha)
    }

    private fun drawBackgroundStretched(
        batch: Batch,
        xPixel: Float,
        viewport: Viewport,
        yPixel: Float,
        parentAlpha: Float
    ): Boolean {
        val backgroundHandle = backgroundHandle
        if (backgroundHandle != null && background == null) background = ResourceManager.get(screen, backgroundHandle)
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
        val scissorBack = Rectangle(
            xPixel * x + viewport.leftGutterWidth,
            yPixel * y + viewport.topGutterHeight,
            xPixel * width,
            yPixel * height
        )
        if (!ScissorStack.pushScissors(scissorBack)) return true
        batch.flush()
        ScissorStack.popScissors()
        val saveBackground = backgroundHandle
        this.backgroundHandle = null
        super.draw(batch, parentAlpha)
        this.backgroundHandle = saveBackground
        return false
    }

    override fun initStyles(screen: OnjScreen) {
        addScrollFlexBoxStyles(screen)
        addBackgroundStyles(screen)
        addDetachableStyles(screen)
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
                    lastPos = viewport.camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).xy
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
    private val screen: OnjScreen
) : HorizontalGroup(), ZIndexGroup, ZIndexActor, BackgroundActor {

    override var fixedZIndex: Int = 0

    override var backgroundHandle: String? = null
        set(value) {
            field = value
            background = null
        }

    private var background: Drawable? = null

    private fun getBackground(): Drawable? {
        if (backgroundHandle == null) return null
        if (background == null) background = ResourceManager.get(screen, backgroundHandle!!)
        return background
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        val background = getBackground()
        background?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
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
    private val screen: OnjScreen
) : VerticalGroup(), ZIndexGroup, ZIndexActor, StyledActor, BackgroundActor {

    override var fixedZIndex: Int = 0
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false

    override var backgroundHandle: String? = null
        set(value) {
            field = value
            background = null
        }

    private var background: Drawable? = null

    private fun getBackground(): Drawable? {
        if (backgroundHandle == null) return null
        if (background == null) background = ResourceManager.get(screen, backgroundHandle!!)
        return background
    }


    override fun draw(batch: Batch?, parentAlpha: Float) {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        val background = getBackground()
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
