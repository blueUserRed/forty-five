package com.microwavestudios.fortyfive.profile

class ProfileManager {

    var currentProfile: Profile? = null
        private set

    lateinit var availableProfiles: List<Profile.Preview>
        private set


    fun init() {
        availableProfiles = listOf(
            Profile.loadPreview("A"),
            Profile.loadPreview("B"),
            Profile.loadPreview("C"),
        )
    }

    fun deselectProfile() {
        currentProfile?.let {
            it.write()
            it.writeMaps()
        }
        currentProfile = null
        rereadPreviews()
    }

    /**
     * @return true when the profile was loaded successfully
     */
    fun selectProfile(preview: Profile.Preview): Boolean {
        if (!preview.loadedSuccessfully) return false
        deselectProfile()
        val loaded = Profile.loadProfile(preview.name)
        currentProfile = loaded
        return loaded != null
    }

    fun rereadPreviews() {
        availableProfiles.filter { it.loadedSuccessfully }.forEach { it.read() }
    }

    fun reloadPreviews() {
        availableProfiles = listOf(
            Profile.loadPreview("A"),
            Profile.loadPreview("B"),
            Profile.loadPreview("C"),
        )
    }

}
