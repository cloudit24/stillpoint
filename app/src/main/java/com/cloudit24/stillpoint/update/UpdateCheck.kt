package com.cloudit24.stillpoint.update

/**
 * Result of asking for a newer release. Shared by both build flavors:
 * `github` implements [Updater] against GitHub Releases, `fdroid` ships a no-op (F-Droid updates the app itself).
 */
sealed interface UpdateCheck {
    data class UpToDate(val current: String) : UpdateCheck
    data class Available(val version: String, val apkUrl: String) : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/** True when [candidate] ("1.2.0") is a higher dotted version than [current]. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
