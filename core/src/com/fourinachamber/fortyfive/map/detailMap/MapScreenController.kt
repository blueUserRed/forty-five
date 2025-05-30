package com.fourinachamber.fortyfive.map.detailMap

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*

class MapScreenController : ScreenController() {

    override fun init(context: Any?) {
        FortyFive.soundPlayer.changeMusicTo(SoundPlayer.Theme.MAIN)
        PermaSaveState.visitedNewArea(MapManager.currentDetailMap.name)
    }
}
