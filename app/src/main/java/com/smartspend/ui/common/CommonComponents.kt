package com.smartspend.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspend.data.models.BudgetProgress
import com.smartspend.ui.theme.statusColor
import java.text.NumberFormat
import java.util.Locale

/** Formats a double as ₹ currency string, e.g. ₹1,234.50 or ₹1.2L for large values */
fun formatCurrency(amount: Double, compact: Boolean = false): String {
    val abs = kotlin.math.abs(amount)
    return when {
        compact && abs >= 1_00_00_000 -> "₹%.2fCr".format(amount / 1_00_00_000)
        compact && abs >= 1_00_000 -> "₹%.2fL".format(amount / 1_00_000)
        compact && abs >= 1_000 -> "₹%.1fK".format(amount / 1_000)
        else -> {
            val fmt = NumberFormat.getInstance(Locale("en", "IN"))
            fmt.maximumFractionDigits = 2
            fmt.minimumFractionDigits = 0
            "₹${fmt.format(amount)}"
        }
    }
}

/** Animated linear progress bar with status color coding */
@Composable
fun BudgetLinearProgress(
    progress: Float,
    status: BudgetProgress.Status,
    modifier: Modifier = Modifier,
    height: Int = 8
) {
    val animated by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(800), label = "progress"
    )
    val color = statusColor(status)

    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

/** Animated circular progress ring, used on budget summary cards */
@Composable
fun BudgetCircularProgress(
    progress: Float,
    status: BudgetProgress.Status,
    modifier: Modifier = Modifier,
    sizeDp: Int = 64,
    strokeDp: Int = 7,
    centerContent: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(900), label = "circularProgress"
    )
    val color = statusColor(status)

    Box(modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            val stroke = Stroke(width = strokeDp.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width),
                topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width),
                topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2)
            )
        }
        centerContent()
    }
}

@Composable
private fun Canvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}

/** Status chip — Pending / Paid / Overdue / Upcoming, or any label+color combo */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Emoji avatar in a colored circle — used for categories, persons, bills */
@Composable
fun EmojiAvatar(emoji: String, colorHex: String, sizeDp: Int = 44, modifier: Modifier = Modifier) {
    val color = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
    Box(
        modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (sizeDp * 0.42f).sp)
    }
}

/** Premium empty-state block with icon, title, subtitle */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Skeleton loading placeholder shimmer block */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    )
}
