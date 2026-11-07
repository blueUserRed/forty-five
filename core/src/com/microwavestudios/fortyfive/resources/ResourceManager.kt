package com.microwavestudios.fortyfive.resources

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.collections.map
import kotlin.reflect.KClass

/**
 * this interface only exists to mark classes, it has no functions. Classes
 * that implement this interface manage resources and ensure the resources that they
 * borrowed are given back once they're not needed anymore
 */
interface ResourceBorrower

/**
 * a string that refers to a resource as defined in the
 * `assets/config/assets.onj` file
 */
typealias ResourceHandle = String

/**
 * globally manages all resources used by the game.
 * The `assets/config/assets.onj` file contains definitions for all available resources.
 * The resource manager loads resources when they are needed, and frees them again once they
 * are not needed anymore.
 *
 * A resource can be borrowed using various functions, like [request] or [forceGet].
 * The ResourceManager uses Lifetimes (see [Lifetime]) to know how long a resource is borrowed for.
 * It guarantees that the resource will be valid for as long as the lifetime is alive. A resource may
 * stay valid for longer (e.g. if another part of the code also borrows it), but this can't be relied
 * upon.
 *
 * **Important:**
 *
 * **Never call dispose on a resource that you got from the ResourceManager!** The ResourceManager will
 * do that automatically once the lifetime ends. Keep in mind that even if you don't need the
 * resource anymore, other parts of the game might have borrowed it as well!
 *
 * Each resource can have different 'variants', which all reference the same resource, but
 * have a different type. For example, variants of a Texture are TextureRegion or
 * TextureRegionDrawable. Because all variants use the same underlying data, they are all
 * loaded and unloaded simultaneously and they all share a common handle. Functions for
 * borrowing resources have a generic type parameter that is used to select a variant.
 *
 * Whenever possible, the ResourceManager will perform heavy operations on the [ServiceThread],
 * to avoid blocking the render thread. However, because only the render thread can communicate
 * with the GPU, large chunks of the resource loading process still need to be performed on the render
 * thread.
 */
class ResourceManager {

    val resourceFactory: ResourceFactory by lazy { ResourceFactory() }

    /** list of all available resources */
    lateinit var resources: List<Resource>
        private set

    /** all directories containing cards, null for mainGame, plugin name for the plugin directory */
    lateinit var cardDirectories: Map<String?, String>
        private set

    /** listeners that get called if the game closes with resources still loaded (useful for tests) */
    private val resourceLeakListeners: MutableList<(String) -> Unit> = mutableListOf()

    /**
     * Loads the resource with the specified [handle], if it isn't already loaded, and returns it.
     * Note that this function will block the render thread for the time it takes to load the resource.
     * Whenever possible, use [request] instead
     */
    inline fun <reified T : Any> forceGet(borrower: ResourceBorrower, lifetime: Lifetime, handle: ResourceHandle) =
        forceGet(borrower, lifetime, handle, T::class)

    /**
     * Takes a [promise] that was returned from [request] and forces it to resolve immediately.
     * The function will block the render thread for the time it takes the resource to finish
     * loading, so it should only be used when absolutely necessary.
     */
    fun forceResolve(promise: Promise<*>) {
        val resource = resources.find { it.promiseMatches(promise) }
            ?: throw RuntimeException("no resource with matching promise found")
        resource.forceResolve()
    }

    /**
     * Loads the resource with the specified [handle], if it isn't already loaded, and returns it.
     * Note that this function will block the render thread for the time it takes to load the resource.
     * Whenever possible, use [request] instead
     */
    fun <T : Any> forceGet(borrower: ResourceBorrower, lifetime: Lifetime, handle: ResourceHandle, type: KClass<T>): T {
        val resource = resources.find { it.combinedHandle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        return resource.forceGet(borrower, lifetime, type)
    }

    /**
     * requests the resource with the handle [handle]. The resource will be loaded in the background
     * and the returned [Promise] will resolve once the resource finished loading. If the resource
     * was already loaded when the function was called, a promise that is already resolved may be
     * returned.
     */
    inline fun <reified T : Any> request(
        borrower: ResourceBorrower,
        lifetime: Lifetime,
        handle: ResourceHandle
    ): Promise<T> =
        request(borrower, lifetime, handle, T::class)

    /**
     * requests the resource with the handle [handle]. The resource will be loaded in the background
     * and the returned [Promise] will resolve once the resource finished loading. If the resource
     * was already loaded when the function was called, a promise that is already resolved may be
     * returned.
     */
    fun <T : Any> request(
        borrower: ResourceBorrower,
        lifetime: Lifetime,
        handle: ResourceHandle,
        type: KClass<T>
    ): Promise<T> {
        val resource = resources.find { it.combinedHandle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        return resource.request(borrower, lifetime, type)
    }

    fun giveBack(borrower: ResourceBorrower, handle: ResourceHandle) {
        val toGiveBack = resources.find { it.combinedHandle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toGiveBack.giveBack(borrower)
    }

    fun findCardFileOrError(namespace: String?, cardName: String, postfix: String?): File {
        val cardDir = cardDirectories[namespace] ?: throw RuntimeException("no card directory for namespace $namespace")
        val fileName = postfix?.let { "$cardName-$it.png" } ?: "$cardName.png"
        val file = if (namespace == null) {
            File(cardDir).resolve(fileName)
        } else {
            val plugin = FortyFive.pluginManager.findActivePlugin(namespace)
                ?: throw RuntimeException("no plugin with name $namespace")
            plugin.directory.resolve(cardDir).resolve(fileName)
        }
        if (!file.exists()) {
            throw RuntimeException("couldn't find card texture at: $file")
        }
        return file
    }

    /**
     * add listeners that gets called if the game closes with resources still loaded (useful for tests)
     */
    fun addResourceLeakListener(listener: (String) -> Unit) {
        resourceLeakListeners.add(listener)
    }

    fun init() {
        val resources = mutableListOf<Resource>()
        val cardDirectories = mutableMapOf<String?, String>()

        val assetFiles = mutableMapOf<String?, OnjObject>()
        assetFiles[null] = ConfigFileManager.getConfigFile("assets")
        val plugins = FortyFive.pluginManager.activePlugins
        plugins.forEach { plugin ->
            ConfigFileManager.getConfigFile(plugin.name, "assets")?.let { assetFiles[plugin.name] = it }
        }

        assetFiles.forEach { (namespace, file) ->
            file.value.forEach { (key, value) ->
                if (key == "cardDir") {
                    cardDirectories[namespace] = value.value as String
                    return@forEach
                }
                require(value is OnjArray) { "keys in assets files must have array values" }
                val res = resourceFactory.createResourceFromOnjArray(key, namespace, value)
                resources.addAll(res)
            }
        }

        this.cardDirectories = cardDirectories
        cardDirectories.forEach { (from, directory) ->
            Gdx.files.internal(directory)
                .file()
                .walk()
                .filter { it.isFile }
                .forEach {
                    val resource = TextureResource(
                        from,
                        "${Card.cardTexturePrefix}${it.nameWithoutExtension}",
                        it.path,
                        false,
                        1f,
                        false,
                        null
                    )
                    resources.add(
                        resource
                    )
                }
        }

        this.resources = resources
    }

    fun end() {
        val message = StringBuilder()
        for (resource in resources) {
            if (resource.handle.startsWith(Card.cardTexturePrefix)) continue
            if (resource.state == Resource.ResourceState.NOT_LOADED) continue
            if (resource.stayLoaded) continue
            message.append("resource $resource was still loaded when the Game closed!\n")
            for (borrower in resource.borrowedBy) message.append("is borrowed by: $borrower\n")
        }
        resources.forEach {
            if (it.state != Resource.ResourceState.NOT_LOADED) it.dispose()
        }
        if (message.isEmpty()) return
        val messageString = message.toString()
        resourceLeakListeners.forEach { it(messageString) }
        FortyFive.logger.warn(
            logTag, "Resources were loaded when the game closed. This could " +
                "be indicative of a memory leak. Summary:")
        FortyFive.logger.dump(FortyFiveLogger.LogLevel.MEDIUM, messageString)
    }

    companion object {
        const val DROP_SHADOW_END = "_drop_shadow"
        private const val logTag = "ResourceManager"
    }

}
