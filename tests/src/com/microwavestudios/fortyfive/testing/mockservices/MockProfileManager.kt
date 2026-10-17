package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.profile.IProfileManager
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.testing.notAvailableInMock

class MockProfileManager(
    private val profileCreator: () -> List<MockProfile> = { listOf() }
) : IProfileManager {

    override var currentProfile: IProfile? = null

    override lateinit var availableProfiles: List<IProfile.IPreview>
        private set

    private lateinit var profiles: List<MockProfile>

    override fun init() {
        profiles = profileCreator()
        availableProfiles = profiles.map { it.constructPreview() }
    }

    override fun deselectProfile() {
        currentProfile = null
    }

    override fun selectProfile(preview: IProfile.IPreview): Boolean {
        val index = availableProfiles.indexOf(preview)
        require(index != -1) { "unknown preview: ${preview.name}" }
        currentProfile = profiles[index]
        return true
    }

    override fun rereadPreviews() {
    }

    override fun reloadPreviews() {
    }
}
