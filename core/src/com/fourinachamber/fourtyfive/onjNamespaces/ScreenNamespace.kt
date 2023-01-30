package com.fourinachamber.fourtyfive.onjNamespaces

import com.badlogic.gdx.Input.Keys
import onj.builder.buildOnjObject
import onj.customization.Namespace.*
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.OnjArray
import onj.value.OnjInt
import onj.value.OnjNamedObject
import onj.value.OnjObject

@Suppress("unused") // values are read via reflection
@OnjNamespace
object ScreenNamespace {

    @OnjNamespaceVariables
    val variables: Map<String, OnjObject> = mapOf(
        "keys" to buildOnjObject {
            for (code in 0..(Keys.MAX_KEYCODE)) {
                (Keys.toString(code) ?: continue) with OnjInt(code.toLong())
            }
        }
    )

    @RegisterOnjFunction(schema = "params: [{...*}, {...*}[]] ", OnjFunctionType.INFIX)
    fun children(obj: OnjObject, arr: OnjArray): OnjObject {
        val pairs = obj.value.toMutableMap()
        pairs["children"] = arr
        return when (obj) {
            is OnjNamedObject -> OnjNamedObject(obj.name, pairs)
            else -> OnjObject(pairs)
        }
    }

}