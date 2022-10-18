package com.blueuserred.testgame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE0
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE1
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.blueuserred.testgame.OnjReaderUtils.Animation
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
import ktx.actors.onTouchEvent
import onj.OnjArray
import onj.OnjFloat
import onj.OnjNamedObject
import onj.OnjObject
import kotlin.math.abs

interface Maskable {
    var mask: Texture?
    var invert: Boolean
    var maskScaleX: Float
    var maskScaleY: Float
    var maskOffsetX: Float
    var maskOffsetY: Float
}

interface ZIndexActor {
    var fixedZIndex: Int
}

interface ZIndexGroup {
    fun resortZIndices()
}

class CustomLabel(
    text: String,
    labelStyle: LabelStyle,
    var background: Drawable? = null
) : Label(text, labelStyle), ZIndexActor {

    override var fixedZIndex: Int = 0

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

open class CustomImageActor(private val region: TextureRegion) : Image(region), Maskable, ZIndexActor {

    override var fixedZIndex: Int = 0

    override var mask: Texture? = null
    override var invert: Boolean = false
    override var maskScaleX: Float = 1f
    override var maskScaleY: Float = 1f
    override var maskOffsetX: Float = 0f
    override var maskOffsetY: Float = 0f

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val mask = mask

        if (batch == null || mask == null) {
            super.draw(batch, parentAlpha)
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

        batch.draw(region, x, y, width, height)
        batch.flush()

        batch.shader = prevShader
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

class AnimatedImage(
    private val animation: Animation
) : CustomImageActor(animation.frames[animation.initialFrame]) {

    override var fixedZIndex: Int = 0

    private var curFrame = animation.initialFrame
    private val refTime = TimeUtils.millis()

    override fun draw(batch: Batch?, parentAlpha: Float) {
        curFrame = ((TimeUtils.timeSinceMillis(refTime) / animation.frameTime) % animation.frames.size).toInt()
        drawable = TextureRegionDrawable(animation.frames[curFrame])
        super.draw(batch, parentAlpha)
    }
}

class CustomTable : Table(), ZIndexGroup, ZIndexActor {

    override var fixedZIndex: Int = 0

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
            (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

}

class CustomHorizontalGroup : HorizontalGroup(), ZIndexGroup, ZIndexActor {

    override var fixedZIndex: Int = 0

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
            (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

}

class CustomVerticalGroup : VerticalGroup(), ZIndexGroup, ZIndexActor {

    override var fixedZIndex: Int = 0

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
            (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

}

class VBox : Table(), ZIndexActor, ZIndexGroup {

    override var fixedZIndex: Int = 0

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
            (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun row(): Cell<*>? = null

    fun addVBox(actor: Actor): Cell<Actor> {
        super.row()
        return super.add(actor)
    }

}

class HBox : Table(), ZIndexActor, ZIndexGroup {

    override var fixedZIndex: Int = 0

    init {
        super.row()
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
            (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    override fun row(): Cell<*>? = null

    fun addHBox(actor: Actor): Cell<Actor> {
        return super.add(actor)
    }

}


