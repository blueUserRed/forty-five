package com.fourinachamber.fortyfive.screen.screenBuilder

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.utils.AdvancedTextParser
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.value.OnjArray

abstract class ScreenCreator : ResourceBorrower {

    abstract val name: String

    abstract val viewport: Viewport

    abstract val playAmbientSounds: Boolean

    abstract val background: String?

    abstract val transitionAwayTimes: Map<String, Int>

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

    inline fun newHorizontalGroup(builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        val group = CustomHorizontalGroup(screen)
        builder(group)
        return group
    }

    inline fun newVerticalGroup(builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
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
        val image = CustomImageActor(null, screen, false, "", false)
        this.addActor(image)
        builder(image)
        return image
    }

    inline fun Group.box(isScrollable: Boolean = false, builder: CustomBox.() -> Unit = {}): CustomBox {
        val box = if (isScrollable) CustomScrollableBox(screen)
        else CustomBox(screen)
        this.addActor(box)
        builder(box)
        return box
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
        val advancedText = AdvancedTextWidget(Triple(defaultFont, defaultColor, defaultFontScale), screen, isDistanceField)
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
        onLayout { x = parent.width / 2 - width / 2 }
    }

    fun <T> T.backgrounds(normal: String?, hover: String) where T : Actor, T : BackgroundActor {
        backgroundHandle = normal
        onHoverEnter { backgroundHandle = hover }
        onHoverLeave { backgroundHandle = normal }
    }

    fun <T> T.styles(
        normal: () -> Unit = {},
        focused: () -> Unit = {},
        selected: () -> Unit = {},
        selectedAndFocused: () -> Unit = {},
        resetEachTime:Boolean = false
    ) where T : Actor, T : KotlinStyledActor {
        fun update() {
            if (resetEachTime) normal()
            if (isSelected) {
                if (isFocused) selectedAndFocused()
                else selected()
            } else if (isFocused) focused()
            else normal()
        }
        onFocusChange { _, _ -> update() }
        onSelectChange { _, _ -> update() }
        update()
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

}
