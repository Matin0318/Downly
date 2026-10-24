package app.downly.ui

import kotlin.math.roundToLong

fun formatBytes(bytes: Long, s: AppStrings): String {
    val units = s.byteUnits
    if (bytes < 1024) return s.digits("$bytes ${units[0]}")
    var value = bytes / 1024.0
    var i = 1
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    val tenths = (value * 10).roundToLong()
    return s.digits("${tenths / 10}${s.decimalSeparator}${tenths % 10} ${units[i]}")
}

fun formatSpeed(bytesPerSecond: Long, s: AppStrings): String = formatBytes(bytesPerSecond, s) + s.more.perSecond

fun formatDuration(totalSeconds: Long, s: AppStrings): String {
    val sec = totalSeconds.coerceAtLeast(0)
    val text = when {
        sec < 60 -> "$sec ${s.secondsUnit}"
        sec < 3600 -> "${sec / 60} ${s.minutesUnit}"
        else -> "${sec / 3600} ${s.hoursUnit}${s.and}${(sec % 3600) / 60} ${s.minutesUnit}"
    }
    return s.digits(text)
}

fun formatPercent(fraction: Float, s: AppStrings): String = s.digits("${(fraction * 100).toInt()}%")

/** "07:05" from minutes since midnight, with the digits of the current language. */
fun formatClock(minuteOfDay: Int, s: AppStrings): String {
    val m = minuteOfDay.coerceIn(0, 1439)
    return s.digits("${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}")
}
