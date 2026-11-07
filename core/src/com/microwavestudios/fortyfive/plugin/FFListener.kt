package com.microwavestudios.fortyfive.plugin

@PluginExtensionPoint
interface FFListener {

    fun earlyInit() {}

    fun start() {}

    fun render() {}

    fun end() {}

}
