package com.gowda.crimehunter

import android.graphics.RectF

enum class ScenePhase {
    TITLE,
    PLAYING,
    LEVEL_CLEAR,
    MISSION_FAILED,
    UPGRADES,
}

enum class ActorRole {
    ENEMY,
    CIVILIAN,
}

enum class UpgradeType(
    val label: String,
    val blurb: String,
) {
    MAGAZINE("Extended Mag", "+1 round before reload"),
    FAST_HANDS("Fast Hands", "Reload the rifle faster"),
    FOCUS("Hunter Focus", "Enemies slow down while aiming"),
    PRECISION("Precision Barrel", "Tighter shot spread"),
    PAYOUT("Bounty Chip", "More cash per takedown"),
}

data class Rooftop(
    val left: Float,
    val right: Float,
    val top: Float,
    val exitLeft: Float,
    val exitRight: Float,
    val accent: Int,
) {
    val centerX: Float get() = (left + right) * 0.5f
    val width: Float get() = right - left
}

data class Actor(
    val role: ActorRole,
    val roofIndex: Int,
    val radius: Float,
    val walkSpeed: Float,
    var x: Float,
    var y: Float,
    var targetX: Float,
    var reactionDelay: Float,
    var isAlive: Boolean = true,
    var isEscaped: Boolean = false,
    var animationTime: Float = 0f,
    var crouchAmount: Float = 0f,
)

data class Projectile(
    var x: Float,
    var y: Float,
    var previousX: Float,
    var previousY: Float,
    var vx: Float,
    var vy: Float,
    var life: Float = 1.5f,
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var life: Float,
    val color: Int,
)

data class UpgradeCard(
    val type: UpgradeType,
    val rect: RectF,
    val accent: Int,
)

data class PlayerStats(
    var magazineSize: Int = 3,
    var reloadTime: Float = 1.2f,
    var focusFactor: Float = 0.78f,
    var spreadRadians: Float = 0.026f,
    var bountyMultiplier: Float = 1f,
)
