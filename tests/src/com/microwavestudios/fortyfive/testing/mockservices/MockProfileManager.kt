package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.profile.IProfile
import com.microwavestudios.fortyfive.profile.IProfileManager
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.testing.notAvailableInMock

class MockProfileManager() : IProfileManager {

    override var currentProfile: IProfile? = null

    override val availableProfiles: List<Profile.Preview> = listOf()

    override fun init() {
    }

    override fun deselectProfile() {
    }

    override fun selectProfile(preview: Profile.Preview): Boolean = notAvailableInMock()

    override fun rereadPreviews() {
    }

    override fun reloadPreviews() {
    }
}
