package com.example.innertemp

enum class ActivityMode {
    TRAINING,
    RACE,
    CUSTOM
}

enum class Sport {
    RUNNING,
    CYCLING,
    KAYAKING
}

fun getTemperatureColorRanges(
    mode: ActivityMode,
    athleticLevel: String,
    customTop: Double? = null,
    customBottom: Double? = null
): Triple<Double, Double, Double> {
    var lowThreshold = 36.0
    var highThreshold = 38.0

    if (mode == ActivityMode.CUSTOM && customBottom != null && customTop != null) {
        lowThreshold = customBottom
        highThreshold = customTop
    } else {
        when (athleticLevel) {
            "Low" -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.0
                    highThreshold = 37.8
                } else {
                    lowThreshold = 37.5
                    highThreshold = 38.0
                }
            }
            "Medium" -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.2
                    highThreshold = 38.0
                } else {
                    lowThreshold = 37.8
                    highThreshold = 38.5
                }
            }
            "High" -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.5
                    highThreshold = 38.3
                } else {
                    lowThreshold = 38.0
                    highThreshold = 39.0
                }
            }
            else -> {
                if (mode == ActivityMode.RACE) {
                    lowThreshold = 37.2
                    highThreshold = 39.0
                } else {
                    lowThreshold = 37.2
                    highThreshold = 38.7
                }
            }
        }
    }
    val optimalTemp = (lowThreshold + highThreshold) / 2
    return Triple(lowThreshold, optimalTemp, highThreshold)
}

