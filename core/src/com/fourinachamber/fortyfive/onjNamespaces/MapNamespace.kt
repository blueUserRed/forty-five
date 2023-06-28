package com.fourinachamber.fortyfive.onjNamespaces

import com.fourinachamber.fortyfive.game.PermaSaveState
import onj.customization.Namespace.*
import onj.customization.OnjFunction.*
import onj.value.OnjInt

@OnjNamespace
object MapNamespace {

    @RegisterOnjFunction(schema = "params: [int]")
    fun runRandom(i: OnjInt): OnjInt {
        return OnjInt(PermaSaveState.runRandom(i.value.toInt()))
    }

}
