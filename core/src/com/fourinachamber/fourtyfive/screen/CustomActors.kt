package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE0
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE1
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.utils.*
import ktx.actors.onTouchEvent
import onj.OnjArray
import onj.OnjFloat
import onj.OnjNamedObject
import onj.OnjObject
import kotlin.math.abs

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
 * An Actor that needs to be initialised after screen is built
 */
interface InitialiseableActor {

    /**
     * automatically called by the screenBuilder
     * @see InitialiseableActor
     */
    fun init(screenDataProvider: ScreenDataProvider)
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
 * Label that uses a custom shader to render distance-field fonts correctly
 * @param background If not set to null, it is drawn behind the text using the default-shader. Will be scaled to fit the
 *  label
 */
open class CustomLabel(
    text: String,
    labelStyle: LabelStyle,
    var background: Drawable? = null
) : Label(text, labelStyle), ZIndexActor, DisableActor {

    override var fixedZIndex: Int = 0

    override var isDisabled: Boolean = false

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) {
            super.draw(batch, parentAlpha)
            return
        }
        background?.draw(batch, x, y, width, height)
        val prevShader = batch.shader
        batch.shader = fontShader
        super.draw(batch, parentAlpha)
        batch.shader = prevShader
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

/**
 * custom Image that implements functionality for z-indices and masking
 */
open class CustomImageActor(private val region: TextureRegion) : Image(region), Maskable, ZIndexActor, DisableActor {

    override var fixedZIndex: Int = 0
    override var isDisabled: Boolean = false

    override var mask: Texture? = null
    override var invert: Boolean = false
    override var maskScaleX: Float = 1f
    override var maskScaleY: Float = 1f
    override var maskOffsetX: Float = 0f
    override var maskOffsetY: Float = 0f

    /**
     * if set to true, the preferred-, min-, and max-dimension functions will return the dimensions with the scaling
     * already applied
     */
    var reportDimensionsWithScaling: Boolean = false

    /**
     * if set to true, the scale of the image will be ignored when drawing
     */
    var ignoreScalingWhenDrawing: Boolean = false

    var texture: TextureRegion = region
        set(value) {
            drawable = TextureRegionDrawable(value)
            field = value
        }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val mask = mask

        if (batch == null) {
            super.draw(batch, parentAlpha)
            return
        }

        val width = if (ignoreScalingWhenDrawing) width else width * scaleX
        val height = if (ignoreScalingWhenDrawing) height else height * scaleY

        if (mask == null) {
            batch.draw(texture, x, y, width, height)
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

        batch.draw(texture, x, y, width, height)
        batch.flush()

        batch.shader = prevShader
    }

    override fun getMinWidth(): Float =
        if (reportDimensionsWithScaling) super.getMinWidth() * scaleX else super.getMinWidth()
    override fun getPrefWidth(): Float =
        if (reportDimensionsWithScaling) super.getPrefWidth() * scaleX else super.getPrefWidth()
    override fun getMaxWidth(): Float =
        if (reportDimensionsWithScaling) super.getMaxWidth() * scaleX else super.getMaxWidth()
    override fun getMinHeight(): Float =
        if (reportDimensionsWithScaling) super.getMinHeight() * scaleY else super.getMinHeight()
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
                else -> { }
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

/**
 * An animated image
 * @param animation the animation; contains the frames and data such as the frameTime
 */
class AnimatedImage(
    private val animation: Animation
) : CustomImageActor(animation.frames[animation.initialFrame]) {

    override var fixedZIndex: Int = 0

    private var curFrame = animation.initialFrame
    private val refTime = TimeUtils.millis()

    override fun draw(batch: Batch?, parentAlpha: Float) {
        curFrame = ((TimeUtils.timeSinceMillis(refTime) / animation.frameTime) % animation.frames.size).toInt()
        texture = animation.frames[curFrame]
        super.draw(batch, parentAlpha)
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
open class CustomHorizontalGroup : HorizontalGroup(), ZIndexGroup, ZIndexActor {

    override var fixedZIndex: Int = 0

    var background: Drawable? = null

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

}

/**
 * custom v-group, that implements [ZIndexActor] and [ZIndexGroup]
 */
open class CustomVerticalGroup : VerticalGroup(), ZIndexGroup, ZIndexActor {

    override var fixedZIndex: Int = 0

    var background: Drawable? = null

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

}
