package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.TimeUtils
import onj.parser.OnjParser
import onj.value.OnjObject
import kotlin.reflect.KProperty

class TemplateString(
    var rawString: String,
    additionalParams: Map<String, Any>? = null
) {

    private var lastUpdateTime: Long = 0

    private var stringElements: List<() -> String>

    init {
        val builderElements = mutableListOf<() -> String>()
        var cur = 0
        var openBracePosition = -1
        var lastClosingBracePosition = 0
        while (cur < rawString.length) {
            val c = rawString[cur]
            if (c == '{') {
                if (openBracePosition != -1) {
                    cur++
                    continue
                }
                openBracePosition = cur + 1
                val string = rawString.substring(lastClosingBracePosition, cur)
                if (string != "") {
                    builderElements.add { string }
                }
            }
            if (c == '}') {
                if (openBracePosition == -1) {
                    cur++
                    continue
                }
                val identifier = rawString.substring(openBracePosition, cur)
                lastClosingBracePosition = cur + 1
                openBracePosition = -1
                if (additionalParams != null && identifier in additionalParams) {
                    val param = additionalParams[identifier]!!.toString()
                    builderElements.add { param }
                } else {
                    builderElements.add { getParam(identifier) }
                }
            }
            cur++
        }
        if (lastClosingBracePosition != rawString.length - 1) {
            val string = rawString.substring(lastClosingBracePosition)
            builderElements.add { string }
        }
        stringElements = builderElements
    }

    var string: String = rawString
        private set
        get() {
            if (lastUpdateTime > lastParamUpdateTimeStamp) return field
            field = stringElements
                .joinToString(separator = "") { it() }
            lastUpdateTime = TimeUtils.millis()
            return field
        }

    private fun getParam(param: String): String {
        return globalParams[param]?.toString() ?: "{}"
    }

    override fun toString(): String = rawString

    companion object {

        private var lastParamUpdateTimeStamp = TimeUtils.millis()

        private val globalParams: MutableMap<String, Any?> = mutableMapOf()

        fun updateGlobalParam(param: String, value: Any?) {
            globalParams[param] = value
            lastParamUpdateTimeStamp = TimeUtils.millis()
        }

        const val colorFilePath: String = "imports/colors.onj"

        fun init() {
            val onj = OnjParser.parseFile(Gdx.files.internal(colorFilePath).file())
            // who needs schemas anyway?
            onj as OnjObject
            onj.value.forEach { (key, value) ->
                val color = value.value as Color
                updateGlobalParam("c.$key", "[#$color]")
            }
            updateGlobalParam("c.reset", "[]")
        }

    }

    class TemplateStringParamDelegate<T>(
        val paramName: String,
        initialValue: T,
        val onSet: ((T) -> Unit)? = null,
        val additionalBinds: Map<String, (T) -> Any?>
    ) {

        init {
            updateGlobalParam(paramName, initialValue)
            for ((name, transform) in additionalBinds) {
                updateGlobalParam(name, transform(initialValue))
            }
        }

        private var backingField: T = initialValue

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return backingField
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            backingField = value
            onSet?.invoke(value)
            updateGlobalParam(paramName, value)
            for ((name, transform) in additionalBinds) {
                updateGlobalParam(name, transform(value))
            }
        }

    }

}

fun <T> templateParam(
    paramName: String,
    initialValue: T,
    onSet: ((T) -> Unit)? = null
): TemplateString.TemplateStringParamDelegate<T> {
    return TemplateString.TemplateStringParamDelegate(paramName, initialValue, onSet, mapOf())
}

fun <T> multipleTemplateParam(
    paramName: String,
    intitialValue: T,
    vararg additionals: Pair<String, (T) -> Any?>
): TemplateString.TemplateStringParamDelegate<T> {
    return TemplateString.TemplateStringParamDelegate(
        paramName,
        intitialValue,
        null,
        additionals.associate { it }
    )
}
