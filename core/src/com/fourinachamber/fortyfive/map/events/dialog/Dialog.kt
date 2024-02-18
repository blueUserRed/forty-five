package com.fourinachamber.fortyfive.map.events.dialog

import com.fourinachamber.fortyfive.screen.general.AdvancedText
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject


data class Dialog(
    val parts: List<DialogPart>
) {

    companion object {

        fun readFromOnj(onj: OnjObject, screen: OnjScreen): Dialog {
            val defaults = onj.get<OnjObject>("defaults")
            val parts = onj.get<OnjArray>("parts")
                .value
                .map { readDialogPart(it as OnjObject, defaults, screen) }
            return Dialog(parts)
        }

        private fun readDialogPart(onj: OnjObject, defaults: OnjObject, screen: OnjScreen): DialogPart {
            val text = AdvancedText.readFromOnj(
                onj.get<String>("rawText"),
                true, // TODO: should be passed through the constructor, but because the dialog is usually small it's going to be distance field
                onj.get<OnjArray?>("effects"),
                screen,
                defaults
            )
            val nextSelector = onj.get<OnjNamedObject>("next")
            val next = when (nextSelector.name) {

                "Continue" -> NextDialogPartSelector.Continue

                "EndOfDialog" -> NextDialogPartSelector.End(nextSelector.get<String>("changeToScreen"))

                "FixedNextPart" -> NextDialogPartSelector.Fixed(nextSelector.get<Long>("next").toInt())

                "ChooseNextPart" -> NextDialogPartSelector.Choice(
                    nextSelector
                        .get<OnjArray>("choices")
                        .value
                        .map { it as OnjObject }
                        .associate {
                            it.get<String>("name") to it.get<Long>("next").toInt()
                        }
                )

                "GiftCardEnd" -> NextDialogPartSelector.GiftCardEnd(
                    nextSelector.get<String>("card"),
                    nextSelector.get<String>("changeToScreen"),
                )

                else -> throw RuntimeException("unknown next dialog part selector: ${nextSelector.name}")
            }
            return DialogPart(text, next)
        }

    }

}

data class DialogPart(
    val text: AdvancedText,
    val nextDialogPartSelector: NextDialogPartSelector
)

sealed class NextDialogPartSelector {

    object Continue : NextDialogPartSelector()

    class Fixed(val next: Int) : NextDialogPartSelector()

    class Choice(
        val choices: Map<String, Int>
    ) : NextDialogPartSelector()

    class End(val nextScreen: String) : NextDialogPartSelector()

    class GiftCardEnd(val card: String, val nextScreen: String) : NextDialogPartSelector()

}
