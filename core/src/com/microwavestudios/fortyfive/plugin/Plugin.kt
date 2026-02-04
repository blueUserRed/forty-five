package com.microwavestudios.fortyfive.plugin

interface Plugin {

    fun earlyInit() {}

    fun start() {}

    fun onRender() {}

    fun onEnd() {}

}
