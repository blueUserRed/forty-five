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
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BackgroundActor
import com.fourinachamber.fortyfive.screen.general.customActor.CustomBox
import com.fourinachamber.fortyfive.screen.general.customActor.OnLayoutActor
import com.fourinachamber.fortyfive.screen.general.customActor.Selector
import com.fourinachamber.fortyfive.screen.general.customActor.Slider
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.value.OnjArray
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KMutableProperty

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

    abstract fun getRoot(): Group

    abstract fun getScreenControllers(): List<ScreenController>

    abstract fun getInputMaps(): List<KeyInputMap>
    abstract fun getSelectionHierarchyStructure(): List<FocusableParent>

    inline fun newGroup(builder: CustomGroup.() -> Unit = {}): CustomGroup {
        val group = CustomGroup(screen)
        builder(group)
        return group
    }

    inline fun newBox(builder: CustomBox.() -> Unit = {}): CustomBox {
        val box = CustomBox(screen)
        builder(box)
        return box
    }

    inline fun newHorizontalGroup(builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        val group = CustomHorizontalGroup(screen)
        builder(group)
        return group
    }

    @OptIn(ExperimentalContracts::class)
    inline fun newVerticalGroup(builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
        contract {
            callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
        }
        val group = CustomVerticalGroup(screen)
        builder(group)
        return group
    }

    inline fun Group.group(builder: CustomGroup.() -> Unit = {}): CustomGroup {
        val group = CustomGroup(screen)
        addActor(group)
        builder(group)
        return group
    }

    inline fun Group.horizontalGroup(builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        val group = CustomHorizontalGroup(screen)
        addActor(group)
        builder(group)
        return group
    }

    inline fun Group.verticalGroup(builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
        val group = CustomVerticalGroup(screen)
        addActor(group)
        builder(group)
        return group
    }

    fun Actor.name(name: String) {
        _namedActors[name] = this
        this.name = name
    }

    inline fun Group.image(builder: CustomImageActor.() -> Unit = {}): CustomImageActor {
        val image = CustomImageActor(null, screen, false)
        this.addActor(image)
        builder(image)
        return image
    }

    inline fun Group.box(isScrollable: Boolean = false, builder: CustomBox.() -> Unit = {}): CustomBox {
        val box = if (isScrollable) CustomScrollableBox(screen) else CustomBox(screen)
        this.addActor(box)
        builder(box)
        return box
    }

    inline fun Group.selector(font: String, bindTarget: String, builder: Selector.() -> Unit = {}): Selector {
        val selector = Selector(
            forceLoadFont(font),
            arrowTextureHandle = "common_symbol_arrow_right",
            bind = bindTarget,
            screen = screen
        )
        this.addActor(selector)
        builder(selector)
        return selector
    }

    inline fun Group.slider(min: Float, max: Float, bindTarget: String, builder: Slider.() -> Unit = {}): Slider {
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

    inline fun Group.horizontalSpacer(width: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(definedWidth = width)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    inline fun Group.verticalSpacer(height: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(definedHeight = height)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    inline fun Group.verticalGrowingSpacer(proportion: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(growProportionHeight = proportion)
        this.addActor(spacer)
        builder(spacer)
        return spacer
    }

    inline fun Group.horizontalGrowingSpacer(proportion: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(growProportionWidth = proportion)
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

    fun <T> T.backgrounds(normal: String?, hover: String) where T : Actor, T : BackgroundActor {
        backgroundHandle = normal
        onHoverEnter { backgroundHandle = hover }
        onHoverLeave { backgroundHandle = normal }
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

    fun <T> T.addButtonDefaults() where T : Actor, T : KotlinStyledActor, T : BackgroundActor {
        setFocusableTo(true, this)
        isSelectable = true
        styles(
            normal = {
                if (this is DisableActor && isDisabled)
                    backgroundHandle = "common_button_disabled"
                else backgroundHandle = "common_button_default"
            },
            focused = {
                backgroundHandle = "common_button_hover"
            },
            selectedAndFocused = {
                backgroundHandle = "common_button_hover"
//                backgroundHandle = if (this !is DisableActor || !isDisabled)
//                    "common_button_hover"
//                else
//                    "common_button_disabled"
            }
        )
        onSelect { screen.changeSelectionFor(this) }
    }

    inline fun <T> T.styles(
        crossinline normal: () -> Unit = {},
        crossinline focused: () -> Unit = {},
        crossinline selected: () -> Unit = {},
        crossinline selectedAndFocused: () -> Unit = {},
        crossinline resetEachTime: () -> Unit = {},
    ) where T : Actor, T : KotlinStyledActor {
        onFocusChange { _, _ ->
            resetEachTime()
            if (isSelected) {
                if (isFocused) selectedAndFocused()
                else selected()
            } else if (isFocused) focused()
            else normal()
        }
        onSelectChange { _, _ ->
            resetEachTime()
            if (isSelected) {
                if (isFocused) selectedAndFocused()
                else selected()
            } else if (isFocused) focused()
            else normal()
        }
        resetEachTime()
        if (isSelected) {
            if (isFocused) selectedAndFocused()
            else selected()
        } else if (isFocused) focused()
        else normal()
    }

    var Label.fontColor: Color
        get() = style.fontColor
        set(value) {
            style.fontColor = value
        }

    fun loadInputMap(name: String, screen: OnjScreen): KeyInputMap {
        val file = ConfigFileManager.getConfigFile("inputMaps")
        return KeyInputMap.readFromOnj(file.get<OnjArray>(name), screen)
    }

    companion object {
        val fortyWhite: Color = Color.valueOf("F0EADD")
    }

}
