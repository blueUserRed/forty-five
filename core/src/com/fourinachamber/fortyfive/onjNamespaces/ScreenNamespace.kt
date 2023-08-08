package com.fourinachamber.fortyfive.onjNamespaces

import com.badlogic.gdx.Input.Keys
import com.fourinachamber.fortyfive.keyInput.KeyInputCondition
import onj.builder.buildOnjObject
import onj.customization.Namespace.*
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.*
import kotlin.reflect.KClass

@Suppress("unused") // values are read via reflection
@OnjNamespace
object ScreenNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "KeyInputCondition" to OnjKeyInputCondition::class
    )

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

    @RegisterOnjFunction(schema = "params: []")
    fun always(): OnjKeyInputCondition = OnjKeyInputCondition(KeyInputCondition.Always)

    @RegisterOnjFunction(schema = "params: [string]")
    fun screenState(state: OnjString): OnjKeyInputCondition =
        OnjKeyInputCondition(KeyInputCondition.ScreenState(state.value))

    @RegisterOnjFunction(
        schema = "use Screen; params: [KeyInputCondition, KeyInputCondition]",
        type = OnjFunctionType.INFIX
    )
    fun or(first: OnjKeyInputCondition, second: OnjKeyInputCondition): OnjKeyInputCondition =
        OnjKeyInputCondition(KeyInputCondition.Or(first.value, second.value))

    @RegisterOnjFunction(
        schema = "use Screen; params: [KeyInputCondition, KeyInputCondition]",
        type = OnjFunctionType.INFIX
    )
    fun and(first: OnjKeyInputCondition, second: OnjKeyInputCondition): OnjKeyInputCondition =
        OnjKeyInputCondition(KeyInputCondition.And(first.value, second.value))

    @RegisterOnjFunction(schema = "use Screen; params: [KeyInputCondition]")
    fun not(first: OnjKeyInputCondition): OnjKeyInputCondition =
        OnjKeyInputCondition(KeyInputCondition.Not(first.value))
}

class OnjKeyInputCondition(
    override val value: KeyInputCondition
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--key-input-condition--'")
    }
}
