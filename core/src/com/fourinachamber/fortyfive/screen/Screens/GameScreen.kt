package com.fourinachamber.fortyfive.screen.screens

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.AlphaAction
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.animation.AnimState
import com.fourinachamber.fortyfive.animation.xPositionAbstractProperty
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.game.enemy.NextEnemyAction
import com.fourinachamber.fortyfive.game.enemy.StatusBar
import com.fourinachamber.fortyfive.keyInput.KeyActionFactory
import com.fourinachamber.fortyfive.keyInput.KeyInputCondition
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.KeyInputMapEntry
import com.fourinachamber.fortyfive.keyInput.KeyInputMapKeyEntry
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.keyInput.selection.SelectionTransition
import com.fourinachamber.fortyfive.keyInput.selection.TransitionType
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.components.NavbarCreator
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.components.WarningParent
import com.fourinachamber.fortyfive.screen.gameWidgets.BiomeBackgroundScreenController
import com.fourinachamber.fortyfive.screen.gameWidgets.NewCardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.PutCardsUnderDeckWidget
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.gameWidgets.RevolverSlot
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.AnimatedActor
import com.fourinachamber.fortyfive.screen.general.customActor.CustomAlign
import com.fourinachamber.fortyfive.screen.general.customActor.FlexDirection
import com.fourinachamber.fortyfive.screen.general.onSelect
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.AdvancedTextParser.*
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.plus

class GameScreen : ScreenCreator() {

    override val name: String = "gameScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = true

    override val background: String? = null

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 0
    )

    private val cardDragAndDrop = DragAndDrop()

    val gameEvents: EventPipeline = EventPipeline()

    private lateinit var reservesAnimationTarget: Actor
    private lateinit var deckAnimationTarget: Actor

    private var warningParent: WarningParent? = null

    private lateinit var enemyParent: CustomGroup

    private val revolver by lazy {
        Revolver(
            "revolver_drum",
            "revolver_slot_texture",
            200f,
            screen
        ).apply {
            slotSize = 110f
            cardScale = 0.9f
            animationDuration = 0.2f
            radius = 140f
            rotationOff = (Math.PI / 2f) + (2f * Math.PI) / 5f
            cardZIndex = 100
            initDragAndDrop(cardDragAndDrop)
        }
    }

    private val cardHand by lazy {
        NewCardHand(
            screen,
            300f,
            596f * 0.22f,
            100f
        )
    }

    init {
        bindEventHandlers()
    }

    override fun update() {
        warningParent?.update()
    }

    override fun getRoot(): Group = newGroup {

        x = 0f
        y = 0f
        width = worldWidth
        height = worldHeight

        image {
            backgroundHandle = "game_screen_player"
            x = 20f
            y = 0f
            height = worldHeight
            width = (1305f / 1512f) * worldHeight
        }

        group {
            enemyParent = this@group
            x = 800f
            y = 250f
            width = 800f
            height = 600f
        }

        parryPopup()
        targetSelectionPopup()

        playerBar()

        putCardsUnderStackPopup()

        winPopup()

        val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)
        val navBar = getSharedNavBar(
            worldWidth,
            worldHeight,
            listOf(settingsObject),
            screen,
            isLeft = true
        )
        actor(navBar) {
            onLayoutAndNow { y = worldHeight - height }
            x = 0f
        }
        actor(settings)

        val warningParent = WarningParent(this@GameScreen, screen)
        this@GameScreen.warningParent = warningParent
        actor(warningParent.getActor())
    }

    private fun CustomGroup.putCardsUnderStackPopup() {
        val putCardsUnderDeckPopup = PutCardsUnderDeckWidget(screen, 596f * 0.22f, 10f, gameEvents)
        group {
            x = 0f
            y = 0f
            relativeWidth(100f)
            relativeHeight(100f)
            touchable = Touchable.childrenOnly
            isVisible = false
            gameEvents.watchFor<NewGameController.Events.PutCardsUnderStack> { event ->
                isVisible = true
                event.selectedCards.then { isVisible = false }
            }
            actor(putCardsUnderDeckPopup) {
                backgroundHandle = "under_deck_background"
                width = worldWidth * 0.5f
                height = worldHeight * 0.48f
                touchable = Touchable.disabled
                centerX()
                centerY()
                gameEvents.watchFor<NewGameController.Events.PutCardsUnderStack> { event ->
                    touchable = Touchable.enabled
                    event.selectedCards.then { touchable = Touchable.disabled }
                }
            }
            label(
                "red_wing",
                "Put {game.remainingCardsToPutUnderStack} Cards back under your stack",
                color = Color.FortyWhite,
                isTemplate = true
            ) {
                centerX()
                y = worldHeight * 0.63f
            }
            image {
                backgroundHandle = "draw_bullet"
                width = 300f
                height = 300f
                centerY()
                onLayoutAndNow { x = parent.width / 2 - width / 2 - 500f }
                touchable = Touchable.disabled
                rotation = -10f
            }
        }
    }

    private fun createEnemy(x: Float, y: Float, enemy: Enemy): Float = with(enemyParent) {

        val enemyHeight = 400f
        val enemyWidth = enemyHeight * 0.6f

        var enemySelected = false

        box {
            flexDirection = FlexDirection.COLUMN
            this.x = x
            this.y = y
            width = enemyWidth
            height = enemyHeight

            group = "enemies"
            setFocusableTo(true, this)
            isSelectable = true

            onSelect {
                if (enemySelected) return@onSelect
                gameEvents.fire(NewGameController.Events.EnemySelected(enemy))
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
                    text: String,
                    iconHandle: String,
                    additionalDamage: String? = null,
                    additionalDamageIcon: String? = null
                ) {
                    image {
                        backgroundHandle = iconHandle
                        width = 60f
                        height = 60f
                    }
                    group {
                        width = 60f
                        height = 40f
                        backgroundHandle = "wood_box"
                        val newText = if (additionalDamageIcon != null) {
                            "$text + ?1$additionalDamage?1 §§${additionalDamageIcon}§§"
                        } else {
                            text
                        }
                        advancedText("roadgeek", Color.FortyWhite, 0.9f) {
                            val redEffect = AdvancedTextEffect.AdvancedColorTextEffect("?R", Color.Red)
                            relativeHeight(58f)
                            setRawText(newText, listOf(redEffect))
                            centerX()
                            centerY()
                        }
                    }
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
                enemy.enemyEvents.watchFor<Enemy.EnemyActionChangedEvent> { event ->
                    this.clear()
                    when (val nextAction = event.nextAction) {
                        is NextEnemyAction.ShownEnemyAction -> showAction(
                            nextAction.action.indicatorText ?: "",
                            nextAction.action.prototype.iconHandle,
                            event.additionalDamage.toString(),
                            event.additionalDamageIcon
                        )
                        is NextEnemyAction.None -> {}
                        is NextEnemyAction.HiddenEnemyAction -> showAction("?",  "enemy_action_unknown")
                    }
                }
            }

            group {
                relativeWidth(100f)
                height = enemyHeight * 0.65f

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

                    enemy.enemyEvents.watchFor<Enemy.HealthChangedEvent> { event ->
                        if (!enemy.isDefeated) return@watchFor
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
                    onLayoutAndNow { height = width }
                    animateRotationSinus(
                        amplitude = Math.PI.toFloat() * 1.8f,
                    )
                    animateUpAndDownSinus(
                        method = AnimatedActor.AnimationMethod.DRAW_OFFSET,
                        amplitude = 14f,
                        frequency = 0.4f
                    )
                    gameEvents.watchFor<NewGameController.Events.EnemySelected> { (e) ->
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
        width = 680f
        height = width * (1057f / 1845f)
        centerX()
        y = 340f
        flexDirection = FlexDirection.COLUMN
        horizontalAlign = CustomAlign.SPACE_AROUND
        verticalAlign = CustomAlign.CENTER
        color.a = 0f
        touchable = Touchable.disabled
        gameEvents.watchFor<NewGameController.Events.ParryStateChange> { event ->
            val action = AlphaAction()
            action.duration = 0.2f
            action.alpha = if (event.inParryMenu) 1f else 0f
            addAction(action)
        }
        label("red_wing", "Parry?", Color.BrightYellow, isDistanceField = true) {
            setFontScale(1.4f)
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
        label("roadgeek", "", Color.GRAY) {
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (_, damage, blockable) ->
                setText("Parrying will let ${(damage - blockable).coerceAtLeast(0)} damage through")
            }
            setFontScale(0.7f)
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
        label("roadgeek", "", Color.GRAY) {
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (_, damage, _) ->
                setText("Passing will let $damage damage through")
            }
            setFontScale(0.7f)
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
        }
    }

    private fun CustomGroup.targetSelectionPopup() = box {
        backgroundHandle = "common_popup_background_black_large"
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

        gameEvents.watchFor<NewGameController.Events.TargetSelectionEvent> { event ->
            animateInOut(true)
            event.promise.then { animateInOut(false) }
        }
        label("red_wing", "", Color.BrightYellow, isDistanceField = true) {
            setAlignment(Align.center)
            relativeWidth(100f)
            syncHeight()
            gameEvents.watchFor<NewGameController.Events.TargetSelectionEvent> { event ->
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

        shootButton()
        holsterButton()

        actor(revolver) {
            touchable = Touchable.enabled
            name("revolver")
            centerX()
            y = -30f
            syncDimensions()
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

            label("red_wing", "0/0", Color.White) {
                setFontScale(1.1f)
                centerX()
                centerY()
                gameEvents.watchFor<NewGameController.Events.ReservesChanged> { (_, new) ->
                    setText("${new}/${NewGameController.Config.baseReserves}")
                }
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

            label("red_wing", "{game.cardsInStack}", Color.White, isTemplate = true) {
                setFontScale(1.1f)
                centerX()
                centerY()
            }
        }

    }

    private fun CustomGroup.shootButton() {
        var parryPromise: Promise<Boolean>? = null
        gameEvents.watchFor<NewGameController.Events.ParryStateChange> { event ->
            parryPromise = event.resolutionPromise
        }
        group(backgroundHints = arrayOf("shoot_button_texture", "shoot_button_hover_texture")) {
            name("shoot_button")
            touchable = Touchable.enabled
            x = 370f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 370f, 100, Interpolation.pow2),
                AnimState("hover", 360f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 400),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "shoot_button"
            styles(
                normal = {
                    backgroundHandle = "shoot_button_texture"
                    xAnim.state("open")
                },
                focused = {
                    backgroundHandle = "shoot_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            onSelect {
                screen.deselectActor(this)
                screen.focusedActor = null
                gameEvents.fire(NewGameController.Events.Shoot)
            }
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (inParryMenu) {
                    xAnim.state("closed")
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    touchable = Touchable.enabled
                }
            }
        }

        group(backgroundHints = arrayOf("pass_button_texture", "pass_button_hover_texture")) {
            name("pass_button")
            var closed = true
            touchable = Touchable.disabled
            x = 600f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 370f, 100, Interpolation.pow2),
                AnimState("hover", 360f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 400),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "pass_button"
            onSelect {
                screen.deselectActor(this)
                screen.focusedActor = null
                parryPromise?.let {
                    if (parryPromise.isNotResolved) parryPromise.resolve(false)
                }
            }
            styles(
                normal = {
                    backgroundHandle = "pass_button_texture"
                    xAnim.state(if (closed) "closed" else "open")
                },
                focused = {
                    backgroundHandle = "pass_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (!inParryMenu) {
                    xAnim.state("closed")
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }
    }

    private fun CustomGroup.holsterButton() {
        var parryPromise: Promise<Boolean>? = null
        gameEvents.watchFor<NewGameController.Events.ParryStateChange> { event ->
            parryPromise = event.resolutionPromise
        }
        group(backgroundHints = arrayOf("end_turn_button_texture", "end_turn_button_hover_texture")) {
            name("holster_button")
            touchable = Touchable.enabled
            x = 990f
            y = 60f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 990f, 100, Interpolation.pow2),
                AnimState("hover", 1000f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 400),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "holster_button"
            onSelect {
                screen.deselectActor(this)
                screen.focusedActor = null
                gameEvents.fire(NewGameController.Events.Holster)
            }
            styles(
                normal = {
                    backgroundHandle = "end_turn_button_texture"
                    xAnim.state("open")
                },
                focused = {
                    backgroundHandle = "end_turn_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (inParryMenu) {
                    xAnim.state("closed")
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    touchable = Touchable.enabled
                }
            }
        }

        group(backgroundHints = arrayOf("parry_button_texture", "parry_button_hover_texture")) {
            name("parry_button")
            var closed = true
            touchable = Touchable.disabled
            x = 990f
            y = 50f
            val xAnim = propertyAnimation<CustomGroup, Float>(
                xPositionAbstractProperty(),
                AnimState("open", 990f, 100, Interpolation.pow2),
                AnimState("hover", 1000f, 100, Interpolation.pow2),
                AnimState("closed", 600f, 400),
            )
            width = 250f
            height = 250f * (543f / 655f)
            isSelectable = true
            isFocusable = true
            group = "parry_button"
            onSelect {
                screen.deselectActor(this)
                screen.focusedActor = null
                parryPromise?.let {
                    if (parryPromise.isNotResolved) parryPromise.resolve(true)
                }
            }
            styles(
                normal = {
                    backgroundHandle = "parry_button_texture"
                    xAnim.state(if (closed) "closed" else "open")
                },
                focused = {
                    backgroundHandle = "parry_button_hover_texture"
                    xAnim.state("hover")
                },
            )
            gameEvents.watchFor<NewGameController.Events.ParryStateChange> { (inParryMenu) ->
                if (!inParryMenu) {
                    xAnim.state("closed")
                    closed = true
                    touchable = Touchable.disabled
                } else {
                    xAnim.state("open")
                    closed = false
                    touchable = Touchable.enabled
                }
            }
        }
    }

    private fun CustomGroup.winPopup() {
        var continuePromise: Promise<Unit>? = null
        box {
            backgroundHandle = "win_popup_background"
            relativeHeight(120f)
            onLayoutAndNow { width = height * (850f / 973f) }
            centerX()
            centerY()
            flexDirection = FlexDirection.COLUMN
            verticalAlign = CustomAlign.SPACE_BETWEEN
            horizontalAlign = CustomAlign.CENTER
            isVisible = false

            gameEvents.watchFor<NewGameController.Events.ShowPlayerWonPopup> { event ->
                isVisible = true
                continuePromise = event.popupPromise
            }

            box {
                horizontalAlign = CustomAlign.CENTER
                relativeWidth(100f)
                // TODO: randomize text
                label("red_wing_bmp", "You survived", Color.FortyWhite, isDistanceField = false) {
                    setFontScale(0.5f)
                    setAlignment(Align.center)
                    marginTop = 150f
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

                    gameEvents.watchFor<NewGameController.Events.ShowPlayerWonPopup> { (_, money, _) ->
                        isVisible = money > 0
                    }

                    label("red_wing", "", Color.FortyWhite) {
                        gameEvents.watchFor<NewGameController.Events.ShowPlayerWonPopup> { (_, money, _) ->
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

                    gameEvents.watchFor<NewGameController.Events.ShowPlayerWonPopup> { (gotCard, _, _) ->
                        isVisible = gotCard
                    }

                    label("red_wing", "You get a card", Color.FortyWhite)
                }
            }
            box {
                width = 200f
                height = 50f
                isFocusable = true
                isSelectable = true
                touchable = Touchable.enabled
                verticalAlign = CustomAlign.CENTER
                horizontalAlign = CustomAlign.CENTER
                styles(
                    normal = {
                        backgroundHandle = "common_button_default"
                    },
                    focused = {
                        backgroundHandle = "common_button_hover"
                    }
                )
                label("red_wing", "Claim & Continue", Color.FortyWhite) {
                    setFontScale(0.7f)
                    setAlignment(Align.center)
                }
                marginBottom = 120f
                onSelect {
                    continuePromise?.resolve(Unit)
                    SoundPlayer.situation("money_earned", screen)
                    val navBarSymbol = screen.namedActorOrError("cash_symbol")
                    val winPopupSymbol = screen.namedActorOrError("overkill_cash_symbol")
                    val renderPipeline = FortyFive.currentRenderPipeline!!
                    val moneyAnim = GraphicsConfig.cashOrbAnimation(
                        winPopupSymbol.localToScreenCoordinates(Vector2(
                            winPopupSymbol.width / 2,
                            winPopupSymbol.height / 2
                        )),
                        navBarSymbol.localToScreenCoordinates(Vector2(
                            navBarSymbol.width / 2,
                            navBarSymbol.height / 2
                        )),
                        renderPipeline
                    )
                    renderPipeline.addOrbAnimation(moneyAnim)
                }
            }
        }
    }

    override fun getScreenControllers(): List<ScreenController> = listOf(
        BiomeBackgroundScreenController(screen, false),
        NewGameController(screen, gameEvents, warningParent!!)
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(KeyInputMap.createFromKotlin(listOf(

        KeyInputMapEntry(
            priority = 20,
            condition = KeyInputCondition.Always,
            singleKeys = listOf(
                KeyInputMapKeyEntry(Keys.S, modifierKeys = listOf(Keys.SHIFT_LEFT)),
                KeyInputMapKeyEntry(Keys.S, modifierKeys = listOf(Keys.SHIFT_RIGHT)),
            ),
            defaultActions = listOf(
                KeyActionFactory.getAction("FocusSpecific", "shoot_button"),
                KeyActionFactory.getAction("FocusSpecific", "pass_button"),
            )
        ),

        KeyInputMapEntry(
            priority = 20,
            condition = KeyInputCondition.Always,
            singleKeys = listOf(
                KeyInputMapKeyEntry(Keys.H, modifierKeys = listOf(Keys.SHIFT_LEFT)),
                KeyInputMapKeyEntry(Keys.H, modifierKeys = listOf(Keys.SHIFT_RIGHT)),
            ),
            defaultActions = listOf(
                KeyActionFactory.getAction("FocusSpecific", "holster_button"),
                KeyActionFactory.getAction("FocusSpecific", "parry_button"),
            )
        ),

    ), screen))

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf(
        FocusableParent(
            listOf(
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf("enemies", NavbarCreator.navbarFocusGroup)
                ),
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf("shoot_button", "holster_button", "pass_button", "parry_button")
                ),
                SelectionTransition(
                    TransitionType.Seamless,
                    groups = listOf(NewCardHand.cardFocusGroupName, RevolverSlot.revolverSlotFocusGroupName, "enemies")
                ),
                SelectionTransition(
                    TransitionType.LastResort,
                    groups = listOf(RevolverSlot.revolverSlotFocusGroupName),
                )
            )
        )
    )

    private fun orbAnimationTimeline(
        source: Actor,
        target: Actor,
        amount: Int,
        isReserves: Boolean,
        duration: Int = 300
    ): Timeline = Timeline.timeline {
        val renderPipeline = FortyFive.currentRenderPipeline ?: return@timeline
        repeat(amount) {
            action {
                SoundPlayer.situation("orb_anim_playing", screen)
                renderPipeline.addOrbAnimation(
                    GraphicsConfig.orbAnimation(
                        source.localToStageCoordinates(Vector2(0f, 0f)) +
                                Vector2(source.width / 2, source.height / 2),
                        target.localToStageCoordinates(Vector2(0f, 0f)) +
                                Vector2(target.width / 2, target.height / 2),
                        isReserves,
                        renderPipeline,
                        duration = duration
                    ))
            }
            delay(50)
        }
    }

    private fun reservesPaidAnim(amount: Int, animTarget: Actor): Timeline =
        orbAnimationTimeline(reservesAnimationTarget, animTarget, amount = amount, isReserves = true)

    private fun reservesGainedAnim(amount: Int, animSource: Actor): Timeline =
        orbAnimationTimeline(animSource, reservesAnimationTarget, amount = amount, isReserves = true)


    private fun bindEventHandlers() {
        gameEvents.watchFor<NewGameController.Events.ReservesChanged>(::reservesChangedAnim)
        gameEvents.watchFor<NewGameController.Events.PlayCardOrbAnimation> { event ->
            event.orbAnimationTimeline = orbAnimationTimeline(deckAnimationTarget, event.targetActor, 1, false, duration = 200)
        }
        gameEvents.watchFor<NewGameController.Events.SetupEnemies>(::setupEnemies)
    }

    private fun setupEnemies(event: NewGameController.Events.SetupEnemies) {
        var x = 10f
        var y = 160f
        event.enemies.forEach { enemy ->
            val neededWidth = createEnemy(x, y, enemy)
            x += neededWidth
            y -= 30f
        }
    }

    private fun reservesChangedAnim(event: NewGameController.Events.ReservesChanged) {
        val (old, new, source, controller) = event
        source ?: return
        val amount = new - old
        val anim = when {
            amount > 0 -> reservesGainedAnim(amount, source)
            amount < 0 -> reservesPaidAnim(amount, source)
            else -> null
        }
        anim?.let { controller.dispatchAnimTimeline(it) }
    }

}
