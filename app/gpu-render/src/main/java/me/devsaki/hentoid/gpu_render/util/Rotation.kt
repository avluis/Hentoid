package me.devsaki.hentoid.gpu_render.util

enum class Rotation {
    NORMAL, ROTATION_90, ROTATION_180, ROTATION_270;

    /**
     * Retrieves the int representation of the Rotation.
     *
     * @return 0, 90, 180 or 270
     */
    open fun asInt(): Int {
        return when (this) {
            NORMAL -> 0
            ROTATION_90 -> 90
            ROTATION_180 -> 180
            ROTATION_270 -> 270
            else -> throw IllegalStateException("Unknown Rotation!")
        }
    }

    /**
     * Create a Rotation from an integer. Needs to be either 0, 90, 180 or 270.
     *
     * @param rotation 0, 90, 180 or 270
     * @return Rotation object
     */
    open fun fromInt(rotation: Int): Rotation? {
        return when (rotation) {
            0 -> NORMAL
            90 -> ROTATION_90
            180 -> ROTATION_180
            270 -> ROTATION_270
            360 -> NORMAL
            else -> throw IllegalStateException(
                "$rotation is an unknown rotation. Needs to be either 0, 90, 180 or 270!"
            )
        }
    }
}