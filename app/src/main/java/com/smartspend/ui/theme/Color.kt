package com.smartspend.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val PrimaryLight = Color(0xFF2E5C8A)
val PrimaryDark  = Color(0xFFA8C8EC)
val SecondaryLight = Color(0xFF565F71)
val SecondaryDark  = Color(0xFFBEC6DC)

// Status colors (budget/bill states)
val StatusSafe    = Color(0xFF1B8E5A)   // green
val StatusWarning = Color(0xFFE08A1E)   // orange
val StatusDanger  = Color(0xFFD64545)   // red
val StatusOver    = Color(0xFF9B1C1C)   // deep red

val SurfaceLight = Color(0xFFFBFCFF)
val SurfaceDark  = Color(0xFF1A1C1E)

val BackgroundLight = Color(0xFFF5F7FB)
val BackgroundDark  = Color(0xFF101214)

// Khata
val GivenColor    = Color(0xFFD64545)   // red = money out
val ReceivedColor = Color(0xFF1B8E5A)   // green = money in

// Category palette (used when generating random colors for new custom categories)
val CategoryPalette = listOf(
    Color(0xFF7F77DD), Color(0xFFD85A30), Color(0xFF639922), Color(0xFFD4537E),
    Color(0xFFE24B4A), Color(0xFFBA7517), Color(0xFF378ADD), Color(0xFFEF9F27),
    Color(0xFF1D9E75), Color(0xFF85B7EB), Color(0xFF888780), Color(0xFF5F5E5A)
)
