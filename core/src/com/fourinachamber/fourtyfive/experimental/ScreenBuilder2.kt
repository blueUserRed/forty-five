package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.screen.gameComponents.CardHand
import com.fourinachamber.fourtyfive.screen.gameComponents.CoverArea
import com.fourinachamber.fourtyfive.screen.gameComponents.EnemyArea
import com.fourinachamber.fourtyfive.screen.gameComponents.Revolver
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.FrameAnimation
import com.fourinachamber.fourtyfive.utils.OnjReaderUtils
import com.fourinachamber.fourtyfive.utils.TemplateString
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.enums.YogaEdge
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

class ScreenBuilder2(val file: FileHandle) : ScreenBuilder {

    private var fonts: MutableMap<String, BitmapFont> = mutableMapOf()
    private val drawables: MutableMap<String, Drawable> = mutableMapOf()
    private var cursors: MutableMap<String, Cursor> = mutableMapOf()
    private var postProcessors: MutableMap<String, PostProcessor> = mutableMapOf()
    private var animations: MutableMap<String, FrameAnimation> = mutableMapOf()
    private var particles: MutableMap<String, ParticleEffect> = mutableMapOf()
    private val styles: MutableMap<String, Style> = mutableMapOf()
    private val toDispose: MutableList<Disposable> = mutableListOf()

    private val earlyRenderTasks: MutableList<OnjScreen.() -> Unit> = mutableListOf()
    private val lateRenderTasks: MutableList<OnjScreen.() -> Unit> = mutableListOf()

    private val behavioursToBind: MutableList<Behaviour> = mutableListOf()
    private var actorsWithDragAndDrop: MutableMap<String, MutableList<Pair<Actor, OnjNamedObject>>> = mutableMapOf()

    private val styleTargets: MutableList<StyleTarget> = mutableListOf()
    private val namedActors: MutableMap<String, Actor> = mutableMapOf()

    private var screenController: ScreenController? = null

    override fun build(): OnjScreen {
        val onj = OnjParser.parseFile(file.file())
        screenSchema.assertMatches(onj)
        onj as OnjObject

        readAssets(onj)
        doOptions(onj)

        val root = FlexBox()
        root.setFillParent(true)
        getWidget(onj.get<OnjNamedObject>("root"), root)

        val screen = StyleableOnjScreen(
            drawables = drawables,
            cursors = cursors,
            fonts = fonts,
            particles = particles,
            postProcessors = postProcessors,
            children = listOf(root),
            viewport = getViewport(onj.get<OnjNamedObject>("viewport")),
            batch = SpriteBatch(),
            styleTargets = styleTargets,
            toDispose = toDispose,
            earlyRenderTasks = earlyRenderTasks,
            lateRenderTasks = lateRenderTasks,
            namedActors = namedActors,
            behaviours = behavioursToBind,
            printFrameRate = false
        )

        screen.screenController = screenController

        val dragAndDrops = doDragAndDrop(screen)
        screen.dragAndDrop = dragAndDrops

        for (behaviour in behavioursToBind) behaviour.bindCallbacks(screen)

        return screen
    }

    private fun doOptions(onj: OnjObject) {
        val options = onj.get<OnjObject>("options")
        options.ifHas<String>("background") {
            val drawable = drawableOrError(it)
            earlyRenderTasks.add {
                drawable.draw(stage.batch, 0f, 0f, stage.viewport.worldWidth, stage.viewport.worldHeight)
            }
        }
        options.ifHas<OnjNamedObject>("screenController") {
            screenController = ScreenControllerFactory.controllerOrError(it.name, it)
        }
    }

    private fun readAssets(onj: OnjObject) {
        val assets = onj.get<OnjObject>("assets")

        if (assets.hasKey<OnjArray>("textures")) {
            val textures = OnjReaderUtils.readTextures(assets.get<OnjArray>("textures"))
            drawables.putAll(
                textures.mapValues { TextureRegionDrawable(it.value) }
            )
            textures.values.forEach { region -> region.texture?.let { toDispose.add(it) } }
        }
        if (assets.hasKey<OnjArray>("fonts")) {
            fonts = OnjReaderUtils.readFonts(assets.get<OnjArray>("fonts")).toMutableMap()
            toDispose.addAll(fonts.values)
        }
        if (assets.hasKey<OnjArray>("textureAtlases")) {
            val (regions, atlases) = OnjReaderUtils.readAtlases(assets.get<OnjArray>("textureAtlases"))
            drawables.putAll(
                regions.mapValues { TextureRegionDrawable(it.value) }
            )
            toDispose.addAll(atlases)
        }
        if (assets.hasKey<OnjArray>("cursors")) {
            val cursors = OnjReaderUtils.readCursors(assets.get<OnjArray>("cursors"))
            this.cursors = cursors.toMutableMap()
            toDispose.addAll(cursors.values)
        }
        if (assets.hasKey<OnjArray>("postProcessors")) {
            val postProcessors = OnjReaderUtils.readPostProcessors(assets.get<OnjArray>("postProcessors"))
            this.postProcessors = postProcessors.toMutableMap()
            toDispose.addAll(postProcessors.values)
        }
        if (assets.hasKey<OnjArray>("animations")) {
            val animations = OnjReaderUtils.readAnimations(assets.get<OnjArray>("animations"))
            this.animations = animations.toMutableMap()
            toDispose.addAll(animations.values)
        }
        if (assets.hasKey<OnjArray>("colorTextures")) {
            val colorTextures = OnjReaderUtils.readColorTextures(assets.get<OnjArray>("colorTextures"))
            drawables.putAll(
                colorTextures.mapValues { TextureRegionDrawable(it.value) }
            )
            colorTextures.values.forEach { region -> region.texture?.let { toDispose.add(it) } }
        }
        if (assets.hasKey<OnjArray>("particles")) {
            val particles = OnjReaderUtils.readParticles(assets.get<OnjArray>("particles"))
            this.particles = particles.toMutableMap()
            toDispose.addAll(particles.values)
        }
        if (assets.hasKey<OnjArray>("styleFiles")) {
            assets
                .get<OnjArray>("styleFiles")
                .value
                .forEach {
                    styles.putAll(Style.readFromFile(it.value as String))
                }
        }
        if (assets.hasKey<OnjArray>("styles")) {
            assets
                .get<OnjArray>("styles")
                .value
                .map { Style.readStyle(it as OnjObject) }
                .forEach {
                    styles[it.first] = it.second
                }
        }
        assets.ifHas<OnjArray>("ninepatches") {
            val (textures, ninepatches) = OnjReaderUtils.readNinepatches(it)
            drawables.putAll(ninepatches)
            toDispose.addAll(textures)
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

    private fun getFlexBox(widgetOnj: OnjObject): FlexBox {
        val flexBox = CustomFlexBox()
        flexBox.root.setPosition(YogaEdge.ALL, 0f)
        if (widgetOnj.hasKey<OnjArray>("children")) {
            widgetOnj
                .get<OnjArray>("children")
                .value
                .forEach {
                    getWidget(it as OnjNamedObject, flexBox)
                }
        }
        return flexBox
    }

    private fun getViewport(viewportOnj: OnjNamedObject): Viewport = when (viewportOnj.name) {

        "FitViewport" -> {
            val worldHeight = viewportOnj.get<Double>("worldHeight").toFloat()
            val worldWidth = viewportOnj.get<Double>("worldWidth").toFloat()
            FitViewport(worldWidth, worldHeight)
        }

        "ExtendViewport" -> {
            val minWidth = viewportOnj.get<Double>("minWidth").toFloat()
            val minHeight = viewportOnj.get<Double>("minWidth").toFloat()
            val viewport = ExtendViewport(minWidth, minHeight)
            viewport
        }

        else -> throw RuntimeException("unknown Viewport ${viewportOnj.name}")

    }.apply {
        if (!viewportOnj.hasKey<String>("backgroundTexture")) return@apply
        val background = drawableOrError(viewportOnj.get<String>("backgroundTexture"))
        earlyRenderTasks.add {
            background.draw(stage.batch, 0f, 0f, viewport.worldWidth, viewport.worldHeight)
        }
    }

    private fun getWidget(widgetOnj: OnjNamedObject, parent: FlexBox?): Actor = when (widgetOnj.name) {

        "Image" -> CustomImageActor(drawableOrError(widgetOnj.get<String>("textureName"))).apply {
            applyImageKeys(this, widgetOnj)
        }

        "Box" -> getFlexBox(widgetOnj)

        "Label" -> CustomLabel(
            text = widgetOnj.get<String>("text"),
            labelStyle = Label.LabelStyle(
                fontOrError(widgetOnj.get<String>("font")),
                widgetOnj.get<Color>("color")
            )
        ).apply {
            setFontScale(widgetOnj.getOr("fontScale", 1.0).toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { background = drawableOrError(it) }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
        }

        "AnimatedImage" -> AnimatedImage(animationOrError(widgetOnj.get<String>("animationName"))).apply {
            applyImageKeys(this, widgetOnj)
        }

        "CardHand" -> CardHand(
            widgetOnj.get<Double>("targetWidth").toFloat()
        ).apply {
            cardScale = widgetOnj.get<Double>("cardScale").toFloat()
            hoveredCardScale = widgetOnj.get<Double>("hoveredCardScale").toFloat()
            cardSpacing = widgetOnj.get<Double>("cardSpacing").toFloat()
            startCardZIndicesAt = widgetOnj.get<Long>("startCardZIndicesAt").toInt()
            hoveredCardZIndex = widgetOnj.get<Long>("hoveredCardZIndex").toInt()
            draggedCardZIndex = widgetOnj.get<Long>("draggedCardZIndex").toInt()
        }

        "Revolver" -> Revolver(
            widgetOnj.getOr<String?>("background", null)?.let { drawableOrError(it) },
            widgetOnj.get<Double>("radiusExtension").toFloat()
        ).apply {
            slotDrawable = drawableOrError(widgetOnj.get<String>("slotTexture"))
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
            widgetOnj.get<Double>("detailFontScale").toFloat(),
            widgetOnj.get<Double>("stackSpacing").toFloat(),
            widgetOnj.get<Double>("areaSpacing").toFloat(),
            widgetOnj.get<Double>("cardScale").toFloat(),
            widgetOnj.get<Double>("stackMinSize").toFloat()
        )

        "TemplateLabel" -> TemplateStringLabel(
            TemplateString(widgetOnj.get<String>("template")),
            Label.LabelStyle(
                fontOrError(widgetOnj.get<String>("font")),
                widgetOnj.get<Color>("color")
            )
        ).apply {
            setFontScale(widgetOnj.get<Double>("fontScale").toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { background = drawableOrError(it) }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
        }

        else -> throw RuntimeException("Unknown widget name ${widgetOnj.name}")
    }.let { actor ->

        applySharedWidgetKeys(actor, widgetOnj)
        val node = parent?.add(actor)

        val styles = if (widgetOnj.hasKey<OnjArray>("styles")) {
            val styles = widgetOnj.get<OnjArray>("styles")
            styles
                .value
               .map { styleOrError(it.value as String) }
        } else null

        val directProperties = if (widgetOnj.hasKey<OnjArray>("properties")) {
            val properties = widgetOnj.get<OnjArray>("properties")
            properties
                .value
                .map {
                    it as OnjStyleProperty
                    it.value
                }
        } else null

        if (styles != null || directProperties != null) {
            node ?: throw RuntimeException(
                "root box can currently not be styled"
            )
            styleTargets.add(StyleTarget(
                node,
                actor,
                styles ?: listOf(),
                directProperties ?: listOf()
            ))
        }

        return actor
    }

    private fun applyImageKeys(image: CustomImageActor, widgetOnj: OnjNamedObject) {
        image.scaleX = widgetOnj.get<Double>("scaleX").toFloat()
        image.scaleY = widgetOnj.get<Double>("scaleY").toFloat()
        if (widgetOnj.getOr("reportDimensionsWithScaling", false)) {
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

        widgetOnj.ifHas<Long>("zIndex") {
            if (this !is ZIndexActor) throw RuntimeException("can only apply z-index to ZIndexActors")
            fixedZIndex = it.toInt()
        }

        widgetOnj.ifHas<Boolean>("visible") { isVisible = it }
        widgetOnj.ifHas<String>("name") { namedActors[it] = this }
    }

    private fun applyDragAndDrop(actor: Actor, onj: OnjNamedObject) {
        val group = onj.get<String>("group")
        if (!actorsWithDragAndDrop.containsKey(group)) actorsWithDragAndDrop[group] = mutableListOf()
        actorsWithDragAndDrop[group]!!.add(actor to onj)
    }

    private fun fontOrError(name: String): BitmapFont {
        return fonts[name] ?: throw RuntimeException("Unknown font: $name")
    }

    private fun drawableOrError(name: String): Drawable {
        return drawables[name] ?: throw RuntimeException("Unknown drawable: $name")
    }

    private fun animationOrError(name: String): FrameAnimation {
        return animations[name] ?: throw RuntimeException("unknown animation: $name")
    }

    private fun styleOrError(name: String): Style {
        return styles[name] ?: throw RuntimeException("unknown style: $name")
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

    companion object {

        val screenSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/screen2.onjschema").file())
        }

    }

}
