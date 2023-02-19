package com.fourinachamber.fourtyfive.rendering

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger.LogLevel
import com.fourinachamber.fourtyfive.utils.eitherLeft
import com.fourinachamber.fourtyfive.utils.eitherRight

class BetterShaderPreProcessor(private val fileHandle: FileHandle) {

    fun preProcess(): Either<ShaderProgram, String> {
        val text = fileHandle.file().readText(Charsets.UTF_8)
        val lines = text.split("\n").toMutableList()
        val sections = splitIntoSections(lines)
        if (sections.containsKey("export")) {
            return sections["export"]!!.joinToString(separator = "\n").eitherRight()
        } else {
            val fragment = sections["fragment"]!!.joinToString(separator = "\n")
            val vertex = sections["vertex"]!!.joinToString(separator = "\n")
            val shader = ShaderProgram(vertex, fragment)
            if (shader.isCompiled) return shader.eitherLeft()
            FourtyFiveLogger.severe(logTag, "compilation of shader ${fileHandle.name()} failed")
            FourtyFiveLogger.dump(LogLevel.SEVERE, shader.log, "log")
            FourtyFiveLogger.dump(LogLevel.SEVERE, vertex, "pre-processed vertex shader")
            FourtyFiveLogger.dump(LogLevel.SEVERE, fragment, "pre-processed fragment shader")
            throw RuntimeException("compilation of shader ${fileHandle.name()} failed")
        }
    }

    private fun splitIntoSections(lines: MutableList<String>): MutableMap<String, MutableList<String>> {
        val sections: MutableMap<String, MutableList<String>> = mutableMapOf()
        var curSection = "__discarded"
        lines.groupByTo(sections) { line ->
            if (line.startsWith(sectionMarker)) {
                curSection = line.substringAfter(sectionMarker).trim()
                if (curSection !in arrayOf("vertex", "fragment", "export")) {
                    throw RuntimeException("invalid section $curSection in file ${fileHandle.name()}")
                }
                "__discarded"
            } else {
                curSection
            }
        }
        sections.remove("__discarded")
        if ("export" in sections.keys && sections.size != 1) {
            throw RuntimeException("file ${fileHandle.name()} defines sections in addition to the export section")
        }
        if ("vertex" in sections.keys && sections.size != 2) {
            throw RuntimeException("file ${fileHandle.name()} must define both a vertex and a fragment section")
        }
        return sections
    }

    companion object {

        const val sectionMarker = "~~~section "
        const val logTag = "BetterShaderPreProcessor"

    }

}