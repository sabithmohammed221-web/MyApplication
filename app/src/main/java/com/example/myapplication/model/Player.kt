package com.example.myapplication.model

import androidx.compose.ui.graphics.Color

data class Player(
    val name: String,
    val color: Color
)

val ludoPlayers = listOf(
    Player("Red", Color(0xFFE53935)),
    Player("Green", Color(0xFF43A047)),
    Player("Yellow", Color(0xFFFFC107)),
    Player("Cyan", Color(0xFF00ACC1))
)
