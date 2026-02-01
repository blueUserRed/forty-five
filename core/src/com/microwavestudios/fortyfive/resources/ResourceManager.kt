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

    /** list of all available resources */
    lateinit var resources: List<Resource>
        private set

    /** list of all available fonts */
    lateinit var fonts: List<FontGroup>
        private set

    /** all directories containing cards, null for mainGame, plugin name for the plugin directory */
    lateinit var cardDirectories: Map<String?, String>
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

    fun findCardFileOrError(namespace: String?, cardName: String): File {
        val cardDir = cardDirectories[namespace] ?: throw RuntimeException("no card directory for namespace $namespace")
        val file = if (namespace == null) {
            File(cardDir).resolve("$cardName.png")
        } else {
            val plugin = FortyFive.pluginManager.findActivePlugin(namespace)
                ?: throw RuntimeException("no plugin with name $namespace")
            plugin.directory.resolve(cardDir).resolve("$cardName.png")
        }
        if (!file.exists()) {
            throw RuntimeException("couldn't find card texture at: $file")
        }
        return file
    }

    private fun collectResources(): CollectedResources {
        val pluginAssets = FortyFive.pluginManager.collectAssetFiles()
        val assets = ConfigFileManager.getConfigFile("assets")

        fun collectToMap(key: String): List<Pair<String?, OnjObject>> = pluginAssets
            .associateTo(mutableMapOf<String?, List<OnjObject>>()) { (name, file) ->
                name to file.get<OnjArray>(key).value.map { it as OnjObject }
            }
            .also { map ->
                map[null] = assets.get<OnjArray>(key).value.map { it as OnjObject }
            }
            .flatMap { (key, value) -> value.map { key to it } }

        return CollectedResources(
            collectToMap("textures"),
            collectToMap("fonts"),
            collectToMap("pixmapFonts"),
            collectToMap("textureAtlases"),
            collectToMap("cursors"),
            collectToMap("shaders"),
            collectToMap("colorTextures"),
            collectToMap("particles"),
            collectToMap("ninepatches"),
            collectToMap("frameAnimations"),
            collectToMap("sounds"),
            collectToMap("music"),
            pluginAssets
                .associateTo(mutableMapOf<String?, String>()) { (name, file) ->
                    name to file.access<String>(".cards.directory")
                }
                .also { it[null] = assets.access<String>(".cards.directory") }
        )
    }

    private data class CollectedResources(
        val textures: List<Pair<String?, OnjObject>>,
        val fonts: List<Pair<String?, OnjObject>>,
        val pixmapFonts: List<Pair<String?, OnjObject>>,
        val textureAtlases: List<Pair<String?, OnjObject>>,
        val cursors: List<Pair<String?, OnjObject>>,
        val shaders: List<Pair<String?, OnjObject>>,
        val colorTextures: List<Pair<String?, OnjObject>>,
        val particles: List<Pair<String?, OnjObject>>,
        val ninepatches: List<Pair<String?, OnjObject>>,
        val frameAnimations: List<Pair<String?, OnjObject>>,
        val sounds: List<Pair<String?, OnjObject>>,
        val music: List<Pair<String?, OnjObject>>,
        val cardDirectories: Map<String?, String>
    )

    fun init() {
        val resources = mutableListOf<Resource>()
//        val assets = ConfigFileManager.getConfigFile("assets")
        val collected = collectResources()

        collected.textures.forEach { (from, texture) ->
            val name = texture.get<String>("name")
            val dropShadowData = texture
                .getOr<OnjObject?>("dropShadow", null)
                ?.let { TextureResource.DropShadowData.fromOnj(it) }
            val resource = TextureResource(
                from,
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
                from,
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
        collected.fonts.forEach { (from, font) ->
            val fontName = font.get<String>("name")
            val variants = mutableListOf<FontVariant>()
            font.get<OnjArray>("variants").value.forEach { variant ->
                variant as OnjObject
                val resourceHandle = variant.get<ResourceHandle>("resourceHandle")
                val fontFile = variant.get<ResourceHandle>("fontFile")
                val imageFile = variant.get<ResourceHandle>("imageFile")
                val size = variant.get<Long>("size").toInt()
                val resource = FontResource(from, resourceHandle, imageFile, fontFile, false)
                resource.stayLoaded = font.getOr("stayLoaded", false)
                resources.add(resource)
                val variant = FontVariant(resourceHandle, size)
                variants.add(variant)
            }
            fonts.add(FontGroup(fontName, variants))
        }
        this.fonts = fonts

        collected.pixmapFonts.forEach { (from, font) ->
            val resource = PixmapFontResource(
                from,
                font.get<String>("name"),
                font.get<String>("fontFile")
            )
            resource.stayLoaded = font.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.textureAtlases.forEach { (from, obj) ->
            val name = obj.get<String>("name")
            val file = obj.get<String>("file")
            val atlasResource = AtlasResource(from, name, file)
            atlasResource.stayLoaded = obj.getOr("stayLoaded", false)
            val regionResources = obj.get<OnjArray>("regions").value.map {
                it as OnjObject
                val handle = it.get<String>("handle")
                val regionName = it.get<String>("regionName")
                val resource = AtlasRegionResource(from, handle, regionName, name)
                resource.stayLoaded = it.getOr("stayLoaded", false)
                resource
            }
            resources.add(atlasResource)
            resources.addAll(regionResources)
        }

        collected.cursors.forEach { (from, cursor) ->
            val resource = CursorResource(
                from,
                cursor.get<String>("name"),
                cursor.get<String>("file"),
                cursor.get<Long>("hotspotX").toInt(),
                cursor.get<Long>("hotspotY").toInt()
            )
            resource.stayLoaded = cursor.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.shaders.forEach { (from, shader) ->
            val resource = ShaderResource(
                from,
                shader.get<String>("name"),
                shader.get<String>("file"),
                shader.get<OnjObject>("constantArgs").value.entries.associate { (key, value) ->
                    "ca_$key" to value.value as Any
                }
            )
            resource.stayLoaded = shader.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.colorTextures.forEach { (from, texture) ->
            val resource = ColorTextureResource(
                from,
                texture.get<String>("name"),
                texture.get<Color>("color")
            )
            resource.stayLoaded = texture.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.particles.forEach { (from, particle) ->
            val resource = ParticleResource(
                from,
                particle.get<String>("name"),
                particle.get<String>("file"),
                particle.get<String>("textureDir"),
                particle.get<Double>("scale").toFloat()
            )
            resource.stayLoaded = particle.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.ninepatches.forEach { (from, ninepatch) ->
            val resource = NinepatchResource(
                from,
                ninepatch.get<String>("name"),
                ninepatch.get<String>("file"),
                ninepatch.get<Long>("left").toInt(),
                ninepatch.get<Long>("right").toInt(),
                ninepatch.get<Long>("top").toInt(),
                ninepatch.get<Long>("bottom").toInt(),
                ninepatch.getOr("scale", 1.0).toFloat()
            )
            resource.stayLoaded = ninepatch.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.frameAnimations.forEach { (from, anim) ->
            val resource = DeferredFrameAnimationResource(
                from,
                anim.get<String>("name"),
                anim.get<String>("preview"),
                anim.get<String>("atlas"),
                anim.get<Long>("frameTime").toInt()
            )
            resource.stayLoaded = anim.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.sounds.forEach { (from, sound) ->
            val resource = SoundResource(
                from,
                sound.get<String>("name"),
                sound.get<String>("file")
            )
            resource.stayLoaded = sound.getOr("stayLoaded", false)
            resources.add(resource)
        }

        collected.music.forEach { (from, music) ->
            val resource = MusicResource(
                from,
                music.get<String>("name"),
                music.get<String>("file")
            )
            resource.stayLoaded = music.getOr("stayLoaded", false)
            resources.add(resource)
        }

        cardDirectories = collected.cardDirectories
        collected.cardDirectories.forEach { (from, directory) ->
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
