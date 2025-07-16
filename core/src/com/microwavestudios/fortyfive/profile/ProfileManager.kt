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

    fun selectProfile(preview: Profile.Preview) {
        deselectProfile()
        currentProfile = Profile.loadProfile(preview.name)
    }

    fun rereadPreviews() {
        availableProfiles.forEach { it.read() }
    }

}
