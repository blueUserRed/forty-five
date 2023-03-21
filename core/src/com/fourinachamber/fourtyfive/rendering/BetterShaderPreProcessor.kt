package com.fourinachamber.fourtyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger.LogLevel
import com.fourinachamber.fourtyfive.utils.eitherLeft
import com.fourinachamber.fourtyfive.utils.eitherRight

class BetterShaderPreProcessor(
    private val fileHandle: FileHandle,
    private val constantArgs: Map<String, Any>
) {

    private val uniformsToBind: MutableList<String> = mutableListOf()

    fun preProcess(): Either<Pair<String /*=Vertex*/, String /*=Fragment*/>, String /*=export*/> {
        val text = fileHandle.file().readText(Charsets.UTF_8)
        val lines = text.split("\n").toMutableList()
        val sections = splitIntoSections(lines)
        return if (sections.containsKey("export")) {
            sections["export"]!!.joinToString(separator = "\n").eitherRight()
        } else {
            val fragment = processCode(sections["fragment"]!!)
            val vertex = processCode(sections["vertex"]!!)
            (vertex to fragment).eitherLeft()
        }
    }

    fun compile(code: Pair<String, String>): BetterShader {
        val shader = ShaderProgram(code.first, code.second)
        if (shader.isCompiled) return BetterShader(shader, uniformsToBind)
        FourtyFiveLogger.severe(logTag, "compilation of shader ${fileHandle.name()} failed")
        FourtyFiveLogger.dump(LogLevel.SEVERE, shader.log, "log")
        FourtyFiveLogger.dump(LogLevel.SEVERE, code.first, "pre-processed vertex shader")
        FourtyFiveLogger.dump(LogLevel.SEVERE, code.second, "pre-processed fragment shader")
        throw RuntimeException("compilation of shader ${fileHandle.name()} failed")
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
        if ("export" !in sections.keys && sections.size != 2) {
            throw RuntimeException("file ${fileHandle.name()} must define both a vertex and a fragment section")
        }
        return sections
    }

    private fun processCode(lines: List<String>): String = lines
        .map { line ->
            if (line.startsWith("%uniform")) {
                val uniformName = line.substringAfter("%uniform").trim()
                val uniformType = getUniformTypeOrError(uniformName)
                uniformsToBind.add(uniformName)
                return@map "uniform $uniformType $uniformName;"
            } else if (line.startsWith("%constArg")) {
                val definition = line.substringAfter("%constArg").trim()
                val parts = definition.split(" ")
                if (parts.size != 2) {
                    throw RuntimeException("expected constArg declaration in shader ${fileHandle.name()} to" +
                            "have a name and a type separated by a space")
                }
                val value = getConstArgValueOrError(parts[0], parts[1])
                return@map "#define ${parts[0]} $value"
            } else if (line.startsWith("%include")) {
                val toInclude = line.substringAfter("%include").trim()
                return@map include(toInclude)
            } else {
                return@map line
            }
        }
        .joinToString(separator = "\n")

    private fun include(toInclude: String): String {
        val preProcessor = BetterShaderPreProcessor(Gdx.files.internal(toInclude), mapOf())
        val result = preProcessor.preProcess()
        if (result !is Either.Right) {
            throw RuntimeException("shader ${preProcessor.fileHandle.name()} is not meant for being included")
        }
        return result.value
    }

    private fun getUniformTypeOrError(uniform: String): String = uniforms[uniform]
        ?: throw RuntimeException("unknown uniform $uniform in shader ${fileHandle.name()}")

    private fun getConstArgValueOrError(constArg: String, type: String): String {
        val arg = constantArgs[constArg]
            ?: throw RuntimeException("unknown constArg $constArg in shader ${fileHandle.name()}")

        return when (type) {

            "float" -> {
                if (arg !is Number) throw RuntimeException("expected constArg $constArg to be a number")
                arg.toDouble().toString()
            }

            "int" -> {
                if (arg !is Number) throw RuntimeException("expected constArg $constArg to be a number")
                arg.toLong().toString()
            }

            "vec4" -> {
                if (arg !is Collection<*> || arg.size != 4) {
                    throw RuntimeException("expected constArg $constArg to be a collection of floats of size 4")
                }
                val vec = StringBuilder("vec4(")
                arg.forEachIndexed { i, f ->
                    vec.append(f.toString())
                    if (i != 3) vec.append(", ")
                }
                vec.append(")")
                vec.toString()
            }

            "color" -> {
                if (arg !is Color) throw RuntimeException("expected constArg $constArg to be a color")
                return "vec4(${arg.r}, ${arg.g}, ${arg.b}, ${arg.a})"
            }

            else -> throw RuntimeException("unknown type $type in constArg declaration in shader ${fileHandle.name()}")

        }
    }

    companion object {

        const val sectionMarker = "~~~section "
        const val logTag = "BetterShaderPreProcessor"

        private val uniforms: Map<String, String> = mapOf(
            "u_time" to "float",
            "u_resolution" to "vec2"
        )

    }

}