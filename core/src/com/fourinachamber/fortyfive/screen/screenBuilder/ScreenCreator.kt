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
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BackgroundActor
import com.fourinachamber.fortyfive.screen.general.customActor.OnLayoutActor
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

    fun start(screen: OnjScreen) {
        this.screen = screen
    }

    abstract fun getRoot(): Group

    abstract fun getScreenControllers(): List<ScreenController>

    abstract fun getInputMaps(): List<KeyInputMap>

    protected inline fun group(builder: CustomGroup.() -> Unit = {}): CustomGroup {
        val group = CustomGroup(screen)
        builder(group)
        return group
    }

    protected inline fun horizontalGroup(builder: CustomHorizontalGroup.() -> Unit = {}): CustomHorizontalGroup {
        val group = CustomHorizontalGroup(screen)
        builder(group)
        return group
    }

    protected inline fun verticalGroup(builder: CustomVerticalGroup.() -> Unit = {}): CustomVerticalGroup {
        val group = CustomVerticalGroup(screen)
        builder(group)
        return group
    }

    protected inline fun Group.image(builder: CustomImageActor.() -> Unit = {}): CustomImageActor {
        val image = CustomImageActor(null, screen, false, "", false)
        builder(image)
        this.addActor(image)
        return image
    }

    protected inline fun Group.horizontalSpacer(width: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(definedWidth = width)
        builder(spacer)
        this.addActor(spacer)
        return spacer
    }

    protected inline fun Group.verticalSpacer(height: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(definedHeight = height)
        builder(spacer)
        this.addActor(spacer)
        return spacer
    }

    protected inline fun Group.verticalGrowingSpacer(proportion: Float, builder: Spacer.() -> Unit = {}): Spacer {
        val spacer = Spacer(growProportion = proportion)
        builder(spacer)
        this.addActor(spacer)
        return spacer
    }

    protected inline fun Group.label(
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

    protected fun forceLoadFont(handle: String): BitmapFont = ResourceManager.forceGet(this, screen, handle)

    protected inline fun <T : Actor> Group.actor(actor: T, builder: T.() -> Unit): T {
        builder(actor)
        this.addActor(actor)
        return actor
    }

    protected fun <T> T.relativeWidth(percent: Float) where T : Actor, T : OnLayoutActor {
        width = parent.width * (percent / 100f)
        onLayout { width = parent.width * (percent / 100f) }
    }

    protected fun <T> T.relativeHeight(percent: Float) where T : Actor, T : OnLayoutActor {
        height = parent.height * (percent / 100f)
        onLayout { height = parent.height * (percent / 100f) }
    }

    protected fun <T> T.syncHeight() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayout { height = prefHeight }
    }

    protected fun <T> T.syncWidth() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayout { width = prefWidth }
    }

    protected fun <T> T.syncDimensions() where T : Actor, T : Layout, T : OnLayoutActor {
        syncWidth()
        syncHeight()
    }

    protected fun <T> T.centerX() where T : Actor, T : Layout, T : OnLayoutActor {
        onLayout { x = parent.width / 2 - width / 2 }
    }

    protected fun <T> T.backgrounds(normal: String?, hover: String) where T : Actor, T : BackgroundActor {
        backgroundHandle = normal
        onHoverEnter { backgroundHandle = hover }
        onHoverLeave { backgroundHandle = normal }
    }

    protected var Label.fontColor: Color
        get() = style.fontColor
        set(value) {
            style.fontColor = value
        }

    protected fun loadInputMap(name: String, screen: OnjScreen): KeyInputMap {
        val file = ConfigFileManager.getConfigFile("inputMaps")
        return KeyInputMap.readFromOnj(file.get<OnjArray>(name), screen)
    }

}
