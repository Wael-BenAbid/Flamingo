package com.example.flamingoandroid.presentation.util

import android.content.Context
import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.example.flamingoandroid.R
import java.text.NumberFormat
import java.util.Locale

fun initials(value: String?, fallback: String = "O"): String {
    val cleaned = value?.trim().orEmpty()
    if (cleaned.isBlank()) return fallback
    return cleaned
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { fallback }
}

fun formatMoney(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.FRANCE)
    return "${formatter.format(amount)} DT"
}

fun TextView.bindReservationStatus(context: Context, status: String) {
    text = status.uppercase(Locale.getDefault())
    val (bg, fg) = when (status.lowercase(Locale.getDefault())) {
        "confirmed" -> R.color.success to R.color.white
        "pending" -> R.color.warning to R.color.white
        "cancelled" -> R.color.error to R.color.white
        "absent" -> R.color.text_secondary to R.color.white
        else -> R.color.ocean_light to R.color.ocean_dark
    }
    ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, bg)))
    setTextColor(ContextCompat.getColor(context, fg))
}

fun TextView.bindPresenceStatus(context: Context, status: String) {
    text = status.uppercase(Locale.getDefault())
    val (bg, fg) = when (status.lowercase(Locale.getDefault())) {
        "present" -> R.color.success to R.color.white
        "absent" -> R.color.error to R.color.white
        "half" -> R.color.warning to R.color.white
        "off" -> R.color.text_secondary to R.color.white
        else -> R.color.ocean_light to R.color.ocean_dark
    }
    ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, bg)))
    setTextColor(ContextCompat.getColor(context, fg))
}

fun TextView.bindInventoryLevel(context: Context, quantity: Int, minStock: Int) {
    text = quantity.toString()
    val lowStock = quantity <= minStock
    val bg = if (lowStock) R.color.error else R.color.ocean_light
    val fg = if (lowStock) R.color.white else R.color.ocean_dark
    ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, bg)))
    setTextColor(ContextCompat.getColor(context, fg))
}
