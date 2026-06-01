package com.splitpay.ui.theme

fun formatAmount(amount: Double): String {
    val abs = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        abs >= 1_000_000_000 -> "$sign${"%.1f".format(abs / 1_000_000_000)}B"
        abs >= 1_000_000     -> "$sign${"%.1f".format(abs / 1_000_000)}M"
        abs >= 1_000         -> "$sign${"%.1f".format(abs / 1_000)}K"
        else                 -> "$sign${"%.2f".format(abs)}"
    }
}
