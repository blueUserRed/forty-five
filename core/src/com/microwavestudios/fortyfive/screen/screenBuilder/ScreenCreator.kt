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
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.ScreenController
import com.microwavestudios.fortyfive.screen.commonComponents.AdvancedTextWidget
import com.microwavestudios.fortyfive.screen.commonComponents.BackpackCreator.getSharedBackpack
import com.microwavestudios.fortyfive.screen.commonComponents.NavbarCreator
import com.microwavestudios.fortyfive.screen.commonComponents.NavbarCreator.getSharedNavBar
import com.microwavestudios.fortyfive.screen.commonComponents.SettingsCreator.getSharedSettingsMenu
import com.microwavestudios.fortyfive.screen.commonComponents.WarningParent
import com.microwavestudios.fortyfive.screen.commonComponents.TutorialInfoActor
import com.microwavestudios.fortyfive.screen.commonComponents.RunBoardCreator.getSharedRunBoard
import com.microwavestudios.fortyfive.screen.actors.*
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.TemplateString
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KMutableProperty

@OptIn(ExperimentalContracts::class)
abstract class ScreenCreator : ResourceBorrower {

    abstract val name: String

    abstract val viewport: Viewport

    abstract val playAmbientSounds: Boolean

    abstract val background: String?

    abstract val transitionAwayTimes: Map<String, Int>

    lateinit var screen: OnjScreen
        private set

    var _context: Any? = null

    private val _namedActors: MutableMap<String, Actor> = mutableMapOf()
    val namedActors: Map<String, Actor>
        get() = _namedActors


    fun start(screen: OnjScreen, context: Any?) {
        this.screen = screen
        this._context = context
    }

    open fun update() { }

    abstract fun getRoot(): Group

    abstract fun getScreenControllers(): List<ScreenController>

    open fun debugMenuPages(): List<String> = emptyList()

    inline fun <reified T> context(): T {
        val context = _context
            ?: throw RuntimeException("screen $name expects a context, but context was null")
        if (context !is T) {
            throw RuntimeException("screen $name expected context of type ${T::class.simpleName} but received" +
                    "${context::class.simpleName}")
        }
        return context
    }

    inline fun newGroup(backgroundHints: Array<String> = arrayOf(), builder: CustomGroup.() -> Unit = {}): CustomGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomGroup(screen, backgroundHints = backgroundHints)
        builder(group)
        return group
    }

    inline fun newBox(backgroundHints: Array<String> = arrayOf(), builder: CustomBox.() -> Unit = {}): CustomBox {
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

    inline fun newHorizontalGroup(backgroundHints: Array<String> = arrayOf(), builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomHorizontalGroup(screen, backgroundHints = backgroundHints)
        builder(group)
        return group
    }

    inline fun newVerticalGroup(backgroundHints: Array<String> = arrayOf(), builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomVerticalGroup(screen, backgroundHints = backgroundHints)
        builder(group)
        return group
    }

    inline fun Group.group(backgroundHints: Array<String> = arrayOf(), builder: CustomGroup.() -> Unit = {}): CustomGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomGroup(screen, backgroundHints = backgroundHints)
        addActor(group)
        builder(group)
        return group
    }

    inline fun Group.horizontalGroup(backgroundHints: Array<String> = arrayOf(), builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomHorizontalGroup(screen, backgroundHints = backgroundHints)
        addActor(group)
        builder(group)
        return group
    }

    inline fun Group.verticalGroup(backgroundHints: Array<String> = arrayOf(), builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomVerticalGroup(screen, backgroundHints = backgroundHints)
        addActor(group)
        builder(group)
        return group
    }

    fun Actor.name(name: String) {
        _namedActors[name] = this
        this.name = name
    }

    inline fun Group.image(backgroundHints: Array<String> = arrayOf(), builder: CustomImageActor.() -> Unit = {}): CustomImageActor {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val image = CustomImageActor(null, screen, backgroundHints)
        this.addActor(image)
        builder(image)
        return image
    }

    inline fun Group.box(backgroundHints: Array<String> = arrayOf(), isScrollable: Boolean = false, builder: CustomBox.() -> Unit = {}): CustomBox {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val box = if (isScrollable) {
            _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomScrollableBox(
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

    inline fun Group.selector(
        font: String,
        bindTarget: String,
        fontScale: Float = 1f,
        builder: Selector.() -> Unit = {}
    ): Selector {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val selector = Selector(
            forceLoadFont(font),
            arrowTextureHandle = "common_symbol_arrow_right",
            bind = bindTarget,
            fontScale = fontScale,
            screen = screen
        )
        this.addActor(selector)
        builder(selector)
        return selector
    }

    inline fun Group.slider(min: Float, max: Float, bindTarget: String, builder: Slider.() -> Unit = {}): Slider {
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

    inline fun Group.inputField(
        font: String,
        fontColor: Color,
        defaultText: String = "",
        backgroundHints: Array<String> = arrayOf(),
        builder: CustomInputField.() -> Unit = {}
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

    inline fun Group.horizontalSpacer(width: Float, builder: Spacer.() -> Unit = {}): Spacer {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val spacer = Spacer(definedWidth = width)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    inline fun Group.verticalSpacer(height: Float, builder: Spacer.() -> Unit = {}): Spacer {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val spacer = Spacer(definedHeight = height)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    inline fun Group.label(
        font: String,
        text: String,
        color: Color = Color.BLACK,
        isTemplate: Boolean = false,
        isDistanceField: Boolean = true,
        backgroundHints: Array<String> = arrayOf(),
        builder: CustomLabel.() -> Unit = {}
    ): CustomLabel {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val label = if (isTemplate) {
            TemplateStringLabel(
                screen,
                TemplateString(text),
                Label.LabelStyle(forceLoadFont(font), color),
                isDistanceField = isDistanceField,
                backgroundHints = backgroundHints
            )
        } else {
            CustomLabel(
                screen,
                text,
                Label.LabelStyle(forceLoadFont(font), color),
                isDistanceField = isDistanceField,
                backgroundHints = backgroundHints
            )
        }
        this.addActor(label)
        builder(label)
        return label
    }

    inline fun Group.advancedText(
        defaultFont: String,
        defaultColor: Color,
        defaultFontScale: Float,
        isDistanceField: Boolean = true,
        builder: AdvancedTextWidget.() -> Unit = {}
    ): AdvancedTextWidget {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val advancedText =
            AdvancedTextWidget(Triple(defaultFont, defaultColor, defaultFontScale), screen, isDistanceField)
        this.addActor(advancedText)
        builder(advancedText)
        return advancedText
    }

    inline fun Group.advancedText(
        defaults: Triple<String, Color, Float>,
        isDistanceField: Boolean = true,
        builder: AdvancedTextWidget.() -> Unit = {}
    ): AdvancedTextWidget {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val advancedText =
            AdvancedTextWidget(defaults, screen, isDistanceField)
        this.addActor(advancedText)
        builder(advancedText)
        return advancedText
    }

    fun forceLoadFont(handle: String): BitmapFont = FortyFive.resourceManager.forceGet(this, screen.lifetime, handle)

    inline fun <T : Actor> Group.actor(actor: T, builder: T.() -> Unit = {}): T {
        this.addActor(actor)
        builder(actor)
        return actor
    }

    inline fun <T : Actor> Group.allActors(actors: Iterable<T>, builder: T.() -> Unit = {}): Iterable<T> {
        actors.forEach { actor ->
            this.addActor(actor)
            builder(actor)
        }
        return actors
    }

    fun <T> T.relativeWidth(percent: Float) where T : Actor, T : OnLayoutActor {
        onLayoutAndNow { width = parent.width * (percent / 100f) }
    }

    fun <T> T.relativeHeight(percent: Float) where T : Actor, T : OnLayoutActor {
        onLayoutAndNow { height = parent.height * (percent / 100f) }
    }

    fun <T> T.onLayoutAndNow(callback: () -> Unit) where T : Actor, T : OnLayoutActor {
        callback()
        onLayout(callback)
    }

    fun <T> T.syncHeight() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { height = prefHeight }
    }

    fun <T> T.syncWidth() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { width = prefWidth }
    }

    fun <T> T.syncDimensions() where T : Actor, T : Layout, T : OnLayoutActor {
        syncWidth()
        syncHeight()
    }

    fun <T> T.centerX() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { x = parent.width / 2 - width / 2 }
    }

    fun <T> T.centerY() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayoutAndNow { y = parent.height / 2 - height / 2 }
    }

    fun CustomBox.defaultButtonBackgrounds() {
        backgroundHandle = "common_button_default"
        observeInputState(
            GameInputs.States.focused,
            { backgroundHandle = "common_button_hover" },
            { backgroundHandle = "common_button_default" }
        )
    }

    fun buttonBackgroundHints() = arrayOf("common_button_default", "common_button_hover" )

    fun CustomLabel.defaultButtonBackgrounds() {
        backgroundHandle = "common_button_default"
        observeInputState(
            GameInputs.States.focused,
            { backgroundHandle = "common_button_hover" },
            { backgroundHandle = "common_button_default" }
        )
    }

    fun CustomGroup.addDefaultOverlays(
        worldWidth: Float,
        worldHeight: Float,
        events: EventPipeline,
        hasSettings: Boolean = true,
        hasBackpack: Boolean = true,
        hasNavbar: Boolean = true,
        navbarIsLeft: Boolean = false,
        warnings: WarningParent? = null,
        hasTutorial: Boolean = true,
        hasTitleScreen: Boolean = true,
        canHaveRunBoard: Boolean = false,
    ) {

        val navbarObjects = mutableListOf<NavbarCreator.NavBarObject>()

//        if (hasTitleScreenInNavbar) navbarObjects.add(getSharedTitleScreen())

        val settings: CustomGroup? = if (hasSettings) {
            val (settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)
            navbarObjects.add(settingsObject)
            settings
        } else {
            null
        }

        val backpack = if (hasBackpack) {
            val (backpack, backpackObject) = getSharedBackpack(worldWidth, worldHeight, events, events)
            navbarObjects.add(backpackObject)
            backpack
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
            advancedText("red_wing", com.microwavestudios.fortyfive.utils.Color.FortyWhite, 1f) {
                name("tutorial_info_text")
                horizontalTextAlign = _root_ide_package_.com.microwavestudios.fortyfive.screen.actors.CustomAlign.CENTER
                centerX()
                onLayout { y = worldHeight - prefHeight }
                syncHeight()
                relativeWidth(40f)
                isVisible = false
            }
        }

        warnings?.let {
            actor(warnings.getActor())
        }
    }

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

    companion object {
        val fortyWhite: Color = Color.valueOf("F0EADD")
    }

}
