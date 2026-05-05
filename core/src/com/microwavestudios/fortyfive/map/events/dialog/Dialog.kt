package com.microwavestudios.fortyfive.map.events.dialog

import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.map.MapPredicate
import com.microwavestudios.fortyfive.screen.commonComponents.AdvancedText
import com.microwavestudios.fortyfive.screen.RenderableScreen
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject


data class Dialog(
    val parts: List<DialogPart>,
    val name: String
) {

    fun partWithLabel(label: String): DialogPart? = parts.find { it.label == label }

    companion object {

        fun readFromOnj(onj: OnjObject, screen: RenderableScreen): Dialog {
            val defaults = onj.get<OnjObject>("defaults")
            val parts = onj.get<OnjArray>("parts")
                .value
                .map { readDialogPart(it as OnjObject, defaults, screen) }
            return Dialog(parts, onj.get<String>("name"))
        }

        private fun readDialogPart(onj: OnjObject, defaults: OnjObject, screen: RenderableScreen): DialogPart {
            val text = AdvancedText.readFromOnj(
                onj.get<String>("rawText"),
                onj.get<OnjArray?>("effects"),
                screen,
                defaults
            )
            val label = onj.getOr<String?>("label", null)
            val nextSelector = onj.get<OnjNamedObject>("next")
            val leftNpc = onj.get<String?>("leftNpc")
            val rightNpc = onj.get<String?>("rightNpc")
            val talkingNpc = onj.get<String>("talkingNpc")

            if (talkingNpc != leftNpc && talkingNpc != rightNpc) {
                throw RuntimeException("talkingNpc $talkingNpc is neither the left or the right npc")
            }

            val leftNpcTalking = talkingNpc == leftNpc

            val next = when (nextSelector.name) {

                "Continue" -> NextDialogPartSelector.Continue

                "ToCreditScreenEnd" -> NextDialogPartSelector.ToCreditScreenEnd

                "EndOfDialog" -> NextDialogPartSelector.End

                "FixedNextPart" -> NextDialogPartSelector.Fixed(nextSelector.get<String>("next"))

                "ChooseNextPart" -> NextDialogPartSelector.Choice(
                    nextSelector
                        .get<OnjArray>("choices")
                        .value
                        .map { it as OnjObject }
                        .associate {
                            it.get<String>("name") to it.get<String>("next")
                        }
                )

                "ByPredicate" -> NextDialogPartSelector.ByPredicate(
                    MapPredicate.fromOnj(nextSelector.get<OnjNamedObject>("predicate")),
                    nextSelector.get<String>("ifTrue"),
                    nextSelector.get<String>("ifFalse"),
                )

                "MatchPredicate" -> NextDialogPartSelector.MatchPredicate(
                    nextSelector
                        .get<OnjArray>("options")
                        .value
                        .map {
                            it as OnjObject
                            MapPredicate.fromOnj(it.get<OnjNamedObject>("predicate")) to it.get<String>("label")
                        },
                    nextSelector.get<String>("default")
                )

                "StartSpecialRunEnd" -> NextDialogPartSelector.StartSpecialRunEnd(
                    nextSelector.get<String>("run")
                )

                "AddSpecialRunEnd" -> NextDialogPartSelector.AddSpecialRunEnd(
                    nextSelector.get<String>("run")
                )

                "GiftCardEnd" -> NextDialogPartSelector.GiftCardEnd(
                    CardType.fromOnj(nextSelector.get<OnjObject>("card"))
                )

                else -> throw RuntimeException("unknown next dialog part selector: ${nextSelector.name}")
            }
            return DialogPart(text, next, leftNpc, rightNpc, label, leftNpcTalking)
        }
    }
}

data class DialogPart(
    val text: AdvancedText,
    val nextDialogPartSelector: NextDialogPartSelector,
    val leftNpc: String?,
    val rightNpc: String?,
    val label: String?,
    val leftNpcTalking: Boolean,
)

sealed class NextDialogPartSelector {

    data object Continue : NextDialogPartSelector()

    class Fixed(val next: String) : NextDialogPartSelector()

    class Choice(
        val choices: Map<String, String>
    ) : NextDialogPartSelector()

    class ByPredicate(
        val predicate: MapPredicate,
        val ifTrue: String,
        val ifFalse: String
    ) : NextDialogPartSelector()

    class MatchPredicate(
        val options: List<Pair<MapPredicate, String>>,
        val default: String
    ) : NextDialogPartSelector()

    data object End : NextDialogPartSelector()

    class GiftCardEnd(val card: CardType) : NextDialogPartSelector()

    class StartSpecialRunEnd(val run: String) : NextDialogPartSelector()

    class AddSpecialRunEnd(val run: String) : NextDialogPartSelector()

    data object ToCreditScreenEnd : NextDialogPartSelector()

}
