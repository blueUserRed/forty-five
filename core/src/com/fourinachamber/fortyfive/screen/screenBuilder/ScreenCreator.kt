package com.fourinachamber.fortyfive.screen.screenBuilder

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.animation.AbstractProperty
import com.fourinachamber.fortyfive.animation.AnimState
import com.fourinachamber.fortyfive.animation.DefaultInterpolators
import com.fourinachamber.fortyfive.animation.Interpolator
import com.fourinachamber.fortyfive.animation.PropertyAnimation
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.components.BackpackCreator.getSharedBackpack
import com.fourinachamber.fortyfive.screen.components.NavbarCreator
import com.fourinachamber.fortyfive.screen.components.NavbarCreator.getSharedNavBar
import com.fourinachamber.fortyfive.screen.components.SettingsCreator.getSharedSettingsMenu
import com.fourinachamber.fortyfive.screen.components.ToTitleScreenCreator.getSharedTitleScreen
import com.fourinachamber.fortyfive.screen.components.WarningParent
import com.fourinachamber.fortyfive.screen.gameWidgets.TutorialInfoActor
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.OnLayoutActor
import com.fourinachamber.fortyfive.screen.general.customActor.Selector
import com.fourinachamber.fortyfive.screen.general.customActor.Slider
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.TemplateString
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

//    val addWidgetData: (Map<String, (Map<String, Any?>, Group?, OnjScreen, Actor, Boolean) -> Unit>)? = null

    lateinit var screen: OnjScreen
        private set

    private val _namedActors: MutableMap<String, Actor> = mutableMapOf()
    val namedActors: Map<String, Actor>
        get() = _namedActors

    fun start(screen: OnjScreen) {
        this.screen = screen
    }

    open fun update() { }

    abstract fun getRoot(): Group

    abstract fun getScreenControllers(): List<ScreenController>

    open fun debugMenuPages(): List<String> = emptyList()

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
            CustomScrollableBox(backgroundHints, screen)
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
                isDistanceField = isDistanceField
            )
        } else {
            CustomLabel(
                screen,
                text,
                Label.LabelStyle(forceLoadFont(font), color),
                isDistanceField = isDistanceField
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

    fun forceLoadFont(handle: String): BitmapFont = ResourceManager.forceGet(this, screen, handle)

    inline fun <T : Actor> Group.actor(actor: T, builder: T.() -> Unit = {}): T {
        this.addActor(actor)
        builder(actor)
        return actor
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

    fun CustomGroup.addDefaultOverlays(
        worldWidth: Float,
        worldHeight: Float,
        warningEvents: EventPipeline,
        hasSettings: Boolean = true,
        hasBackpack: Boolean = true,
        hasNavbar: Boolean = true,
        navbarIsLeft: Boolean = false,
        hasWarnings: Boolean = true,
        hasTutorial: Boolean = true,
        hasTitleScreenInNavbar: Boolean = true,
    ): WarningParent? {

        val warningParent = WarningParent(this@ScreenCreator, screen, warningEvents)
        val navbarObjects = mutableListOf<NavbarCreator.NavBarObject>()

        if (hasTitleScreenInNavbar) navbarObjects.add(getSharedTitleScreen())

        var settings: CustomGroup? = null
        if (hasSettings) {
            val (_settings, settingsObject) = getSharedSettingsMenu(worldWidth, worldHeight)
            settings = _settings
            navbarObjects.add(settingsObject)
        }

        var backpack: CustomGroup? = null
        if (hasBackpack) {
            val (_backpack, backpackObject) = getSharedBackpack(worldWidth, worldHeight, warningEvents)
            backpack = _backpack
            navbarObjects.add(backpackObject)
        }

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
            advancedText("red_wing", com.fourinachamber.fortyfive.utils.Color.FortyWhite, 1f) {
                name("tutorial_info_text")
                horizontalTextAlign = CustomAlign.CENTER
                centerX()
                onLayout { y = worldHeight - prefHeight }
                syncHeight()
                relativeWidth(40f)
                isVisible = false
            }
        }

        if (hasWarnings) {
            actor(warningParent.getActor())
            return warningParent
        } else {
            return null
        }
    }

    inline fun <A, reified P> A.propertyAnimation(
        property: KMutableProperty<P>,
        vararg states: AnimState<P>,
        interpolator: Interpolator<P>? = DefaultInterpolators.getDefaultInterpolator(P::class)
    ): PropertyAnimation<P> where A : Actor, P : Any = PropertyAnimation(
        this,
        AbstractProperty.fromKotlin(property),
        P::class,
        interpolator,
        *states
    )

    inline fun <A, reified P> A.propertyAnimation(
        property: AbstractProperty<P>,
        vararg states: AnimState<P>,
        interpolator: Interpolator<P>? = DefaultInterpolators.getDefaultInterpolator(P::class)
    ): PropertyAnimation<P> where A : Actor, P : Any = PropertyAnimation(
        this,
        property,
        P::class,
        interpolator,
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
