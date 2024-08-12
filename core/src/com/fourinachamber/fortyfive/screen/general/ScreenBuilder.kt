package com.fourinachamber.fortyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.DetailMapWidget
import com.fourinachamber.fortyfive.map.events.dialog.DialogWidget
import com.fourinachamber.fortyfive.map.statusbar.Backpack
import com.fourinachamber.fortyfive.map.statusbar.StatusbarWidget
import com.fourinachamber.fortyfive.onjNamespaces.OnjColor
import com.fourinachamber.fortyfive.onjNamespaces.OnjStyleInstruction
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.gameComponents.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.enums.YogaEdge
import ktx.actors.alpha
import ktx.actors.onClick
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*

class ScreenBuilder(val screenName: String, val onj: OnjObject) : ResourceBorrower {

    private val earlyRenderTasks: MutableList<OnjScreen.() -> Unit> = mutableListOf()
    private val lateRenderTasks: MutableList<OnjScreen.() -> Unit> = mutableListOf()
    private var actorsWithDragAndDrop: MutableMap<String, MutableList<Pair<Actor, OnjNamedObject>>> = mutableMapOf()
    private val addedActorsDragAndDrops: MutableMap<String, MutableList<Actor>> = mutableMapOf()

    private val namedActors: MutableMap<String, Actor> = mutableMapOf()

    private var screenControllers: List<ScreenController> = listOf()
    private var background: String? = null
    private var music: ResourceHandle? = null
    private var playAmbientSounds: Boolean = false
    private val templateObjects: MutableMap<String, OnjNamedObject> = mutableMapOf()

    @MainThreadOnly
    fun build(controllerContext: Any? = null): OnjScreen {

        earlyRenderTasks.clear()
        lateRenderTasks.clear()
        actorsWithDragAndDrop.clear()
        addedActorsDragAndDrops.clear()
        namedActors.clear()

        doOptions(onj)
        doTemplates(onj)

        val screen = OnjScreen(
            viewport = getViewport(onj.get<OnjNamedObject>("viewport")),
            batch = SpriteBatch(),
            controllerContext = controllerContext,
            styleManagers = listOf(),
            earlyRenderTasks = earlyRenderTasks,
            lateRenderTasks = lateRenderTasks,
            namedActors = namedActors,
            printFrameRate = false,
            namedCells = mapOf(),
            transitionAwayTimes = doTransitionAwayTimes(),
            screenBuilder = this,
            music = music,
            playAmbientSounds = playAmbientSounds
        )
        screen.background = background

        onj.get<OnjObject>("options").ifHas<OnjArray>("inputMap") {
            screen.inputMap = KeyInputMap.readFromOnj(it, screen)
        }

        val root = CustomFlexBox(screen, false)
        root.setFillParent(true)
        getWidget(onj.get<OnjNamedObject>("root"), root, screen)

        screen.addActorToRoot(root)
        screen.buildKeySelectHierarchy()

        onj
            .get<OnjObject>("options")
            .getOr<OnjArray?>("screenControllers", null)
            ?.value
            ?.map { obj ->
                obj as OnjNamedObject
                ScreenControllerFactory.controllerOrError(obj.name, screen, obj).also { it.initEventHandler() }
            }
            ?.forEach { screen.addScreenController(it) }

        screenControllers.forEach { screen.addScreenController(it) }

        doDragAndDrop(screen)

        root.addListener { event ->
            screen.screenControllers.forEach { it.onUnhandledEvent(event) }
            false
        }

        return screen
    }

    private fun doTemplates(onj: OnjObject) {
        onj.ifHas<OnjArray>("templates") {
            for (a in it.value) {
                a as OnjNamedObject
                if (!a.hasKey<String>("template_name") || !a.hasKey<OnjObject>("template_keys")) {
                    throw RuntimeException("templates must define both template_name and template_keys!")
                }
                templateObjects[a.get<String>("template_name")] = a
            }
        }
    }

    fun generateFromTemplate(name: String, data: Map<String, Any?>, parent: FlexBox?, screen: OnjScreen): Actor? {
        val onjData: MutableMap<String, OnjValue> = mutableMapOf()
        data.forEach { onjData[it.key] = getAsOnjValue(it.value) }
        val template = templateObjects[name] ?: return null
        val combinedData = combineTemplateValues(template.get<OnjObject>("template_keys"), onjData)
        val widgetOnj = generateTemplateOnjValue(template, combinedData, "")
        widgetOnj as OnjNamedObject
        val curActor = getWidget(widgetOnj, parent, screen)
        doDragAndDrop(screen)
        return curActor
    }

    fun addDataToWidgetFromTemplate(
        name: String,
        data: Map<String, Any?>,
        parent: FlexBox?,
        screen: OnjScreen,
        actor: Actor,
        removeOldData: Boolean = true,
    ) {
        val onjData: MutableMap<String, OnjValue> = mutableMapOf()
        data.forEach { onjData[it.key] = getAsOnjValue(it.value) }
        val template = templateObjects[name] ?: return
        val combinedData = combineTemplateValues(template.get<OnjObject>("template_keys"), onjData)
        val widgetOnj = generateTemplateOnjValue(template, combinedData, "")
        widgetOnj as OnjNamedObject
        if (removeOldData) {
//            behavioursToBind.removeIf { it.actor == actor } //I don't know how to do that properly
            val dragAndDrops = screen.dragAndDrop
            addedActorsDragAndDrops.forEach {
                if (it.value.remove(actor)){
                    dragAndDrops[it.key]?.removeAllListenersWithActor(actor)
                }
            }
        }
        applyWidgetKeysFromOnj(actor, widgetOnj, parent, screen)
        doDragAndDrop(screen)
    }

    fun getAsOnjValue(value: Any?): OnjValue {
        return when (value) {
            is OnjValue -> value
            is Float, Double -> OnjFloat((value as Number).toDouble())
            is Long, Int -> OnjInt((value as Number).toLong())
            is String -> OnjString(value)
            is Color -> OnjColor(value)
            is Array<*> -> OnjArray((value as List<*>).map { getAsOnjValue(it) })
            is Map<*, *> -> OnjObject(value.map { it.key as String to getAsOnjValue(it.value) }.toMap())
            null -> OnjNull()
            else -> {
                throw java.lang.Exception("Unexpected Onj Type, not implemented: " + value::class)
            }
        }
    }

    private fun generateTemplateOnjValue(
        original: OnjValue,
        combinedData: Map<String, OnjValue>,
        name: String
    ): OnjValue = when (original) { //"styles.0.background" in combinedData.keys && combinedData.get("styles.0.background").value =="collection_slot_locked"

        is OnjObject -> {
            val new = original.value.mapValues { (key, value) ->
                val childKey = if (name == "") key else "$name.$key"
                combinedData[childKey] ?: generateTemplateOnjValue(value, combinedData, childKey)
            }
            if (original is OnjNamedObject) OnjNamedObject(original.name, new) else OnjObject(new)
        }

        is OnjArray -> {
            val new = original.value.mapIndexed { index, value ->
                val childKey = "$name.$index"
                combinedData[childKey] ?: generateTemplateOnjValue(value, combinedData, childKey)
            }
            OnjArray(new)
        }

        else -> original
    }

    private fun combineTemplateValues(templateKeys: OnjObject, data: Map<String, OnjValue>): Map<String, OnjValue> {
        val result = mutableMapOf<String, OnjValue>()
        val keys = templateKeys
            .value
            .mapValues { (_, value) ->
                if (value !is OnjString) throw RuntimeException("template_keys can only contain OnjStrings!")
                value
            }
        data.forEach { (dataPointName, value) ->
            val curKeys = keys.entries.filter { it.value.value == dataPointName }.map { it.key }
            if (curKeys.isEmpty())
                throw RuntimeException("cannot set $dataPointName in template because it is not defined in the template keys")
            curKeys.forEach { result[it] = value }
        }
        return result
    }

    private fun doTransitionAwayTimes(): Map<String, Int> = onj
        .get<OnjObject>("options")
        .getOr<OnjArray?>("transitionAwayTimes", null)
        ?.let { arr ->
            arr
                .value
                .associate {
                    it as OnjObject
                    it.get<String>("screen") to (it.get<Double>("time") * 1000).toInt()
                }
        } ?: mapOf()

    private fun doOptions(onj: OnjObject) {
        val options = onj.get<OnjObject>("options")
        options.ifHas<String>("background") {
            background = it
        }
        options.ifHas<String>("music") {
            music = it
        }
        playAmbientSounds = options.get<Boolean>("playAmbientSounds")
    }

    private fun doDragAndDrop(screen: OnjScreen) {
        val dragAndDrops = screen.dragAndDrop.toMutableMap()
        for ((group, actors) in actorsWithDragAndDrop) {

            if (addedActorsDragAndDrops[group] == null) addedActorsDragAndDrops[group] = mutableListOf()

            val dragAndDrop = dragAndDrops[group] ?: DragAndDrop()
            dragAndDrop.dragTime = 10
            for ((actor, onj) in actors) {
                if (actor in addedActorsDragAndDrops[group]!!) continue

                val behaviour = DragAndDropBehaviourFactory.behaviourOrError(
                    onj.name,
                    dragAndDrop,
                    screen,
                    actor,
                    onj
                )
                if (behaviour is Either.Left) dragAndDrop.addSource(behaviour.value)
                else dragAndDrop.addTarget((behaviour as Either.Right).value)

                addedActorsDragAndDrops[group]!!.add(actor)
            }
            dragAndDrops[group] = dragAndDrop
        }
        screen.dragAndDrop = dragAndDrops
    }

    private fun initFlexBox(
        flexBox: CustomFlexBox,
        widgetOnj: OnjObject,
        screen: OnjScreen
    ) {
        flexBox.root.setPosition(YogaEdge.ALL, 0f)
        if (widgetOnj.hasKey<OnjArray>("children")) {
            widgetOnj
                .get<OnjArray>("children")
                .value
                .forEach {
                    getWidget(it as OnjNamedObject, flexBox, screen)
                }
        }
        flexBox.isTransform = widgetOnj.getOr("enableTransform", false)
        flexBox.resortZIndices()
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
            widgetOnj.getOr<String?>("textureName", null),
            screen,
            widgetOnj.getOr("partOfSelectionHierarchy", false)
        ).apply {
            applyImageKeys(this, widgetOnj)
        }

        "Box" -> CustomFlexBox(
            screen,
            widgetOnj.getOr("hasHoverDetail", false),
            widgetOnj.getOr("hoverText", "")
        ).apply {
            initFlexBox(this, widgetOnj, screen)
        }

        "ScrollBox" -> CustomScrollableFlexBox(
            screen,
            widgetOnj.get<Boolean>("isScrollDirectionVertical"),
            widgetOnj.get<Double>("scrollDistance").toFloat(),
            widgetOnj.get<Boolean>("backgroundStretched"),
            widgetOnj.get<String?>("scrollbarBackgroundName"),
            widgetOnj.get<String?>("scrollbarName"),
            widgetOnj.get<String?>("scrollbarSide"),
        ).apply {
            initFlexBox(this, widgetOnj, screen)
            this.touchable = Touchable.enabled
        }

        "Statusbar" -> StatusbarWidget(
            widgetOnj.get<String?>("mapIndicatorWidgetName"),
            widgetOnj.get<String>("optionsWidgetName"),
            widgetOnj.get<String>("backgroundWidgetName"),
            widgetOnj.get<OnjArray>("options").value as List<OnjObject>,
            screen
        ).apply {
            initFlexBox(this, widgetOnj, screen)
        }

        "Label" -> CustomLabel(
            text = widgetOnj.get<String>("text"),
            labelStyle = LabelStyle().apply {
                font = forceLoadFont(
                    widgetOnj.get<String>("font"),
                    screen
                ) // TODO: figure out how to not load the font immediatley
                if (!widgetOnj.get<OnjValue>("color").isNull()) {
                    fontColor = widgetOnj.get<Color>("color")
                }
            },
            isDistanceField = widgetOnj.getOr("isDistanceFiled", true),
            partOfHierarchy = widgetOnj.getOr("partOfSelectionHierarchy", false),
            hasHoverDetail = widgetOnj.getOr("hasHoverDetail", false),
            hoverText = widgetOnj.getOr("hoverText", ""),
            screen = screen
        ).apply {
            setFontScale(widgetOnj.getOr("fontScale", 1.0).toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { backgroundHandle = it }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap = it }
        }

        "InputField" -> CustomInputField(
            defText = widgetOnj.get<String>("text"),
            labelStyle = LabelStyle().apply {
                font = forceLoadFont(
                    widgetOnj.get<String>("font"),
                    screen
                ) // TODO: figure out how to not load the font immediatley
                if (!widgetOnj.get<OnjValue>("color").isNull()) {
                    fontColor = widgetOnj.get<Color>("color")
                }
            },
            partOfHierarchy = widgetOnj.getOr("partOfSelectionHierarchy", false),
            screen = screen
        ).apply {
            setFontScale(widgetOnj.getOr("fontScale", 1.0).toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { backgroundHandle = it }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap = it }
        }

        "CardHand" -> CardHand(
            widgetOnj.get<Double>("targetWidth").toFloat(),
            widgetOnj.get<Double>("cardSize").toFloat(),
            widgetOnj.get<Double>("opacityIfNotPlayable").toFloat(),
            widgetOnj.get<Double>("centerGap").toFloat(),
            screen
        ).apply {
            hoveredCardScale = widgetOnj.get<Double>("hoveredCardScale").toFloat()
            maxCardSpacing = widgetOnj.get<Double>("maxCardSpacing").toFloat()
            startCardZIndicesAt = widgetOnj.get<Long>("startCardZIndicesAt").toInt()
            hoveredCardZIndex = widgetOnj.get<Long>("hoveredCardZIndex").toInt()
            draggedCardZIndex = widgetOnj.get<Long>("draggedCardZIndex").toInt()
        }

        "Revolver" -> Revolver(
            widgetOnj.get<String>("background"),
            widgetOnj.get<String>("slotTexture"),
            widgetOnj.get<Double>("radiusExtension").toFloat(),
            screen
        ).apply {
            slotScale = widgetOnj.get<Double>("slotScale").toFloat()
            cardScale = widgetOnj.get<Double>("cardScale").toFloat()
            animationDuration = widgetOnj.get<Double>("animationDuration").toFloat()
            radius = widgetOnj.get<Double>("radius").toFloat()
            rotationOff = widgetOnj.get<Double>("rotationOff")
            cardZIndex = widgetOnj.get<Long>("cardZIndex").toInt()
        }

        "EnemyArea" -> EnemyArea(
            widgetOnj.get<String>("enemySelectionDrawable"),
            screen
        )

        "TemplateLabel" -> TemplateStringLabel(
            screen,
            templateString = TemplateString(widgetOnj.get<String>("template")),
            labelStyle = LabelStyle(
                forceLoadFont(widgetOnj.get<String>("font"), screen),
                widgetOnj.get<Color>("color")
            ),
            isDistanceField = widgetOnj.getOr("isDistanceField", true),
            hasHoverDetail = widgetOnj.getOr("hasHoverDetail", false),
            hoverText = widgetOnj.getOr("hoverText", ""),
            partOfHierarchy = widgetOnj.getOr("partOfSelectionHierarchy", false)
        ).apply {
            setFontScale(widgetOnj.get<Double>("fontScale").toFloat())
            widgetOnj.ifHas<String>("backgroundTexture") { backgroundHandle = it }
            widgetOnj.ifHas<String>("align") { setAlignment(alignmentOrError(it)) }
            widgetOnj.ifHas<Boolean>("wrap") { wrap = it }
        }

        "Map" -> DetailMapWidget(
            screen,
            MapManager.currentDetailMap,
            widgetOnj.get<String>("defaultNodeTexture"),
            widgetOnj.get<String>("edgeTexture"),
            widgetOnj.get<String>("playerTexture"),
            widgetOnj.get<Double>("playerWidth").toFloat(),
            widgetOnj.get<Double>("playerHeight").toFloat(),
            widgetOnj.get<Double>("playerHeightOffset").toFloat(),
            widgetOnj.get<Double>("nodeSize").toFloat(),
            widgetOnj.get<Double>("lineWidth").toFloat(),
            (widgetOnj.get<Double>("playerMovementTime") * 1000).toInt(),
            widgetOnj.get<String>("directionIndicator"),
            widgetOnj.get<String>("startButtonName"),
            widgetOnj.get<String>("encounterModifierParentName"),
            widgetOnj.get<String>("encounterModifierDisplayTemplateName"),
            widgetOnj.get<Double>("screenSpeed").toFloat(),
            widgetOnj.get<Double>("scrollMargin").toFloat(),
            widgetOnj.get<Double>("disabledDirectionIndicatorAlpha").toFloat(),
            widgetOnj.get<Double>("mapScale").toFloat(),
        )

        "AdvancedText" -> AdvancedTextWidget(
            widgetOnj.get<OnjObject>("defaults"),
            screen,
            widgetOnj.getOr("isDistanceField", true),
        ).apply {
            setRawText(
                widgetOnj.get<String>("rawText"),
                widgetOnj.get<OnjArray?>("effects")?.value?.map {
                    AdvancedTextParser.AdvancedTextEffect.getFromOnj(
                        screen,
                        it as OnjNamedObject
                    )
                } ?: listOf())
        }

        "DialogWidget" -> DialogWidget(
            (widgetOnj.get<Double>("progressTime") * 1000).toInt(),
            widgetOnj.get<String>("advanceArrowDrawable"),
            widgetOnj.get<Double>("advanceArrowOffset").toFloat(),
            widgetOnj.get<String>("optionsBox"),
            widgetOnj.get<String>("speakingPersonLabel"),
            widgetOnj.get<OnjObject>("defaults"),
            screen
        )

        "Backpack" -> Backpack(
            screen,
            widgetOnj.get<String>("cardsFile"),
            widgetOnj.get<String>("backpackFile"),
            widgetOnj.get<String>("deckNameWidgetName"),
            widgetOnj.get<String>("deckSelectionParentWidgetName"),
            widgetOnj.get<String>("deckCardsWidgetName"),
            widgetOnj.get<String>("backPackCardsWidgetName"),
            widgetOnj.get<String>("backpackEditIndicationWidgetName"),
            widgetOnj.get<String>("sortWidgetName"),
            widgetOnj.get<String>("sortReverseWidgetName"),
        ).apply {
            initFlexBox(this, widgetOnj, screen)
            initAfterChildrenExist()
        }

        "WarningParent" -> CustomWarningParent(screen).apply { initFlexBox(this, widgetOnj, screen) }

        "FromTemplate" -> generateFromTemplate(
            widgetOnj.get<String>("generateFrom"),
            widgetOnj.get<OnjObject>("data").value,
            parent,
            screen
        )!!.apply {
            return this
        }

        "PutCardsUnderDeckWidget" -> PutCardsUnderDeckWidget(
            screen,
            widgetOnj.get<Double>("cardSize").toFloat(),
            widgetOnj.get<Double>("cardSpacing").toFloat(),
        )

        "StatusEffectDisplay" -> HorizontalStatusEffectDisplay(
            screen,
            forceLoadFont(widgetOnj.get<String>("font"), screen),
            widgetOnj.get<Color>("fontColor"),
            widgetOnj.get<Double>("fontScale").toFloat(),
            widgetOnj.getOr<Double>("iconScale", 1.0).toFloat(),
        )

        "TextEffectEmitter" -> TextEffectEmitter(
            TextEffectEmitter.configsFromOnj(widgetOnj.get<OnjArray>("config"), screen),
            screen
        )

        "TutorialInfoActor" -> TutorialInfoActor(
            widgetOnj.get<String>("background"),
            widgetOnj.get<Double>("circleRadiusMultiplier").toFloat(),
            widgetOnj.get<Double>("circleRadiusExtension").toFloat(),
            screen
        ).apply {
            initFlexBox(this, widgetOnj, screen)
        }

        "Slider" -> Slider(
            widgetOnj.get<String>("sliderBackground"),
            widgetOnj.get<Double>("handleRadius").toFloat(),
            widgetOnj.get<Color>("handleColor"),
            widgetOnj.get<Double>("sliderHeight").toFloat(),
            widgetOnj.get<Double>("max").toFloat(),
            widgetOnj.get<Double>("min").toFloat(),
            widgetOnj.getOr<String?>("bindTo", null),
            screen
        )

        "SettingsWidget" -> SettingsWidget(screen)

        "Selector" -> Selector(
            forceLoadFont(widgetOnj.get<String>("font"), screen),
            widgetOnj.get<Double>("fontScale").toFloat(),
            widgetOnj.get<String>("arrowTexture"),
            widgetOnj.get<Double>("arrowWidth").toFloat(),
            widgetOnj.get<Double>("arrowHeight").toFloat(),
            widgetOnj.get<String>("bindTo"),
            screen,
        )

        else -> throw RuntimeException("Unknown widget name ${widgetOnj.name}")

    }.let { actor -> applyWidgetKeysFromOnj(actor, widgetOnj, parent, screen) }

    private fun applyWidgetKeysFromOnj( //this is needed as a function to give styles to the cards,  // TODO ugly
        actor: Actor,
        widgetOnj: OnjNamedObject,
        parent: FlexBox?,
        screen: OnjScreen
    ): Actor {
        // TODO: split this into multiple functions
        applySharedWidgetKeys(actor, widgetOnj, screen)
        val node = parent?.add(actor)

        node ?: return actor
        if (actor !is StyledActor) {
            if (widgetOnj.hasKey<OnjArray>("styles")) {
                throw RuntimeException("actor $actor defines styles but does not implement StyledActor")
            }
            return actor
        }

        val styleManager = StyleManager(actor, node)
        actor.styleManager = styleManager
        actor.initStyles(screen)
        screen.addStyleManager(styleManager)

        widgetOnj.ifHas<OnjArray>("styles") { arr ->
            arr.value.forEach { obj ->
                obj as OnjObject
                val priority = obj.getOr("style_priority", -1L).toInt()
                val condition = obj.getOr<StyleCondition>("style_condition", StyleCondition.Always)
                var duration: Int? = null
                var interpolation: Interpolation? = null
                var delay: Int? = null
                obj.ifHas<OnjObject>("style_animation") {
                    val result = readStyleAnimation(it)
                    duration = result.first
                    interpolation = result.second
                    delay = result.third
                }
                obj.value
                    .filter { !it.key.startsWith("style_") }
                    .forEach { (key, value) ->
                        val data = getDataForStyle(value, key)
                        val instruction: StyleInstruction<Any> = when {
                            duration != null -> AnimatedStyleInstruction(
                                data,
                                priority,
                                condition,
                                data::class,
                                duration!!,
                                interpolation!!,
                                delay!!
                            )
                            data is OnjStyleInstruction -> data.value(priority, condition)
                            else -> StyleInstruction(data, priority, condition, data::class)
                        }
                        styleManager.addInstruction(key, instruction, instruction.dataTypeClass)
                    }
            }
        }

        widgetOnj.ifHas<OnjArray>("actorStates") {
            it.value.forEach { onjStr ->
                onjStr as OnjString
                actor.enterActorState(onjStr.value)
            }
        }

        return actor
    }

    private fun readStyleAnimation(animation: OnjObject): Triple<Int, Interpolation, Int> = Triple(
        (animation.get<Double>("duration") * 1000).toInt(),
        animation.get<Interpolation>("interpolation"),
        (animation.getOr<Double>("delay", 0.0) * 1000).toInt()
    )

    private fun getDataForStyle(onjValue: OnjValue, keyName: String): Any {
        var data = onjValue.value ?: throw RuntimeException("style instruction $keyName cannot be null")
        if (data is Double) data = data.toFloat()
        if (data is Long) data = data.toInt()
        if (onjValue is OnjStyleInstruction) return onjValue
        return data
    }

    private fun applyImageKeys(image: CustomImageActor, widgetOnj: OnjNamedObject) {
        image.scaleX = widgetOnj.get<Double>("scaleX").toFloat()
        image.scaleY = widgetOnj.get<Double>("scaleY").toFloat()
        if (widgetOnj.getOr("reportDimensionsWithScaling", false)) {
            image.reportDimensionsWithScaling = true
            image.ignoreScalingWhenDrawing = true
        }
    }

    private fun applySharedWidgetKeys(actor: Actor, widgetOnj: OnjNamedObject, screen: OnjScreen) = with(actor) {
        debug = widgetOnj.getOr("debug", false)

        widgetOnj.ifHas<OnjNamedObject>("dragAndDrop") {
            applyDragAndDrop(actor, it)
        }

        widgetOnj.ifHas<OnjArray>("behaviours") { arr ->
            arr.value.forEach {
                it as OnjNamedObject
                BehaviourFactory.behaviorOrError(it.name, it, this, screen)
            }
        }

        widgetOnj.ifHas<String>("hoverDetailActor") { name ->
            actor as DisplayDetailsOnHoverActor
            actor.actorTemplate = name
        }

        widgetOnj.ifHas<Long>("zIndex") {
            if (this !is ZIndexActor) throw RuntimeException("can only apply z-index to ZIndexActors")
            fixedZIndex = it.toInt()
        }

        widgetOnj.ifHas<String>("name") {
            namedActors[it] = this
            this.name = it
        }
        widgetOnj.ifHas<Boolean>("visible") { isVisible = it }
        widgetOnj.ifHas<Float>("alpha") { alpha = it }
        widgetOnj.ifHas<String>("touchable") { touchable = Touchable.valueOf(it) }

        widgetOnj.ifHas<Double>("width") { width = it.toFloat() }

        if (widgetOnj.getOr("setFillParent", false)) {
            if (this is Layout) setFillParent(true)
        }

        widgetOnj.ifHas<String>("onClick") { name -> // TODO: support more of these
            this.onButtonClick { event ->
                val noMatch = screen
                    .screenControllers
                    .map { it.handleEventListener(name, event) }
                    .all { !it }
                if (noMatch) {
                    FortyFiveLogger.warn("screen", "No match found for event $name")
                }
                screen.screenControllers.forEach { it.handleEventListener(name, event) }
            }
        }

        onClick { fire(ButtonClickEvent()) }
    }

    private fun applyDragAndDrop(actor: Actor, onj: OnjNamedObject) {
        val group = onj.get<String>("group")
        if (!actorsWithDragAndDrop.containsKey(group)) actorsWithDragAndDrop[group] = mutableListOf()
        actorsWithDragAndDrop[group]!!.add(actor to onj)
    }

    private fun forceLoadFont(name: String, screen: OnjScreen): BitmapFont =
        ResourceManager.forceGet(this, screen, name)

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
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/screen.onjschema").file())
        }
    }

}
