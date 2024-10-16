package com.fourinachamber.fortyfive.game.controller

import onj.value.OnjNamedObject


sealed class RevolverRotation {

    abstract val amount: Int

    abstract val directionString: String

    class Right(override val amount: Int) : RevolverRotation() {

        override val directionString: String = "right"

        override fun withAmount(amount: Int): RevolverRotation = Right(amount)

        override fun toString(): String = "Right($amount)"
    }
    class Left(override val amount: Int) : RevolverRotation() {

        override val directionString: String = "left"

        override fun withAmount(amount: Int): RevolverRotation = Left(amount)

        override fun toString(): String = "Left($amount)"
    }
    object None : RevolverRotation() {

        override val amount: Int = 0

        override val directionString: String = "none"

        override fun withAmount(amount: Int): RevolverRotation = None

        override fun toString(): String = "None"
    }

    abstract fun withAmount(amount: Int): RevolverRotation

    companion object {

        fun fromOnj(onj: OnjNamedObject): RevolverRotation = when (onj.name) {
            "Right" -> Right(onj.get<Long>("amount").toInt())
            "Left" -> Left(onj.get<Long>("amount").toInt())
            "Dont" -> None
            else -> throw RuntimeException("unknown revolver rotation: ${onj.name}")
        }

    }

}
