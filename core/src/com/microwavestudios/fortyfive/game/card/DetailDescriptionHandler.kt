package com.microwavestudios.fortyfive.game.card

import com.badlogic.gdx.graphics.Color
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.utils.AdvancedTextParser
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import onj.value.OnjValue

object DetailDescriptionHandler {

    private val groups: Map<String, Color>
    private val _allTextEffects: OnjArray
    val descriptions: Map<String, Pair<String, String>>  // keyword   then    group with description

    val allTextEffects: List<AdvancedTextParser.AdvancedTextEffect> by lazy {
        _allTextEffects.value.map {
            AdvancedTextParser.AdvancedTextEffect.getFromOnj(it as OnjNamedObject)
        }
    }

    private val keywordRegexes: Map<String, Regex> by lazy {
        groups.mapValues { Regex("\\\$${it.key}\\\$(.*?)\\\$${it.key}\\\$") }
    }

    init {
        val onj = ConfigFileManager.getConfigFile("descriptions")
        val pluginFiles = FortyFive.pluginManager.collectDescriptionFiles()
        val allFiles = pluginFiles.map { it.second } + onj
        groups = allFiles
            .flatMap { it.get<OnjArray>("hoverDetailDescriptionGroups").value }
            .filterIsInstance<OnjObject>()
            .associate { it.get<String>("name") to it.get<Color>("color") }
        _allTextEffects = getAllTextEffects(
            allFiles
                .flatMap { it.get<OnjArray>("defaultTextEffects").value }
        )
        descriptions = allFiles
            .flatMap { it.get<OnjArray>("hoverDetailDescriptions").value }
            .filterIsInstance<OnjObject>()
            .associate {
                it.get<String>("keyword").lowercase() to (it.get<String>("groupName") to it.get<String>("description"))
            }
    }

    private fun getAllTextEffects(default: List<OnjValue>): OnjArray {
        val res = mutableListOf<OnjValue>()
        for (i in groups) {
            res.add(AdvancedTextParser.AdvancedTextEffect.AdvancedColorTextEffect("\$${i.key}$", i.value).asOnjObject())
        }
        res.addAll(default)
        return OnjArray(res)
    }

    fun getKeyWordsFromDescription(desc: String): List<String> {
        val res = mutableListOf<String>()
        for (group in groups.keys) {
            val regex = keywordRegexes[group]!!
            val matches = regex.findAll(desc)
            val keyWords = matches.map { it.groupValues[1].lowercase() }.toList()
            res.addAll(descriptions.filter { it.value.first == group && it.key in keyWords }.map { it.key })
        }
        return res
    }

    fun extractAllExtraDescriptions(descriptions: List<String>): List<String> {
        val addedDescriptions = mutableSetOf<String>()
        val texts = mutableListOf<String>()
        val allKeys = descriptions.flatMap { getKeyWordsFromDescription(it) }
        allKeys.forEach { key ->
            if (key in addedDescriptions) return@forEach
            addedDescriptions.add(key)
            DetailDescriptionHandler.descriptions[key]?.let { texts.add(it.second) }
        }
        while (true) {
            val keywords = texts.flatMap { text ->
                getKeyWordsFromDescription(text)
            }
            var addedText = false
            keywords.forEach { keyword ->
                if (keyword in addedDescriptions) return@forEach
                addedDescriptions.add(keyword)
                addedText = true
                DetailDescriptionHandler.descriptions[keyword]?.let { texts.add(it.second) }
            }
            if (!addedText) break
        }
        return texts
    }
}