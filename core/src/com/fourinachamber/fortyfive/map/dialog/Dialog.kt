package com.fourinachamber.fortyfive.map.dialog

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
            val text = AdvancedText.readFromOnj(onj.get<OnjArray>("text"), screen, defaults)
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
                else -> throw RuntimeException()
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

}
