package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.onjNamespaces.OnjStyleProperty
import com.fourinachamber.fourtyfive.screen.general.styles.Style
import com.fourinachamber.fourtyfive.screen.general.styles.StyleTarget
import com.fourinachamber.fourtyfive.keyInput.KeyInputMap
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.gameComponents.CardHand
import com.fourinachamber.fourtyfive.screen.gameComponents.CoverArea
import com.fourinachamber.fourtyfive.screen.gameComponents.EnemyArea
import com.fourinachamber.fourtyfive.screen.gameComponents.Revolver
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.FrameAnimation
import com.fourinachamber.fourtyfive.utils.TemplateString
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.enums.YogaEdge
import ktx.actors.onClick
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*

class ScreenBuilder(val file: FileHandle) {

    private val styles: MutableMap<String, Style> = mutableMapOf()
    private var borrowed: List<String> = listOf()

    private val earlyRenderTasks: MutableList<OnjScreen.() -> Unit> = mutableListOf()
    private val lateRenderTasks: MutableList<OnjScreen.() -> Unit> = mutableListOf()

    private val behavioursToBind: MutableList<Behaviour> = mutableListOf()
    private var actorsWithDragAndDrop: MutableMap<String, MutableList<Pair<Actor, OnjNamedObject>>> = mutableMapOf()

    private val styleTargets: MutableList<StyleTarget> = mutableListOf()
    private val namedActors: MutableMap<String, Actor> = mutableMapOf()

    private var screenController: ScreenController? = null
    private var background: String? = null
    private var postProcessor: String? = null

    fun build(): OnjScreen {
        val onj = OnjParser.parseFile(file.file())
        screenSchema.assertMatches(onj)
        onj as OnjObject

        readAssets(onj)
        doOptions(onj)

        val screen = StyleableOnjScreen(
            viewport = getViewport(onj.get<OnjNamedObject>("viewport")),
            batch = SpriteBatch(),
            styleTargets = styleTargets,
            background = background,
            useAssets = borrowed,
            earlyRenderTasks = earlyRenderTasks,
            lateRenderTasks = lateRenderTasks,
            namedActors = namedActors,
            printFrameRate = false
        )


        onj.get<OnjObject>("options").ifHas<OnjArray>("inputMap") {
            screen.inputMap = KeyInputMap.readFromOnj(it, screen)
        }

        val root = CustomFlexBox()
        root.setFillParent(true)
//        root.debug = true
        getWidget(onj.get<OnjNamedObject>("root"), root, screen)

        screen.addActorToRoot(root)
        screen.buildKeySelectHierarchy()

        postProcessor?.let {
            screen.postProcessor = ResourceManager.get<PostProcessor>(screen, it)
        }

        screen.screenController = screenController

        val dragAndDrops = doDragAndDrop(screen)
        screen.dragAndDrop = dragAndDrops

        for (behaviour in behavioursToBind) behaviour.bindCallbacks(screen)

        return screen
    }

    private fun doOptions(onj: OnjObject) {
        val options = onj.get<OnjObject>("options")
        options.ifHas<String>("background") {
            background = it
        }
        options.ifHas<OnjNamedObject>("screenController") {
            screenController = ScreenControllerFactory.controllerOrError(it.name, it)
        }
        options.ifHas<String>("postProcessor") {
            postProcessor = it
        }
    }

    private fun readAssets(onj: OnjObject) {
        val assets = onj.get<OnjObject>("assets")

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

        val toBorrow = mutableListOf<String>()

        assets.ifHas<OnjArray>("useAssets") { arr ->
            toBorrow.addAll(arr.value.map { (it as OnjString).value })
        }

        if (assets.getOr("useCardAtlas", false)) {
            val cardResources = ResourceManager
                .resources
                .map { it.handle }
                .filter { it.startsWith(Card.cardTexturePrefix) }
            toBorrow.addAll(cardResources)
            toBorrow.add(ResourceManager.cardAtlasResourceHandle)
        }

        borrowed = toBorrow
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

    private fun getFlexBox(widgetOnj: OnjObject, screen: OnjScreen): FlexBox {
        val flexBox = CustomFlexBox(widgetOnj.getOr("partOfSelectionHierarchy", false))
        flexBox.root.setPosition(YogaEdge.ALL, 0f)
        if (widgetOnj.hasKey<OnjArray>("children")) {
            widgetOnj
                .get<OnjArray>("children")
                .value
                .forEach {
                    getWidget(it as OnjNamedObject, flexBox, screen)
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

    }

    private fun getWidget(
        widgetOnj: OnjNamedObject,
        parent: FlexBox?,
        screen: OnjScreen
    ): Actor = when (widgetOnj.name) {

        "Image" -> CustomImageActor(
            drawableOrError(widgetOnj.get<String>("textureName"), screen),
            widgetOnj.getOr("partOfSelectionHierarchy", false)
        ).apply {
            applyImageKeys(this, widgetOnj)
        }

        "Box" -> getFlexBox(widgetOnj, screen)

        "Label" -> CustomLabel(
            text = widgetOnj.get<String>("text"),
            labelStyle = Label.LabelStyle().apply {
                font = fontOrError(widgetOnj.get<String>("font"), screen)
                if (!widgetOnj.get<OnjValue>("color").isNull()) {
                    fontColor = widgetOnj.get<Color>("color")
                }
            },
            partOfHierarchy = widgetOnj.getOr("partOfSelectionHierarchy", false)
        ).apply {
            setFontScale(widgetOnj.getOr("fontScale", 1.0).toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { background = drawableOrError(it, screen) }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap = it }
        }

        "AnimatedImage" -> AnimatedImage(
            animationOrError(widgetOnj.get<String>("animationName"), screen),
            widgetOnj.getOr("partOfSelectionHierarchy", false)
        ).apply {
            applyImageKeys(this, widgetOnj)
        }

        "CardHand" -> CardHand(
            widgetOnj.get<Double>("targetWidth").toFloat(),
            screen
        ).apply {
            cardScale = widgetOnj.get<Double>("cardScale").toFloat()
            hoveredCardScale = widgetOnj.get<Double>("hoveredCardScale").toFloat()
            cardSpacing = widgetOnj.get<Double>("cardSpacing").toFloat()
            startCardZIndicesAt = widgetOnj.get<Long>("startCardZIndicesAt").toInt()
            hoveredCardZIndex = widgetOnj.get<Long>("hoveredCardZIndex").toInt()
            draggedCardZIndex = widgetOnj.get<Long>("draggedCardZIndex").toInt()
        }

        "Revolver" -> Revolver(
            widgetOnj.getOr<String?>("background", null)?.let { drawableOrError(it, screen) },
            widgetOnj.get<Double>("radiusExtension").toFloat(),
            screen
        ).apply {
            slotDrawable = drawableOrError(widgetOnj.get<String>("slotTexture"), screen)
            slotFont = fontOrError(widgetOnj.get<String>("font"), screen)
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
            fontOrError(widgetOnj.get<String>("detailFont"), screen),
            widgetOnj.get<Color>("detailFontColor"),
            widgetOnj.get<Double>("detailFontScale").toFloat(),
            widgetOnj.get<Double>("areaSpacing").toFloat(),
            widgetOnj.get<Double>("cardScale").toFloat(),
            widgetOnj.get<Double>("stackHeight").toFloat(),
            widgetOnj.get<Double>("stackMinWidth").toFloat(),
            widgetOnj.get<Double>("cardInitialX").toFloat(),
            widgetOnj.get<Double>("cardInitialY").toFloat(),
            widgetOnj.get<Double>("cardDeltaX").toFloat(),
            widgetOnj.get<Double>("cardDeltaY").toFloat(),
            drawableOrError(widgetOnj.get<String>("stackHook"), screen)
        )

        "TemplateLabel" -> TemplateStringLabel(
            templateString = TemplateString(widgetOnj.get<String>("template")),
            labelStyle = Label.LabelStyle(
                fontOrError(widgetOnj.get<String>("font"), screen),
                widgetOnj.get<Color>("color")
            ),
            partOfHierarchy = widgetOnj.getOr("partOfSelectionHierarchy", false)
        ).apply {
            setFontScale(widgetOnj.get<Double>("fontScale").toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { background = drawableOrError(it, screen) }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap = it }
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

        onClick { fire(ButtonClickEvent()) }
    }

    private fun applyDragAndDrop(actor: Actor, onj: OnjNamedObject) {
        val group = onj.get<String>("group")
        if (!actorsWithDragAndDrop.containsKey(group)) actorsWithDragAndDrop[group] = mutableListOf()
        actorsWithDragAndDrop[group]!!.add(actor to onj)
    }

    private fun fontOrError(name: String, screen: OnjScreen): BitmapFont {
        return ResourceManager.get(screen, name)
    }

    private fun drawableOrError(name: String, screen: OnjScreen): Drawable {
        return ResourceManager.get(screen, name)
    }

    private fun animationOrError(name: String, screen: OnjScreen): FrameAnimation {
        return ResourceManager.get(screen, name)
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
