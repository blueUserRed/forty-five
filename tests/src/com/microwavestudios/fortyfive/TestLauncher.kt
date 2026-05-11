package com.microwavestudios.fortyfive

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl.LwjglFiles
import com.microwavestudios.fortyfive.testing.mockservices.MockSoundPlayer
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.tests.BasicGameControllerTest


class TestLauncher {

    fun run() {
        Gdx.files = LwjglFiles()
        val profileManager = MockProfileManager()
        FortyFive.initTest(MockSoundPlayer(), profileManager)
        BasicGameControllerTest(profileManager).run()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            TestLauncher().run()
        }
    }

}
