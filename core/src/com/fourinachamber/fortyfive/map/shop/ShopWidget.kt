package com.fourinachamber.fortyfive.map.shop

import com.badlogic.gdx.graphics.g2d.Batch
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import onj.value.OnjObject
import kotlin.math.max
import kotlin.random.Random

class ShopWidget(
    texture: String,
    dataFile: String,
    dataNamePath: String,
    dataPricePath: String,
    dataProbabilityPath: String,
    val maxPerLine: Int,
    val widthPercentagePerItem: Float,
    val screen: OnjScreen
) : CustomFlexBox(screen) {
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        children[0].width = width / 4
        children[0].height = height / 3
//        println("${width}   ${height}")
    }

    private val cardWithDefaultProbs = mapOf<String, OnjObject>()

    init {
        backgroundHandle = texture
    }


    public fun addItems(
        seed: Long,
        boughtIndices: List<Int>
    ) {//TODO indicies be careful, because multiple use same "space" (start with 0)
        val rnd = Random(seed)
        val nbrOfItems = (8)
        for (i in 0 until nbrOfItems) {
            val curItem = ShopItemWidget("enemy_texture", 10, screen, i)
            val node = this.add(curItem)
            val styleManager = StyleManager(curItem, node)
            curItem.styleManager = styleManager
            curItem.initStyles(screen)
            screen.addStyleManager(styleManager)
        }
    }

    override fun layout() {
        super.layout()

        if (children.isEmpty) return
        val distanceBetweenX = width * ((100 - (maxPerLine * widthPercentagePerItem)) / (maxPerLine + 1) / 100)
        val sizePerItem = width * widthPercentagePerItem / 100
        val distanceBetweenY = (height - 2 * sizePerItem) / 3
        for (i in 0 until children.size) {
            val child = children[i] as ShopItemWidget
            child.imgData[0] = sizePerItem
            child.imgData[1] = x + distanceBetweenX * (i % maxPerLine + 1) + sizePerItem * (i % maxPerLine)
            child.imgData[2] = y + height - distanceBetweenY * (i / maxPerLine + 1) - sizePerItem * (i / maxPerLine + 1)
        }
    }

}


class ShopItemWidget(texture: String, price: Long, screen: OnjScreen, val index: Int) :
    CustomFlexBox(screen) {

    val image: CustomImageActor
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        image.draw(batch, parentAlpha)
    }

    val imgData = FloatArray(3)

    override fun layout() {
        super.layout()
        image.width = imgData[0]
        image.height = imgData[0]
        image.x = imgData[1]
        image.y = imgData[2]
//        println("width: ${image.width}, ${image.x}")
    }

    init {
        image = CustomImageActor(texture, screen)
        val node = this.add(image)
        val styleManager = StyleManager(image, node)
        image.styleManager = styleManager
        image.initStyles(screen)
        screen.addStyleManager(styleManager)
    }
}
