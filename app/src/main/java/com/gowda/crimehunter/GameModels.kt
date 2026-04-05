package com.gowda.crimehunter

import android.graphics.RectF

enum class ScenePhase {
    TITLE,
    OPTIONS,
    PLAYING,
    ROUND_CLEAR,
    ROUND_FAILED,
    UPGRADES,
}

enum class FighterSide {
    PLAYER,
    ENEMY,
}

enum class AttackType(
    val label: String,
    val damage: Float,
    val reach: Float,
    val startup: Float,
    val active: Float,
    val recovery: Float,
    val knockbackX: Float,
    val knockbackY: Float,
    val energyGain: Float,
    val cost: Float = 0f,
) {
    JAB("Jab", 10f, 82f, 0.05f, 0.08f, 0.15f, 130f, 120f, 10f),
    KICK("Kick", 16f, 108f, 0.08f, 0.11f, 0.2f, 180f, 170f, 14f),
    DASH("Dash", 14f, 122f, 0.04f, 0.09f, 0.24f, 220f, 170f, 16f),
    SPECIAL("Burst", 28f, 138f, 0.12f, 0.13f, 0.28f, 280f, 220f, 0f, 100f),
    ;

    val totalDuration: Float
        get() = startup + active + recovery
}

enum class UpgradeType(
    val label: String,
    val blurb: String,
) {
    POWER("Heavy Hands", "Punches and kicks hit harder."),
    FOOTWORK("Quick Steps", "Move faster across the stage."),
    SPRING("Sky Leap", "Jump higher and recover sooner."),
    SURGE("Aura Charge", "Build special energy faster."),
    SPIRIT("Iron Spirit", "Start each round with more health."),
}

data class AttackState(
    val type: AttackType,
    var timer: Float = 0f,
    var hasConnected: Boolean = false,
)

data class Fighter(
    val side: FighterSide,
    val name: String,
    var x: Float,
    var y: Float,
    var facing: Float,
    var color: Int,
    var accent: Int,
    var outline: Int,
    var maxHealth: Float,
    var health: Float,
    var energy: Float = 0f,
    var moveSpeed: Float,
    var jumpForce: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var onGround: Boolean = true,
    var attackState: AttackState? = null,
    var attackCooldown: Float = 0f,
    var stunRemaining: Float = 0f,
    var blockRemaining: Float = 0f,
    var hitFlash: Float = 0f,
    var comboCount: Int = 0,
    var comboTimer: Float = 0f,
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var life: Float,
    val color: Int,
)

data class UpgradeCard(
    val type: UpgradeType,
    val rect: RectF,
    val accent: Int,
)

data class PlayerStats(
    var maxHealth: Float = 120f,
    var moveSpeed: Float = 340f,
    var jumpForce: Float = 920f,
    var jabBonus: Float = 0f,
    var kickBonus: Float = 0f,
    var dashBonus: Float = 0f,
    var specialBonus: Float = 0f,
    var energyGainBonus: Float = 0f,
    var guardPower: Float = 0.12f,
)

data class GameOptions(
    var cinematicShake: Boolean = true,
    var hitPause: Boolean = true,
    var chillMode: Boolean = true,
)
