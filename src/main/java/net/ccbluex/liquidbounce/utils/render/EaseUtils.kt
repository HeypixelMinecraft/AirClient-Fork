package net.ccbluex.liquidbounce.utils.render

object EaseUtils {
    @JvmStatic
    fun easeOutQuart(x: Double): Double {
        return 1 - Math.pow(1 - x, 4.0)
    }

    @JvmStatic
    fun easeInQuart(x: Double): Double {
        return x * x * x * x
    }

    @JvmStatic
    fun easeInOutQuart(x: Double): Double {
        return if (x < 0.5) 8 * x * x * x * x else 1 - Math.pow(-2 * x + 2, 4.0) / 2
    }

    @JvmStatic
    fun easeOutCubic(x: Double): Double {
        return 1 - Math.pow(1 - x, 3.0)
    }

    @JvmStatic
    fun easeInCubic(x: Double): Double {
        return x * x * x
    }

    @JvmStatic
    fun easeInOutCubic(x: Double): Double {
        return if (x < 0.5) 4 * x * x * x else 1 - Math.pow(-2 * x + 2, 3.0) / 2
    }

    @JvmStatic
    fun easeOutElastic(x: Double): Double {
        val c4 = (2 * Math.PI) / 3
        return if (x == 0.0) 0.0 else if (x == 1.0) 1.0 else Math.pow(2.0, -10 * x) * Math.sin((x * 10 - 0.75) * c4) + 1
    }

    @JvmStatic
    fun easeOutBack(x: Double): Double {
        val c1 = 1.70158
        val c3 = c1 + 1
        return 1 + c3 * Math.pow(x - 1, 3.0) + c1 * Math.pow(x - 1, 2.0)
    }

    @JvmStatic
    fun easeInBack(x: Double): Double {
        val c1 = 1.70158
        val c3 = c1 + 1
        return c3 * x * x * x - c1 * x * x
    }
}
