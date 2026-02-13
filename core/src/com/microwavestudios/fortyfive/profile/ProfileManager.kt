package com.microwavestudios.fortyfive.profile

import com.microwavestudios.fortyfive.FortyFive

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
        FortyFive.logger.debug(logTag, "deselect profile ${currentProfile?.name}")
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
        if (!preview.loadedSuccessfully) {
            FortyFive.logger.warn(logTag, "can't select profile ${preview.name}")
            return false
        }
        deselectProfile()
        val loaded = Profile.loadProfile(preview.name)
        currentProfile = loaded
        if (loaded == null) {
            FortyFive.logger.warn(logTag, "failed loading profile ${preview.name}")
        } else {
            FortyFive.logger.debug(logTag, "loaded profile ${preview.name}")
        }
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

    companion object {
        private const val logTag = "ProfileManager"
    }

}
