package com.example.flamingoandroid.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Flamingo colour tokens ───────────────────────────────────────────
private val OceanNight      = Color(0xFF0B1628)
private val DeepSurface     = Color(0xFF13203A)
private val RaisedSurface   = Color(0xFF1C2E4A)
private val WarmAmber       = Color(0xFFF59B35)
private val CoralSunset     = Color(0xFFE8603A)
private val LagoonTeal      = Color(0xFF2EC4B6)
private val PearlWhite      = Color(0xFFF0EEE8)
private val MutedMist       = Color(0xFF9DB4C0)
private val CrimsonAlert    = Color(0xFFE63946)
private val GlassBorder     = Color(0x1AF59B35)  // 10% amber

/**
 * Animated stock card for the Inventory screen — Jetpack Compose.
 *
 * Behaviours:
 *  • Spring-scale press feedback (feels physical)
 *  • Tap to expand/collapse a detail panel with a spring transition
 *  • Stock progress bar animated via tween on first composition
 *  • Glassmorphism surface with neumorphic shadows
 *  • Colour-coded status badge (Teal / Amber / Crimson)
 *
 * Usage:
 *   AnimatedStockCard(
 *     name     = "Eau minérale",
 *     category = "Boissons",
 *     stock    = 24,
 *     minStock = 10,
 *     unit     = "bouteilles",
 *     price    = 1.5f
 *   )
 */
@Composable
fun AnimatedStockCard(
    name:     String,
    category: String,
    stock:    Int,
    minStock: Int,
    unit:     String = "unités",
    price:    Float? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring scale: compress slightly on press, bounce back
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh,
        ),
        label = "cardScale",
    )

    // Stock fill fraction (clamped)
    val maxStock   = maxOf(minStock * 3, stock, 1).toFloat()
    val fillTarget = (stock.toFloat() / maxStock).coerceIn(0f, 1f)

    val fillAnim by animateFloatAsState(
        targetValue  = fillTarget,
        animationSpec = tween(durationMillis = 900, easing = { t -> 1 - (1 - t) * (1 - t) }),
        label = "stockFill",
    )

    val (statusLabel, statusColor) = stockStatus(stock, minStock)

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (expanded) 18.dp else 10.dp,
                shape     = RoundedCornerShape(20.dp),
                spotColor = OceanNight,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(DeepSurface, Color(0xFF14243E)),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end   = androidx.compose.ui.geometry.Offset(400f, 400f),
                )
            )
            // Amber glass border
            .background(
                brush = Brush.linearGradient(listOf(GlassBorder, Color.Transparent)),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null) {
                expanded = !expanded
            }
            .padding(18.dp),
    ) {
        Column {

            // ── Header row ─────────────────────────────────────────
            Row(
                modifier      = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(WarmAmber.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = WarmAmber,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text  = category.uppercase(),
                            color = MutedMist,
                            fontSize  = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text  = name,
                            color = PearlWhite,
                            fontSize  = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text  = statusLabel,
                        color = statusColor,
                        fontSize  = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Stock quantity + progress ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text  = "$stock",
                        color = PearlWhite,
                        fontSize  = 30.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text  = " / $minStock min",
                        color = MutedMist,
                        fontSize  = 13.sp,
                        modifier  = Modifier.padding(bottom = 3.dp),
                    )
                }

                if (price != null) {
                    Text(
                        text  = "${"%.2f".format(price)} DT",
                        fontSize  = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarmAmber,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Animated progress bar
            LinearProgressIndicator(
                progress = { fillAnim },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50.dp)),
                color           = statusColor,
                trackColor      = RaisedSurface,
                strokeCap       = StrokeCap.Round,
            )

            // ── Expandable detail panel ─────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness    = Spring.StiffnessMedium,
                    )
                ) + fadeIn(tween(180)),
                exit    = shrinkVertically(tween(160)) + fadeOut(tween(120)),
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Divider
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, GlassBorder, Color.Transparent)
                                )
                            )
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        DetailItem(label = "Unité",    value = unit)
                        DetailItem(label = "Quantité", value = "$stock")
                        DetailItem(label = "Seuil",    value = "$minStock")
                        if (price != null) {
                            DetailItem(label = "Prix", value = "${"%.2f".format(price)} DT")
                        }
                    }
                }
            }

            // Expand / collapse icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Réduire" else "Détails",
                    tint     = MutedMist,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MutedMist,   fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value, color = PearlWhite,  fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun stockStatus(stock: Int, minStock: Int): Pair<String, Color> = when {
    stock <= 0         -> "Épuisé"    to CrimsonAlert
    stock <= minStock  -> "Stock bas" to WarmAmber
    else               -> "En stock"  to LagoonTeal
}
