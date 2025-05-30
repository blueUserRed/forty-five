package com.fourinachamber.fortyfive.screen.screenBuilder

import com.fourinachamber.fortyfive.screen.general.OnjScreen

interface ScreenBuilder {

    val name: String

    fun build(controllerContext: Any? = null, previousScreen: OnjScreen?): OnjScreen

}