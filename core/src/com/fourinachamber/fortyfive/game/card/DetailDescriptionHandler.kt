package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Color
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.utils.AdvancedTextParser
import onj.parser.OnjParser
import onj.value.OnjArray
import onj.value.OnjObject
import onj.value.OnjValue

object DetailDescriptionHandler {

    private val groups: Map<String, Color>
    val allTextEffects: OnjArray
    val descriptions: Map<String, Pair<String, String>>  // keyword   then    group with description

    init {
        val onj = ConfigFileManager.getConfigFile("descriptions")
        groups = onj
            .get<OnjArray>("hoverDetailDescriptionGroups")
            .value
            .filterIsInstance<OnjObject>()
            .associate { it.get<String>("name") to it.get<Color>("color") }
        allTextEffects = getAllTextEffects(onj.get<OnjArray>("defaultTextEffects"))
        descriptions = onj
            .get<OnjArray>("hoverDetailDescriptions")
            .value
            .filterIsInstance<OnjObject>()
            .associate {
                it.get<String>("keyword").lowercase() to (it.get<String>("groupName") to it.get<String>("description"))
            }
    }

    private fun getAllTextEffects(default: OnjArray): OnjArray {
        val res = mutableListOf<OnjValue>()
        for (i in groups) {
            res.add(AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$${i.key}$", i.value).asOnjObject())
        }
        res.addAll(default.value)
        return OnjArray(res)
    }

    fun getKeyWordsFromDescription(desc: String): List<String> {
        val res = mutableListOf<String>()
        for (group in groups.keys) {
            val regex = Regex("\\\$$group\\\$(.*?)\\\$$group\\\$")
            val matches = regex.findAll(desc)
            val keyWords = matches.map { it.groupValues[1].lowercase() }.toList()
            res.addAll(descriptions.filter { it.value.first == group && it.key in keyWords }.map { it.key })
        }
        return res
    }
}