package com.gowda.crimehunter

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class CrimehunterGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private fun sp(value: Float) = value * density * resources.configuration.fontScale

    private data class ArenaPalette(
        val name: String,
        val skyTop: Int,
        val skyMid: Int,
        val skyWarm: Int,
        val skylineBack: Int,
        val skylineFront: Int,
        val floor: Int,
        val platform: Int,
        val glowA: Int,
        val glowB: Int,
    )

    private data class EnemyProfile(
        val title: String,
        val accent: Int,
        val healthScale: Float,
        val moveScale: Float,
        val jumpScale: Float,
        val blockBias: Float,
        val jumpBias: Float,
        val dashBias: Float,
        val kickBias: Float,
        val specialBias: Float,
        val styleNote: String,
    )

    private val random = Random(4412)
    private val prefs: SharedPreferences = context.getSharedPreferences("skyline_stick_clash", Context.MODE_PRIVATE)
    private val particles = mutableListOf<Particle>()
    private val upgradeCards = mutableListOf<UpgradeCard>()

    private var phase = ScenePhase.TITLE
    private var running = false
    private var layoutReady = false
    private var lastFrameNanos = 0L

    private var currentRound = 1
    private var coins = 0
    private var bestCombo = 0
    private var skylineTime = 0f
    private var roundTimer = 0f
    private var aiDecisionTimer = 0f
    private var freezeRemaining = 0f
    private var screenShake = 0f
    private var groundY = 0f
    private var arenaLeft = 0f
    private var arenaRight = 0f
    private var statusText = "Enter the arena."
    private var resultEyebrow = ""
    private var resultTitle = ""
    private var resultDescription = ""
    private var optionsReturnPhase = ScenePhase.TITLE
    private var roundIntroTimer = 0f
    private var roundIntroTitle = ""
    private var lastRoundPerfect = false
    private var championshipClears = 0
    private var highestRoundReached = 1
    private var lifetimeCoins = 0
    private var runsStarted = 0
    private var tutorialActive = true
    private var campaignComplete = false
    private var currentEnemyProfile = EnemyProfile(
        title = "Street Ace",
        accent = Color.parseColor("#FF5A52"),
        healthScale = 1f,
        moveScale = 1f,
        jumpScale = 1f,
        blockBias = 1f,
        jumpBias = 1f,
        dashBias = 1f,
        kickBias = 1f,
        specialBias = 1f,
        styleNote = "Balanced footwork and pressure.",
    )
    private var activeArena = ArenaPalette(
        name = "Skyline Rooftop",
        skyTop = Color.parseColor("#2A1847"),
        skyMid = Color.parseColor("#55306A"),
        skyWarm = Color.parseColor("#F58B5E"),
        skylineBack = Color.parseColor("#543563"),
        skylineFront = Color.parseColor("#1D1731"),
        floor = Color.parseColor("#120F20"),
        platform = Color.parseColor("#241A37"),
        glowA = Color.parseColor("#57D7FF"),
        glowB = Color.parseColor("#FF7187"),
    )

    private var player = Fighter(
        side = FighterSide.PLAYER,
        name = "Nova",
        x = 0f,
        y = 0f,
        facing = 1f,
        color = Color.WHITE,
        accent = Color.CYAN,
        outline = Color.BLACK,
        maxHealth = 120f,
        health = 120f,
        moveSpeed = 0f,
        jumpForce = 0f,
    )
    private var enemy = Fighter(
        side = FighterSide.ENEMY,
        name = "Riot",
        x = 0f,
        y = 0f,
        facing = -1f,
        color = Color.WHITE,
        accent = Color.RED,
        outline = Color.BLACK,
        maxHealth = 100f,
        health = 100f,
        moveSpeed = 0f,
        jumpForce = 0f,
    )

    private var stats = PlayerStats()
    private var options = GameOptions()

    private var moveLeftHeld = false
    private var moveRightHeld = false
    private var jumpHeld = false
    private var jabHeld = false
    private var kickHeld = false
    private var specialHeld = false

    private var playButton = RectF()
    private var optionsButton = RectF()
    private var optionsBackButton = RectF()
    private var primaryButton = RectF()
    private var secondaryButton = RectF()
    private var hudOptionsButton = RectF()
    private var shakeToggleButton = RectF()
    private var pauseToggleButton = RectF()
    private var chillToggleButton = RectF()
    private var leftButton = RectF()
    private var rightButton = RectF()
    private var jumpButton = RectF()
    private var jabButton = RectF()
    private var kickButton = RectF()
    private var specialButton = RectF()

    private val enemyNames = listOf("Riot", "Viper", "Echo", "Mako", "Volt", "Drift")
    private val enemyAccents = listOf(
        Color.parseColor("#FF5A52"),
        Color.parseColor("#FF8F41"),
        Color.parseColor("#FF3D7F"),
        Color.parseColor("#F35F3F"),
    )
    private val cardAccents = listOf(
        Color.parseColor("#7CF26A"),
        Color.parseColor("#58C5FF"),
        Color.parseColor("#FFD34F"),
    )
    private val arenaThemes = listOf(
        ArenaPalette(
            name = "Skyline Rooftop",
            skyTop = Color.parseColor("#2A1847"),
            skyMid = Color.parseColor("#55306A"),
            skyWarm = Color.parseColor("#F58B5E"),
            skylineBack = Color.parseColor("#543563"),
            skylineFront = Color.parseColor("#1D1731"),
            floor = Color.parseColor("#120F20"),
            platform = Color.parseColor("#241A37"),
            glowA = Color.parseColor("#57D7FF"),
            glowB = Color.parseColor("#FF7187"),
        ),
        ArenaPalette(
            name = "Sunset Overpass",
            skyTop = Color.parseColor("#142C58"),
            skyMid = Color.parseColor("#2E6BAA"),
            skyWarm = Color.parseColor("#FFB06A"),
            skylineBack = Color.parseColor("#496C9D"),
            skylineFront = Color.parseColor("#17314A"),
            floor = Color.parseColor("#111C2B"),
            platform = Color.parseColor("#203049"),
            glowA = Color.parseColor("#80F2FF"),
            glowB = Color.parseColor("#FFC266"),
        ),
        ArenaPalette(
            name = "Neon Storm",
            skyTop = Color.parseColor("#170F2E"),
            skyMid = Color.parseColor("#243A74"),
            skyWarm = Color.parseColor("#8B7DFF"),
            skylineBack = Color.parseColor("#3A458A"),
            skylineFront = Color.parseColor("#11182E"),
            floor = Color.parseColor("#0B1220"),
            platform = Color.parseColor("#1D2740"),
            glowA = Color.parseColor("#60F5D6"),
            glowB = Color.parseColor("#B36CFF"),
        ),
    )
    private val enemyProfiles = listOf(
        EnemyProfile(
            title = "Street Ace",
            accent = Color.parseColor("#FF6B61"),
            healthScale = 1f,
            moveScale = 1f,
            jumpScale = 1f,
            blockBias = 1f,
            jumpBias = 0.9f,
            dashBias = 1f,
            kickBias = 1f,
            specialBias = 1f,
            styleNote = "Balanced pressure and clean counters.",
        ),
        EnemyProfile(
            title = "Rushdown",
            accent = Color.parseColor("#FF944D"),
            healthScale = 0.94f,
            moveScale = 1.16f,
            jumpScale = 0.96f,
            blockBias = 0.8f,
            jumpBias = 1f,
            dashBias = 1.45f,
            kickBias = 0.9f,
            specialBias = 0.85f,
            styleNote = "Fast feet, frequent dash bursts.",
        ),
        EnemyProfile(
            title = "Sky Breaker",
            accent = Color.parseColor("#7FE3FF"),
            healthScale = 0.98f,
            moveScale = 0.98f,
            jumpScale = 1.22f,
            blockBias = 0.95f,
            jumpBias = 1.85f,
            dashBias = 0.92f,
            kickBias = 1.18f,
            specialBias = 0.92f,
            styleNote = "Leaps high and kicks from odd angles.",
        ),
        EnemyProfile(
            title = "Iron Guard",
            accent = Color.parseColor("#B586FF"),
            healthScale = 1.2f,
            moveScale = 0.9f,
            jumpScale = 0.88f,
            blockBias = 1.55f,
            jumpBias = 0.72f,
            dashBias = 0.8f,
            kickBias = 1.24f,
            specialBias = 1.1f,
            styleNote = "Heavier body, slower feet, more defense.",
        ),
    )
    private val maxCampaignRound = 12

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) {
                0f
            } else {
                (((now - lastFrameNanos) / 1_000_000_000f)).coerceIn(0f, 0.033f)
            }
            lastFrameNanos = now
            update(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        isFocusable = true
        isClickable = true
        loadProgress()
    }

    fun resumeGame() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        removeCallbacks(frameRunnable)
        postOnAnimation(frameRunnable)
    }

    fun pauseGame() {
        running = false
        removeCallbacks(frameRunnable)
        lastFrameNanos = 0L
    }

    fun handleBackPress(): Boolean {
        return when (phase) {
            ScenePhase.TITLE -> false
            ScenePhase.OPTIONS -> {
                closeOptions()
                true
            }

            else -> {
                resetToTitle()
                true
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (layoutReady) {
            resumeGame()
        }
    }

    override fun onDetachedFromWindow() {
        pauseGame()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutReady = w > 0 && h > 0
        groundY = h * 0.78f
        arenaLeft = w * 0.14f
        arenaRight = w * 0.86f
        rebuildUiLayout()
        if (phase == ScenePhase.PLAYING) {
            buildRound(currentRound, keepStats = true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (phase) {
            ScenePhase.TITLE -> handleTitleTouch(event)
            ScenePhase.OPTIONS -> handleOptionsTouch(event)
            ScenePhase.PLAYING -> handlePlayingTouch(event)
            ScenePhase.ROUND_CLEAR, ScenePhase.ROUND_FAILED -> handleResultTouch(event)
            ScenePhase.UPGRADES -> handleUpgradeTouch(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawArena(canvas)
        drawWorld(canvas)
        drawHud(canvas)
        drawOverlays(canvas)
    }

    private fun update(dt: Float) {
        skylineTime += dt
        screenShake = max(0f, screenShake - dt * 18f)

        if (freezeRemaining > 0f) {
            freezeRemaining = max(0f, freezeRemaining - dt)
            updateParticles(dt * 0.6f)
            return
        }

        if (phase != ScenePhase.PLAYING) {
            updateParticles(dt)
            return
        }

        roundTimer += dt
        aiDecisionTimer = max(0f, aiDecisionTimer - dt)

        if (roundIntroTimer > 0f) {
            roundIntroTimer = max(0f, roundIntroTimer - dt)
            updateParticles(dt)
            return
        }

        updateFighterTimers(player, dt)
        updateFighterTimers(enemy, dt)
        updatePlayer(dt)
        updateEnemyAi(dt)
        updateFighterMotion(player, dt)
        updateFighterMotion(enemy, dt)
        separateFighters()
        resolveAttack(player, enemy)
        resolveAttack(enemy, player)
        updateParticles(dt)

        if (enemy.health <= 0f) {
            finishRound(won = true)
        } else if (player.health <= 0f) {
            finishRound(won = false)
        }
    }

    private fun updateFighterTimers(fighter: Fighter, dt: Float) {
        fighter.hitFlash = max(0f, fighter.hitFlash - dt * 6f)
        fighter.attackCooldown = max(0f, fighter.attackCooldown - dt)
        fighter.stunRemaining = max(0f, fighter.stunRemaining - dt)
        fighter.blockRemaining = max(0f, fighter.blockRemaining - dt)
        fighter.comboTimer = max(0f, fighter.comboTimer - dt)
        if (fighter.comboTimer == 0f) {
            fighter.comboCount = 0
        }
        fighter.energy = min(100f, fighter.energy + dt * if (fighter.side == FighterSide.PLAYER) 5.5f else 4.2f)

        fighter.attackState?.let { attack ->
            attack.timer += dt
            if (attack.timer >= attack.type.totalDuration) {
                fighter.attackState = null
            }
        }
    }

    private fun updatePlayer(dt: Float) {
        if (player.stunRemaining > 0f) {
            return
        }

        val moveDirection =
            when {
                moveLeftHeld && !moveRightHeld -> -1f
                moveRightHeld && !moveLeftHeld -> 1f
                else -> 0f
            }

        if (player.attackState?.type == AttackType.DASH && player.attackState != null) {
            val dashSpeed = player.moveSpeed * 1.22f
            player.vx = player.facing * dashSpeed
        } else {
            val desired = moveDirection * player.moveSpeed
            player.vx = approach(player.vx, desired, player.moveSpeed * dt * 8f)
        }

        if (moveDirection != 0f) {
            player.facing = moveDirection
        }
    }

    private fun updateEnemyAi(dt: Float) {
        if (enemy.stunRemaining > 0f) {
            return
        }

        val bossRound = currentRound % 4 == 0
        val blockBias = currentEnemyProfile.blockBias * if (bossRound) 1.15f else 1f
        val jumpBias = currentEnemyProfile.jumpBias * if (bossRound) 1.1f else 1f
        val dashBias = currentEnemyProfile.dashBias * if (bossRound) 1.12f else 1f
        val kickBias = currentEnemyProfile.kickBias * if (bossRound) 1.08f else 1f
        val specialBias = currentEnemyProfile.specialBias * if (bossRound) 1.22f else 1f
        val distance = player.x - enemy.x
        val absDistance = abs(distance)
        enemy.facing = if (distance >= 0f) 1f else -1f

        if (enemy.attackState?.type == AttackType.DASH && enemy.attackState != null) {
            enemy.vx = enemy.facing * enemy.moveSpeed * 1.18f
            return
        }

        val desiredMovement =
            when {
                absDistance > dp(150f) -> enemy.facing * enemy.moveSpeed * 0.82f
                absDistance < dp(84f) && player.attackState == null -> -enemy.facing * enemy.moveSpeed * 0.38f
                else -> 0f
            }
        enemy.vx = approach(enemy.vx, desiredMovement, enemy.moveSpeed * dt * 7f)

        if (aiDecisionTimer > 0f || enemy.attackState != null || enemy.attackCooldown > 0f) {
            return
        }

        aiDecisionTimer = if (bossRound) 0.08f + random.nextFloat() * 0.14f else 0.12f + random.nextFloat() * 0.2f

        if (player.attackState != null && absDistance < dp(120f) && random.nextFloat() < (if (bossRound) 0.45f else 0.28f) * blockBias) {
            enemy.blockRemaining = 0.22f
            return
        }

        if (enemy.onGround && random.nextFloat() < (if (bossRound) 0.12f else 0.05f) * jumpBias) {
            jump(enemy, if (bossRound) 1.05f else 0.9f * currentEnemyProfile.jumpScale)
            return
        }

        if (absDistance < dp(104f)) {
            val attack =
                when {
                    enemy.energy >= 100f && (bossRound || random.nextFloat() < 0.2f * specialBias) -> AttackType.SPECIAL
                    random.nextFloat() < (if (bossRound) 0.62f else 0.45f) * kickBias -> AttackType.KICK
                    else -> AttackType.JAB
                }
            attemptAttack(enemy, attack)
        } else if (absDistance < dp(if (bossRound) 230f else 190f) && random.nextFloat() < (if (bossRound) 0.46f else 0.3f) * dashBias) {
            attemptAttack(enemy, AttackType.DASH)
        }
    }

    private fun updateFighterMotion(fighter: Fighter, dt: Float) {
        fighter.x += fighter.vx * dt
        fighter.vy += dp(2400f) * dt
        fighter.y += fighter.vy * dt

        if (fighter.y >= groundY) {
            if (!fighter.onGround && abs(fighter.vy) > dp(120f)) {
                spawnGroundBurst(fighter.x, groundY, fighter.accent)
            }
            fighter.y = groundY
            fighter.vy = 0f
            fighter.onGround = true
        } else {
            fighter.onGround = false
        }

        val minX = arenaLeft + dp(34f)
        val maxX = arenaRight - dp(34f)
        if (fighter.x < minX) {
            fighter.x = minX
            fighter.vx = 0f
        } else if (fighter.x > maxX) {
            fighter.x = maxX
            fighter.vx = 0f
        }

        if (fighter.attackState?.type != AttackType.DASH) {
            fighter.vx *= 0.82f
        }
    }

    private fun resolveAttack(attacker: Fighter, defender: Fighter) {
        if (phase != ScenePhase.PLAYING) return
        val attack = attacker.attackState ?: return
        if (attack.hasConnected) return
        if (attack.timer < attack.type.startup || attack.timer > attack.type.startup + attack.type.active) return

        val facing = attacker.facing
        val dx = defender.x - attacker.x
        if (dx * facing < dp(8f) || dx * facing > dp(attack.type.reach)) return
        if (abs(defender.y - attacker.y) > dp(78f)) return

        val blocked = defender.blockRemaining > 0f
        var damage = damageFor(attacker, attack.type)
        if (blocked) {
            damage *= 0.38f
        } else if (defender.side == FighterSide.PLAYER) {
            val chillReduction = if (options.chillMode) 0.88f else 1f
            damage *= (1f - stats.guardPower) * chillReduction
        }

        defender.health = max(0f, defender.health - damage)
        defender.vx = facing * dp(attack.type.knockbackX) * if (blocked) 0.35f else 1f
        defender.vy = -dp(attack.type.knockbackY) * if (blocked) 0.25f else 1f
        defender.onGround = false
        defender.hitFlash = 0.18f
        defender.stunRemaining = if (blocked) 0.08f else 0.16f + attack.type.active * 0.5f

        val gain = attack.type.energyGain + if (attacker.side == FighterSide.PLAYER) stats.energyGainBonus * 18f else 0f
        attacker.energy = min(100f, attacker.energy + gain)
        attacker.comboCount += 1
        attacker.comboTimer = 1.2f
        if (attacker.side == FighterSide.PLAYER) {
            bestCombo = max(bestCombo, attacker.comboCount)
        }

        attack.hasConnected = true
        if (options.hitPause) {
            freezeRemaining = if (attack.type == AttackType.SPECIAL) 0.06f else 0.03f
        }
        if (options.cinematicShake) {
            screenShake = if (attack.type == AttackType.SPECIAL) dp(16f) else dp(9f)
        }
        spawnImpact(
            x = attacker.x + facing * dp(attack.type.reach * 0.55f),
            y = min(attacker.y, defender.y) - dp(44f),
            accent = attacker.accent,
            strong = attack.type == AttackType.SPECIAL,
        )
    }

    private fun damageFor(attacker: Fighter, type: AttackType): Float {
        val upgradeBonus =
            if (attacker.side == FighterSide.PLAYER) {
                when (type) {
                    AttackType.JAB -> stats.jabBonus
                    AttackType.KICK -> stats.kickBonus
                    AttackType.DASH -> stats.dashBonus
                    AttackType.SPECIAL -> stats.specialBonus
                }
            } else {
                currentRound * 0.85f
            }
        return type.damage + upgradeBonus
    }

    private fun attemptAttack(fighter: Fighter, type: AttackType): Boolean {
        if (fighter.attackCooldown > 0f || fighter.stunRemaining > 0f || fighter.attackState != null) return false
        if (type == AttackType.SPECIAL && fighter.energy < type.cost) return false

        fighter.attackState = AttackState(type = type)
        fighter.attackCooldown = type.totalDuration + 0.05f
        if (type == AttackType.SPECIAL) {
            fighter.energy = max(0f, fighter.energy - type.cost)
        }
        if (type == AttackType.DASH) {
            fighter.vx = fighter.facing * fighter.moveSpeed * 1.26f
        }
        return true
    }

    private fun jump(fighter: Fighter, multiplier: Float = 1f) {
        if (!fighter.onGround || fighter.stunRemaining > 0f) return
        fighter.vy = -fighter.jumpForce * multiplier
        fighter.onGround = false
        spawnGroundBurst(fighter.x, groundY, fighter.accent)
    }

    private fun separateFighters() {
        val spacing = dp(86f)
        val overlap = spacing - abs(player.x - enemy.x)
        if (overlap <= 0f) return

        val push = overlap * 0.5f
        if (player.x <= enemy.x) {
            player.x -= push
            enemy.x += push
        } else {
            player.x += push
            enemy.x -= push
        }
    }

    private fun finishRound(won: Boolean) {
        if (phase != ScenePhase.PLAYING) return
        clearTouchControls()
        if (won) {
            phase = ScenePhase.ROUND_CLEAR
            val perfectBonus = if (player.health >= player.maxHealth * 0.72f) 45 + currentRound * 10 else 0
            val roundCoins = 110 + currentRound * 24 + perfectBonus
            lastRoundPerfect = perfectBonus > 0
            coins += roundCoins
            lifetimeCoins += roundCoins
            highestRoundReached = max(highestRoundReached, currentRound)

            if (currentRound >= maxCampaignRound) {
                campaignComplete = true
                championshipClears += 1
                resultEyebrow = "CHAMPIONSHIP CLEAR"
                resultTitle = "Skyline Conquered"
                resultDescription =
                    if (perfectBonus > 0) {
                        "You closed the twelve-round ladder with a perfect finish and claimed the rooftop crown."
                    } else {
                        "You survived the full twelve-round ladder and became the skyline champion."
                    }
            } else {
                campaignComplete = false
                resultEyebrow = "ROUND WON"
                resultTitle = "Arena Dominated"
                resultDescription =
                    if (perfectBonus > 0) {
                        "You dropped ${enemy.name} clean and earned a perfect-round bonus of $perfectBonus coins."
                    } else {
                        "You dropped ${enemy.name} and kept the combo alive. Step deeper into the ladder."
                    }
            }
        } else {
            phase = ScenePhase.ROUND_FAILED
            campaignComplete = false
            lastRoundPerfect = false
            resultEyebrow = "ROUND LOST"
            resultTitle = "You Were Broken"
            resultDescription =
                if (currentRound == 1) {
                    "${enemy.name} stole the opener. Read the guide, feel the tempo, and go again."
                } else {
                    "${enemy.name} took the round. Reset your rhythm and strike back."
                }
        }
        saveProgress()
        rebuildUiLayout()
    }

    private fun buildRound(round: Int, keepStats: Boolean) {
        if (!layoutReady) return
        if (!keepStats) {
            stats = PlayerStats()
            coins = 0
            bestCombo = 0
            lastRoundPerfect = false
            campaignComplete = false
            runsStarted += 1
        }

        clearTouchControls()
        particles.clear()
        upgradeCards.clear()
        phase = ScenePhase.PLAYING
        currentRound = round
        roundTimer = 0f
        aiDecisionTimer = 0.18f
        freezeRemaining = 0f
        screenShake = 0f
        roundIntroTimer = 1.35f
        activeArena = arenaThemes[(round - 1).mod(arenaThemes.size)]
        val bossRound = round % 4 == 0
        currentEnemyProfile =
            if (bossRound) {
                enemyProfiles.last()
            } else {
                enemyProfiles[(round - 1).mod(enemyProfiles.size)]
            }
        roundIntroTitle = if (bossRound) "BOSS ROUND" else "ROUND $round"
        highestRoundReached = max(highestRoundReached, round)
        statusText =
            if (bossRound) {
                "${currentEnemyProfile.title} arrives in ${activeArena.name}. This is the round to own."
            } else {
                "Round $round. ${currentEnemyProfile.styleNote}"
            }

        player = Fighter(
            side = FighterSide.PLAYER,
            name = "Nova",
            x = width * 0.28f,
            y = groundY,
            facing = 1f,
            color = Color.parseColor("#F7F9FD"),
            accent = Color.parseColor("#5CEBFF"),
            outline = Color.parseColor("#08101E"),
            maxHealth = stats.maxHealth,
            health = stats.maxHealth,
            energy = if (round == 1) 24f else min(60f, 16f + round * 9f),
            moveSpeed = dp(stats.moveSpeed),
            jumpForce = dp(stats.jumpForce),
        )

        player.x = player.x.coerceIn(arenaLeft + dp(64f), arenaRight - dp(220f))

        player.blockRemaining = if (options.chillMode) 0.08f else 0f
        val enemyBaseHealth = 84f + round * 18f
        val enemyHealth = (if (bossRound) enemyBaseHealth * 1.38f else enemyBaseHealth) * currentEnemyProfile.healthScale
        enemy = Fighter(
            side = FighterSide.ENEMY,
            name = if (bossRound) "${enemyNames.random(random)} Prime" else "${enemyNames.random(random)} ${currentEnemyProfile.title}",
            x = width * 0.72f,
            y = groundY,
            facing = -1f,
            color = Color.parseColor("#FFF3ED"),
            accent = if (bossRound) Color.parseColor("#C46BFF") else currentEnemyProfile.accent,
            outline = Color.parseColor("#120B10"),
            maxHealth = enemyHealth,
            health = enemyHealth,
            energy = min(56f, round * 7f * currentEnemyProfile.specialBias),
            moveSpeed = dp((312f + round * 10f) * if (bossRound) 1.08f else currentEnemyProfile.moveScale),
            jumpForce = dp((840f + round * 8f) * if (bossRound) 1.05f else currentEnemyProfile.jumpScale),
        )
        tutorialActive = tutorialActive && round <= 2
        saveProgress()
        rebuildUiLayout()
    }

    private fun buildUpgrades() {
        upgradeCards.clear()
        phase = ScenePhase.UPGRADES

        val cardWidth = width * 0.22f
        val cardHeight = height * 0.34f
        val gap = width * 0.03f
        val totalWidth = cardWidth * 3f + gap * 2f
        val startX = (width - totalWidth) * 0.5f
        val top = height * 0.32f
        val choices = UpgradeType.entries.shuffled(random).take(3)

        choices.forEachIndexed { index, type ->
            val left = startX + index * (cardWidth + gap)
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)
            upgradeCards += UpgradeCard(
                type = type,
                rect = rect,
                accent = cardAccents[index % cardAccents.size],
            )
        }
    }

    private fun applyUpgrade(type: UpgradeType) {
        when (type) {
            UpgradeType.POWER -> {
                stats.jabBonus += 2f
                stats.kickBonus += 3f
                stats.specialBonus += 4f
            }

            UpgradeType.FOOTWORK -> {
                stats.moveSpeed += 28f
                stats.dashBonus += 2f
            }

            UpgradeType.SPRING -> {
                stats.jumpForce += 74f
                stats.guardPower += 0.02f
            }

            UpgradeType.SURGE -> {
                stats.energyGainBonus += 0.12f
                stats.specialBonus += 2f
            }

            UpgradeType.SPIRIT -> {
                stats.maxHealth += 14f
            }
        }
        buildRound(currentRound + 1, keepStats = true)
    }

    private fun handleTitleTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        when {
            playButton.contains(event.x, event.y) -> buildRound(1, keepStats = false)
            optionsButton.contains(event.x, event.y) -> openOptions(ScenePhase.TITLE)
        }
    }

    private fun handleOptionsTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        when {
            shakeToggleButton.contains(event.x, event.y) -> options.cinematicShake = !options.cinematicShake
            pauseToggleButton.contains(event.x, event.y) -> options.hitPause = !options.hitPause
            chillToggleButton.contains(event.x, event.y) -> options.chillMode = !options.chillMode
            optionsBackButton.contains(event.x, event.y) -> closeOptions()
        }
        saveProgress()
        invalidate()
    }

    private fun handlePlayingTouch(event: MotionEvent) {
        if ((event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) &&
            hudOptionsButton.contains(event.getX(event.actionIndex), event.getY(event.actionIndex))
        ) {
            clearTouchControls()
            openOptions(ScenePhase.PLAYING)
            return
        }
        refreshTouchControls(event)
    }

    private fun handleResultTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        when {
            primaryButton.contains(event.x, event.y) -> {
                if (phase == ScenePhase.ROUND_CLEAR) {
                    if (campaignComplete) {
                        resetToTitle()
                    } else if (currentRound % 2 == 0) {
                        buildUpgrades()
                    } else {
                        buildRound(currentRound + 1, keepStats = true)
                    }
                } else {
                    buildRound(currentRound, keepStats = true)
                }
            }

            secondaryButton.contains(event.x, event.y) -> resetToTitle()
        }
    }

    private fun handleUpgradeTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        upgradeCards.firstOrNull { it.rect.contains(event.x, event.y) }?.let { applyUpgrade(it.type) }
    }

    private fun refreshTouchControls(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            clearTouchControls()
            return
        }

        val previousJump = jumpHeld
        val previousJab = jabHeld
        val previousKick = kickHeld
        val previousSpecial = specialHeld

        moveLeftHeld = false
        moveRightHeld = false
        jumpHeld = false
        jabHeld = false
        kickHeld = false
        specialHeld = false

        for (index in 0 until event.pointerCount) {
            val x = event.getX(index)
            val y = event.getY(index)
            if (leftButton.contains(x, y)) moveLeftHeld = true
            if (rightButton.contains(x, y)) moveRightHeld = true
            if (jumpButton.contains(x, y)) jumpHeld = true
            if (jabButton.contains(x, y)) jabHeld = true
            if (kickButton.contains(x, y)) kickHeld = true
            if (specialButton.contains(x, y)) specialHeld = true
        }

        if (jumpHeld && !previousJump) jump(player)
        if (jabHeld && !previousJab) attemptAttack(player, AttackType.JAB)
        if (kickHeld && !previousKick) attemptAttack(player, AttackType.KICK)
        if (specialHeld && !previousSpecial) {
            if (!attemptAttack(player, AttackType.SPECIAL)) {
                attemptAttack(player, AttackType.DASH)
            }
        }
    }

    private fun clearTouchControls() {
        moveLeftHeld = false
        moveRightHeld = false
        jumpHeld = false
        jabHeld = false
        kickHeld = false
        specialHeld = false
    }

    private fun openOptions(returnPhase: ScenePhase) {
        optionsReturnPhase = returnPhase
        phase = ScenePhase.OPTIONS
        clearTouchControls()
        rebuildUiLayout()
    }

    private fun closeOptions() {
        phase = optionsReturnPhase
        if (phase == ScenePhase.TITLE) {
            statusText = "Enter the arena."
        }
        saveProgress()
        rebuildUiLayout()
    }

    private fun resetToTitle() {
        phase = ScenePhase.TITLE
        particles.clear()
        upgradeCards.clear()
        clearTouchControls()
        optionsReturnPhase = ScenePhase.TITLE
        statusText = "Enter the arena."
        campaignComplete = false
        saveProgress()
        rebuildUiLayout()
    }

    private fun rebuildUiLayout() {
        val centerX = width * 0.5f
        val buttonWidth = width * 0.19f
        val buttonHeight = height * 0.085f
        playButton = RectF(centerX - buttonWidth * 0.5f, height * 0.68f, centerX + buttonWidth * 0.5f, height * 0.68f + buttonHeight)
        optionsButton = RectF(centerX - buttonWidth * 0.5f, height * 0.79f, centerX + buttonWidth * 0.5f, height * 0.79f + buttonHeight)

        primaryButton = RectF(centerX - buttonWidth - dp(18f), height * 0.72f, centerX - dp(18f), height * 0.72f + buttonHeight)
        secondaryButton = RectF(centerX + dp(18f), height * 0.72f, centerX + buttonWidth + dp(18f), height * 0.72f + buttonHeight)

        hudOptionsButton = RectF(dp(14f), dp(14f), dp(50f), dp(50f))

        val optionsLeft = width * 0.34f
        val optionsRight = width * 0.66f
        val optionsTop = height * 0.38f
        val rowHeight = dp(54f)
        shakeToggleButton = RectF(optionsLeft, optionsTop, optionsRight, optionsTop + rowHeight)
        pauseToggleButton = RectF(optionsLeft, optionsTop + rowHeight + dp(14f), optionsRight, optionsTop + rowHeight * 2f + dp(14f))
        chillToggleButton = RectF(optionsLeft, optionsTop + (rowHeight + dp(14f)) * 2f, optionsRight, optionsTop + rowHeight * 3f + dp(28f))
        optionsBackButton = RectF(optionsLeft + dp(48f), chillToggleButton.bottom + dp(26f), optionsRight - dp(48f), chillToggleButton.bottom + dp(26f) + dp(48f))

        val moveSize = dp(84f)
        leftButton = RectF(dp(22f), height - moveSize - dp(28f), dp(22f) + moveSize, height - dp(28f))
        rightButton = RectF(leftButton.right + dp(18f), leftButton.top, leftButton.right + dp(18f) + moveSize, leftButton.bottom)
        jumpButton = RectF(rightButton.left + dp(26f), leftButton.top - moveSize - dp(18f), rightButton.left + dp(26f) + moveSize, leftButton.top - dp(18f))

        val actionWidth = dp(96f)
        val actionHeight = dp(78f)
        jabButton = RectF(width - actionWidth * 3f - dp(50f), height - actionHeight - dp(28f), width - actionWidth * 2f - dp(34f), height - dp(28f))
        kickButton = RectF(jabButton.right + dp(14f), jabButton.top - dp(18f), jabButton.right + dp(14f) + actionWidth, jabButton.bottom - dp(18f))
        specialButton = RectF(kickButton.right + dp(14f), jabButton.top, kickButton.right + dp(14f) + actionWidth, jabButton.bottom)
    }

    private fun drawBackground(canvas: Canvas) {
        skyPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                activeArena.skyTop,
                activeArena.skyMid,
                activeArena.skyWarm,
                Color.parseColor("#FFCF9B"),
            ),
            floatArrayOf(0f, 0.36f, 0.73f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)
        skyPaint.shader = null

        fillPaint.color = Color.argb(130, 255, 217, 161)
        canvas.drawCircle(width * 0.78f, height * 0.2f, dp(58f), fillPaint)
        fillPaint.color = Color.argb(45, 255, 255, 255)
        drawCloud(canvas, width * 0.2f, height * 0.18f, dp(74f))
        drawCloud(canvas, width * 0.48f, height * 0.11f, dp(54f))
        drawCloud(canvas, width * 0.7f, height * 0.28f, dp(66f))

        fillPaint.color = activeArena.skylineBack
        repeat(8) { index ->
            val towerWidth = width * (0.09f + index % 3 * 0.014f)
            val left = index * width / 7.6f - dp(20f)
            val top = height * (0.43f + (index % 4) * 0.03f)
            canvas.drawRoundRect(RectF(left, top, left + towerWidth, height.toFloat()), dp(18f), dp(18f), fillPaint)
        }

        fillPaint.color = activeArena.skylineFront
        repeat(9) { index ->
            val towerWidth = width * (0.12f + (index % 2) * 0.02f)
            val left = index * width / 8.4f - dp(18f)
            val top = height * (0.56f + (index % 3) * 0.025f)
            canvas.drawRoundRect(RectF(left, top, left + towerWidth, height.toFloat()), dp(20f), dp(20f), fillPaint)
        }
    }

    private fun drawArena(canvas: Canvas) {
        fillPaint.color = activeArena.floor
        canvas.drawRect(0f, groundY + dp(18f), width.toFloat(), height.toFloat(), fillPaint)

        val platform = RectF(arenaLeft, groundY - dp(18f), arenaRight, groundY + dp(18f))
        fillPaint.color = activeArena.platform
        canvas.drawRoundRect(platform, dp(18f), dp(18f), fillPaint)

        fillPaint.color = activeArena.glowA
        canvas.drawRect(platform.left + dp(20f), platform.top + dp(10f), platform.right - dp(20f), platform.top + dp(14f), fillPaint)
        fillPaint.color = activeArena.glowB
        canvas.drawRect(platform.left + dp(20f), platform.bottom - dp(14f), platform.right - dp(20f), platform.bottom - dp(10f), fillPaint)

        fillPaint.color = Color.parseColor("#302245")
        repeat(5) { index ->
            val x = arenaLeft + dp(54f) + index * ((arenaRight - arenaLeft - dp(108f)) / 4f)
            canvas.drawRoundRect(RectF(x - dp(10f), groundY - dp(72f), x + dp(10f), groundY - dp(18f)), dp(8f), dp(8f), fillPaint)
        }
    }

    private fun drawWorld(canvas: Canvas) {
        val shakeX = if (screenShake > 0f) random.nextFloat() * screenShake - screenShake * 0.5f else 0f
        val shakeY = if (screenShake > 0f) random.nextFloat() * screenShake - screenShake * 0.5f else 0f
        canvas.save()
        canvas.translate(shakeX, shakeY)
        drawFighter(canvas, player)
        drawFighter(canvas, enemy)
        drawParticles(canvas)
        canvas.restore()
    }

    private fun drawFighter(canvas: Canvas, fighter: Fighter) {
        val scale = if (fighter.side == FighterSide.ENEMY && currentRound % 4 == 0) 1.1f else 1f
        val bob = if (fighter.onGround && fighter.attackState == null) sin(skylineTime * 7f + fighter.x * 0.008f) * dp(2.5f) else 0f
        val headRadius = dp(18f) * scale
        val torsoTop = fighter.y - dp(116f) * scale + bob
        val torsoBottom = fighter.y - dp(54f) * scale + bob
        val shoulderY = torsoTop + dp(18f)
        val attack = fighter.attackState
        val animTime = attack?.timer ?: 0f
        val attackPose = if (attack != null) min(1f, animTime / max(attack.type.startup + attack.type.active, 0.01f)) else 0f
        val reachBoost =
            when (attack?.type) {
                AttackType.JAB -> dp(26f)
                AttackType.KICK -> dp(34f)
                AttackType.DASH -> dp(44f)
                AttackType.SPECIAL -> dp(56f)
                null -> 0f
            }

        val leadArmX = fighter.x + fighter.facing * (dp(18f) * scale + reachBoost * attackPose)
        val leadArmY = shoulderY + if (attack?.type == AttackType.KICK) dp(20f) * scale else -dp(6f) * scale
        val rearArmX = fighter.x - fighter.facing * dp(18f) * scale
        val rearArmY = shoulderY + dp(12f) * scale
        val leadLegX = fighter.x + fighter.facing * (dp(16f) * scale + if (attack?.type == AttackType.KICK) reachBoost * 0.72f else 0f)
        val leadLegY = fighter.y
        val rearLegX = fighter.x - fighter.facing * dp(18f) * scale
        val rearLegY = fighter.y
        val neckY = torsoTop - dp(6f) * scale

        fillPaint.color = Color.argb(65, 0, 0, 0)
        canvas.drawOval(RectF(fighter.x - dp(28f) * scale, fighter.y - dp(8f), fighter.x + dp(28f) * scale, fighter.y + dp(8f)), fillPaint)

        if (fighter.side == FighterSide.PLAYER && fighter.energy >= 100f) {
            fillPaint.color = Color.argb(70, Color.red(fighter.accent), Color.green(fighter.accent), Color.blue(fighter.accent))
            canvas.drawCircle(fighter.x, torsoTop + dp(24f), dp(48f) + sin(skylineTime * 7f) * dp(4f), fillPaint)
        }

        val flashColor = if (fighter.hitFlash > 0f) blendWithWhite(fighter.accent, fighter.hitFlash * 4f) else fighter.accent
        drawStickmanSkeleton(
            canvas = canvas,
            fighter = fighter,
            neckY = neckY,
            torsoBottom = torsoBottom,
            leadArmX = leadArmX,
            leadArmY = leadArmY,
            rearArmX = rearArmX,
            rearArmY = rearArmY,
            leadLegX = leadLegX,
            leadLegY = leadLegY,
            rearLegX = rearLegX,
            rearLegY = rearLegY,
            color = fighter.outline,
            width = dp(13f) * scale,
        )
        drawStickmanSkeleton(
            canvas = canvas,
            fighter = fighter,
            neckY = neckY,
            torsoBottom = torsoBottom,
            leadArmX = leadArmX,
            leadArmY = leadArmY,
            rearArmX = rearArmX,
            rearArmY = rearArmY,
            leadLegX = leadLegX,
            leadLegY = leadLegY,
            rearLegX = rearLegX,
            rearLegY = rearLegY,
            color = flashColor,
            width = dp(7f) * scale,
        )

        fillPaint.color = if (fighter.hitFlash > 0f) Color.WHITE else fighter.color
        canvas.drawCircle(fighter.x, torsoTop - headRadius, headRadius, fillPaint)
        strokePaint.color = fighter.outline
        strokePaint.strokeWidth = dp(4f) * scale
        canvas.drawCircle(fighter.x, torsoTop - headRadius, headRadius, strokePaint)

        strokePaint.color = fighter.accent
        strokePaint.strokeWidth = dp(4f) * scale
        canvas.drawLine(
            fighter.x - fighter.facing * dp(14f) * scale,
            torsoTop - headRadius * 0.95f,
            fighter.x + fighter.facing * dp(10f) * scale,
            torsoTop - headRadius * 0.85f,
            strokePaint,
        )

        if (fighter.blockRemaining > 0f) {
            strokePaint.color = blendWithWhite(fighter.accent, 0.35f)
            strokePaint.strokeWidth = dp(5f) * scale
            canvas.drawArc(
                RectF(fighter.x - dp(40f) * scale, torsoTop - dp(26f) * scale, fighter.x + dp(40f) * scale, torsoBottom + dp(18f) * scale),
                if (fighter.facing > 0f) -70f else 250f,
                140f,
                false,
                strokePaint,
            )
        }
    }

    private fun drawStickmanSkeleton(
        canvas: Canvas,
        fighter: Fighter,
        neckY: Float,
        torsoBottom: Float,
        leadArmX: Float,
        leadArmY: Float,
        rearArmX: Float,
        rearArmY: Float,
        leadLegX: Float,
        leadLegY: Float,
        rearLegX: Float,
        rearLegY: Float,
        color: Int,
        width: Float,
    ) {
        strokePaint.color = color
        strokePaint.strokeWidth = width
        canvas.drawLine(fighter.x, neckY, fighter.x, torsoBottom, strokePaint)
        canvas.drawLine(fighter.x, neckY + dp(18f), leadArmX, leadArmY, strokePaint)
        canvas.drawLine(fighter.x, neckY + dp(18f), rearArmX, rearArmY, strokePaint)
        canvas.drawLine(fighter.x, torsoBottom, leadLegX, leadLegY, strokePaint)
        canvas.drawLine(fighter.x, torsoBottom, rearLegX, rearLegY, strokePaint)
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            fillPaint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.size, fillPaint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        if (phase == ScenePhase.TITLE || phase == ScenePhase.OPTIONS) return

        drawHealthPanel(canvas, RectF(dp(24f), dp(22f), width * 0.31f, dp(90f)), player, alignLeft = true)
        drawHealthPanel(canvas, RectF(width * 0.69f, dp(22f), width - dp(24f), dp(90f)), enemy, alignLeft = false)
        drawRoundChip(canvas)
        drawCoinChip(canvas)
        drawIconButton(canvas, hudOptionsButton, "II")
        drawStatusStrip(canvas)
        drawComboChip(canvas)

        if (phase == ScenePhase.PLAYING) {
            drawControls(canvas)
            if (roundIntroTimer > 0f) {
                drawRoundIntro(canvas)
            }
            if (tutorialActive) {
                drawTutorialHint(canvas)
            }
        }
    }

    private fun drawHealthPanel(canvas: Canvas, rect: RectF, fighter: Fighter, alignLeft: Boolean) {
        fillPaint.color = Color.argb(165, 14, 15, 26)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fillPaint)

        val padding = dp(14f)
        val barTop = rect.top + dp(32f)
        val barHeight = dp(12f)
        val healthRatio = (fighter.health / fighter.maxHealth).coerceIn(0f, 1f)
        val energyRatio = (fighter.energy / 100f).coerceIn(0f, 1f)
        val barRect = RectF(rect.left + padding, barTop, rect.right - padding, barTop + barHeight)
        fillPaint.color = Color.parseColor("#312E48")
        canvas.drawRoundRect(barRect, dp(8f), dp(8f), fillPaint)
        fillPaint.color = if (fighter.side == FighterSide.PLAYER) Color.parseColor("#58D8FF") else Color.parseColor("#FF6675")
        canvas.drawRoundRect(RectF(barRect.left, barRect.top, barRect.left + barRect.width() * healthRatio, barRect.bottom), dp(8f), dp(8f), fillPaint)

        val energyRect = RectF(barRect.left, barRect.bottom + dp(8f), barRect.right, barRect.bottom + dp(16f))
        fillPaint.color = Color.parseColor("#262337")
        canvas.drawRoundRect(energyRect, dp(7f), dp(7f), fillPaint)
        fillPaint.color = fighter.accent
        canvas.drawRoundRect(RectF(energyRect.left, energyRect.top, energyRect.left + energyRect.width() * energyRatio, energyRect.bottom), dp(7f), dp(7f), fillPaint)

        textPaint.textSize = sp(13f)
        textPaint.color = Color.WHITE
        textPaint.textAlign = if (alignLeft) Paint.Align.LEFT else Paint.Align.RIGHT
        val nameX = if (alignLeft) rect.left + padding else rect.right - padding
        canvas.drawText(fighter.name.uppercase(), nameX, rect.top + dp(18f), textPaint)

        textPaint.textSize = sp(11f)
        textPaint.color = Color.argb(215, 219, 223, 231)
        canvas.drawText("${fighter.health.toInt()} HP", nameX, rect.bottom - dp(10f), textPaint)
    }

    private fun drawRoundChip(canvas: Canvas) {
        val rect = RectF(width * 0.42f, dp(22f), width * 0.58f, dp(66f))
        buttonPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            Color.parseColor("#A53366"),
            Color.parseColor("#5E65F5"),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, dp(22f), dp(22f), buttonPaint)
        buttonPaint.shader = null

        centerTextPaint.textSize = sp(12f)
        centerTextPaint.color = Color.argb(220, 255, 255, 255)
        canvas.drawText("$currentRound / $maxCampaignRound", rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawCoinChip(canvas: Canvas) {
        val rect = RectF(width - dp(132f), dp(30f), width - dp(24f), dp(74f))
        fillPaint.color = Color.argb(168, 18, 18, 30)
        canvas.drawRoundRect(rect, dp(22f), dp(22f), fillPaint)
        centerTextPaint.textSize = sp(11f)
        centerTextPaint.color = Color.parseColor("#FFD25A")
        canvas.drawText("$coins", rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawStatusStrip(canvas: Canvas) {
        val rect = RectF(width * 0.28f, dp(88f), width * 0.72f, dp(122f))
        fillPaint.color = Color.argb(145, 14, 16, 27)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fillPaint)
        centerTextPaint.textSize = sp(11f)
        centerTextPaint.color = Color.argb(225, 236, 239, 246)
        val detail = if (player.energy >= 100f) "$statusText  Burst ready." else statusText
        canvas.drawText(detail, rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawComboChip(canvas: Canvas) {
        val rect = RectF(width - dp(132f), dp(82f), width - dp(24f), dp(126f))
        fillPaint.color = Color.argb(155, 18, 18, 30)
        canvas.drawRoundRect(rect, dp(22f), dp(22f), fillPaint)
        centerTextPaint.textSize = sp(10f)
        centerTextPaint.color = Color.argb(205, 227, 231, 240)
        val comboText = if (player.comboCount > 1) "COMBO x${player.comboCount}" else activeArena.name.uppercase()
        canvas.drawText(comboText, rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawIconButton(canvas: Canvas, rect: RectF, label: String) {
        fillPaint.color = Color.argb(150, 14, 18, 28)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), fillPaint)
        strokePaint.color = Color.argb(100, 255, 255, 255)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), strokePaint)
        centerTextPaint.textSize = sp(15f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + dp(5f), centerTextPaint)
    }

    private fun drawRoundIntro(canvas: Canvas) {
        val alpha = (roundIntroTimer / 1.35f).coerceIn(0f, 1f)
        val rect = RectF(width * 0.34f, height * 0.34f, width * 0.66f, height * 0.48f)
        fillPaint.color = Color.argb((150 * alpha).toInt(), 9, 13, 22)
        canvas.drawRoundRect(rect, dp(28f), dp(28f), fillPaint)
        strokePaint.color = Color.argb((255 * alpha).toInt(), Color.red(activeArena.glowA), Color.green(activeArena.glowA), Color.blue(activeArena.glowA))
        strokePaint.strokeWidth = dp(3f)
        canvas.drawRoundRect(rect, dp(28f), dp(28f), strokePaint)

        centerTextPaint.textSize = sp(12f)
        centerTextPaint.color = Color.argb((230 * alpha).toInt(), 255, 255, 255)
        canvas.drawText(activeArena.name.uppercase(), rect.centerX(), rect.top + dp(32f), centerTextPaint)
        centerTextPaint.textSize = sp(26f)
        canvas.drawText(roundIntroTitle, rect.centerX(), rect.centerY() + dp(2f), centerTextPaint)
        centerTextPaint.textSize = sp(11f)
        canvas.drawText(currentEnemyProfile.title.uppercase(), rect.centerX(), rect.bottom - dp(18f), centerTextPaint)
    }

    private fun drawControls(canvas: Canvas) {
        drawMoveButton(canvas, leftButton, "<", moveLeftHeld)
        drawMoveButton(canvas, rightButton, ">", moveRightHeld)
        drawMoveButton(canvas, jumpButton, "UP", jumpHeld)

        drawActionButton(canvas, jabButton, "JAB", moveLeftHeld || moveRightHeld, jabHeld, Color.parseColor("#57D7FF"))
        drawActionButton(canvas, kickButton, "KICK", false, kickHeld, Color.parseColor("#FF8A65"))
        val specialLabel = if (player.energy >= 100f) "BURST" else "DASH"
        drawActionButton(canvas, specialButton, specialLabel, player.energy >= 100f, specialHeld, Color.parseColor("#86FF91"))
    }

    private fun drawMoveButton(canvas: Canvas, rect: RectF, label: String, active: Boolean) {
        fillPaint.color = if (active) Color.argb(220, 72, 121, 214) else Color.argb(130, 11, 16, 28)
        canvas.drawRoundRect(rect, dp(26f), dp(26f), fillPaint)
        strokePaint.color = if (active) Color.argb(255, 190, 223, 255) else Color.argb(100, 255, 255, 255)
        strokePaint.strokeWidth = dp(3f)
        canvas.drawRoundRect(rect, dp(26f), dp(26f), strokePaint)
        centerTextPaint.textSize = sp(16f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + dp(6f), centerTextPaint)
    }

    private fun drawActionButton(canvas: Canvas, rect: RectF, label: String, charged: Boolean, active: Boolean, accent: Int) {
        val fill = if (active) blendWithWhite(accent, 0.18f) else Color.argb(165, 16, 18, 30)
        fillPaint.color = fill
        canvas.drawRoundRect(rect, dp(24f), dp(24f), fillPaint)
        strokePaint.color = if (charged) accent else Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent))
        strokePaint.strokeWidth = if (charged) dp(4f) else dp(2.5f)
        canvas.drawRoundRect(rect, dp(24f), dp(24f), strokePaint)
        centerTextPaint.textSize = sp(13f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawOverlays(canvas: Canvas) {
        when (phase) {
            ScenePhase.TITLE -> drawTitleOverlay(canvas)
            ScenePhase.OPTIONS -> drawOptionsOverlay(canvas)
            ScenePhase.ROUND_CLEAR, ScenePhase.ROUND_FAILED -> drawResultOverlay(canvas)
            ScenePhase.UPGRADES -> drawUpgradeOverlay(canvas)
            ScenePhase.PLAYING -> Unit
        }
    }

    private fun drawTitleOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(145, 6, 7, 13)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val card = RectF(width * 0.08f, height * 0.12f, width * 0.54f, height * 0.9f)
        fillPaint.color = Color.argb(180, 15, 16, 28)
        canvas.drawRoundRect(card, dp(32f), dp(32f), fillPaint)
        strokePaint.color = Color.argb(110, 109, 177, 255)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(card, dp(32f), dp(32f), strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.parseColor("#7EDBFF")
        textPaint.textSize = sp(13f)
        canvas.drawText("ORIGINAL 2D FIGHTER", card.left + dp(28f), card.top + dp(42f), textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = sp(31f)
        canvas.drawText("Skyline Stick Clash", card.left + dp(28f), card.top + dp(94f), textPaint)

        textPaint.textSize = sp(14f)
        textPaint.color = Color.argb(225, 235, 236, 241)
        drawMultilineText(
            canvas = canvas,
            text = "A fast original stickman arena brawler for Android. Chain clean jabs, kicks, air bursts, and climb through themed rooftop fights with boss rounds and upgrade breaks.",
            left = card.left + dp(28f),
            top = card.top + dp(128f),
            maxWidth = card.width() - dp(56f),
            lineHeight = dp(24f),
            paint = textPaint,
        )

        drawFeatureStrip(canvas, card.left + dp(28f), card.top + dp(290f), "Touch controls built for phones")
        drawFeatureStrip(canvas, card.left + dp(28f), card.top + dp(346f), "Boss rounds and shifting arena themes")
        drawFeatureStrip(canvas, card.left + dp(28f), card.top + dp(402f), "Arcade rounds with upgrade choices")
        drawFeatureStrip(canvas, card.left + dp(28f), card.top + dp(458f), "Twelve-round championship ladder")

        drawMetricChip(canvas, RectF(card.left + dp(28f), card.bottom - dp(142f), card.left + dp(148f), card.bottom - dp(94f)), "BEST", highestRoundReached.toString(), Color.parseColor("#72C3FF"))
        drawMetricChip(canvas, RectF(card.left + dp(162f), card.bottom - dp(142f), card.left + dp(302f), card.bottom - dp(94f)), "LIFETIME", lifetimeCoins.toString(), Color.parseColor("#FFD15A"))
        drawMetricChip(canvas, RectF(card.left + dp(316f), card.bottom - dp(142f), card.left + dp(462f), card.bottom - dp(94f)), "CLEARS", championshipClears.toString(), Color.parseColor("#7AF0A6"))

        drawMenuButton(canvas, playButton, "Start Fight", "ENTER ARENA", Color.parseColor("#64E6A2"))
        drawMenuButton(canvas, optionsButton, "Options", "TUNE MATCH", Color.parseColor("#74A9FF"))

        if (tutorialActive) {
            textPaint.textSize = sp(12f)
            textPaint.color = Color.argb(220, 227, 232, 238)
            drawMultilineText(
                canvas = canvas,
                text = "First run tip: left side moves, top-left button pauses, right side attacks. Fill Burst for the finisher.",
                left = card.left + dp(28f),
                top = card.bottom - dp(66f),
                maxWidth = card.width() - dp(56f),
                lineHeight = dp(20f),
                paint = textPaint,
            )
        }

        drawHeroSilhouette(canvas)
    }

    private fun drawOptionsOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(165, 8, 10, 16)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val card = RectF(width * 0.28f, height * 0.2f, width * 0.72f, height * 0.82f)
        fillPaint.color = Color.parseColor("#FBFCFF")
        canvas.drawRoundRect(card, dp(28f), dp(28f), fillPaint)
        fillPaint.color = Color.parseColor("#67A8FF")
        canvas.drawRoundRect(RectF(card.left, card.top, card.right, card.top + dp(78f)), dp(28f), dp(28f), fillPaint)
        fillPaint.color = Color.parseColor("#67A8FF")
        canvas.drawRect(card.left, card.top + dp(40f), card.right, card.top + dp(78f), fillPaint)

        centerTextPaint.textSize = sp(20f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("Fight Options", card.centerX(), card.top + dp(48f), centerTextPaint)

        drawOptionRow(canvas, shakeToggleButton, "Cinematic Shake", options.cinematicShake, Color.parseColor("#67B5FF"))
        drawOptionRow(canvas, pauseToggleButton, "Hit Pause", options.hitPause, Color.parseColor("#FF7D8C"))
        drawOptionRow(canvas, chillToggleButton, "Chill Mode", options.chillMode, Color.parseColor("#7CE39C"))
        drawMenuButton(canvas, optionsBackButton, "Back", "RETURN", Color.parseColor("#6C87FF"))
    }

    private fun drawResultOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(145, 7, 9, 16)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val card = RectF(width * 0.25f, height * 0.2f, width * 0.75f, height * 0.82f)
        fillPaint.color = Color.argb(225, 17, 18, 31)
        canvas.drawRoundRect(card, dp(28f), dp(28f), fillPaint)
        strokePaint.color = if (phase == ScenePhase.ROUND_CLEAR) Color.parseColor("#70E6A6") else Color.parseColor("#FF7889")
        strokePaint.strokeWidth = dp(3f)
        canvas.drawRoundRect(card, dp(28f), dp(28f), strokePaint)

        centerTextPaint.textSize = sp(13f)
        centerTextPaint.color = if (phase == ScenePhase.ROUND_CLEAR) Color.parseColor("#87FFC8") else Color.parseColor("#FF8D9A")
        canvas.drawText(resultEyebrow, card.centerX(), card.top + dp(42f), centerTextPaint)

        centerTextPaint.textSize = sp(28f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(resultTitle, card.centerX(), card.top + dp(98f), centerTextPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(15f)
        textPaint.color = Color.argb(230, 232, 234, 242)
        drawMultilineText(canvas, resultDescription, card.left + dp(36f), card.top + dp(138f), card.width() - dp(72f), dp(24f), textPaint, centered = true)

        centerTextPaint.textSize = sp(14f)
        centerTextPaint.color = Color.parseColor("#FFD873")
        val resultFooter =
            if (phase == ScenePhase.ROUND_CLEAR && lastRoundPerfect) {
                "Perfect Finish    Coins $coins    Best Combo x$bestCombo"
            } else {
                "Coins $coins    Best Combo x$bestCombo"
            }
        canvas.drawText(resultFooter, card.centerX(), card.bottom - dp(132f), centerTextPaint)

        drawMenuButton(
            canvas,
            primaryButton,
            when {
                phase == ScenePhase.ROUND_CLEAR && campaignComplete -> "New Run"
                phase == ScenePhase.ROUND_CLEAR -> "Continue"
                else -> "Retry"
            },
            when {
                phase == ScenePhase.ROUND_CLEAR && campaignComplete -> "BACK TO TITLE"
                phase == ScenePhase.ROUND_CLEAR -> "NEXT ROUND"
                else -> "RUN IT BACK"
            },
            if (phase == ScenePhase.ROUND_CLEAR) Color.parseColor("#6FE7A8") else Color.parseColor("#FF8D9A"),
        )
        drawMenuButton(canvas, secondaryButton, "Main Menu", "LEAVE ARENA", Color.parseColor("#74A9FF"))
    }

    private fun drawUpgradeOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(130, 8, 10, 16)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        centerTextPaint.textSize = sp(13f)
        centerTextPaint.color = Color.parseColor("#8CE3FF")
        canvas.drawText("UPGRADE BREAK", width * 0.5f, height * 0.18f, centerTextPaint)
        centerTextPaint.textSize = sp(26f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("Choose Your Edge", width * 0.5f, height * 0.24f, centerTextPaint)

        upgradeCards.forEach { card ->
            fillPaint.color = Color.argb(228, 18, 19, 31)
            canvas.drawRoundRect(card.rect, dp(28f), dp(28f), fillPaint)
            strokePaint.color = card.accent
            strokePaint.strokeWidth = dp(3f)
            canvas.drawRoundRect(card.rect, dp(28f), dp(28f), strokePaint)

            fillPaint.color = Color.argb(42, Color.red(card.accent), Color.green(card.accent), Color.blue(card.accent))
            canvas.drawRoundRect(RectF(card.rect.left, card.rect.top, card.rect.right, card.rect.top + dp(56f)), dp(28f), dp(28f), fillPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = sp(18f)
            textPaint.color = Color.WHITE
            canvas.drawText(card.type.label, card.rect.left + dp(22f), card.rect.top + dp(38f), textPaint)

            textPaint.textSize = sp(13f)
            textPaint.color = Color.argb(230, 229, 232, 239)
            drawMultilineText(
                canvas = canvas,
                text = card.type.blurb,
                left = card.rect.left + dp(22f),
                top = card.rect.top + dp(84f),
                maxWidth = card.rect.width() - dp(44f),
                lineHeight = dp(22f),
                paint = textPaint,
            )

            val innerButton = RectF(card.rect.left + dp(18f), card.rect.bottom - dp(64f), card.rect.right - dp(18f), card.rect.bottom - dp(18f))
            drawMenuButton(canvas, innerButton, "Equip", "LOCK IN", card.accent)
        }
    }

    private fun drawMenuButton(canvas: Canvas, rect: RectF, title: String, subtitle: String, accent: Int) {
        buttonPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            Color.argb(235, 32, 37, 57),
            Color.argb(235, 18, 21, 33),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, dp(28f), dp(28f), buttonPaint)
        buttonPaint.shader = null

        strokePaint.color = accent
        strokePaint.strokeWidth = dp(3f)
        canvas.drawRoundRect(rect, dp(28f), dp(28f), strokePaint)

        centerTextPaint.textSize = sp(18f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(title, rect.centerX(), rect.centerY() - dp(2f), centerTextPaint)
        centerTextPaint.textSize = sp(10f)
        centerTextPaint.color = Color.argb(210, 214, 222, 232)
        canvas.drawText(subtitle, rect.centerX(), rect.centerY() + dp(16f), centerTextPaint)
    }

    private fun drawOptionRow(canvas: Canvas, rect: RectF, label: String, enabled: Boolean, accent: Int) {
        fillPaint.color = Color.parseColor("#EEF2FF")
        canvas.drawRoundRect(rect, dp(20f), dp(20f), fillPaint)
        strokePaint.color = Color.argb(35, 0, 0, 0)
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, dp(20f), dp(20f), strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(15f)
        textPaint.color = Color.parseColor("#131B2A")
        canvas.drawText(label, rect.left + dp(18f), rect.centerY() + dp(5f), textPaint)
        drawMiniToggle(canvas, RectF(rect.right - dp(86f), rect.centerY() - dp(16f), rect.right - dp(18f), rect.centerY() + dp(16f)), enabled, accent)
    }

    private fun drawMiniToggle(canvas: Canvas, rect: RectF, enabled: Boolean, accent: Int) {
        fillPaint.color = if (enabled) accent else Color.parseColor("#CCD2E4")
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fillPaint)
        val knobRadius = dp(12f)
        val cx = if (enabled) rect.right - dp(18f) else rect.left + dp(18f)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, rect.centerY(), knobRadius, fillPaint)
    }

    private fun drawFeatureStrip(canvas: Canvas, left: Float, top: Float, text: String) {
        fillPaint.color = Color.argb(190, 18, 24, 36)
        val rect = RectF(left, top, left + width * 0.34f, top + dp(38f))
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fillPaint)
        fillPaint.color = Color.parseColor("#77EDAB")
        canvas.drawCircle(left + dp(18f), rect.centerY(), dp(5f), fillPaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(13f)
        textPaint.color = Color.WHITE
        canvas.drawText(text, left + dp(34f), rect.centerY() + dp(4f), textPaint)
    }

    private fun drawMetricChip(canvas: Canvas, rect: RectF, label: String, value: String, accent: Int) {
        fillPaint.color = Color.argb(188, 18, 24, 36)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), fillPaint)
        strokePaint.color = Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), strokePaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = sp(10f)
        textPaint.color = Color.argb(205, 208, 216, 228)
        canvas.drawText(label, rect.left + dp(14f), rect.top + dp(18f), textPaint)
        textPaint.textSize = sp(17f)
        textPaint.color = Color.WHITE
        canvas.drawText(value, rect.left + dp(14f), rect.bottom - dp(14f), textPaint)
    }

    private fun drawTutorialHint(canvas: Canvas) {
        val rect = RectF(width * 0.31f, height - dp(146f), width * 0.69f, height - dp(70f))
        fillPaint.color = Color.argb(156, 12, 16, 28)
        canvas.drawRoundRect(rect, dp(20f), dp(20f), fillPaint)
        strokePaint.color = Color.argb(120, 114, 198, 255)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(20f), dp(20f), strokePaint)
        centerTextPaint.textSize = sp(10.5f)
        centerTextPaint.color = Color.WHITE
        drawMultilineText(
            canvas = canvas,
            text = "Move on the left, attack on the right, and fill Burst for the finisher. Clear $maxCampaignRound rounds to win the ladder.",
            left = rect.left + dp(16f),
            top = rect.top + dp(26f),
            maxWidth = rect.width() - dp(32f),
            lineHeight = dp(18f),
            paint = centerTextPaint,
            centered = true,
        )
    }

    private fun drawHeroSilhouette(canvas: Canvas) {
        val baseX = width * 0.76f
        val baseY = height * 0.76f
        fillPaint.color = Color.argb(72, 124, 232, 255)
        canvas.drawCircle(baseX - dp(12f), baseY - dp(126f), dp(62f), fillPaint)
        drawShowcaseStick(canvas, baseX, baseY, 1f, Color.parseColor("#F3F7FF"), Color.parseColor("#69D9FF"))
        drawShowcaseStick(canvas, width * 0.12f, height * 0.84f, -1f, Color.parseColor("#FFECE8"), Color.parseColor("#FF7B8C"), scale = 0.72f)
    }

    private fun drawShowcaseStick(canvas: Canvas, x: Float, y: Float, facing: Float, skin: Int, accent: Int, scale: Float = 1f) {
        val head = dp(20f) * scale
        strokePaint.color = Color.parseColor("#172033")
        strokePaint.strokeWidth = dp(10f) * scale
        canvas.drawLine(x, y - dp(126f) * scale, x, y - dp(54f) * scale, strokePaint)
        canvas.drawLine(x, y - dp(106f) * scale, x + facing * dp(38f) * scale, y - dp(134f) * scale, strokePaint)
        canvas.drawLine(x, y - dp(98f) * scale, x - facing * dp(28f) * scale, y - dp(74f) * scale, strokePaint)
        canvas.drawLine(x, y - dp(54f) * scale, x + facing * dp(24f) * scale, y, strokePaint)
        canvas.drawLine(x, y - dp(54f) * scale, x - facing * dp(18f) * scale, y, strokePaint)
        fillPaint.color = skin
        canvas.drawCircle(x, y - dp(146f) * scale, head, fillPaint)
        strokePaint.color = Color.parseColor("#172033")
        strokePaint.strokeWidth = dp(4f) * scale
        canvas.drawCircle(x, y - dp(146f) * scale, head, strokePaint)
        strokePaint.color = accent
        strokePaint.strokeWidth = dp(4f) * scale
        canvas.drawLine(x - facing * dp(10f) * scale, y - dp(150f) * scale, x + facing * dp(14f) * scale, y - dp(146f) * scale, strokePaint)
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.drawCircle(x, y, size * 0.34f, fillPaint)
        canvas.drawCircle(x + size * 0.26f, y - size * 0.08f, size * 0.28f, fillPaint)
        canvas.drawCircle(x + size * 0.52f, y, size * 0.24f, fillPaint)
        canvas.drawRoundRect(RectF(x - size * 0.18f, y, x + size * 0.64f, y + size * 0.2f), size * 0.12f, size * 0.12f, fillPaint)
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint,
        centered: Boolean = false,
    ) {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val builder = StringBuilder()

        for (word in words) {
            val candidate = if (builder.isEmpty()) word else "${builder} $word"
            if (paint.measureText(candidate) > maxWidth && builder.isNotEmpty()) {
                lines += builder.toString()
                builder.clear()
                builder.append(word)
            } else {
                if (builder.isNotEmpty()) builder.append(" ")
                builder.append(word)
            }
        }
        if (builder.isNotEmpty()) lines += builder.toString()

        lines.forEachIndexed { index, line ->
            val x = if (centered) left + maxWidth * 0.5f else left
            canvas.drawText(line, x, top + index * lineHeight, paint)
        }
    }

    private fun spawnImpact(x: Float, y: Float, accent: Int, strong: Boolean) {
        val count = if (strong) 18 else 10
        repeat(count) {
            val vx = random.nextFloat() * dp(if (strong) 320f else 180f) - dp(if (strong) 160f else 90f)
            val vy = random.nextFloat() * -dp(if (strong) 240f else 160f)
            particles += Particle(x, y, vx, vy, dp(if (strong) 6f else 4f), if (strong) 0.42f else 0.28f, accent)
        }
    }

    private fun spawnGroundBurst(x: Float, y: Float, accent: Int) {
        repeat(7) {
            val vx = random.nextFloat() * dp(160f) - dp(80f)
            val vy = -random.nextFloat() * dp(110f)
            particles += Particle(x, y - dp(8f), vx, vy, dp(3.5f), 0.22f, accent)
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.life -= dt
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            particle.vy += dp(980f) * dt
            particle.vx *= 0.98f
            particle.size *= 0.992f
            if (particle.life <= 0f) iterator.remove()
        }
    }

    private fun loadProgress() {
        highestRoundReached = max(1, prefs.getInt("highest_round", 1))
        lifetimeCoins = prefs.getInt("lifetime_coins", 0)
        championshipClears = prefs.getInt("championship_clears", 0)
        runsStarted = prefs.getInt("runs_started", 0)
        tutorialActive = prefs.getBoolean("tutorial_active", true)
        options =
            GameOptions(
                cinematicShake = prefs.getBoolean("option_shake", true),
                hitPause = prefs.getBoolean("option_hit_pause", true),
                chillMode = prefs.getBoolean("option_chill", true),
            )
    }

    private fun saveProgress() {
        prefs.edit()
            .putInt("highest_round", highestRoundReached)
            .putInt("lifetime_coins", lifetimeCoins)
            .putInt("championship_clears", championshipClears)
            .putInt("runs_started", runsStarted)
            .putBoolean("tutorial_active", tutorialActive)
            .putBoolean("option_shake", options.cinematicShake)
            .putBoolean("option_hit_pause", options.hitPause)
            .putBoolean("option_chill", options.chillMode)
            .apply()
    }

    private fun approach(current: Float, target: Float, amount: Float): Float {
        return when {
            current < target -> min(current + amount, target)
            current > target -> max(current - amount, target)
            else -> target
        }
    }

    private fun blendWithWhite(color: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        val r = (Color.red(color) + (255 - Color.red(color)) * clamped).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * clamped).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * clamped).toInt()
        return Color.rgb(r, g, b)
    }
}
