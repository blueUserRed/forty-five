package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.Screen
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Cursor.SystemCursor
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.utils.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.system.measureTimeMillis

interface ScreenBuilder {
    fun build(): Screen
}

/**
 * builds a screen from an Onj file
 */
class ScreenBuilderFromOnj(val file: FileHandle) : ScreenBuilder {

    private lateinit var textures: Map<String, TextureRegion>
    private lateinit var fonts: Map<String, BitmapFont>
    private lateinit var animations: Map<String, FrameAnimation>
    private lateinit var earlyRenderTasks: MutableList<OnjScreen.() -> Unit>
    private lateinit var lateRenderTasks: MutableList<OnjScreen.() -> Unit>
    private lateinit var behavioursToBind: MutableList<Behaviour>
    private lateinit var namedCells: MutableMap<String, Cell<*>>
    private lateinit var namedActors: MutableMap<String, Actor>
    private lateinit var actorsWithDragAndDrop: MutableMap<String, MutableList<Pair<Actor, OnjNamedObject>>>
    private lateinit var toDispose: MutableList<Disposable>
    private lateinit var viewport: Viewport

    override fun build(): OnjScreen = try {
        FourtyFiveLogger.debug(logTag, "building screen ${file.name()}")
        val onj = OnjParser.parseFile(file.file())
        screenSchema.assertMatches(onj)
        onj as OnjObject
        getScreen(onj)
    } catch (e: RuntimeException) {
        FourtyFiveLogger.severe(logTag, "an error occurred while parsing ${file.name()}")
        FourtyFiveLogger.stackTrace(e)
        throw e
    }

    private fun getScreen(onj: OnjObject): OnjScreen {
        earlyRenderTasks = mutableListOf()
        lateRenderTasks = mutableListOf()
        behavioursToBind = mutableListOf()
        namedCells = mutableMapOf()
        namedActors = mutableMapOf()
        actorsWithDragAndDrop = mutableMapOf()
        toDispose = mutableListOf()

        val onjAssets = onj.get<OnjObject>("assets")

        val textures = OnjReaderUtils.readTextures(onjAssets.get<OnjArray>("textures"))
        textures.values.forEach { region -> region.texture?.let { toDispose.add(it) } }

        val (textureRegions, atlases) = OnjReaderUtils.readAtlases(onjAssets.get<OnjArray>("textureAtlases"))
        toDispose.addAll(atlases)

        val colorTextures = OnjReaderUtils.readColorTextures(onjAssets.get<OnjArray>("colorTextures"))
        colorTextures.values.forEach { region -> region.texture?.let { toDispose.add(it) } }

        this.textures = textures + textureRegions + colorTextures

        fonts = OnjReaderUtils.readFonts(onjAssets.get<OnjArray>("fonts"))
        toDispose.addAll(fonts.values)

        val cursors = OnjReaderUtils.readCursors(onjAssets.get<OnjArray>("cursors"))
        toDispose.addAll(cursors.values)

        val particles = OnjReaderUtils.readParticles(onjAssets.get<OnjArray>("particles"))
        toDispose.addAll(particles.values)

        val postProcessors = OnjReaderUtils.readPostProcessors(onjAssets.get<OnjArray>("postProcessors"))
        toDispose.addAll(postProcessors.values)

        animations = OnjReaderUtils.readAnimations(onjAssets.get<OnjArray>("animations"))
        toDispose.addAll(animations.values)

        viewport = getViewport(onj)
        val children = getChildren(onj.get<OnjArray>("children"))

        val options = onj.get<OnjObject>("options")

        if (options.get<Boolean>("setFillParentOnRoot")) {
            if (children.isEmpty()) {
                throw RuntimeException(
                    "'setFillParentOnRoot' is set to true, but there is no direct child of the scene"
                )
            }
            val root = children[0]
            if (root !is Layout) {
                throw RuntimeException("'setFillParentOnRoot' is set to true, but the root is not a layout")
            }
            root.setFillParent(true)
        }

        onj.ifHas<OnjArray>("unmanagedChildren") {
            doUnmanagedActors(it)
        }

        val onjScreen = OnjScreen(
            this.textures.toMutableMap(),
            cursors,
            fonts,
            particles,
            postProcessors,
            children,
            viewport,
            SpriteBatch(),
            toDispose,
            earlyRenderTasks,
            lateRenderTasks,
            namedCells,
            namedActors,
            behavioursToBind,
            options.getOr("printFrameRate", false)
        )

        val cursorOnj = options.get<OnjObject>("defaultCursor")
        onjScreen.defaultCursor = Utils.loadCursor(
            cursorOnj.get<Boolean>("useSystemCursor"),
            cursorOnj.get<String>("cursorName"),
            onjScreen
        )

        onjScreen.dragAndDrop = doDragAndDrop(onjScreen).toMap()

        behavioursToBind.forEach { it.bindCallbacks(onjScreen) }

        children.forEach {
            if (it is Layout) it.invalidate()
        }

        onjScreen.postProcessor = if (!options["postProcessor"]!!.isNull()) {
            val name = options.get<String>("postProcessor")
            postProcessors[name] ?: run {
                throw RuntimeException("Unknown post processor: $name")
            }
        } else null

        onjScreen.screenController = if (!options["controller"]!!.isNull()) {
            val controller = options.get<OnjNamedObject>("controller")
            ScreenControllerFactory.controllerOrError(controller.name, controller)
        } else null

        return onjScreen
    }

    private fun doUnmanagedActors(children: OnjArray) = children
        .value
        .map { it as OnjNamedObject }
        .map { getWidget(it) }
        .forEach {
            lateRenderTasks.add {
                if (it.isVisible) it.draw(stage.batch, 1f)
            }
        }

    private fun getViewport(onj: OnjObject): Viewport {
        val viewportOnj = onj.get<OnjNamedObject>("viewport")

        when (viewportOnj.name) {

            "FitViewport" -> {
                val worldHeight = viewportOnj.get<Double>("worldHeight").toFloat()
                val worldWidth = viewportOnj.get<Double>("worldWidth").toFloat()
                if (viewportOnj.hasKey<String>("backgroundTexture")) earlyRenderTasks.add {
                    stage.batch.draw(
                        textureOrError(viewportOnj.get<String>("backgroundTexture")),
                        0f, 0f,
                        worldWidth, worldHeight
                    )
                }
                return FitViewport(worldWidth, worldHeight)
            }

            "ExtendViewport" -> {
                val minWidth = viewportOnj.get<Double>("minWidth").toFloat()
                val minHeight = viewportOnj.get<Double>("minWidth").toFloat()
                val viewport = ExtendViewport(minWidth, minHeight)
                if (viewportOnj.hasKey<String>("backgroundTexture")) earlyRenderTasks.add {
                    stage.batch.draw(
                        textureOrError(viewportOnj.get<String>("backgroundTexture")),
                        0f, 0f,
                        viewport.worldWidth, viewport.worldHeight
                    )
                }
                return viewport
            }

            else -> throw RuntimeException("unknown Viewport ${viewportOnj.name}")
        }
    }

    private fun doDragAndDrop(screen: OnjScreen): MutableMap<String, DragAndDrop> {
        val dragAndDrops = mutableMapOf<String, DragAndDrop>()
        for ((group, actors) in actorsWithDragAndDrop) {
            val dragAndDrop = DragAndDrop()
            for ((actor, onj) in actors) {
                val behaviour = DragAndDropBehaviourFactory.behaviourOrError(
                    onj.name,
                    dragAndDrop,
                    screen,
                    actor,
                    onj
                )
                if (behaviour is Either.Left) dragAndDrop.addSource(behaviour.value)
                else dragAndDrop.addTarget((behaviour as Either.Right).value)
            }
            dragAndDrops[group] = dragAndDrop
        }
        return dragAndDrops
    }

    private fun getChildren(onj: OnjArray): List<Actor> = onj
        .value
        .map { getWidget(it as OnjNamedObject) }


    private fun getWidget(widgetOnj: OnjNamedObject): Actor = when (widgetOnj.name) {

        "Image" -> CustomImageActor(textureOrError(widgetOnj.get<String>("textureName"))).apply {
            if (widgetOnj.getOr("reportDimensionsWithScaling", false)) {
                reportDimensionsWithScaling = true
                ignoreScalingWhenDrawing = true
            }
            applyImageKeys(this, widgetOnj)
        }

        "Label" -> CustomLabel(
            text = widgetOnj.get<String>("text"),
            labelStyle = Label.LabelStyle(
                fontOrError(widgetOnj.get<String>("font")),
                widgetOnj.get<Color>("color")
            )
        ).apply {
            setFontScale(widgetOnj.get<Double>("fontScale").toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { background = TextureRegionDrawable(textureOrError(it)) }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
        }

        "HorizontalGroup" -> CustomHorizontalGroup().apply {
            getChildren(widgetOnj.get<OnjArray>("children")).forEach(::addActor)
            align(alignmentOrError(widgetOnj.get<String>("align")))
            widgetOnj.ifHas<Double>("spacing") { space(it.toFloat()) }
            widgetOnj.ifHas<Boolean>("expand") { expand(it) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap(it) }
        }

        "VerticalGroup" -> CustomVerticalGroup().apply {
            getChildren(widgetOnj.get<OnjArray>("children")).forEach(::addActor)
            align(alignmentOrError(widgetOnj.get<String>("align")))
            widgetOnj.ifHas<Double>("spacing") { space(it.toFloat()) }
            widgetOnj.ifHas<Boolean>("expand") { expand(it) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap(it) }
        }

        "Table" -> CustomTable().apply {
            if (widgetOnj.getOr("fillX", false)) defaults().expandX().fillX()
            if (widgetOnj.getOr("fillY", false)) defaults().expandY().fillY()
            widgetOnj.ifHas<String>("backgroundTexture") {
                background = TextureRegionDrawable(textureOrError(it))
            }
            widgetOnj.ifHas<String>("align") { align(alignmentOrError(it)) }
            widgetOnj.get<OnjArray>("rows").value.forEach { row ->
                row as OnjObject
                val tableRow = row()
                widgetOnj.ifHas<Double>("width") { tableRow.width(it.toFloat()) }
                widgetOnj.ifHas<Double>("height") { tableRow.height(it.toFloat()) }
                row.get<OnjArray>("cells").value.forEach { cellOnj ->
                    cellOnj as OnjObject
                    val el = cellOnj.get<OnjNamedObject>("element")
                    val cell = add(getWidget(el))
                    applyTableCellKeys(cellOnj, cell)
                }
            }
            if (widgetOnj.getOr("applyZIndices", false)) {
                resortZIndices()
            }
        }

        "RotatableImageActor" -> RotatableImageActor(
            textureOrError(widgetOnj.get<String>("textureName")),
            viewport,
            widgetOnj
        )

        "AnimatedImage" -> AnimatedImage(animationOrError(widgetOnj.get<String>("animationName"))).apply {
            applyImageKeys(this, widgetOnj)
        }

        "CardHand" -> CardHand(
            widgetOnj.get<Double>("targetWidth").toFloat(),
            fontOrError(widgetOnj.get<String>("detailFont")),
            widgetOnj.get<Color>("detailFontColor"),
            TextureRegionDrawable(textureOrError(widgetOnj.get<String>("detailBackgroundTexture"))),
            widgetOnj.get<Double>("detailFontScale").toFloat(),
            Vector2(
                widgetOnj.get<Double>("detailOffsetX").toFloat(),
                widgetOnj.get<Double>("detailOffsetY").toFloat(),
            ),
            widgetOnj.get<Double>("detailWidth").toFloat()
        ).apply {
            cardScale = widgetOnj.get<Double>("cardScale").toFloat()
            hoveredCardScale = widgetOnj.get<Double>("hoveredCardScale").toFloat()
            cardSpacing = widgetOnj.get<Double>("cardSpacing").toFloat()
            startCardZIndicesAt = widgetOnj.get<Long>("startCardZIndicesAt").toInt()
            hoveredCardZIndex = widgetOnj.get<Long>("hoveredCardZIndex").toInt()
            draggedCardZIndex = widgetOnj.get<Long>("draggedCardZIndex").toInt()
        }

        "Revolver" -> Revolver(
            fontOrError(widgetOnj.get<String>("detailFont")),
            widgetOnj.get<Color>("detailFontColor"),
            TextureRegionDrawable(textureOrError(widgetOnj.get<String>("detailBackgroundTexture"))),
            widgetOnj.get<Double>("detailFontScale").toFloat(),
            Vector2(
                widgetOnj.get<Double>("detailOffsetX").toFloat(),
                widgetOnj.get<Double>("detailOffsetY").toFloat(),
            ),
            widgetOnj.get<Double>("detailWidth").toFloat(),
            widgetOnj.getOr<String?>("background", null)?.let { TextureRegionDrawable(textureOrError(it)) },
            widgetOnj.get<Double>("radiusExtension").toFloat()
        ).apply {
            slotTexture = textureOrError(widgetOnj.get<String>("slotTexture"))
            slotFont = fontOrError(widgetOnj.get<String>("font"))
            fontColor = widgetOnj.get<Color>("fontColor")
            fontScale = widgetOnj.get<Double>("fontScale").toFloat()
            slotScale = widgetOnj.get<Double>("slotScale").toFloat()
            cardScale = widgetOnj.get<Double>("cardScale").toFloat()
            animationDuration = widgetOnj.get<Double>("animationDuration").toFloat()
            radius = widgetOnj.get<Double>("radius").toFloat()
            rotationOff = widgetOnj.get<Double>("rotationOff")
            cardZIndex = widgetOnj.get<Long>("cardZIndex").toInt()
        }

        "EnemyArea" -> EnemyArea().apply {
        }

        "CoverArea" -> CoverArea(
            widgetOnj.get<Long>("numStacks").toInt(),
            widgetOnj.get<Long>("maxCards").toInt(),
            widgetOnj.get<Boolean>("onlyAllowAddingOnTheSameTurn"),
            fontOrError(widgetOnj.get<String>("detailFont")),
            widgetOnj.get<Color>("detailFontColor"),
            textureOrError(widgetOnj.get<String>("stackBackgroundTexture")),
            widgetOnj.get<Double>("detailFontScale").toFloat(),
            widgetOnj.get<Double>("stackSpacing").toFloat(),
            widgetOnj.get<Double>("areaSpacing").toFloat(),
            widgetOnj.get<Double>("cardScale").toFloat(),
            widgetOnj.get<Double>("stackMinSize").toFloat(),
            fontOrError(widgetOnj.get<String>("detailFont")),
            widgetOnj.get<Color>("detailFontColor"),
            TextureRegionDrawable(textureOrError(widgetOnj.get<String>("detailBackgroundTexture"))),
            widgetOnj.get<Double>("detailFontScale").toFloat(),
            Vector2(
                widgetOnj.get<Double>("detailOffsetX").toFloat(),
                widgetOnj.get<Double>("detailOffsetY").toFloat(),
            ),
            widgetOnj.get<Double>("detailWidth").toFloat()
        )

        "TemplateLabel" -> TemplateStringLabel(
            TemplateString(widgetOnj.get<String>("template")),
            Label.LabelStyle(
                fontOrError(widgetOnj.get<String>("font")),
                widgetOnj.get<Color>("color")
            )
        ).apply {
            setFontScale(widgetOnj.get<Double>("fontScale").toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { background = TextureRegionDrawable(textureOrError(it)) }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
        }

        else -> throw RuntimeException("Unknown widget name ${widgetOnj.name}")
    }.apply {
        applySharedWidgetKeys(this, widgetOnj)
    }

    private fun applyTableCellKeys(cellOnj: OnjObject, cell: Cell<Actor>) {

        cellOnj.ifHas<Double>("width") { cell.width(it.toFloat()) }
        cellOnj.ifHas<Double>("height") { cell.height(it.toFloat()) }
        cellOnj.ifHas<Long>("colspan") { cell.colspan(it.toInt()) }
        cellOnj.ifHas<String>("align") { cell.align(alignmentOrError(it)) }
        cellOnj.ifHas<Double>("padTop") { cell.padTop(it.toFloat()) }
        cellOnj.ifHas<Double>("padBottom") { cell.padBottom(it.toFloat()) }
        cellOnj.ifHas<Double>("padLeft") { cell.padLeft(it.toFloat()) }
        cellOnj.ifHas<Double>("padRight") { cell.padRight(it.toFloat()) }
        cellOnj.ifHas<String>("align") { cell.align(alignmentOrError(it)) }

        cellOnj.ifHas<String>("cellName") { namedCells[it] = cell }

        if (cellOnj.getOr("sizeToActor", false)) {
            cell.width(Value.prefWidth)
            cell.height(Value.prefHeight)
        }
        if (cellOnj.getOr("expandX", false)) cell.expandX()
        if (cellOnj.getOr("expandY", false)) cell.expandY()
    }

    private fun applyImageKeys(image: CustomImageActor, widgetOnj: OnjNamedObject) {
//        image.height *= widgetOnj.get<Double>("scaleY").toFloat()
//        image.width *= widgetOnj.get<Double>("scaleX").toFloat()
        image.scaleX = widgetOnj.get<Double>("scaleX").toFloat()
        image.scaleY = widgetOnj.get<Double>("scaleY").toFloat()
        if (widgetOnj.getOr("scalingWorkaround", false)) {
            image.reportDimensionsWithScaling = true
            image.ignoreScalingWhenDrawing = true
        }
    }

    private fun applySharedWidgetKeys(actor: Actor, widgetOnj: OnjNamedObject) = with(actor) {
        debug = widgetOnj.getOr("debug", false)

        widgetOnj.ifHas<OnjNamedObject>("dragAndDrop") {
            applyDragAndDrop(actor, it)
        }

        widgetOnj.ifHas<OnjArray>("behaviours") { arr ->
            arr.value.forEach {
                it as OnjNamedObject
                behavioursToBind.add(BehaviourFactory.behaviorOrError(it.name, it, this))
            }
        }

        widgetOnj.ifHas<Double>("width") { width = it.toFloat() }
        widgetOnj.ifHas<Double>("height") { height = it.toFloat() }

        widgetOnj.ifHas<Long>("zIndex") {
            if (this !is ZIndexActor) throw RuntimeException("can only apply z-index to ZIndexActors")
            fixedZIndex = it.toInt()
        }

        widgetOnj.ifHas<Double>("x") { x = it.toFloat() }
        widgetOnj.ifHas<Double>("y") { y = it.toFloat() }
        widgetOnj.ifHas<Boolean>("visible") { isVisible = it }
        widgetOnj.ifHas<String>("name") { namedActors[it] = this }
    }

    private fun applyDragAndDrop(actor: Actor, onj: OnjNamedObject) {
        val group = onj.get<String>("group")
        if (!actorsWithDragAndDrop.containsKey(group)) actorsWithDragAndDrop[group] = mutableListOf()
        actorsWithDragAndDrop[group]!!.add(actor to onj)
    }

    private fun alignmentOrError(alignment: String): Int = when (alignment) {
        "center" -> Align.center
        "top" -> Align.top
        "bottom" -> Align.bottom
        "left" -> Align.left
        "bottom left" -> Align.bottomLeft
        "top left" -> Align.topLeft
        "right" -> Align.right
        "bottom right" -> Align.bottomRight
        "top right" -> Align.topRight
        else -> throw RuntimeException("unknown alignment: $alignment")
    }

    private fun textureOrError(name: String): TextureRegion {
        return textures[name] ?: throw RuntimeException("Unknown texture: $name")
    }

    private fun fontOrError(name: String): BitmapFont {
        return fonts[name] ?: throw RuntimeException("Unknown font: $name")
    }

    private fun animationOrError(name: String): FrameAnimation {
        return animations[name] ?: throw RuntimeException("Unknown animation: $name")
    }

    companion object {

        const val logTag = "screenBuilder"

        private const val screenSchemaPath = "onjschemas/screen.onjschema"

        private val screenSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(screenSchemaPath).file())
        }

    }

}


/**
 * a postProcessor that is applied to the whole screen
 * @param shader the shader that is applied to the screen
 * @param uniformsToBind the names of the uniforms used by the shader (without prefix)
 * @param arguments additional uniforms to bind
 * @param timeOffset if the shader has a time-uniform, the time will be offset by [timeOffset] ms
 */
data class PostProcessor(
    val shader: ShaderProgram,
    val uniformsToBind: List<String>,
    val arguments: Map<String, Any?>,
    val timeOffset: Int = 0
) : Disposable {

    private val creationTime = TimeUtils.millis()
    private var referenceTime = creationTime + timeOffset

    /**
     * resets the point relative to which the time is calculated to now (+[timeOffset])
     */
    fun resetReferenceTime() {
        referenceTime = TimeUtils.millis() + timeOffset
    }

    override fun dispose() = shader.dispose()

    /**
     * binds the uniforms specified in [uniformsToBind] to the shader
     */
    fun bindUniforms() {
        for (uniform in uniformsToBind) when (uniform) {

            "time" -> {
                val uTime = (TimeUtils.timeSinceMillis(referenceTime) / 100.0).toFloat()
                shader.setUniformf("u_time", uTime)
            }

            "cursorPosition" -> {
                shader.setUniformf(
                    "u_cursorPos",
                    Vector2(
                        Gdx.input.x.toFloat(),
                        Gdx.graphics.height - Gdx.input.y.toFloat()
                    )
                )
            }

            "resolution" -> {
                shader.setUniformf(
                    "u_resolution",
                    Vector2(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
                )
            }

            else -> throw RuntimeException("unknown uniform: $uniform")
        }
    }

    /**
     * binds the arguments specified in [arguments] to the shader
     */
    fun bindArgUniforms() {
        for ((key, value) in arguments) when (value) {

            is Float -> {
                shader.setUniformf("u_arg_$key", value)
            }

            is Color -> {
                shader.setUniformf("u_arg_$key", value.r,  value.g, value.b, value.a)
            }

            else -> throw RuntimeException("binding uniform arguments of type ${
                value?.let { it::class.simpleName } ?: "null"
            } is currently not supported")

        }
    }

}
