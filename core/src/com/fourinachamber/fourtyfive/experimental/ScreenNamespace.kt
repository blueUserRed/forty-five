package com.fourinachamber.fourtyfive.experimental

import onj.customization.Namespace.OnjNamespace
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

@OnjNamespace
object ScreenNamespace {

    @RegisterOnjFunction(
        schema = "params: [{...*}, {...*}[]] "
    )
    fun children(obj: OnjObject, arr: OnjArray): OnjObject {
        val pairs = obj.value.toMutableMap()
        pairs["children"] = arr
        return when (obj) {
            is OnjNamedObject -> OnjNamedObject(obj.name, pairs)
            else -> OnjObject(pairs)
        }
    }

}