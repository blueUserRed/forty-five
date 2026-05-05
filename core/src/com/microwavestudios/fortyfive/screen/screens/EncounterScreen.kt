package com.microwavestudios.fortyfive.screen.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.AlphaAction
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.animation.xPositionAbstractProperty
import com.microwavestudios.fortyfive.game.BannerAnimation
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.GraphicsConfig
import com.microwavestudios.fortyfive.game.StatusEffect
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardActor
import com.microwavestudios.fortyfive.game.card.CardPresentation
import com.microwavestudios.fortyfive.game.card.DetailDescriptionHandler
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.enemy.EnemyActionPrototype
import com.microwavestudios.fortyfive.game.enemy.NextEnemyAction
import com.microwavestudios.fortyfive.game.enemy.StatusBar
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.game.widgets.Afterlife
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent
import com.microwavestudios.fortyfive.screen.screenController.BiomeBackgroundScreenController
import com.microwavestudios.fortyfive.game.widgets.CardHand
import com.microwavestudios.fortyfive.game.widgets.Revolver
import com.microwavestudios.fortyfive.game.widgets.RevolverSlot
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.rendering.RenderPipeline
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.BakedDropShadow
import com.microwavestudios.fortyfive.screen.actors.CustomGroup
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.actors.AnimatedActor
import com.microwavestudios.fortyfive.screen.actors.CustomAlign
import com.microwavestudios.fortyfive.screen.actors.FlexDirection
import com.microwavestudios.fortyfive.screen.actors.setText
import com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget
import com.microwavestudios.fortyfive.screen.screenBuilder.ScreenCreator
import com.microwavestudios.fortyfive.utils.*
import com.microwavestudios.fortyfive.utils.AdvancedTextParser.*
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.reflect.KClass

class EncounterScreen : ScreenCreator() {

    override val name: String = "gameScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true

    override val background: String? = null

    override val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf(
        name to noTransition(),
        "*" to geometricFadeTransition()
    )

    val gameEvents: EventPipeline = EventPipeline()

    private lateinit var reservesAnimationTarget: Actor
    private lateinit var deckAnimationTarget: Actor

    private val warningParent: WarningParent by lazy {
        WarningParent(this, screen, screen.events)
    }

    private lateinit var enemyParent: CustomGroup

    private val afterlife: Afterlife by lazy {
        Afterlife(screen, gameEvents)
    }

    private val revolver by lazy {
        Revolver(
            "revolver_drum",
            "revolver_slot_texture",
            200f,
            110f,
            0.2f,
            gameEvents,
            screen
        ).apply {
            cardScale = 0.9f
            radius = 140f
            rotationOff = (Math.PI / 2f) + (2f * Math.PI) / 5f
        }
    }

    private val cardHand by lazy {
        CardHand(
            screen,
            300f,
            596f * 0.22f,
            100f
        )
    }

    private val bgZoom: Float = 1.07f
    private val bgScreenController by lazy {
        BiomeBackgroundScreenController(screen, false, bgZoom)
    }

    private var bgOffX: Float = 0f
    private var bgOffY: Float = 0f

    private val shieldIconPromise: Promise<Drawable> by lazy {
        FortyFive.resourceManager.request(this, this.screen.lifetime, "shield_icon_large")
    }

    private val shieldShaderPromise: Promise<BetterShader> by lazy {
        FortyFive.resourceManager.request(this, screen.lifetime, "glow_shader_shield")
    }

    private val enemyBannerPromise: Promise<Drawable> by lazy {
        FortyFive.resourceManager.request(this, screen.lifetime, "enemy_turn_banner")
    }

    private val playerBannerPromise: Promise<Drawable> by lazy {
        FortyFive.resourceManager.request(this, screen.lifetime, "player_turn_banner")
    }


    private lateinit var cardRevolverDragAndDrop: InputManager.DragAndDrop
    private lateinit var cardUnderDeckDragAndDrop: InputManager.DragAndDrop

    init {
        bindEventHandlers()
    }

    override fun getRoot(): Group = newGroup {
        // call getters for each resource so that the lazy loaders execute and request the resources as early as
        // possible. They have to be lazy because `screen` is not available when the constructor runs
        shieldIconPromise; shieldShaderPromise; enemyBannerPromise; playerBannerPromise

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        cardRevolverDragAndDrop =
            screen.inputManager.addDragAndDrop(CardActor.cardGroup, RevolverSlot.revolverSlotGroup)
        cardUnderDeckDragAndDrop =
            screen.inputManager.addDragAndDrop(CardActor.cardGroup, underDeckGroup)

        image {
            backgroundHandle = "game_screen_player"
            x = 20f
            y = 0f
            height = worldHeight
            width = (1305f / 1512f) * worldHeight

            gameEvents.watchFor<UpdateUiEvent> {
                drawOffsetX = bgOffX
                drawOffsetY = bgOffY
            }
        }

        group {
            enemyParent = this@group
            x = 800f
            y = 250f
            width = 800f
            height = 600f
        }

        encounterModifierDisplay()
        parryPopup()
        targetSelectionPopup()

        playerBar()
        actor(afterlife.getActor(this@EncounterScreen)) {
            y = worldHeight * 0.4f
        }

        playerStatusEffectDisplay()

        group {
            backgroundHandle = "transparent_black_texture"
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight
            isVisible = false
            var promise: Promise<Unit>? = null
            gameEvents.watchFor<GameControllerImpl.Events.PlayEnemySpecialAttackAnim> { event ->
                promise = event.finishedPromise
                event.append { action {
                    isVisible = true
                    touchable = Touchable.enabled
                } }
                promise.then {
                    isVisible = false
                    touchable = Touchable.disabled
                }
            }
            onInput(GameInputs.enemyAnimConfirmation) {
                promise?.resolve(Unit)
            }
        }
        enemySpecialAttackAnim()

        putCardsUnderStackPopup()

        winPopup()

        addDefaultOverlays(
            worldWidth, worldHeight,
            screen.events,
            navbarIsLeft = true,
            hasTitleScreen = false,
            hasBackpack = true,
            warnings = warningParent
        )
    }

    private fun CustomGroup.enemySpecialAttackAnim() = group {
        val parentWidth = 400f
        height = worldHeight
        width = parentWidth
        centerY()
        onLayoutAndNow { x = worldWidth - parentWidth }
        y = 200f
        touchable = Touchable.disabled

        val borrower = object : ResourceBorrower {}

        fun commonPanelHandle(i: Int, proto: EnemyActionPrototype) = when (i) {
            0 -> proto.commonPanel1
            1 -> proto.commonPanel2
            2 -> proto.commonPanel3
            else -> unreachable()
        }

        fun commonPanel(i: Int, panel: String) = group {
            width = 170f
            onLayoutAndNow { y = parent.height / 2 + 100 }
            val promise = FortyFive.resourceManager.request<TextureRegionDrawable>(
                borrower,
                screen.lifetime,
                panel
            )
            promise.then { texture ->
                height = width * (texture.minHeight / texture.minWidth)
                manualBackground = texture
            }
            val xAnim = propertyAnimation(
                xPositionAbstractProperty(),
                AnimState("open", parentWidth - (width - 8f) * (i + 1)),
                AnimState("closed", parentWidth + 100),
                initialState = "closed",
                defaultTime = 300,
                defaultInterpolation = Interpolation.pow2,
            )
            xAnim.transition("open", "closed", 0, Interpolation.linear)
            gameEvents.watchFor<GameControllerImpl.Events.PlayEnemySpecialAttackAnim> { event ->
                val promise = FortyFive.resourceManager.request<TextureRegionDrawable>(
                    borrower,
                    screen.lifetime,
                    commonPanelHandle(i, event.enemyAction.prototype)
                )
                promise.then { texture ->
                    height = width * (texture.minHeight / texture.minWidth)
                    manualBackground = texture
                }
                event.finishedPromise.then {
                    xAnim.state("closed")
                    manualBackground = null
                }
                event.append { includeAction(xAnim.stateAction("open")) }
            }
        }

        fun actionPanel() = group {
            height = 220f
            onLayoutAndNow { y = parent.height / 2 + 100 - height + 20f }
            x = parentWidth + 100f
            gameEvents.watchFor<GameControllerImpl.Events.PlayEnemySpecialAttackAnim> { event ->
                val promise = FortyFive.resourceManager.request<TextureRegionDrawable>(
                    borrower,
                    screen.lifetime,
                    event.enemyAction.prototype.specialPanel
                )
                promise.then { texture ->
                    width = height * (texture.minWidth / texture.minHeight)
                    manualBackground = texture
                }
                event.finishedPromise.then {
                    x = parentWidth + 100f
                    manualBackground = null
                }
                val action = MoveToAction()
                action.duration = 0.3f
                action.interpolation = Interpolation.Pow(10)
                event.append {
                    delayUntil { manualBackground != null }
                    delay(100)
                    action {
                        action.x = parentWidth - width - 5f
                        action.y = y
                        addAction(action)
                        event.controller.dispatchAnimTimeline(Timeline.timeline {
                            delay(200)
                            include(FortyFive.currentRenderPipeline!!.getScreenShakeTimeline())
                        })
                    }
                    delayUntil { action.isComplete }
                }
            }
        }

        fun descriptionBox() = box {
            width = 500f
            height = 300f

            onLayoutAndNow { y = parent.height / 2 - 120f - height + 100 }

            backgroundHandle = "common_popup_background_black_large"
            dropShadow = BakedDropShadow(
                "common_popup_background_black_large",
                screen,
                0f, 0f,
                1.3f, 1.3f
            )
            flexDirection = FlexDirection.COLUMN
            verticalAlign = CustomAlign.CENTER
            horizontalAlign = CustomAlign.CENTER

            val title = label("red wing", "Hot Potato", Color.Red, 35) {
                syncDimensions()
            }
            verticalSpacer(10f)
            val body = label("roadgeek", "A scorching Bullet will be put in your hand!", Color.FortyWhite, 22) {
                wrap = true
                setAlignment(Align.center)
                relativeWidth(60f)
                syncHeight()
            }
            val xAnim = propertyAnimation(
                xPositionAbstractProperty(),
                AnimState("open", parentWidth - width + 60f),
                AnimState("closed", parentWidth + 100),
                initialState = "closed",
                defaultTime = 300,
                defaultInterpolation = Interpolation.pow5,
            )
            xAnim.transition("open", "closed", 0, Interpolation.linear)
            gameEvents.watchFor<GameControllerImpl.Events.PlayEnemySpecialAttackAnim> { event ->
                val enemyAction = event.enemyAction
                val prototype = enemyAction.prototype
                title.setText(prototype.title)
                val bodyTemplate = TemplateString(prototype.descriptionTemplate, enemyAction.descriptionParams)
                body.setText(bodyTemplate.string)
                event.finishedPromise.then {
                    xAnim.state("closed")
                }
                event.append {
                    includeAction(xAnim.stateAction("open"))
                }
            }
        }

        commonPanel(0, "enemy_pyro_action_comic_common_panel_1")
        commonPanel(1, "enemy_pyro_action_comic_common_panel_2")
        commonPanel(2, "enemy_pyro_action_comic_common_panel_3")
        descriptionBox()
        actionPanel()
    }

    private fun CustomGroup.playerStatusEffectDisplay() = box {
        badTexture("status effect background", comment = "??????")
        backgroundHandle = "status_effect_background"
        height = 90f
        horizontalAlign = CustomAlign.CENTER
        verticalAlign = CustomAlign.CENTER
        flexDirection = FlexDirection.ROW
        onLayoutAndNow { width = parent.width * 0.5f }
        x = parent.width / 2 - 180
        y = worldHeight - height + 10
        isVisible = false

        val effects: MutableList<StatusEffect> = mutableListOf()
        val updaters: MutableList<() -> Unit> = mutableListOf()

        fun effect(effect: StatusEffect) = box {
            relativeHeight(100f)
            width = 200f
            horizontalAlign = CustomAlign.CENTER
            verticalAlign = CustomAlign.CENTER
            flexDirection = FlexDirection.ROW
            touchable = Touchable.enabled
            val text = DetailDescriptionHandler.descriptions[effect.name.lowercase()]?.second
            detailWidget = DetailWidget.SimpleSmallDetailActor(
                screen,
                effects = DetailDescriptionHandler.allTextEffects
            ) { text ?: "" }
            bindDetailToInputState(GameInputs.States.focused)
            image {
                backgroundHandle = effect.iconHandle
                width = 50f
                height = 50f
            }
            label("red wing", effect.getDisplayText(), fontSize = 45) {
                syncDimensions()
                updaters.add { setText(effect.getDisplayText()) }
            }
        }

        fun effectsChanged() {
            clearChildren()
            updaters.clear()
            effects.forEach { effect(it) }
        }

        gameEvents.watchFor<UpdateUiEvent> {
            updaters.forEach { it() }
        }

        gameEvents.watchFor<GameControllerImpl.Events.AddedPlayerStatusEffect> { event ->
            effects.add(event.statusEffect)
            effectsChanged()
            isVisible = true
        }
        gameEvents.watchFor<GameControllerImpl.Events.RemovedPlayerStatusEffect> { event ->
            effects.removeIf { it === event.statusEffect }
            effectsChanged()
            if (effects.isEmpty()) isVisible = false
        }
    }

    private fun CustomGroup.encounterModifierDisplay() = box {
        width = 500f
        x = worldWidth - 100f
        onLayoutAndNow { height = children.sumOf { it.height.toDouble() }.toFloat() + 50f }
        onLayoutAndNow { y = worldHeight * 0.8f - height }
        backgroundHandle = "encounter_modifier_background"
        flexDirection = FlexDirection.COLUMN
        verticalAlign = CustomAlign.SPACE_AROUND
        touchable = Touchable.enabled
        keyboardFocusable = KeyboardFocusable.LEAF
        isVisible = false

        val xAnim = propertyAnimation(
            xPositionAbstractProperty(),
            AnimState("open", worldWidth - width + 50f),
            AnimState("closed", worldWidth - 100f),
            defaultTime = 100,
            defaultInterpolation = Interpolation.pow2,
            initialState = "closed"
        )

        observeInputState(
            GameInputs.States.focused,
            { xAnim.state("open") },
            { xAnim.state("closed") },
        )

        fun encounterModifier(encounterModifier: EncounterModifier) = box {
            flexDirection = FlexDirection.ROW
            relativeWidth(100f)
            height = 80f
            verticalAlign = CustomAlign.CENTER
            horizontalSpacer(30f)
            box {
                width = 50f
                height = 50f
                backgroundHandle = encounterModifier.iconHandle
            }
            horizontalSpacer(20f)
            box {
                onLayoutAndNow { width = parent.width - 50f - 160f }
                syncHeight()
                flexDirection = FlexDirection.COLUMN

                label("roadgeek", encounterModifier.displayName, fontSize = (24 * 0.9).toInt()) {
                    syncDimensions()
                }
                box {
                    backgroundHandle = "black_texture"
                    height = 1f
                    relativeWidth(100f)
                }
                label("roadgeek", encounterModifier.description, fontSize = (24 * 0.6).toInt()) {
                    wrap = true
                    relativeWidth(100f)
                    syncHeight()
                }
            }
        }

        gameEvents.watchFor<GameControllerImpl.Events.EncounterModifierAdded> { (modifier) ->
            isVisible = true
            encounterModifier(modifier)
        }

    }

    private fun CustomGroup.putCardsUnderStackPopup() = group {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        touchable = Touchable.childrenOnly
        isVisible = false
        cardUnderDeckDragAndDrop.disable()

        val filter = InputManager.FocusFilter(listOf(underDeckGroup), screen)
        val modal = InputManager.Modal(listOf(underDeckGroup, CardActor.cardGroup), screen)
        filter.start()

        gameEvents.watchFor<GameControllerImpl.Events.PutCardsUnderStack> { (_, _, promise) ->
            filter.end()
            modal.push()
            isVisible = true
            promise.then {
                filter.start()
                modal.finished()
                isVisible = false
            }
        }

        val underDeck = group {
            name("underDeck")
            width = worldWidth * 0.5f
            height = worldHeight * 0.48f
            centerX()
            centerY()
            backgroundHandle = "under_deck_background"
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            isDropTarget = true
            joinGroup(underDeckGroup)

            badTexture("underDeckGroup", missingFocusTexture = true)

            var cardCount = 0
            var targetAmount = 0
            var cards = mutableListOf<Card>()
            var currentPromise: Promise<List<Card>>? = null
            var cardAddedCallback: ((card: Card) -> Unit)? = null
            val random = Random(34789267)

            gameEvents.watchFor<GameControllerImpl.Events.PutCardsUnderStack> { (amount, callback, promise) ->
                cardCount = 0
                targetAmount = amount
                cards = mutableListOf()
                currentPromise = promise
                cardAddedCallback = callback
                cardUnderDeckDragAndDrop.enable()
                cardRevolverDragAndDrop.disable()
                promise.then {
                    children.filterIsInstance<CardActor>().forEach { it.rotation = 0f }
                    clearChildren()
                    cardUnderDeckDragAndDrop.disable()
                    cardRevolverDragAndDrop.enable()
                }
            }

            onDrop { actor ->
                if (actor !is CardActor) return@onDrop
                val card = actor.card
                if (!card.inZone(GameControllerImpl.Zone.HAND)) return@onDrop
                cardAddedCallback?.invoke(card)
                cards.add(card)
                actor(actor) {
                    width = 150f
                    height = 150f
                    touchable = Touchable.disabled
                    isDraggable = false
                    x = 40f + cardCount * 50f
                    val heightOffset = random.nextDouble(-10.0, 10.0).toFloat()
                    y = parent.height / 2 - height / 2 + heightOffset
                    rotation = random.nextDouble(-15.0, 15.0).toFloat()
                }
                cardCount++
                if (cardCount >= targetAmount) currentPromise?.resolve(cards)
            }
        }

        label("red wing", "Select cards to put under the deck", Color.White, 32) {
            syncDimensions()
            onLayoutAndNow { y = underDeck.y + underDeck.height - height - 40f }
            centerX()
        }
    }

    private fun createEnemy(x: Float, y: Float, enemy: Enemy): Float = with(enemyParent) {

        val enemyHeight = 400f
        val enemyWidth = enemyHeight * 0.6f

        var enemySelected = false

        box {
            enemy.actor = this
            flexDirection = FlexDirection.COLUMN
            this.x = x
            this.y = y
            width = enemyWidth
            height = enemyHeight

            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.enabled

            gameEvents.watchFor<UpdateUiEvent> {
                drawOffsetX = bgOffX * 1.1f
                drawOffsetY = bgOffY * 1.1f
            }

            onInput(GameInputs.interact) {
                if (enemySelected || enemy.isDefeated) return@onInput
                gameEvents.fire(GameControllerImpl.Events.EnemySelected(enemy))
            }

            fun chargeTimeline(): Timeline = Timeline.timeline {
                val origX = x
                val forwardAction = MoveToAction()
                forwardAction.x = origX - 50f
                forwardAction.y = y
                forwardAction.duration = 0.2f
                forwardAction.interpolation = Interpolation.pow4
                val backAction = MoveToAction()
                backAction.x = origX
                backAction.y = y
                backAction.duration = 0.4f
                backAction.interpolation = Interpolation.smooth
                action { addAction(forwardAction) }
                delayUntil { forwardAction.isComplete }
                action {
                    removeAction(forwardAction)
                    addAction(backAction)
                }
                delayUntil { backAction.isComplete }
                action { removeAction(backAction) }
            }

            enemy.enemyEvents.watchFor<Enemy.PlayChargeAnimationEvent> { event ->
                event.timeline.resolve(chargeTimeline())
            }

            box {

                fun showAction(
                    text: String?,
                    iconHandle: String,
                    damageChanges: List<Pair<String, Int>>
                ) {
                    image {
                        backgroundHandle = iconHandle
                        width = 60f
                        height = 60f
                    }

                    val effects = listOf(
                        AdvancedTextEffect.AdvancedColorTextEffect("?R", Color.Red),
                        AdvancedTextEffect.AdvancedColorTextEffect("?G", Color.LIGHT_GRAY),
                    )

                    val texts = mutableListOf<String>()
                    if (text != null) texts.add(text)
                    damageChanges.forEach { (icon, damage) ->
                        val text = when {
                            damage > 0 -> "?R+$damage§§$icon§§?R"
                            damage < 0 -> "?G$damage§§$icon§§?G"
                            else -> ""
                        }
                        texts.add(text)
                    }

                    texts.forEach { text -> group {
                        height = 40f
                        backgroundHandle = "wood_box"
                        touchable = Touchable.disabled
                        val text = advancedText("roadgeek", Color.FortyWhite, 23) {
                            relativeHeight(58f)
                            setRawText(text, effects)
                            centerX()
                            centerY()
                            wrap = false
                            syncWidth()
                        }
                        onLayoutAndNow { width = (text.width + 25).coerceAtLeast(40f) }
                    } }

                }

                relativeWidth(100f)
                animateUpAndDownSinus(
                    method = AnimatedActor.AnimationMethod.DRAW_OFFSET,
                    frequency = 0.3f,
                    amplitude = 6f
                )
                flexDirection = FlexDirection.ROW
                horizontalAlign = CustomAlign.CENTER
                verticalAlign = CustomAlign.CENTER
                height = enemyHeight * 0.15f
                touchable = Touchable.enabled
                bindDetailToInputState(GameInputs.States.focused)
                enemy.enemyEvents.watchFor<Enemy.EnemyActionChangedEvent> { event ->
                    this.clear()
                    val hoverText = when (val nextAction = event.nextAction) {
                        is NextEnemyAction.ShownEnemyAction -> {
                            val template = nextAction.action.prototype.descriptionTemplate
                            val templateString = TemplateString(template, nextAction.action.descriptionParams)
                            templateString.string
                        }
                        is NextEnemyAction.HiddenEnemyAction -> "You cant see the action of the enemy yet!"
                        is NextEnemyAction.None -> null
                    }
                    detailWidget = if (hoverText != null) {
                        DetailWidget.SimpleSmallDetailActor(screen) { hoverText }
                    } else {
                        null
                    }
                    when (val nextAction = event.nextAction) {
                        is NextEnemyAction.ShownEnemyAction -> showAction(
                            nextAction.action.indicatorText,
                            nextAction.action.prototype.iconHandle,
                            nextAction.action.damageChanges
                        )
                        is NextEnemyAction.None -> {}
                        is NextEnemyAction.HiddenEnemyAction -> showAction("?",  "enemy_action_unknown", listOf())
                    }
                }
            }

            group {
                relativeWidth(100f)
                height = enemyHeight * 0.65f
                touchable = Touchable.disabled

                image {
                    // TODO: add anims back
                    var heightPercent = 1f
                    backgroundHandle = enemy.drawableHandle
                    relativeHeight(100f)
                    centerX()
                    onLayout {
                        height = parent.height * heightPercent
                        val drawable = drawable ?: return@onLayout
                        width = height * (drawable.minWidth / drawable.minHeight)
                    }

                    enemy.enemyEvents.watchFor<Enemy.EnemyDefeated> {
                        backgroundHandle = "enemy_gravestone"
                        heightPercent = 0.6f
                        invalidate()
                    }
                }

                image {
                    backgroundHandle = "card_symbol_marked"
                    centerX()
                    centerY()
                    relativeWidth(60f)
                    touchable = Touchable.disabled
                    onLayoutAndNow { height = width }
                    animateRotationSinus(
                        amplitude = Math.PI.toFloat() * 1.8f,
                    )
                    animateUpAndDownSinus(
                        method = AnimatedActor.AnimationMethod.DRAW_OFFSET,
                        amplitude = 14f,
                        frequency = 0.4f
                    )
                    gameEvents.watchFor<GameControllerImpl.Events.EnemySelected> { (e) ->
                        enemySelected = e === enemy
                        isVisible = enemySelected
                    }
                }
            }

            group {
                relativeWidth(100f)
                height = enemyHeight * 0.2f
                val statusBar = StatusBar(screen, enemy)
                actor(statusBar) {
                    relativeWidth(110f)
                    relativeHeight(100f)
                }
            }

        }
        return enemyWidth
    }

    private fun CustomGroup.parryPopup() = box {
        backgroundHandle = "common_popup_background_black_large"
        dropShadow = BakedDropShadow(
            "common_popup_background_black_large",
            screen,
            0f, 0f,
            1.3f, 1.3f
        )
        width = 680f
        height = width * (1057f / 1845f)
        centerX()
        y = 340f
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.CENTER
        verticalAlign = CustomAlign.CENTER
        color.a = 0f
        touchable = Touchable.disabled
        gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { event ->
            val action = AlphaAction()
            action.duration = 0.2f
            action.alpha = if (event.inParryMenu) 1f else 0f
            addAction(action)
        }
        label("red wing", "Parry?", Color.BrightYellow, (32 * 1.4).toInt()) {
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
        verticalSpacer(20f)
        label("roadgeek", "", Color.GRAY, 24) {
            gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { (_, damage, blockable) ->
                setText("Parrying will let ${(damage - blockable).coerceAtLeast(0)} damage through")
            }
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
        verticalSpacer(20f)
        label("roadgeek", "", Color.GRAY, 24) {
            gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { (_, damage, _) ->
                setText("Passing will let $damage damage through")
            }
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
        verticalSpacer(60f)
    }

    private fun CustomGroup.targetSelectionPopup() = box {
        backgroundHandle = "common_popup_background_black_large"
        dropShadow = BakedDropShadow(
            "common_popup_background_black_large",
            screen,
            0f, 0f,
            1.3f, 1.3f
        )
        width = 680f
        height = width * (1057f / 1845f)
        centerX()
        y = 340f
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.SPACE_AROUND
        verticalAlign = CustomAlign.CENTER
        color.a = 0f
        touchable = Touchable.disabled

        fun animateInOut(isIn: Boolean) {
            val action = AlphaAction()
            action.duration = 0.2f
            action.alpha = if (isIn) 1f else 0f
            addAction(action)
        }

        gameEvents.watchFor<GameControllerImpl.Events.SelectionChangedEvent> { event ->
            if (event.text == null) {
                animateInOut(false)
            } else {
                animateInOut(true)
            }
        }
        label("red wing", "", Color.BrightYellow, 32) {
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
            gameEvents.watchFor<GameControllerImpl.Events.SelectionChangedEvent> { event ->
                setText(event.text)
            }
        }
    }

    private fun CustomGroup.playerBar() = group {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldWidth * (505f / 1920f)
        backgroundHandle = "player_bar"

        val buttonModal = InputManager.Modal(listOf("shoot-button", "parry-button"), screen)

        gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { event ->
            if (event.inParryMenu) buttonModal.push() else buttonModal.finished()
        }

        shootButton()
        holsterButton()

        actor(revolver) {
            touchable = Touchable.enabled
            name("revolver")
            centerX()
            y = -30f
            syncDimensions()
        }

        label("red wing", "10", Color.Red, 90) {
            centerX()
            y = 210f
            width = 50f
            setAlignment(Align.center)
            isVisible = false
            gameEvents.watchFor<GameControllerImpl.Events.SteelNervesCountdown> { (newNumber) ->
                setText(newNumber.toString())
                isVisible = true
            }
        }

        actor(cardHand) {
            name("cardHand")
            centerX()
            y = 0f
            width = worldWidth
            gameEvents.link(events)
        }

        group {
            backgroundHandle = "wood_box"
            x = 70f
            y = 180f
            width = 120f
            height = 120f
            reservesAnimationTarget = this
            animateRotationSinus(
                frequency = 0.15f
            )

            image {
                backgroundHandle = "reserves_texture"
                width = 60f
                height = 60f
                x = 30f
                y = 90f
            }

            label("red wing", "0/0", Color.White, (32 * 1.1).toInt()) {
                centerX()
                centerY()
                gameEvents.watchFor<GameControllerImpl.Events.ReservesChanged> { (_, new, base) ->
                    setText("${new}/$base")
                }
                syncDimensions()
            }
        }

        group {
            backgroundHandle = "wood_box"
            x = worldWidth - 70f - 120f
            y = 180f
            width = 120f
            height = 120f
            deckAnimationTarget = this

            animateRotationSinus(
                frequency = 0.15f
            )

            image {
                backgroundHandle = "deck_icon"
                width = 60f
                height = 60f
                x = 30f
                y = 90f
            }

            label("red wing", "", Color.White, (32 * 1.1).toInt()) {
                gameEvents.watchFor<UpdateUiEvent> { (controller) ->
                    setText(controller.cardStack.size().toString())
                }
                centerX()
                centerY()
                syncDimensions()
            }
        }

    }

    private fun CustomGroup.shootButton() {
        var parryPromise: Promise<Boolean>? = null
        gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { event ->
            parryPromise = event.resolutionPromise
        }
        group(backgroundHints = arrayOf("shoot_button_texture", "shoot_button_hover_texture")) {
            name("shoot_button")
            joinGroup("shoot-button")
            val filter = InputManager.FocusFilter(listOf("shoot-button"), screen)
            touchable = Touchable.enabled
            focusShortcut(GameInputs.focusShortcutShootButton)
            x = 370f
            y = 50f
            var closed = false
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 370f),
                AnimState("hover", 360f),
                AnimState("closed", 600f),
                initialState = "open",
                defaultTime = 100,
                defaultInterpolation = Interpolation.pow2
            )
            xAnim.transition("*", "closed", 400, Interpolation.linear)
            xAnim.transition("closed", "*", 400, Interpolation.linear)
            width = 250f
            height = 250f * (543f / 655f)
            keyboardFocusable = KeyboardFocusable.LEAF
            backgroundHandle = "shoot_button_texture"
            observeInputState(
                GameInputs.States.focused,
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "shoot_button_hover_texture"
                    xAnim.state("hover")
                },
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "shoot_button_texture"
                    xAnim.state("open")
                }
            )
            onInput(GameInputs.interact){
                gameEvents.fire(GameControllerImpl.Events.ShootButtonPressed)
            }
            gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { (inParryMenu) ->
                if (inParryMenu) {
                    xAnim.state("closed")
                    filter.start()
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    filter.end()
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }

        group(backgroundHints = arrayOf("pass_button_texture", "pass_button_hover_texture")) {
            name("pass_button")
            joinGroup("pass-button")
            val filter = InputManager.FocusFilter(listOf("pass-button"), screen)
            filter.start()
            var closed = true
            touchable = Touchable.disabled
            focusShortcut(GameInputs.focusShortcutShootButton)
            x = 600f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 370f),
                AnimState("hover", 360f),
                AnimState("closed", 600f),
                initialState = "closed",
                defaultTime = 100,
                defaultInterpolation = Interpolation.pow2
            )
            xAnim.transition("*", "closed", 400, Interpolation.linear)
            xAnim.transition("closed", "*", 400, Interpolation.linear)
            width = 250f
            height = 250f * (543f / 655f)
            keyboardFocusable = KeyboardFocusable.LEAF
            backgroundHandle = "pass_button_texture"
            xAnim.state(if (closed) "closed" else "open")
            observeInputState(
                GameInputs.States.focused,
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "pass_button_hover_texture"
                    xAnim.state("hover")
                },
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "pass_button_texture"
                    xAnim.state(if (closed) "closed" else "open")
                }
            )
            onInput(GameInputs.interact){
                parryPromise?.let {
                    if (it.isNotResolved) it.resolve(false)
                }
            }
            gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { (inParryMenu) ->
                if (!inParryMenu) {
                    xAnim.state("closed")
                    filter.start()
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    filter.end()
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }
    }

    private fun CustomGroup.holsterButton() {
        var parryPromise: Promise<Boolean>? = null
        gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { event ->
            parryPromise = event.resolutionPromise
        }
        group(backgroundHints = arrayOf("end_turn_button_texture", "end_turn_button_hover_texture")) {
            name("holster_button")
            joinGroup("holster-button")
            val filter = InputManager.FocusFilter(listOf("holster-button"), screen)
            var closed = false
            touchable = Touchable.enabled
            keyboardFocusable = KeyboardFocusable.LEAF
            focusShortcut(GameInputs.focusShortcutHolsterButton)
            x = 990f
            y = 60f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 990f),
                AnimState("hover", 1000f),
                AnimState("closed", 600f),
                initialState = "open",
                defaultTime = 100,
                defaultInterpolation = Interpolation.pow2
            )
            xAnim.transition("*", "closed", 400, Interpolation.linear)
            xAnim.transition("closed", "*", 400, Interpolation.linear)

            width = 250f
            height = 250f * (543f / 655f)

            onInput(GameInputs.interact) {
                gameEvents.fire(GameControllerImpl.Events.HolsterButtonPressed)
            }
            backgroundHandle = "end_turn_button_texture"
            observeInputState(
                GameInputs.States.focused,
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "end_turn_button_hover_texture"
                    xAnim.state("hover")
                },
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "end_turn_button_texture"
                    xAnim.state("open")
                }
            )
            gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { (inParryMenu) ->
                if (inParryMenu) {
                    xAnim.state("closed")
                    closed = true
                    filter.start()
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    closed = false
                    filter.end()
                    touchable = Touchable.enabled
                }
            }
        }

        group(backgroundHints = arrayOf("parry_button_texture", "parry_button_hover_texture")) {
            name("parry_button")
            joinGroup("parry-button")
            val filter = InputManager.FocusFilter(listOf("parry-button"), screen)
            filter.start()
            var closed = true
            keyboardFocusable = KeyboardFocusable.LEAF
            touchable = Touchable.disabled
            focusShortcut(GameInputs.focusShortcutHolsterButton)
            x = 990f
            y = 60f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 980f),
                AnimState("hover", 990f),
                AnimState("closed", 600f),
                initialState = "closed",
                defaultTime = 100,
                defaultInterpolation = Interpolation.pow2
            )
            xAnim.transition("*", "closed", 400, Interpolation.linear)
            xAnim.transition("closed", "*", 400, Interpolation.linear)
            width = 250f
            height = 250f * (543f / 655f)

            onInput(GameInputs.interact) {
                parryPromise?.let {
                    if (it.isNotResolved) it.resolve(true)
                }
            }
            backgroundHandle = "parry_button_texture"
            xAnim.state(if (closed) "closed" else "open")
            observeInputState(
                GameInputs.States.focused,
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "parry_button_hover_texture"
                    xAnim.state("hover")
                },
                {
                    if (closed) return@observeInputState
                    backgroundHandle = "parry_button_texture"
                    xAnim.state("open")
                }
            )
            gameEvents.watchFor<GameControllerImpl.Events.ParryStateChange> { (inParryMenu) ->
                if (!inParryMenu) {
                    xAnim.state("closed")
                    filter.start()
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    filter.end()
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }
    }

    private fun CustomGroup.winPopup() {

        val winPopupGroup = "encounter-screen-win-popup"
        val modal = InputManager.Modal(listOf(winPopupGroup), screen)
        val filter = InputManager.FocusFilter(listOf(winPopupGroup), screen)
        var gotCash = true

        filter.start()

        var continuePromise: Promise<Unit>? = null
        box {
            backgroundHandle = "win_popup_background"
            relativeHeight(110f)
            onLayoutAndNow { width = height * (850f / 973f) }
            centerX()
            centerY()
            flexDirection = FlexDirection.COLUMN
            verticalAlign = CustomAlign.SPACE_BETWEEN
            horizontalAlign = CustomAlign.CENTER
            isVisible = false

            gameEvents.watchFor<GameControllerImpl.Events.ShowPlayerWonPopup> { event ->
                isVisible = true
                continuePromise = event.popupPromise
                filter.end()
                modal.push()
            }

            box {
                horizontalAlign = CustomAlign.CENTER
                relativeWidth(100f)
                // TODO: randomize text
                label("red wing", "You survived", Color.FortyWhite, (128 * 0.5).toInt()) {
                    setAlignment(Align.center)
                    marginTop = 150f
                    syncDimensions()
                }

                box {
                    relativeWidth(62f)
                    flexDirection = FlexDirection.ROW
                    verticalAlign = CustomAlign.CENTER
                    height = 70f
                    backgroundHandle = "win_popup_item_cash"

                    image {
                        name("overkill_cash_symbol")
                        width = 40f
                        height = 30f
                        backgroundHandle = "cash_symbol"
                        marginLeft = 10f
                        marginRight = 10f
                    }

                    gameEvents.watchFor<GameControllerImpl.Events.ShowPlayerWonPopup> { (_, money, _) ->
                        isVisible = money > 0
                        gotCash = money > 0
                    }

                    label("red wing", "", Color.FortyWhite, 32) {
                        syncDimensions()
                        gameEvents.watchFor<GameControllerImpl.Events.ShowPlayerWonPopup> { (_, money, _) ->
                            setText("You get \$$money overkill cash")
                        }
                    }
                }

                box {
                    relativeWidth(62f)
                    flexDirection = FlexDirection.ROW
                    verticalAlign = CustomAlign.CENTER
                    height = 70f
                    backgroundHandle = "win_popup_item_card"
                    marginTop = 10f

                    image {
                        width = 60f
                        height = 60f
                        backgroundHandle = "map_node_get_card"
                        marginLeft = 10f
                        marginRight = 10f
                    }

                    gameEvents.watchFor<GameControllerImpl.Events.ShowPlayerWonPopup> { (gotCard, _, _) ->
                        isVisible = gotCard
                    }

                    label("red wing", "You get a card", Color.FortyWhite, 32)
                }
            }

            box(backgroundHints = arrayOf("common_button_default", "common_button_hover")) {
                width = 200f
                height = 50f
                touchable = Touchable.enabled
                keyboardFocusable = KeyboardFocusable.LEAF
                joinGroup(winPopupGroup)
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER
                backgroundHandle = "common_button_default"
                observeInputState(
                    GameInputs.States.focused,
                    { backgroundHandle = "common_button_hover" },
                    { backgroundHandle = "common_button_default" }
                )
                label("red wing", "Claim & Continue", Color.FortyWhite, (32 * 0.7).toInt()) {
                    setAlignment(Align.center)
                }
                marginBottom = 120f
                onInput(GameInputs.interact) {
                    filter.start()
                    modal.finished()
                    continuePromise?.resolve(Unit)
                    FortyFive.soundPlayer.situation("money_earned", screen)
                    val navBarSymbol = screen.namedActorOrError("cash_symbol")
                    val winPopupSymbol = screen.namedActorOrError("overkill_cash_symbol")
                    val renderPipeline = FortyFive.currentRenderPipeline!!
                    val moneyAnim = GraphicsConfig.cashOrbAnimation(
                        winPopupSymbol.localToStageCoordinates(Vector2(
                            winPopupSymbol.width / 2,
                            winPopupSymbol.height / 2
                        )),
                        {
                            navBarSymbol.localToStageCoordinates(Vector2(
                                navBarSymbol.width / 2,
                                navBarSymbol.height / 2
                            ))
                        },
                        renderPipeline
                    )
                    if (gotCash) renderPipeline.addOrbAnimation(moneyAnim)
                }
            }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        bgScreenController,
        GameControllerImpl(screen, gameEvents, warningParent, afterlife, CardPresentation.defaultProvider)
    )

    override fun debugMenuPages(): List<String> = listOf("Encounter")

    private fun playerDamageTimeline(): Timeline = Timeline.timeline { later {
        val duration = 150
        val interpolation = Interpolation.fade
        val startTime = TimeUtils.millis()
        delayUntil {
            val now = TimeUtils.millis()
            val elapsed = now - startTime
            val percent = (elapsed.toFloat() / duration.toFloat()).between(0f, 1f)
            val adjPercent = interpolation.apply(percent)
            val movementRangeFraction = (bgZoom - 1f) / 5
            bgOffY = adjPercent * movementRangeFraction * worldHeight
            bgOffX = adjPercent * movementRangeFraction * worldWidth
            bgScreenController.offY = bgOffY
            bgScreenController.offX = bgOffX
            percent >= 0.999999f
        }
        val middleTime = startTime + duration
        delayUntil {
            val now = TimeUtils.millis()
            val elapsed = now - middleTime
            val percent = (elapsed.toFloat() / duration.toFloat()).between(0f, 1f)
            val adjPercent = 1f - interpolation.apply(percent)
            val movementRangeFraction = (bgZoom - 1f) / 5
            bgOffY = adjPercent * movementRangeFraction * worldHeight
            bgOffX = adjPercent * movementRangeFraction * worldWidth
            bgScreenController.offY = bgOffY
            bgScreenController.offX = bgOffX
            percent >= 0.999999f
        }
        action {
            bgOffY = 0f
            bgScreenController.offY = 0f
        }
    } }

    private fun reserveAnimationTimeline(
        source: Actor,
        target: Actor,
        amount: Int,
    ): Timeline = Timeline.timeline {
        val renderPipeline = FortyFive.currentRenderPipeline ?: return@timeline
        repeat(amount.absoluteValue) {
            later {
                FortyFive.soundPlayer.situation("orb_anim_playing", screen)
                val sourcePosition =
                    source.localToStageCoordinates(Vector2(0f, 0f)) + Vector2(source.width / 2, source.height / 2)
                val targetCallback = {
                    target.localToStageCoordinates(Vector2(0f, 0f)) +
                            Vector2(target.width / 2, target.height / 2)
                }
                val startVelocity = Vector2(
                    (-1000f..2500f).random(),
                    (-4000f..8100f).random()
                )
                val orbAnimation = RenderPipeline.OrbAnimation(
                    "reserves_orb",
                    10f, 10f,
                    renderPipeline,
                    sourcePosition,
                    startVelocity,
                    40f,
                    2500f,
                    20,
                    500,
                    1.2f,
                    targetCallback
                )
                renderPipeline.addOrbAnimation(orbAnimation)
                delay(50)
            }
        }
    }

    private fun cardAnimationTimeline(
        source: Actor,
        target: Actor,
        reverse: Boolean,
    ): Timeline = Timeline.timeline {
        val renderPipeline = FortyFive.currentRenderPipeline ?: return@timeline
        later {
            FortyFive.soundPlayer.situation("orb_anim_playing", screen)
            val sourceCallback = {
                source.localToStageCoordinates(Vector2(0f, 0f)) + Vector2(source.width / 2, source.height / 2)
            }
            val targetCallback = {
                target.localToStageCoordinates(Vector2(0f, 0f)) +
                        Vector2(target.width / 2, target.height / 2)
            }
            val startVelocity = Vector2(0f, 0f)
            val orbAnimation = RenderPipeline.OrbAnimation(
                "card_orb",
                10f, 10f,
                renderPipeline,
                if (reverse) targetCallback() else sourceCallback(),
                startVelocity,
                1000f,
                4000f,
                20,
                5_000,
                2.0f,
                if (reverse) sourceCallback else targetCallback
            )
            renderPipeline.addOrbAnimation(orbAnimation)
            delayUntil { orbAnimation.isFinished() }
        }
    }

    private fun reservesPaidAnim(amount: Int, animTarget: Actor): Timeline =
        reserveAnimationTimeline(reservesAnimationTarget, animTarget, amount = amount)

    private fun reservesGainedAnim(amount: Int, animSource: Actor): Timeline =
        reserveAnimationTimeline(animSource, reservesAnimationTarget, amount = amount)


    private fun bindEventHandlers() {
        gameEvents.watchFor<GameControllerImpl.Events.ReservesChanged>(::reservesChangedAnim)
        gameEvents.watchFor<GameControllerImpl.Events.PlayCardOrbAnimation> { event ->
            event.orbAnimationTimeline = cardAnimationTimeline(
                deckAnimationTarget,
                event.targetActor(),
                event.reverse
            )
        }
        gameEvents.watchFor<GameControllerImpl.Events.SetupEnemies>(::setupEnemies)
        gameEvents.watchFor<GameControllerImpl.Events.PlayerLivesChanged> { event ->
            if (event.newValue >= event.oldValue) return@watchFor
            screen.screenControllers.filterIsInstance<GameControllerImpl>().first().dispatchAnimTimeline(playerDamageTimeline())
        }
        gameEvents.watchFor<GameControllerImpl.Events.PlayPlayerDamagedEffects> { event ->
            event.animationTimeline = Timeline.timeline { parallelActions(
                FortyFive.currentRenderPipeline!!.getScreenShakeTimeline().asAction(),
                GraphicsConfig.damageOverlay(screen, event.controller)
            ) }
        }
        gameEvents.watchFor<GameControllerImpl.Events.PlayShieldAnimation> { event ->
            event.shieldTimeline = getShieldAnim(event.controller)
        }
        gameEvents.watchFor<GameControllerImpl.Events.PlayBannerAnimation> { event ->
            event.timeline = getBannerAnim(event)
        }
    }

    private fun getBannerAnim(event: GameControllerImpl.Events.PlayBannerAnimation): Timeline =
        (if (event.isPlayer) playerBannerPromise else enemyBannerPromise).getOrNull()?.let { banner ->
            BannerAnimation(
                banner,
                this.screen,
                1_500,
                500,
                1.4f,
                1.1f
            ).asTimeline(event.controller)
        } ?: Timeline()

    private fun getShieldAnim(controller: GameController): Timeline {
        val shieldIcon = shieldIconPromise.getOrNull() ?: return Timeline()
        val shieldShader = shieldShaderPromise.getOrNull() ?: return Timeline()
        return Timeline.timeline {
            val bannerAnim = BannerAnimation(
                shieldIcon,
                screen,
                1_000,
                150,
                0.3f,
                0.5f,
                interpolation = Interpolation.pow2In,
                customShader = shieldShader
            ).asTimeline(controller).asAction()
            val postProcessorAction = Timeline.timeline {
                delay(100)
                include(FortyFive.currentRenderPipeline!!.getScreenShakePopoutTimeline())
                delay(50)
                action { FortyFive.soundPlayer.situation("shield_anim", screen) }
            }.asAction()
            parallelActions(bannerAnim, postProcessorAction)
        }
    }

    private fun setupEnemies(event: GameControllerImpl.Events.SetupEnemies) {
        var x = 10f
        var y = 160f
        event.enemies.forEach { enemy ->
            val neededWidth = createEnemy(x, y, enemy)
            x += neededWidth
            y -= 30f
        }
    }

    private fun reservesChangedAnim(event: GameControllerImpl.Events.ReservesChanged) {
        val (old, new, _, source, controller) = event
        source ?: return
        val amount = new - old
        val anim = when {
            amount > 0 -> reservesGainedAnim(amount, source())
            amount < 0 -> reservesPaidAnim(amount, source())
            else -> null
        }
        anim?.let { controller.dispatchAnimTimeline(it) }
    }

    companion object : ScreenManager.ScreenCreatorCompanion {
        override val creatorClass: KClass<out ScreenCreator> = EncounterScreen::class
        const val underDeckGroup: String = "encounter-screen-under-deck"
    }

    data class UpdateUiEvent(val controller: GameController)
}
