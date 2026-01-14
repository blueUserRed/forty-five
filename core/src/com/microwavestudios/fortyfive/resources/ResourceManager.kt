package com.microwavestudios.fortyfive.resources

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
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

    /** list of all available resources */
    lateinit var resources: List<Resource>
        private set

    /** list of all available fonts */
    lateinit var fonts: List<FontGroup>
        private set

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
        val resource = resources.find { it.handle == handle }
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
        val resource = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        return resource.request(borrower, lifetime, type)
    }

    fun giveBack(borrower: ResourceBorrower, handle: ResourceHandle) {
        val toGiveBack = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toGiveBack.giveBack(borrower)
    }

    fun init() {
        val resources = mutableListOf<Resource>()
        val assets = ConfigFileManager.getConfigFile("assets")

        assets.get<OnjArray>("textures").value.forEach { texture ->
            texture as OnjObject
            val name = texture.get<String>("name")
            val dropShadowData = texture
                .getOr<OnjObject?>("dropShadow", null)
                ?.let { TextureResource.DropShadowData.fromOnj(it) }
            val resource = TextureResource(
                name,
                texture.get<String>("file"),
                texture.getOr("tileable", false),
                texture.getOr("tileScale", 1.0).toFloat(),
                texture.getOr("useMipMaps", true),
                dropShadowData
            )
            resource.stayLoaded = texture.getOr("stayLoaded", false)
            resources.add(resource)

            dropShadowData ?: return@forEach
            val resourceDropShadow = TextureResource(
                name + DROP_SHADOW_END,
                "drop_shadows/$name$DROP_SHADOW_END.png",
                false,
                1f,
                false,
                null
            )
            resourceDropShadow.stayLoaded = texture.getOr("stayLoaded", false)
            resources.add(resourceDropShadow)
        }

        val fonts = mutableListOf<FontGroup>()
        assets.get<OnjArray>("fonts").value.forEach { font ->
            font as OnjObject
            val fontName = font.get<String>("name")
            val variants = mutableListOf<FontVariant>()
            font.get<OnjArray>("variants").value.forEach { variant ->
                variant as OnjObject
                val resourceHandle = variant.get<ResourceHandle>("resourceHandle")
                val fontFile = variant.get<ResourceHandle>("fontFile")
                val imageFile = variant.get<ResourceHandle>("imageFile")
                val size = variant.get<Long>("size").toInt()
                val resource = FontResource(resourceHandle, imageFile, fontFile, false)
                resource.stayLoaded = font.getOr("stayLoaded", false)
                resources.add(resource)
                val variant = FontVariant(resourceHandle, size)
                variants.add(variant)
            }
            fonts.add(FontGroup(fontName, variants))
        }
        this.fonts = fonts

        assets.get<OnjArray>("pixmapFonts").value.forEach {
            it as OnjObject
            val resource = PixmapFontResource(
                it.get<String>("name"),
                it.get<String>("fontFile")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("textureAtlases").value.forEach { obj ->
            obj as OnjObject
            val name = obj.get<String>("name")
            val file = obj.get<String>("file")
            val atlasResource = AtlasResource(name, file)
            atlasResource.stayLoaded = obj.getOr("stayLoaded", false)
            val regionResources = obj.get<OnjArray>("regions").value.map {
                it as OnjObject
                val handle = it.get<String>("handle")
                val regionName = it.get<String>("regionName")
                val resource = AtlasRegionResource(handle, regionName, name)
                resource.stayLoaded = it.getOr("stayLoaded", false)
                resource
            }
            resources.add(atlasResource)
            resources.addAll(regionResources)
        }

        assets.get<OnjArray>("cursors").value.forEach {
            it as OnjObject
            val resource = CursorResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<Long>("hotspotX").toInt(),
                it.get<Long>("hotspotY").toInt()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("shaders").value.forEach {
            it as OnjObject
            val resource = ShaderResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<OnjObject>("constantArgs").value.entries.associate { (key, value) ->
                    "ca_$key" to value.value as Any
                }
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("colorTextures").value.forEach {
            it as OnjObject
            val resource = ColorTextureResource(
                it.get<String>("name"),
                it.get<Color>("color")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("particles").value.forEach {
            it as OnjObject
            val resource = ParticleResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<String>("textureDir"),
                it.get<Double>("scale").toFloat()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("ninepatches").value.forEach {
            it as OnjObject
            val resource = NinepatchResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<Long>("left").toInt(),
                it.get<Long>("right").toInt(),
                it.get<Long>("top").toInt(),
                it.get<Long>("bottom").toInt(),
                it.getOr("scale", 1.0).toFloat()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("frameAnimations").value.forEach {
            it as OnjObject
            val resource = DeferredFrameAnimationResource(
                it.get<String>("name"),
                it.get<String>("preview"),
                it.get<String>("atlas"),
                it.get<Long>("frameTime").toInt()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("sounds").value.forEach {
            it as OnjObject
            val resource = SoundResource(
                it.get<String>("name"),
                it.get<String>("file")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("music").value.forEach {
            it as OnjObject
            val resource = MusicResource(
                it.get<String>("name"),
                it.get<String>("file")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        val cardsFile = assets.access<String>(".cards.directory")
        Gdx.files.internal(cardsFile)
            .file()
            .walk()
            .filter { it.isFile }
            .forEach {
                resources.add(
                    TextureResource(
                    "${Card.cardTexturePrefix}${it.nameWithoutExtension}",
                    it.path,
                    false,
                    1f,
                    false,
                    null
                )
                )
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
        if (message.isEmpty()) return
        FortyFive.logger.warn(
            logTag, "Resources were loaded when the game closed. This could " +
                "be indicative of a memory leak. Summary:")
        FortyFive.logger.dump(FortyFiveLogger.LogLevel.MEDIUM, message.toString())
    }

    data class FontVariant(val resourceHandle: ResourceHandle, val size: Int)
    data class FontGroup(val name: String, val variants: List<FontVariant>)

    companion object {
        const val DROP_SHADOW_END = "_drop_shadow"
        private const val logTag = "ResourceManager"
    }

}
