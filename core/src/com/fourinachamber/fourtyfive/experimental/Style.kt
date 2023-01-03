package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import io.github.orioncraftmc.meditate.YogaNode
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class Style(
    val properties: List<StyleProperty>
) {

    companion object {

        fun readFromFile(path: String): Map<String, Style> {
            val obj = OnjParser.parseFile(Gdx.files.internal(path).file())
            schema.assertMatches(obj)
            obj as OnjObject

            return obj
                .get<OnjArray>("styles")
                .value
                .associate { style ->
                    style as OnjObject

                    val properties = style
                        .get<OnjArray>("properties")
                        .value
                        .map {
                            it as OnjStyleProperty
                            it.value
                        }

                    style.get<String>("name") to Style(properties)
                }
        }

        private val schema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/style.onjschema").file())
        }

    }
}

class StyleTarget(
    private val node: YogaNode,
    private val actor: Actor,
    private val styles: List<Style>
) {

    fun initialStyle(screen: StyleableOnjScreen) {
        for (style in styles) {
            for (property in style.properties) property.applyTo(node, actor, screen)
        }
    }

}
