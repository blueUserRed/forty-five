package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.fourinachamber.fortyfive.keyInput.KeyInputMap
import com.fourinachamber.fortyfive.keyInput.selection.FocusableParent
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenCreator
import com.fourinachamber.fortyfive.utils.percent

class CustomBoxPlaygroundScreen : ScreenCreator() {

    override val name: String = "healOrMaxHPScreen"

    val worldWidth = 1600f
    val worldHeight = 900f

    override val background: String = "background_bewitched_forest"

    override val viewport: Viewport = FitViewport(worldWidth, worldHeight)

    override val playAmbientSounds: Boolean = false

    override val transitionAwayTimes: Map<String, Int> = mapOf(
        "*" to 1000
    )

    override fun getInputMaps(): List<KeyInputMap> = listOf(
        loadInputMap("defaultInputMap", screen)
    )

    override fun getSelectionHierarchyStructure(): List<FocusableParent> = listOf()

    override fun getScreenControllers(): List<ScreenController> = listOf(
//        HealOrMaxHPScreenController(screen)
    )

    override fun getRoot(): Group = newGroup {
            x = 0f
            y = 0f
            width = worldWidth
            height = worldHeight

            image {
                x = 0f
                y = 0f
                width = worldWidth
                height = worldHeight
                backgroundHandle = "transparent_black_texture"
            }
           val b = box {
                x = worldWidth.percent(26)
                y = worldHeight.percent(100 - 80 - 13.5) //old Position Top
                width = worldWidth.percent(48)
                height = worldHeight.percent(80)
                backgroundHandle = "heal_or_max_background"
                debug = true
                horizontalAlign = CustomAlign.SPACE_BETWEEN
                verticalAlign = CustomAlign.SPACE_AROUND
                flexDirection = FlexDirection.ROW_REVERSE

                wrap = CustomWrap.WRAP_REVERSE
                minHorizontalDistBetweenElements = 5F
//                minVerticalDistBetweenElements = 10F
//               paddingLeft=10F
//               paddingRight=30F
//               paddingTop=20F
//               paddingBottom=100F

                val size = 150F
                box {
                    width = size
                    height = size
                    backgroundHandle = "card%%leadersBullet"
                    debug = true
                }
                box {
                    width = size
                    height = size
                    backgroundHandle = "card%%bigBullet"
                    debug = true
                    fixedZIndex=20
                }

                for (i in 0 until 7){
                    box {
                        name("bigBulletTestName2")
                        width = size
                        height = size
                        backgroundHandle = "card%%bullet"
                        debug = true
                        marginTop=10F
                        fixedZIndex = 10
                    }
                }

                box {
                    name("bigBulletTestName")
                    width = size
                    height = size
                    backgroundHandle = "card%%workerBullet"
                    debug = true
                }

                box {
                    width = 100F
                    height = 100F
                    backgroundHandle = "background_bewitched_forest"
                    positionType = PositionType.ABSOLUTE
                    x=400F
                    y=500F
                    debug = true
                    fixedZIndex=5
                }
            }
            screen.afterMs(3000){
                val c = screen.namedActorOrError("bigBulletTestName") as CustomBox //this only takes the last one
                c.fixedZIndex=20
                b.invalidate()
            }

            screen.afterMs(4000){
                val c = screen.namedActorOrError("bigBulletTestName2") as CustomBox
                c.fixedZIndex=0
                b.invalidate()
            }
        }
}
