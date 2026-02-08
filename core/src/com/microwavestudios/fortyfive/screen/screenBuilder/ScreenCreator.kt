package com.microwavestudios.fortyfive.screen.screenBuilder

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.viewport.Viewport
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.animation.AbstractProperty
import com.microwavestudios.fortyfive.animation.AnimState
import com.microwavestudios.fortyfive.animation.DefaultInterpolators
import com.microwavestudios.fortyfive.animation.Interpolator
import com.microwavestudios.fortyfive.animation.PropertyAnimation
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.commonComponents.AdvancedTextWidget
import com.microwavestudios.fortyfive.screen.commonComponents.BackpackCreator.getSharedBackpack
import com.microwavestudios.fortyfive.screen.commonComponents.NavbarCreator
import com.microwavestudios.fortyfive.screen.commonComponents.NavbarCreator.getSharedNavBar
import com.microwavestudios.fortyfive.screen.commonComponents.SettingsCreator.getSharedSettingsMenu
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent
import com.microwavestudios.fortyfive.screen.commonComponents.TutorialInfoActor
import com.microwavestudios.fortyfive.screen.commonComponents.RunBoardCreator.getSharedRunBoard
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.screen.commonComponents.PopupCreator.getSharedPopup
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.TemplateString
import com.microwavestudios.fortyfive.utils.Timeline
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KMutableProperty

/**
 * this class can be used together with the [FromKotlinScreenBuilder] class to create screens.
 * To add a new screen, create a new class for that screen that extends this class. Override the
 * properties and functions here to configure you screen and override the [getRoot] function to create
 * the screen structure and return the root of it.
 *
 * Additionally, this class provides an exhaustive DSL that makes creating screens as easy as possible.
 */
@OptIn(ExperimentalContracts::class)
abstract class ScreenCreator : ResourceBorrower {

    abstract val name: String

    abstract val viewport: Viewport

    /**
     * if true, the [SoundPlayer][com.microwavestudios.fortyfive.screen.SoundPlayer] plays ambient sounds
     * in line with the biome the player is in
     */
    abstract val playAmbientSounds: Boolean

    abstract val background: ResourceHandle?

    /**
     * defines what transitions should occur when transitioning away from this screen.
     * The [ScreenManager] looks up the name of the next screen in this map and uses the provided
     * transition. "*" can be used to add a catch-all transition. The ScreenManager also provides
     * functions for creating predefined transitions, like [noTransition], [geometricFadeTransition],
     * or [fadeToBlackTransition]
     */
    open val transitions: Map<String, ScreenManager.ScreenTransition> = mapOf()

    lateinit var screen: CustomScreen
        private set

    /**
     * the context for this screen. Prefer using the [context] function, as it will also
     * automatically cast the context to the correct type.
     */
    var _context: Any? = null

    private val _namedActors: MutableMap<String, Actor> = mutableMapOf()
    val namedActors: Map<String, Actor>
        get() = _namedActors


    fun start(screen: CustomScreen, context: Any?) {
        this.screen = screen
        this._context = context
    }

    /**
     * called every frame as long as the screen is active
     */
    open fun update() { }

    /**
     * Creates the screen structure and returns the root actor
     */
    abstract fun getRoot(): Group

    abstract fun getScreenControllers(): List<ScreenController>

    /**
     * names of the pages that should be displayed in addition to the default debug pages in
     * the debug menu
     */
    open fun debugMenuPages(): List<String> = emptyList()

    /**
     * casts [_context] to [T] and returns it
     */
    inline fun <reified T> context(): T {
        val context = _context
            ?: throw RuntimeException("screen $name expects a context, but context was null")
        if (context !is T) {
            throw RuntimeException("screen $name expected context of type ${T::class.simpleName} but received" +
                    "${context::class.simpleName}")
        }
        return context
    }

    /**
     * constructs a [CustomGroup] with no parent
     */
    inline fun newGroup(
        backgroundHints: Array<ResourceHandle> = arrayOf(),
        builder: (@ScreenDslMarker CustomGroup).() -> Unit = {}
    ): CustomGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomGroup(screen, backgroundHints = backgroundHints)
        builder(group)
        return group
    }

    /**
     * constructs a [CustomBox] with no parent
     */
    inline fun newBox(
        backgroundHints: Array<ResourceHandle> = arrayOf(),
        builder: (@ScreenDslMarker CustomBox).() -> Unit = {}
    ): CustomBox {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val box = CustomBox(
            backgroundHints = backgroundHints,
            screen = screen
        )
        builder(box)
        return box
    }

    /**
     * constructs a [CustomHorizontalGroup] with no parent
     *
     * Note: prefer [newBox]
     */
    inline fun newHorizontalGroup(backgroundHints: Array<ResourceHandle> = arrayOf(), builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomHorizontalGroup(screen, backgroundHints = backgroundHints)
        builder(group)
        return group
    }

    /**
     * constructs a [CustomVerticalGroup] with no parent
     *
     * Note: prefer [newBox]
     */
    inline fun newVerticalGroup(backgroundHints: Array<ResourceHandle> = arrayOf(), builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomVerticalGroup(screen, backgroundHints = backgroundHints)
        builder(group)
        return group
    }

    /**
     * constructs a [CustomGroup] with the receiver as parent
     */
    inline fun Group.group(
        backgroundHints: Array<ResourceHandle> = arrayOf(),
        builder: (@ScreenDslMarker CustomGroup).() -> Unit = {}
    ): CustomGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomGroup(screen, backgroundHints = backgroundHints)
        addActor(group)
        builder(group)
        return group
    }

    fun Actor.name(name: String) {
        _namedActors[name] = this
        this.name = name
    }

    /**
     * constructs a [CustomImageActor] with the receiver as parent
     */
    inline fun Group.image(
        backgroundHints: Array<ResourceHandle> = arrayOf(),
        builder: (@ScreenDslMarker CustomImageActor).() -> Unit = {}
    ): CustomImageActor {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val image = CustomImageActor(null, screen, backgroundHints)
        this.addActor(image)
        builder(image)
        return image
    }

    /**
     * constructs a [CustomBox] with the receiver as parent
     */
    inline fun Group.box(
        backgroundHints: Array<ResourceHandle> = arrayOf(),
        isScrollable: Boolean = false,
        builder: (@ScreenDslMarker CustomBox).() -> Unit = {}
    ): CustomBox {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val box = if (isScrollable) {
            CustomScrollableBox(
                backgroundHints,
                screen
            )
        } else {
            CustomBox(screen, backgroundHints)
        }
        this.addActor(box)
        builder(box)
        return box
    }

    /**
     * constructs a [Selector] with the receiver as parent
     */
    inline fun Group.selector(
        font: String,
        bindTarget: BindTarget<*>,
        fontScale: Float = 1f,
        fontColor: Color,
        noinline settingChangedCallback: (() -> Unit)? = null,
        builder: (@ScreenDslMarker Selector).() -> Unit = {}
    ): Selector {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val selector = Selector(
            forceLoadFont(font),
            arrowTextureHandle = "common_symbol_arrow_right",
            bindTarget = bindTarget,
            fontScale = fontScale,
            fontColor = fontColor,
            settingChangedCallback = settingChangedCallback,
            screen = screen
        )
        this.addActor(selector)
        builder(selector)
        return selector
    }

    /**
     * constructs a [Selector] with the receiver as parent
     */
    inline fun Group.selector(
        font: String,
        bindTarget: String,
        fontScale: Float = 1f,
        fontColor: Color,
        noinline settingChangedCallback: (() -> Unit)? = null,
        builder: (@ScreenDslMarker Selector).() -> Unit = {}
    ): Selector {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        return selector(font, BindTargetFactory.getAnyType(bindTarget), fontScale, fontColor, settingChangedCallback, builder)
    }

    /**
     * constructs a [Slider] with the receiver as parent
     */
    inline fun Group.slider(
        min: Float,
        max: Float,
        bindTarget: String,
        builder: (@ScreenDslMarker Slider).() -> Unit = {}
    ): Slider {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val slider = Slider(
            sliderBackground = "common_slider_background",
            handleRadius = 7f,
            handleColor = Color.GRAY,
            sliderHeight = 10f,
            bind = bindTarget,
            screen = screen,
            min = min,
            max = max,
        )
        this.addActor(slider)
        builder(slider)
        return slider
    }

    /**
     * constructs a [CustomInputField] with the receiver as parent
     */
    inline fun Group.inputField(
        font: String,
        fontColor: Color,
        defaultText: String = "",
        backgroundHints: Array<ResourceHandle> = arrayOf(),
        builder: (@ScreenDslMarker CustomInputField).() -> Unit = {}
    ): CustomInputField {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val inputField = CustomInputField(
            screen,
            defaultText,
            Label.LabelStyle(forceLoadFont(font), color),
            backgroundHints
        )
        this.addActor(inputField)
        builder(inputField)
        return inputField
    }

    /**
     * constructs a [Spacer] with the receiver as parent
     */
    inline fun Group.horizontalSpacer(
        width: Float,
        builder: (@ScreenDslMarker Spacer).() -> Unit = {}
    ): Spacer {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val spacer = Spacer(definedWidth = width)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    /**
     * constructs a [Spacer] with the receiver as parent
     */
    inline fun Group.verticalSpacer(
        height: Float,
        builder: (@ScreenDslMarker Spacer).() -> Unit = {}
    ): Spacer {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val spacer = Spacer(definedHeight = height)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    /**
     * constructs a [NewLabel] with the receiver as parent
     */
    inline fun Group.label(
        font: String,
        text: String,
        color: Color = Color.BLACK,
        fontSize: Int,
        isTemplate: Boolean = false,
        backgroundHints: Array<String> = arrayOf(),
        builder: (@ScreenDslMarker NewLabel).() -> Unit = {}
    ): NewLabel {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val label = NewLabel(screen, text, backgroundHints)
        label.fontSize = fontSize
        label.fontColor = color
        label.fontGroup = font
        if (isTemplate) label.template = TemplateString(text)

        this.addActor(label)
        builder(label)
        return label
    }

    /**
     * constructs an [AdvancedTextWidget] with the receiver as parent
     */
    inline fun Group.advancedText(
        defaultFont: String,
        defaultColor: Color,
        defaultFontSize: Int,
        builder: (@ScreenDslMarker AdvancedTextWidget).() -> Unit = {}
    ): AdvancedTextWidget {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val advancedText =
            AdvancedTextWidget(Triple(defaultFont, defaultColor, defaultFontSize), screen)
        this.addActor(advancedText)
        builder(advancedText)
        return advancedText
    }

    /**
     * constructs an [AdvancedTextWidget] with the receiver as parent
     */
    inline fun Group.advancedText(
        defaults: Triple<String, Color, Int>,
        builder: (@ScreenDslMarker AdvancedTextWidget).() -> Unit = {}
    ): AdvancedTextWidget {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val advancedText = AdvancedTextWidget(defaults, screen)
        this.addActor(advancedText)
        builder(advancedText)
        return advancedText
    }

    fun forceLoadFont(handle: String): BitmapFont = FortyFive.resourceManager.forceGet(this, screen.lifetime, handle)

    inline fun <T : Actor> Group.actor(actor: T, builder: (@ScreenDslMarker T).() -> Unit = {}): T {
        this.addActor(actor)
        builder(actor)
        return actor
    }

    /**
     * adds all actors in [actors] and executes [builder] for each
     */
    inline fun <T : Actor> Group.allActors(
        actors: Iterable<T>,
        builder: (@ScreenDslMarker T).() -> Unit = {}
    ): Iterable<T> {
        actors.forEach { actor ->
            this.addActor(actor)
            builder(actor)
        }
        return actors
    }

    /**
     * sets the width to [percent] of the parent width
     */
    fun <T> T.relativeWidth(percent: Float) where T : Actor, T : OnLayoutActor {
        onLayoutAndNow { width = parent.width * (percent / 100f) }
    }

    /**
     * sets the height to [percent] of the parent height
     */
    fun <T> T.relativeHeight(percent: Float) where T : Actor, T : OnLayoutActor {
        onLayoutAndNow { height = parent.height * (percent / 100f) }
    }

    /**
     * executes [callback] directly when the function is called and when layout() is called on the actor
     */
    fun <T> T.onLayoutAndNow(callback: () -> Unit) where T : Actor, T : OnLayoutActor {
        callback()
        onLayout(callback)
    }

    /**
     * sets the height to the prefHeight of the actor
     */
    fun <T> T.syncHeight() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { height = prefHeight }
    }

    /**
     * sets the width to the prefWidth of the actor
     */
    fun <T> T.syncWidth() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { width = prefWidth }
    }

    /**
     * calls [syncWidth] and [syncHeight]
     */
    fun <T> T.syncDimensions() where T : Actor, T : Layout, T : OnLayoutActor {
        syncWidth()
        syncHeight()
    }

    /**
     * centers the actor on the x axies (Do not use in a box; the box handels layout itself)
     */
    fun <T> T.centerX() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { x = parent.width / 2 - width / 2 }
    }

    /**
     * centers the actor on the y axies (Do not use in a box; the box handels layout itself)
     */
    fun <T> T.centerY() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { y = parent.height / 2 - height / 2 }
    }

    /**
     * adds default button backgrounds and sound effects
     *
     * Use together with [buttonBackgroundHints]
     */
    fun CustomBox.defaultButtonConfig() {
        backgroundHandle = "common_button_default"
        observeInputState(
            GameInputs.States.focused,
            { backgroundHandle = "common_button_hover" },
            { backgroundHandle = "common_button_default" }
        )
        onInput(GameInputs.interact) {
            FortyFive.soundPlayer.situation("general_button_click", screen)
        }
    }

    /**
     * see [defaultButtonConfig]
     */
    fun buttonBackgroundHints() = arrayOf("common_button_default", "common_button_hover" )

    /**
     * adds default button backgrounds and sound effects
     *
     * Use together with [buttonBackgroundHints]
     */
    fun NewLabel.defaultButtonConfig() {
        backgroundHandle = "common_button_default"
        observeInputState(
            GameInputs.States.focused,
            { backgroundHandle = "common_button_hover" },
            { backgroundHandle = "common_button_default" }
        )
        onInput(GameInputs.interact) {
            FortyFive.soundPlayer.situation("general_button_click", screen)
        }
    }

    /**
     * adds overlays that most screens have in common
     *
     * Call in the root group
     */
    fun CustomGroup.addDefaultOverlays(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
        hasSettings: Boolean = true,
        hasBackpack: Boolean = false,
        hasCollection: Boolean = false,
        hasNavbar: Boolean = true,
        navbarIsLeft: Boolean = false,
        warnings: WarningParent? = null,
        hasTutorial: Boolean = true,
        hasTitleScreen: Boolean = true,
        canHaveRunBoard: Boolean = false,
    ) {

        val navbarObjects = mutableListOf<NavbarCreator.NavBarObject>()

        val settings: CustomGroup? = if (hasSettings) {
            val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight, events)
            navbarObjects.add(settingsObject)
            settings
        } else {
            null
        }

        val backpack = if (hasBackpack) {
            val (backpack, backpackObject) = getSharedBackpack(worldWidth, worldHeight, events, events, false)
            navbarObjects.add(backpackObject)
            backpack
        } else {
            null
        }

        val collection = if (hasCollection) {
            val (collection, collectionObject) = getSharedBackpack(worldWidth, worldHeight, events, events, true)
            navbarObjects.add(collectionObject)
            collection
        } else {
            null
        }

        val runBoard = if (canHaveRunBoard) {
            val (runBoard, runBoardObj) = getSharedRunBoard(worldWidth, worldHeight, events)
            navbarObjects.add(runBoardObj)
            runBoard
        } else {
            null
        }

        if (hasNavbar) {
            val navbar = getSharedNavBar(
                worldWidth, worldHeight,
                navbarObjects,
                screen,
                isLeft = navbarIsLeft
            )
            actor(navbar) {
                onLayoutAndNow { y = worldHeight - height }
                centerX()
            }
        }
        runBoard?.let { actor(it) }
        backpack?.let { actor(it) }
        collection?.let { actor(it) }
        settings?.let {
            actor(it) {
                centerX()
            }
        }

        if (hasTutorial) {
            val tutorialInfoActor = TutorialInfoActor(
                "tutorial_info_actor_background",
                2f,
                200f,
                screen
            )
            actor(tutorialInfoActor) {
                name("tutorialInfoActor")
                x = 0f
                y = 0f
                width = worldWidth
                height = worldHeight
                isVisible = false
            }
            advancedText("red wing", com.microwavestudios.fortyfive.utils.Color.FortyWhite, 32) {
                name("tutorial_info_text")
                horizontalTextAlign = CustomAlign.CENTER
                centerX()
                onLayout { y = worldHeight - prefHeight }
                syncHeight()
                relativeWidth(40f)
                isVisible = false
            }
        }

        actor(getSharedPopup(worldWidth, worldHeight, events))
        warnings?.let {
            actor(warnings.getActor())
        }
    }

    /**
     * see [PropertyAnimation]
     */
    inline fun <A, reified P> A.propertyAnimation(
        property: KMutableProperty<P>,
        vararg states: AnimState<P>,
        initialState: String,
        defaultTime: Int,
        defaultInterpolation: Interpolation,
        invalidate: Boolean = false,
        invalidateHierarchy: Boolean = false,
        invalidateParent: Boolean = false,
        interpolator: Interpolator<P>? = DefaultInterpolators.getDefaultInterpolator(P::class)
    ): PropertyAnimation<P> where A : Actor, P : Any = PropertyAnimation(
        this,
        AbstractProperty.fromKotlin(property),
        P::class,
        defaultTime,
        defaultInterpolation,
        initialState,
        interpolator,
        invalidate,
        invalidateHierarchy,
        invalidateParent,
        *states
    )

    /**
     * see [PropertyAnimation]
     */
    inline fun <A, reified P> A.propertyAnimation(
        property: AbstractProperty<P>,
        vararg states: AnimState<P>,
        initialState: String,
        defaultTime: Int,
        defaultInterpolation: Interpolation,
        invalidate: Boolean = false,
        invalidateHierarchy: Boolean = false,
        invalidateParent: Boolean = false,
        interpolator: Interpolator<P>? = DefaultInterpolators.getDefaultInterpolator(P::class)
    ): PropertyAnimation<P> where A : Actor, P : Any = PropertyAnimation(
        this,
        property,
        P::class,
        defaultTime,
        defaultInterpolation,
        initialState,
        interpolator,
        invalidate,
        invalidateHierarchy,
        invalidateParent,
        *states
    )

    var Label.fontColor: Color
        get() = style.fontColor
        set(value) {
            style.fontColor = value
        }

    fun geometricFadeTransition(duration: Int = 1000): ScreenManager.ScreenTransition = ScreenManager.ScreenTransition(
        transitionAway = {
            FortyFive
                .currentRenderPipeline
                ?.getGeometricFadeTimeline(duration / 2, reverse = false, stayBlack = true)
                ?: Timeline.emptyTimeline
        },
        transitionTo = {
            FortyFive
                .currentRenderPipeline
                ?.getGeometricFadeTimeline(duration / 2, reverse = true, stayBlack = false)
                ?: Timeline.emptyTimeline
        }
    )

    fun fadeToBlackTransition(duration: Int = 1500): ScreenManager.ScreenTransition = ScreenManager.ScreenTransition(
        transitionAway = {
            FortyFive
                .currentRenderPipeline
                ?.getFadeToBlackTimeline(duration / 2, stayBlack = true, reverse = false)
                ?: Timeline.emptyTimeline
        },
        transitionTo = {
            FortyFive
                .currentRenderPipeline
                ?.getFadeToBlackTimeline(duration / 2, stayBlack = false, reverse = true)
                ?: Timeline.emptyTimeline
        }
    )

    fun noTransition(): ScreenManager.ScreenTransition = ScreenManager.ScreenTransition(null, null)

    fun delayTransition(waitFor: Int): ScreenManager.ScreenTransition = ScreenManager.ScreenTransition(
        transitionAway = {
            Timeline.timeline { delay(waitFor) }
        },
        transitionTo = null
    )

    companion object {
        val fortyWhite: Color = Color.valueOf("F0EADD")
    }

    @Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
    @DslMarker
    annotation class ScreenDslMarker

}
