package com.microwavestudios.fortyfive.profile

import com.microwavestudios.fortyfive.FortyFive

interface IProfileManager {

    val currentProfile: IProfile?
    val availableProfiles: List<Profile.Preview>

    fun init()

    fun deselectProfile()

    /**
     * @return true when the profile was loaded successfully
     */
    fun selectProfile(preview: Profile.Preview): Boolean

    fun rereadPreviews()

    fun reloadPreviews()

}

class ProfileManager : IProfileManager {

    override var currentProfile: IProfile? = null
        private set

    override lateinit var availableProfiles: List<Profile.Preview>
        private set


    override fun init() {
        availableProfiles = listOf(
            Profile.loadPreview("A"),
            Profile.loadPreview("B"),
            Profile.loadPreview("C"),
        )
    }

    override fun deselectProfile() {
        FortyFive.logger.debug(logTag, "deselect profile ${currentProfile?.name}")
        currentProfile?.write()
        currentProfile = null
        rereadPreviews()
    }

    override fun selectProfile(preview: Profile.Preview): Boolean {
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

    override fun rereadPreviews() {
        availableProfiles.filter { it.loadedSuccessfully }.forEach { it.read() }
    }

    override fun reloadPreviews() {
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
