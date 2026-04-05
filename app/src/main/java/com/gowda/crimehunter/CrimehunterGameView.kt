package com.gowda.crimehunter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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

    private val random = Random(3307)
    private val rooftops = mutableListOf<Rooftop>()
    private val actors = mutableListOf<Actor>()
    private val projectiles = mutableListOf<Projectile>()
    private val particles = mutableListOf<Particle>()
    private val upgradeCards = mutableListOf<UpgradeCard>()

    private var phase = ScenePhase.TITLE
    private var running = false
    private var layoutReady = false
    private var lastFrameNanos = 0L
    private var currentLevel = 1
    private var coins = 0
    private var bestCombo = 0
    private var combo = 0
    private var enemiesRemaining = 0
    private var message = "Scan the rooftops, line up the shot, and keep civilians safe."
    private var gameOverReason = ""
    private var currentAmmo = 3
    private var shotCooldown = 0f
    private var reloadRemaining = 0f
    private var missionTime = 0f
    private var alertTriggered = false
    private var isAiming = false
    private var touchPointerId = MotionEvent.INVALID_POINTER_ID
    private var hasMissionStarted = false
    private var recoilKick = 0f
    private var screenShake = 0f
    private var skylinePulse = 0f
    private var aimPointer = PointF()
    private var playerPosition = PointF()
    private var playerMuzzle = PointF()
    private var titleButton = RectF()
    private var primaryButton = RectF()
    private var secondaryButton = RectF()
    private var optionsButton = RectF()
    private var optionsBackButton = RectF()
    private var hudOptionsButton = RectF()
    private var paceToggleButton = RectF()
    private var aimGuideToggleButton = RectF()
    private var screenShakeToggleButton = RectF()
    private var reloadButton = RectF()
    private var stats = PlayerStats()
    private var gameOptions = GameOptions()
    private var optionsReturnPhase = ScenePhase.TITLE

    private val skinTones = listOf(
        Color.parseColor("#F7D6C1"),
        Color.parseColor("#E8BEA4"),
        Color.parseColor("#C99274"),
        Color.parseColor("#8A5A41"),
    )
    private val enemyShirts = listOf(
        Color.parseColor("#6B1F29"),
        Color.parseColor("#7D2E3B"),
        Color.parseColor("#5C2433"),
    )
    private val civilianShirts = listOf(
        Color.parseColor("#507A93"),
        Color.parseColor("#4A8C74"),
        Color.parseColor("#8D6A4E"),
        Color.parseColor("#5572A5"),
    )
    private val pantColors = listOf(
        Color.parseColor("#1A2434"),
        Color.parseColor("#232B3A"),
        Color.parseColor("#283548"),
    )
    private val hairColors = listOf(
        Color.parseColor("#251A17"),
        Color.parseColor("#3C2B22"),
        Color.parseColor("#5D4735"),
        Color.parseColor("#11161E"),
    )

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
    }
    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val actorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.pathEffect = CornerPathEffect(dp(18f))
        strokePaint.strokeWidth = dp(3f)
        isFocusable = true
        isClickable = true
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
        return if (phase == ScenePhase.TITLE) {
            false
        } else if (phase == ScenePhase.OPTIONS) {
            closeOptions()
            true
        } else {
            resetToTitle()
            true
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
        playerPosition = PointF(w * 0.44f, h * 0.84f)
        aimPointer = PointF(w * 0.67f, h * 0.42f)
        rebuildUiLayout()
        if (phase == ScenePhase.PLAYING) {
            buildMission(currentLevel, keepStats = true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (phase) {
            ScenePhase.TITLE -> handleTitleTouch(event)
            ScenePhase.OPTIONS -> handleOptionsTouch(event)
            ScenePhase.PLAYING -> handlePlayingTouch(event)
            ScenePhase.LEVEL_CLEAR -> handleOverlayTouch(event, continueAction = true)
            ScenePhase.MISSION_FAILED -> handleOverlayTouch(event, continueAction = false)
            ScenePhase.UPGRADES -> handleUpgradeTouch(event)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawWorld(canvas)
        drawHud(canvas)
        drawOverlays(canvas)
    }

    private fun update(dt: Float) {
        skylinePulse += dt
        recoilKick = max(0f, recoilKick - dt * 6f)
        screenShake = max(0f, screenShake - dt * 8f)

        if (phase != ScenePhase.PLAYING) {
            updateParticles(dt)
            return
        }

        val scaledDt = if (isAiming) dt * stats.focusFactor else dt
        missionTime += scaledDt
        shotCooldown = max(0f, shotCooldown - dt)

        if (!hasMissionStarted) {
            hasMissionStarted = true
            message = "Eliminate targets. Miss a civilian and the mission is over."
        }

        if (reloadRemaining > 0f) {
            reloadRemaining = max(0f, reloadRemaining - dt)
            if (reloadRemaining == 0f) {
                currentAmmo = stats.magazineSize
                message = "Fresh magazine. Get back on the scope."
            }
        }

        updateActors(scaledDt)
        updateProjectiles(dt)
        updateParticles(dt)

        if (phase == ScenePhase.PLAYING && enemiesRemaining == 0) {
            bestCombo = max(bestCombo, combo)
            coins += 80 + currentLevel * 12
            message = "Clean sweep. Secure the roofline and move to the next block."
            phase = ScenePhase.LEVEL_CLEAR
            rebuildUiLayout()
        }
    }

    private fun resetToTitle() {
        phase = ScenePhase.TITLE
        optionsReturnPhase = ScenePhase.TITLE
        rooftops.clear()
        actors.clear()
        projectiles.clear()
        particles.clear()
        upgradeCards.clear()
        combo = 0
        currentAmmo = stats.magazineSize
        shotCooldown = 0f
        reloadRemaining = 0f
        message = "Take your time, read the rooftops, and bring every criminal down clean."
        rebuildUiLayout()
    }

    private fun rebuildUiLayout() {
        val buttonWidth = width * 0.18f
        val buttonHeight = height * 0.085f
        val centerX = width * 0.5f
        titleButton = RectF(
            centerX - buttonWidth * 0.5f,
            height * 0.67f,
            centerX + buttonWidth * 0.5f,
            height * 0.67f + buttonHeight,
        )
        optionsButton = RectF(
            centerX - buttonWidth * 0.5f,
            titleButton.bottom + dp(16f),
            centerX + buttonWidth * 0.5f,
            titleButton.bottom + dp(16f) + buttonHeight * 0.9f,
        )
        primaryButton = RectF(
            centerX - buttonWidth * 0.5f,
            height * 0.78f,
            centerX + buttonWidth * 0.5f,
            height * 0.78f + buttonHeight,
        )
        secondaryButton = RectF(
            centerX - buttonWidth * 0.4f,
            primaryButton.bottom + dp(12f),
            centerX + buttonWidth * 0.4f,
            primaryButton.bottom + dp(12f) + buttonHeight * 0.82f,
        )
        reloadButton = RectF(
            width - dp(104f),
            height - dp(92f),
            width - dp(22f),
            height - dp(26f),
        )
        hudOptionsButton = RectF(dp(14f), dp(14f), dp(46f), dp(46f))
        val optionsCardLeft = width * 0.38f
        val optionsCardRight = width * 0.62f
        val optionsTop = height * 0.38f
        val toggleHeight = dp(54f)
        paceToggleButton = RectF(optionsCardLeft, optionsTop, optionsCardRight, optionsTop + toggleHeight)
        aimGuideToggleButton = RectF(optionsCardLeft, paceToggleButton.bottom + dp(12f), optionsCardRight, paceToggleButton.bottom + dp(12f) + toggleHeight)
        screenShakeToggleButton = RectF(optionsCardLeft, aimGuideToggleButton.bottom + dp(12f), optionsCardRight, aimGuideToggleButton.bottom + dp(12f) + toggleHeight)
        optionsBackButton = RectF(optionsCardLeft, screenShakeToggleButton.bottom + dp(18f), optionsCardRight, screenShakeToggleButton.bottom + dp(18f) + dp(46f))
    }

    private fun closeOptions() {
        phase = optionsReturnPhase
        if (phase == ScenePhase.TITLE) {
            message = "Plan the shot, then clear the block."
        }
        rebuildUiLayout()
    }

    private fun drawBackground(canvas: Canvas) {
        skyPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.parseColor("#55C9FF"),
                Color.parseColor("#2EA6F6"),
                Color.parseColor("#2D8AE8"),
                Color.parseColor("#6EC8FF"),
            ),
            floatArrayOf(0f, 0.48f, 0.82f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)
        skyPaint.shader = null

        glowPaint.shader = RadialGradient(
            width * 0.58f,
            height * 0.18f,
            min(width, height) * 0.23f,
            intArrayOf(Color.argb(110, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(width * 0.58f, height * 0.18f, min(width, height) * 0.2f, glowPaint)
        glowPaint.shader = null

        fillPaint.color = Color.argb(120, 255, 255, 255)
        drawCloud(canvas, width * 0.14f, height * 0.12f, dp(40f))
        drawCloud(canvas, width * 0.33f, height * 0.18f, dp(32f))
        drawCloud(canvas, width * 0.62f, height * 0.14f, dp(46f))
        drawCloud(canvas, width * 0.82f, height * 0.2f, dp(30f))

        fillPaint.color = Color.parseColor("#4C9BE8")
        canvas.drawRect(0f, height * 0.5f, width.toFloat(), height * 0.6f, fillPaint)

        val layers = listOf(
            Triple(height * 0.56f, Color.parseColor("#88ACD3"), 0.08f),
            Triple(height * 0.60f, Color.parseColor("#A4C0DD"), 0.11f),
            Triple(height * 0.64f, Color.parseColor("#BDD2E8"), 0.14f),
        )
        layers.forEachIndexed { index, (baseTop, color, blockWidth) ->
            fillPaint.color = color
            var x = -width * 0.04f
            while (x < width * 1.08f) {
                val wave = sin((x / width * PI * 4) + skylinePulse * (0.18f + index * 0.06f)).toFloat()
                val heightFactor = 0.07f + index * 0.04f + abs(wave) * 0.05f
                val top = baseTop - this.height * heightFactor
                canvas.drawRoundRect(
                    RectF(x, top, x + width * blockWidth, height.toFloat()),
                    dp(6f),
                    dp(6f),
                    fillPaint,
                )
                x += width * (blockWidth + 0.02f)
            }
        }

        fillPaint.color = Color.parseColor("#242833")
        canvas.drawRect(0f, height * 0.62f, width.toFloat(), height.toFloat(), fillPaint)
        strokePaint.color = Color.argb(70, 255, 255, 255)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawLine(width * 0.12f, height * 0.64f, width * 0.04f, height.toFloat(), strokePaint)
        canvas.drawLine(width * 0.32f, height * 0.64f, width * 0.26f, height.toFloat(), strokePaint)
        canvas.drawLine(width * 0.5f, height * 0.64f, width * 0.5f, height.toFloat(), strokePaint)
        canvas.drawLine(width * 0.68f, height * 0.64f, width * 0.74f, height.toFloat(), strokePaint)
        canvas.drawLine(width * 0.88f, height * 0.64f, width * 0.96f, height.toFloat(), strokePaint)
    }

    private fun drawWorld(canvas: Canvas) {
        rooftops.forEachIndexed { index, rooftop ->
            drawRoofVolume(canvas, rooftop, index)
        }

        drawPlayer(canvas)
        drawActors(canvas)
        drawProjectiles(canvas)
        drawParticles(canvas)
        if (phase == ScenePhase.PLAYING && isAiming) {
            drawScope(canvas)
        }
    }

    private fun drawHud(canvas: Canvas) {
        if (phase == ScenePhase.TITLE || phase == ScenePhase.OPTIONS) {
            return
        }

        drawIconButton(canvas, hudOptionsButton, "II")
        drawMissionChip(canvas)
        drawCoinCounter(canvas)
        drawAmmoPanel(canvas)
        if (phase == ScenePhase.PLAYING) {
            drawStatusChip(canvas)
        }
    }

    private fun drawOverlays(canvas: Canvas) {
        when (phase) {
            ScenePhase.TITLE -> drawTitleOverlay(canvas)
            ScenePhase.OPTIONS -> drawOptionsOverlay(canvas)
            ScenePhase.LEVEL_CLEAR -> drawResultOverlay(
                canvas = canvas,
                eyebrow = "BLOCK SECURED",
                title = "Roofline Clear",
                description = "Every hostile is down. Cash the bounty, steady your hands, and move to the next shot.",
                primary = "Next Mission",
                secondary = "Main Menu",
                accent = Color.parseColor("#65F4D6"),
            )
            ScenePhase.MISSION_FAILED -> drawResultOverlay(
                canvas = canvas,
                eyebrow = "MISSION FAILED",
                title = "Shot Collapsed",
                description = gameOverReason,
                primary = "Retry Mission",
                secondary = "Main Menu",
                accent = Color.parseColor("#FF7A7A"),
            )
            ScenePhase.UPGRADES -> drawUpgradeOverlay(canvas)
            ScenePhase.PLAYING -> Unit
        }
    }

    private fun handleTitleTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        when {
            titleButton.contains(event.x, event.y) -> {
                stats = PlayerStats()
                coins = 0
                bestCombo = 0
                currentLevel = 1
                buildMission(currentLevel, keepStats = false)
            }

            optionsButton.contains(event.x, event.y) -> {
                optionsReturnPhase = ScenePhase.TITLE
                phase = ScenePhase.OPTIONS
                message = "Tune the mission pace and aiming feel before you deploy."
            }
        }
    }

    private fun handleOptionsTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        when {
            paceToggleButton.contains(event.x, event.y) -> {
                gameOptions.tacticalPace = !gameOptions.tacticalPace
            }

            aimGuideToggleButton.contains(event.x, event.y) -> {
                gameOptions.aimGuide = !gameOptions.aimGuide
            }

            screenShakeToggleButton.contains(event.x, event.y) -> {
                gameOptions.screenShake = !gameOptions.screenShake
            }

            optionsBackButton.contains(event.x, event.y) -> {
                closeOptions()
            }
        }
    }

    private fun handlePlayingTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hudOptionsButton.contains(event.x, event.y)) {
                    isAiming = false
                    touchPointerId = MotionEvent.INVALID_POINTER_ID
                    optionsReturnPhase = ScenePhase.PLAYING
                    phase = ScenePhase.OPTIONS
                    return
                }

                if (reloadButton.contains(event.x, event.y)) {
                    beginReload()
                    return
                }

                touchPointerId = event.getPointerId(0)
                isAiming = true
                aimPointer.set(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(touchPointerId)
                if (index >= 0 && isAiming) {
                    aimPointer.set(event.getX(index), event.getY(index))
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (isAiming) {
                    aimPointer.set(event.x, event.y)
                    fireToward(event.x, event.y)
                }
                isAiming = false
                touchPointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
    }

    private fun handleOverlayTouch(event: MotionEvent, continueAction: Boolean) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        when {
            primaryButton.contains(event.x, event.y) -> {
                if (continueAction) {
                    if (currentLevel % 2 == 0) {
                        buildUpgrades()
                    } else {
                        currentLevel += 1
                        buildMission(currentLevel, keepStats = true)
                    }
                } else {
                    buildMission(currentLevel, keepStats = true)
                }
            }
            secondaryButton.contains(event.x, event.y) -> resetToTitle()
        }
    }

    private fun handleUpgradeTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_UP) return
        val card = upgradeCards.firstOrNull { it.rect.contains(event.x, event.y) } ?: return
        applyUpgrade(card.type)
    }

    private fun buildMission(level: Int, keepStats: Boolean) {
        if (!layoutReady) return

        if (!keepStats) {
            stats = PlayerStats()
            coins = 0
            bestCombo = 0
        }

        phase = ScenePhase.PLAYING
        currentLevel = level
        rooftops.clear()
        actors.clear()
        projectiles.clear()
        particles.clear()
        upgradeCards.clear()
        combo = 0
        enemiesRemaining = 0
        currentAmmo = stats.magazineSize
        shotCooldown = 0f
        reloadRemaining = 0f
        missionTime = 0f
        alertTriggered = false
        hasMissionStarted = false
        gameOverReason = ""

        val leftRoofTop = height * 0.84f
        val enemyTops = floatArrayOf(height * 0.77f, height * 0.69f, height * 0.61f, height * 0.53f)
        rooftops += Rooftop(
            left = width * 0.24f,
            right = width * 0.56f,
            top = leftRoofTop,
            exitLeft = width * 0.18f,
            exitRight = width * 0.62f,
            accent = Color.parseColor("#B47C52"),
        )

        val accents = listOf("#B7CBDD", "#E7BE74", "#DCCAA4", "#CCD7E4")
        val centers = listOf(0.18f, 0.38f, 0.60f, 0.82f)
        centers.forEachIndexed { index, fraction ->
            val roofWidth = width * (0.11f + random.nextFloat() * 0.05f)
            val centerX = width * fraction
            rooftops += Rooftop(
                left = centerX - roofWidth * 0.5f,
                right = centerX + roofWidth * 0.5f,
                top = enemyTops[index],
                exitLeft = centerX - roofWidth * 0.5f - dp(24f),
                exitRight = centerX + roofWidth * 0.5f + dp(24f),
                accent = Color.parseColor(accents[index % accents.size]),
            )
        }

        playerPosition = PointF(width * 0.44f, height * 0.84f)
        aimPointer = PointF(width * 0.67f, height * 0.42f)

        val enemyCount = min(2 + level / 2, 6)
        val civilianCount = when {
            level < 2 -> 0
            level < 5 -> 1
            else -> 2
        }
        val paceSpeed = if (gameOptions.tacticalPace) 30f else 48f
        val paceDelay = if (gameOptions.tacticalPace) 1.2f else 0.45f

        val spots = mutableListOf<Pair<Int, Float>>()
        rooftops.drop(1).forEachIndexed { index, roof ->
            val roofIndex = index + 1
            spots += roofIndex to (roof.left + roof.width * 0.24f)
            spots += roofIndex to roof.centerX
            spots += roofIndex to (roof.right - roof.width * 0.24f)
        }
        spots.shuffle(random)

        repeat(enemyCount) {
            val (roofIndex, x) = spots.removeAt(0)
            val roof = rooftops[roofIndex]
            val style = buildActorPalette(isEnemy = true)
            actors += Actor(
                role = ActorRole.ENEMY,
                roofIndex = roofIndex,
                radius = dp(20f),
                walkSpeed = dp(paceSpeed + currentLevel * 2.6f),
                skinTone = style[0],
                shirtColor = style[1],
                pantsColor = style[2],
                hairColor = style[3],
                x = x,
                y = roof.top,
                targetX = if (x < roof.centerX) roof.exitLeft else roof.exitRight,
                reactionDelay = paceDelay + random.nextFloat() * 1.15f,
            )
            enemiesRemaining += 1
        }

        repeat(min(civilianCount, spots.size)) {
            val (roofIndex, x) = spots.removeAt(0)
            val roof = rooftops[roofIndex]
            val style = buildActorPalette(isEnemy = false)
            actors += Actor(
                role = ActorRole.CIVILIAN,
                roofIndex = roofIndex,
                radius = dp(18f),
                walkSpeed = 0f,
                skinTone = style[0],
                shirtColor = style[1],
                pantsColor = style[2],
                hairColor = style[3],
                x = x,
                y = roof.top,
                targetX = x,
                reactionDelay = 0f,
            )
        }

        message = "Mission $currentLevel. Breathe, track the criminals, protect civilians, and take the clean shot."
        rebuildUiLayout()
    }

    private fun updateActors(dt: Float) {
        actors.forEach { actor ->
            if (!actor.isAlive) return@forEach
            actor.animationTime += dt

            if (actor.role == ActorRole.CIVILIAN) {
                actor.crouchAmount = if (alertTriggered) min(1f, actor.crouchAmount + dt * 1.8f) else max(0f, actor.crouchAmount - dt)
                return@forEach
            }

            if (!alertTriggered) return@forEach
            actor.reactionDelay -= dt
            if (actor.reactionDelay > 0f) return@forEach

            val direction = if (actor.targetX >= actor.x) 1f else -1f
            actor.x += direction * actor.walkSpeed * dt
            if ((direction > 0f && actor.x >= actor.targetX) || (direction < 0f && actor.x <= actor.targetX)) {
                actor.isAlive = false
                actor.isEscaped = true
                triggerMissionFail("A target slipped off the roof. Mission blown.")
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val projectile = iterator.next()
            projectile.life -= dt
            projectile.previousX = projectile.x
            projectile.previousY = projectile.y
            projectile.x += projectile.vx * dt
            projectile.y += projectile.vy * dt

            val hit = firstHitActor(projectile)
            if (hit != null) {
                handleActorHit(hit)
                iterator.remove()
                continue
            }

            val offscreen =
                projectile.x < -dp(80f) ||
                    projectile.x > width + dp(80f) ||
                    projectile.y < -dp(80f) ||
                    projectile.y > height + dp(80f)
            if (projectile.life <= 0f || offscreen) {
                iterator.remove()
            }
        }
    }

    private fun beginReload() {
        if (reloadRemaining > 0f || currentAmmo == stats.magazineSize) return
        reloadRemaining = stats.reloadTime
        message = "Reloading. Keep your head down."
    }

    private fun fireToward(targetX: Float, targetY: Float) {
        if (phase != ScenePhase.PLAYING || shotCooldown > 0f || reloadRemaining > 0f) return
        if (currentAmmo <= 0) {
            beginReload()
            return
        }

        alertTriggered = true
        currentAmmo -= 1
        shotCooldown = 0.22f
        recoilKick = 1f
        screenShake = if (gameOptions.screenShake) dp(12f) else 0f

        val muzzle = computeMuzzlePoint()
        playerMuzzle = muzzle
        val angle = atan2(targetY - muzzle.y, targetX - muzzle.x)
        val spread = (random.nextFloat() - 0.5f) * stats.spreadRadians
        val shotAngle = angle + spread
        val speed = dp(1750f)
        projectiles += Projectile(
            x = muzzle.x,
            y = muzzle.y,
            previousX = muzzle.x,
            previousY = muzzle.y,
            vx = cos(shotAngle) * speed,
            vy = sin(shotAngle) * speed,
        )
        spawnImpact(muzzle.x, muzzle.y, enemyImpact = true, small = true)

        if (currentAmmo == 0) {
            beginReload()
        } else {
            message = "Shot out. Targets are moving."
        }
    }

    private fun triggerMissionFail(reason: String) {
        if (phase != ScenePhase.PLAYING) return
        combo = 0
        message = reason
        gameOverReason = reason
        phase = ScenePhase.MISSION_FAILED
        rebuildUiLayout()
    }

    private fun buildUpgrades() {
        phase = ScenePhase.UPGRADES
        upgradeCards.clear()
        val options = UpgradeType.entries.shuffled(random).take(3)
        val cardWidth = width * 0.22f
        val cardHeight = height * 0.3f
        val spacing = width * 0.04f
        val startX = (width - (cardWidth * 3 + spacing * 2)) * 0.5f
        val top = height * 0.34f
        val accents = listOf(Color.parseColor("#36CFC9"), Color.parseColor("#7C4DFF"), Color.parseColor("#FFB74D"))
        options.forEachIndexed { index, type ->
            val left = startX + index * (cardWidth + spacing)
            upgradeCards += UpgradeCard(
                type = type,
                rect = RectF(left, top, left + cardWidth, top + cardHeight),
                accent = accents[index % accents.size],
            )
        }
        message = "Choose your next edge before the next rooftop opens up."
        rebuildUiLayout()
    }

    private fun applyUpgrade(type: UpgradeType) {
        when (type) {
            UpgradeType.MAGAZINE -> stats.magazineSize += 1
            UpgradeType.FAST_HANDS -> stats.reloadTime = max(0.6f, stats.reloadTime - 0.15f)
            UpgradeType.FOCUS -> stats.focusFactor = max(0.5f, stats.focusFactor - 0.08f)
            UpgradeType.PRECISION -> stats.spreadRadians = max(0.01f, stats.spreadRadians - 0.005f)
            UpgradeType.PAYOUT -> stats.bountyMultiplier += 0.15f
        }
        currentLevel += 1
        buildMission(currentLevel, keepStats = true)
    }

    private fun firstHitActor(projectile: Projectile): Actor? {
        var closestActor: Actor? = null
        var closestDistance = Float.MAX_VALUE

        actors.forEach { actor ->
            if (!actor.isAlive) return@forEach
            val bodyCenterY = actor.y - actor.radius * 1.2f - actor.crouchAmount * actor.radius * 0.3f
            val headCenterY = bodyCenterY - actor.radius * 0.9f
            val bodyDistance = segmentDistanceSquared(
                projectile.previousX,
                projectile.previousY,
                projectile.x,
                projectile.y,
                actor.x,
                bodyCenterY,
            )
            val headDistance = segmentDistanceSquared(
                projectile.previousX,
                projectile.previousY,
                projectile.x,
                projectile.y,
                actor.x,
                headCenterY,
            )
            val limit = actor.radius * actor.radius
            if (bodyDistance <= limit || headDistance <= limit * 0.55f) {
                val dist = hypot(projectile.previousX - actor.x, projectile.previousY - bodyCenterY)
                if (dist < closestDistance) {
                    closestDistance = dist
                    closestActor = actor
                }
            }
        }

        return closestActor
    }

    private fun handleActorHit(actor: Actor) {
        actor.isAlive = false
        combo += 1
        bestCombo = max(bestCombo, combo)
        screenShake = if (gameOptions.screenShake) dp(10f) else 0f
        recoilKick = 1f
        spawnImpact(actor.x, actor.y - actor.radius, actor.role == ActorRole.ENEMY)

        if (actor.role == ActorRole.CIVILIAN) {
            triggerMissionFail("You hit a civilian. Abort and reset the block.")
            return
        }

        enemiesRemaining -= 1
        coins += (40 * stats.bountyMultiplier).toInt() + currentLevel * 3
        message = if (enemiesRemaining > 0) {
            "Target dropped. Keep the angle and clear the block."
        } else {
            "Last target down. Rooftop secure."
        }
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.life -= dt
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            particle.vy += dp(140f) * dt
            if (particle.life <= 0f) {
                iterator.remove()
            }
        }
    }

    private fun spawnImpact(
        x: Float,
        y: Float,
        enemyImpact: Boolean,
        small: Boolean = false,
    ) {
        val baseColor = if (enemyImpact) Color.parseColor("#FFB347") else Color.parseColor("#E57373")
        val count = if (small) 6 else 12
        repeat(count) {
            val angle = random.nextFloat() * (PI * 2f).toFloat()
            val speed = dp(if (small) 50f else 140f) * (0.6f + random.nextFloat())
            particles += Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                radius = dp(if (small) 2.5f else 4f),
                life = if (small) 0.25f else 0.55f,
                color = baseColor,
            )
        }
    }

    private fun computeMuzzlePoint(): PointF {
        val angle = atan2(aimPointer.y - playerPosition.y, aimPointer.x - playerPosition.x)
        val shoulderX = playerPosition.x - dp(8f) - cos(angle) * recoilKick * dp(5f)
        val shoulderY = playerPosition.y - dp(58f) - sin(angle) * recoilKick * dp(5f)
        return PointF(
            shoulderX + cos(angle) * dp(88f),
            shoulderY + sin(angle) * dp(88f),
        )
    }

    private fun segmentDistanceSquared(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        px: Float,
        py: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            val distX = px - x1
            val distY = py - y1
            return distX * distX + distY * distY
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val cx = x1 + dx * t
        val cy = y1 + dy * t
        val distX = px - cx
        val distY = py - cy
        return distX * distX + distY * distY
    }

    private fun roofPerspectiveScale(roofIndex: Int): Float {
        return when (roofIndex) {
            0 -> 1.18f
            1 -> 1f
            2 -> 0.88f
            3 -> 0.77f
            else -> 0.68f
        }
    }

    private fun drawRoofVolume(canvas: Canvas, rooftop: Rooftop, index: Int) {
        val scale = roofPerspectiveScale(index)
        val depth = dp(34f) * scale
        val inset = dp(18f) * scale
        val frontPath = Path().apply {
            moveTo(rooftop.left, rooftop.top)
            lineTo(rooftop.right, rooftop.top)
            lineTo(rooftop.right, height.toFloat())
            lineTo(rooftop.left, height.toFloat())
            close()
        }
        val topPath = Path().apply {
            moveTo(rooftop.left, rooftop.top)
            lineTo(rooftop.right, rooftop.top)
            lineTo(rooftop.right - inset, rooftop.top - depth)
            lineTo(rooftop.left + inset, rooftop.top - depth)
            close()
        }
        val sidePath = Path().apply {
            moveTo(rooftop.right, rooftop.top)
            lineTo(rooftop.right - inset, rooftop.top - depth)
            lineTo(rooftop.right - inset, height.toFloat() - depth * 0.22f)
            lineTo(rooftop.right, height.toFloat())
            close()
        }

        val frontColor = if (index == 0) Color.parseColor("#A7734F") else blendWithWhite(rooftop.accent, 0.18f)
        val topColor = if (index == 0) Color.parseColor("#C89367") else blendWithWhite(rooftop.accent, 0.05f)
        val sideColor = shadeColor(frontColor, 0.82f)

        fillPaint.color = frontColor
        canvas.drawPath(frontPath, fillPaint)
        fillPaint.color = topColor
        canvas.drawPath(topPath, fillPaint)
        fillPaint.color = sideColor
        canvas.drawPath(sidePath, fillPaint)

        fillPaint.color = Color.argb(54, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(rooftop.left + dp(3f), rooftop.top - dp(2f), rooftop.right - dp(3f), rooftop.top + dp(6f)),
            dp(4f),
            dp(4f),
            fillPaint,
        )

        if (index > 0) {
            val windowWidth = max(dp(10f), rooftop.width * 0.12f)
            val windowHeight = max(dp(14f), depth * 0.42f)
            val gap = max(dp(9f), rooftop.width * 0.08f)
            var windowLeft = rooftop.left + rooftop.width * 0.12f
            val windowTop = rooftop.top + dp(28f)
            fillPaint.color = Color.argb(96, 112, 144, 176)
            while (windowLeft + windowWidth < rooftop.right - rooftop.width * 0.1f) {
                canvas.drawRoundRect(
                    RectF(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight),
                    dp(3f),
                    dp(3f),
                    fillPaint,
                )
                windowLeft += windowWidth + gap
            }

            fillPaint.color = shadeColor(frontColor, 0.72f)
            canvas.drawRoundRect(
                RectF(
                    rooftop.left + rooftop.width * 0.16f,
                    rooftop.top - depth * 0.5f,
                    rooftop.left + rooftop.width * 0.32f,
                    rooftop.top - depth * 0.14f,
                ),
                dp(6f),
                dp(6f),
                fillPaint,
            )
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        if (!layoutReady) return

        val shakeX = if (screenShake > 0f) (random.nextFloat() - 0.5f) * screenShake else 0f
        val shakeY = if (screenShake > 0f) (random.nextFloat() - 0.5f) * screenShake else 0f
        val baseX = playerPosition.x + shakeX
        val baseY = playerPosition.y + shakeY
        val aimAngle = atan2(aimPointer.y - baseY, aimPointer.x - baseX)
        val recoilOffset = recoilKick * dp(16f)

        val shoulderX = baseX + cos(aimAngle) * dp(6f) - cos(aimAngle) * recoilOffset * 0.2f
        val shoulderY = baseY - dp(60f) + sin(aimAngle) * dp(2f) - sin(aimAngle) * recoilOffset * 0.2f
        val rifleLength = dp(96f)
        playerMuzzle = PointF(
            shoulderX + cos(aimAngle) * rifleLength,
            shoulderY + sin(aimAngle) * rifleLength,
        )

        drawCrate(canvas, baseX - dp(72f), baseY - dp(20f), dp(54f), dp(54f))
        drawCrate(canvas, baseX + dp(20f), baseY - dp(20f), dp(54f), dp(54f))

        actorPaint.color = Color.parseColor("#F1E9E3")
        canvas.drawCircle(baseX, baseY - dp(92f), dp(18f), actorPaint)
        actorPaint.color = Color.parseColor("#1A1F27")
        canvas.drawArc(RectF(baseX - dp(18f), baseY - dp(105f), baseX + dp(18f), baseY - dp(84f)), 180f, 180f, true, actorPaint)

        actorPaint.color = Color.parseColor("#6B2A2A")
        canvas.drawRoundRect(RectF(baseX - dp(10f), baseY - dp(76f), baseX + dp(10f), baseY - dp(28f)), dp(10f), dp(10f), actorPaint)
        actorPaint.color = Color.parseColor("#EFEFEF")
        canvas.drawRoundRect(RectF(baseX - dp(13f), baseY - dp(74f), baseX + dp(13f), baseY - dp(64f)), dp(8f), dp(8f), actorPaint)

        strokePaint.color = Color.parseColor("#3A2230")
        strokePaint.strokeWidth = dp(8f)
        canvas.drawLine(baseX - dp(5f), baseY - dp(28f), baseX - dp(13f), baseY + dp(14f), strokePaint)
        canvas.drawLine(baseX + dp(5f), baseY - dp(28f), baseX + dp(13f), baseY + dp(14f), strokePaint)
        canvas.drawLine(baseX - dp(8f), baseY - dp(62f), baseX - dp(30f), baseY - dp(42f), strokePaint)
        canvas.drawLine(baseX + dp(8f), baseY - dp(62f), baseX + dp(28f), baseY - dp(48f), strokePaint)

        strokePaint.color = Color.parseColor("#231A1A")
        strokePaint.strokeWidth = dp(9f)
        canvas.drawLine(shoulderX, shoulderY, playerMuzzle.x, playerMuzzle.y, strokePaint)
        fillPaint.color = Color.parseColor("#D2A13A")
        canvas.drawCircle(shoulderX + cos(aimAngle) * dp(22f), shoulderY + sin(aimAngle) * dp(22f), dp(16f), fillPaint)
        strokePaint.color = Color.parseColor("#3B2704")
        strokePaint.strokeWidth = dp(2.5f)
        canvas.drawCircle(shoulderX + cos(aimAngle) * dp(22f), shoulderY + sin(aimAngle) * dp(22f), dp(16f), strokePaint)
    }

    private fun drawActors(canvas: Canvas) {
        actors.forEach { actor ->
            if (!actor.isAlive || actor.isEscaped) return@forEach

            val scale = roofPerspectiveScale(actor.roofIndex)
            val bodyCenterY = actor.y - actor.radius * (1.12f + (1f - scale) * 0.2f) - actor.crouchAmount * actor.radius * 0.34f
            val headCenterY = bodyCenterY - actor.radius * 1.02f
            val shoulderY = bodyCenterY - actor.radius * 0.32f
            val shoulderWidth = actor.radius * (0.72f + scale * 0.08f)
            val torsoBottom = bodyCenterY + actor.radius * 0.86f
            val armSwing = sin(actor.animationTime * 5.5f) * actor.radius * 0.1f

            actorPaint.color = Color.argb(58, 6, 10, 14)
            canvas.drawOval(
                RectF(
                    actor.x - actor.radius * 0.55f,
                    actor.y - actor.radius * 0.12f,
                    actor.x + actor.radius * 0.55f,
                    actor.y + actor.radius * 0.16f,
                ),
                actorPaint,
            )

            actorPaint.color = actor.skinTone
            canvas.drawCircle(actor.x, headCenterY, actor.radius * 0.44f, actorPaint)
            actorPaint.color = actor.hairColor
            canvas.drawArc(
                RectF(actor.x - actor.radius * 0.48f, headCenterY - actor.radius * 0.52f, actor.x + actor.radius * 0.48f, headCenterY + actor.radius * 0.2f),
                185f,
                170f,
                true,
                actorPaint,
            )
            fillPaint.color = adjustAlpha(Color.BLACK, 0.28f)
            canvas.drawOval(
                RectF(
                    actor.x - actor.radius * 0.16f,
                    headCenterY + actor.radius * 0.04f,
                    actor.x + actor.radius * 0.16f,
                    headCenterY + actor.radius * 0.22f,
                ),
                fillPaint,
            )
            fillPaint.color = Color.parseColor("#1A202C")
            canvas.drawCircle(actor.x - actor.radius * 0.14f, headCenterY - actor.radius * 0.02f, actor.radius * 0.04f, fillPaint)
            canvas.drawCircle(actor.x + actor.radius * 0.14f, headCenterY - actor.radius * 0.02f, actor.radius * 0.04f, fillPaint)
            actorPaint.color = actor.shirtColor
            canvas.drawRoundRect(
                RectF(actor.x - shoulderWidth, shoulderY, actor.x + shoulderWidth, torsoBottom),
                actor.radius * 0.26f,
                actor.radius * 0.26f,
                actorPaint,
            )
            actorPaint.color = adjustAlpha(Color.WHITE, 0.12f)
            canvas.drawRoundRect(
                RectF(actor.x - shoulderWidth * 0.78f, shoulderY + actor.radius * 0.08f, actor.x + shoulderWidth * 0.4f, shoulderY + actor.radius * 0.34f),
                actor.radius * 0.2f,
                actor.radius * 0.2f,
                actorPaint,
            )

            actorPaint.color = actor.skinTone
            canvas.drawRect(actor.x - actor.radius * 0.1f, headCenterY + actor.radius * 0.34f, actor.x + actor.radius * 0.1f, shoulderY + actor.radius * 0.08f, actorPaint)

            strokePaint.color = actor.skinTone
            strokePaint.strokeWidth = actor.radius * 0.18f
            canvas.drawLine(actor.x - shoulderWidth * 0.98f, shoulderY + actor.radius * 0.18f, actor.x - shoulderWidth * 1.18f, shoulderY + actor.radius * 0.8f + armSwing, strokePaint)
            canvas.drawLine(actor.x + shoulderWidth * 0.98f, shoulderY + actor.radius * 0.18f, actor.x + shoulderWidth * 1.14f, shoulderY + actor.radius * 0.78f - armSwing, strokePaint)

            strokePaint.color = actor.pantsColor
            strokePaint.strokeWidth = actor.radius * 0.26f
            canvas.drawLine(actor.x - actor.radius * 0.22f, torsoBottom, actor.x - actor.radius * 0.34f, actor.y, strokePaint)
            canvas.drawLine(actor.x + actor.radius * 0.22f, torsoBottom, actor.x + actor.radius * 0.34f, actor.y, strokePaint)

            if (actor.role == ActorRole.ENEMY) {
                actorPaint.color = Color.parseColor("#0F141A")
                canvas.drawRoundRect(
                    RectF(actor.x + shoulderWidth * 0.25f, shoulderY + actor.radius * 0.14f, actor.x + shoulderWidth * 1.22f, shoulderY + actor.radius * 0.28f),
                    actor.radius * 0.08f,
                    actor.radius * 0.08f,
                    actorPaint,
                )
            }
        }
    }

    private fun drawProjectiles(canvas: Canvas) {
        strokePaint.color = Color.parseColor("#FFD166")
        strokePaint.strokeWidth = dp(4f)
        projectiles.forEach { projectile ->
            canvas.drawLine(projectile.previousX, projectile.previousY, projectile.x, projectile.y, strokePaint)
            fillPaint.color = Color.WHITE
            canvas.drawCircle(projectile.x, projectile.y, dp(4f), fillPaint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { particle ->
            fillPaint.color = particle.color
            fillPaint.alpha = (particle.life * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(particle.x, particle.y, particle.radius, fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawScope(canvas: Canvas) {
        val radius = min(width, height) * 0.16f
        fillPaint.color = Color.argb(40, 255, 255, 255)
        canvas.drawCircle(aimPointer.x, aimPointer.y, radius - dp(6f), fillPaint)
        strokePaint.color = Color.BLACK
        strokePaint.strokeWidth = dp(8f)
        canvas.drawCircle(aimPointer.x, aimPointer.y, radius, strokePaint)
        strokePaint.strokeWidth = dp(3f)
        canvas.drawLine(aimPointer.x - radius, aimPointer.y, aimPointer.x - dp(22f), aimPointer.y, strokePaint)
        canvas.drawLine(aimPointer.x + dp(22f), aimPointer.y, aimPointer.x + radius, aimPointer.y, strokePaint)
        canvas.drawLine(aimPointer.x, aimPointer.y - radius, aimPointer.x, aimPointer.y - dp(22f), strokePaint)
        canvas.drawLine(aimPointer.x, aimPointer.y + dp(22f), aimPointer.x, aimPointer.y + radius, strokePaint)
        fillPaint.color = Color.parseColor("#E94A3F")
        canvas.drawCircle(aimPointer.x, aimPointer.y, dp(4f), fillPaint)
        if (gameOptions.aimGuide) {
            strokePaint.color = Color.argb(70, 0, 0, 0)
            strokePaint.strokeWidth = dp(2f)
            canvas.drawLine(playerMuzzle.x, playerMuzzle.y, aimPointer.x, aimPointer.y, strokePaint)
        }
    }

    private fun drawAmmoPanel(canvas: Canvas) {
        val rect = RectF(dp(22f), height - dp(88f), dp(146f), height - dp(24f))
        cardPaint.color = Color.argb(210, 12, 16, 22)
        canvas.drawRoundRect(rect, dp(22f), dp(22f), cardPaint)
        strokePaint.color = Color.argb(90, 255, 255, 255)
        strokePaint.strokeWidth = dp(1.8f)
        canvas.drawRoundRect(rect, dp(22f), dp(22f), strokePaint)

        textPaint.textSize = sp(11f)
        textPaint.color = Color.parseColor("#F7F8FA")
        canvas.drawText("AMMO", rect.left + dp(16f), rect.top + dp(18f), textPaint)

        val bulletGap = dp(14f)
        val bulletTop = rect.top + dp(28f)
        repeat(stats.magazineSize) { index ->
            val left = rect.left + dp(16f) + index * bulletGap
            fillPaint.color = if (index < currentAmmo) Color.parseColor("#FFD94D") else Color.argb(70, 255, 255, 255)
            canvas.drawRoundRect(RectF(left, bulletTop, left + dp(8f), bulletTop + dp(18f)), dp(3f), dp(3f), fillPaint)
        }

        drawButton(canvas, reloadButton, if (reloadRemaining > 0f) "Reloading" else "Reload", Color.parseColor("#F4C63D"), if (reloadRemaining > 0f) "${(100 * (1f - reloadRemaining / stats.reloadTime)).toInt()}%" else "tap")
    }

    private fun drawTitleOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(72, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val card = RectF(width * 0.1f, height * 0.12f, width * 0.5f, height * 0.58f)
        cardPaint.color = Color.argb(206, 21, 33, 52)
        canvas.drawRoundRect(card, dp(24f), dp(24f), cardPaint)
        cardPaint.shader = null
        strokePaint.color = Color.argb(120, 160, 210, 255)
        strokePaint.strokeWidth = dp(1.8f)
        canvas.drawRoundRect(card, dp(24f), dp(24f), strokePaint)

        centerTextPaint.textAlign = Paint.Align.LEFT
        centerTextPaint.textSize = sp(13f)
        centerTextPaint.color = Color.parseColor("#BCE4FF")
        canvas.drawText("CRIMEHUNTER", card.left + dp(24f), card.top + dp(34f), centerTextPaint)

        textPaint.textSize = sp(34f)
        textPaint.color = Color.WHITE
        canvas.drawText("Sniper Ops", card.left + dp(24f), card.top + dp(76f), textPaint)

        textPaint.textSize = sp(15f)
        textPaint.color = Color.parseColor("#E5F1FF")
        drawMultilineText(canvas, "Track thugs across the block, protect civilians, and land the shot before the target escapes.", card.left + dp(24f), card.top + dp(112f), card.width() - dp(48f), textPaint, dp(22f))

        drawFeatureStrip(canvas, card.left + dp(24f), card.top + dp(214f), "Bright 3D city look")
        drawFeatureStrip(canvas, card.left + dp(24f), card.top + dp(254f), "Compact HUD and quick shots")
        drawFeatureStrip(canvas, card.left + dp(24f), card.top + dp(294f), "Clean mission flow")

        drawButton(canvas, titleButton, "Play", Color.parseColor("#A7F231"), "start")
        drawButton(canvas, optionsButton, "Options", Color.parseColor("#4BA7FF"), "settings")

        val heroX = width * 0.76f
        val heroY = height * 0.76f
        drawCrate(canvas, heroX - dp(70f), heroY - dp(18f), dp(58f), dp(58f))
        drawCrate(canvas, heroX + dp(22f), heroY - dp(18f), dp(58f), dp(58f))
        actorPaint.color = Color.parseColor("#F1E9E3")
        canvas.drawCircle(heroX, heroY - dp(92f), dp(18f), actorPaint)
        actorPaint.color = Color.parseColor("#18202A")
        canvas.drawArc(RectF(heroX - dp(18f), heroY - dp(104f), heroX + dp(18f), heroY - dp(82f)), 180f, 180f, true, actorPaint)
        actorPaint.color = Color.parseColor("#6B2A2A")
        canvas.drawRoundRect(RectF(heroX - dp(10f), heroY - dp(76f), heroX + dp(10f), heroY - dp(28f)), dp(10f), dp(10f), actorPaint)
        strokePaint.color = Color.parseColor("#31202F")
        strokePaint.strokeWidth = dp(8f)
        canvas.drawLine(heroX - dp(5f), heroY - dp(28f), heroX - dp(13f), heroY + dp(16f), strokePaint)
        canvas.drawLine(heroX + dp(5f), heroY - dp(28f), heroX + dp(13f), heroY + dp(16f), strokePaint)
        canvas.drawLine(heroX, heroY - dp(62f), heroX + dp(96f), heroY - dp(96f), strokePaint)
    }

    private fun drawOptionsOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(92, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val card = RectF(width * 0.36f, height * 0.24f, width * 0.64f, height * 0.72f)
        cardPaint.color = Color.parseColor("#F7F7F7")
        canvas.drawRoundRect(card, dp(18f), dp(18f), cardPaint)
        cardPaint.shader = null
        fillPaint.color = Color.parseColor("#2E9BFF")
        canvas.drawRoundRect(RectF(card.left, card.top, card.right, card.top + dp(34f)), dp(18f), dp(18f), fillPaint)
        fillPaint.color = Color.parseColor("#F7F7F7")
        canvas.drawRect(card.left, card.top + dp(17f), card.right, card.top + dp(34f), fillPaint)
        strokePaint.color = Color.parseColor("#1C222D")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(card, dp(18f), dp(18f), strokePaint)

        centerTextPaint.textAlign = Paint.Align.CENTER
        centerTextPaint.textSize = sp(13f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("SETTINGS", card.centerX(), card.top + dp(23f), centerTextPaint)
        centerTextPaint.textSize = sp(24f)
        centerTextPaint.color = Color.parseColor("#1A1C24")
        canvas.drawText("Game Options", card.centerX(), card.top + dp(72f), centerTextPaint)

        textPaint.textSize = sp(12.5f)
        textPaint.color = Color.parseColor("#49515E")
        drawMultilineText(canvas, "Adjust shot feel and comfort.", card.left + dp(28f), card.top + dp(96f), card.width() - dp(56f), textPaint, dp(18f), Paint.Align.CENTER)

        drawToggleRow(canvas, paceToggleButton, "Enemy Pace", if (gameOptions.tacticalPace) "Tactical" else "Fast", "Tactical pace gives you more time to line up each shot.")
        drawToggleRow(canvas, aimGuideToggleButton, "Aim Guide", if (gameOptions.aimGuide) "On" else "Off", "Show or hide the shot guide while aiming.")
        drawToggleRow(canvas, screenShakeToggleButton, "Impact Shake", if (gameOptions.screenShake) "On" else "Off", "Reduce camera shake if you want a steadier image.")

        drawButton(canvas, optionsBackButton, "Close", Color.parseColor("#A7F231"), "back")
    }

    private fun drawResultOverlay(canvas: Canvas, eyebrow: String, title: String, description: String, primary: String, secondary: String, accent: Int) {
        fillPaint.color = Color.argb(72, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        val banner = RectF(width * 0.38f, height * 0.1f, width * 0.62f, height * 0.3f)
        cardPaint.color = Color.argb(220, 33, 37, 56)
        canvas.drawRoundRect(banner, dp(16f), dp(16f), cardPaint)
        strokePaint.color = adjustAlpha(accent, 0.95f)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(banner, dp(16f), dp(16f), strokePaint)

        centerTextPaint.textAlign = Paint.Align.CENTER
        centerTextPaint.textSize = sp(15f)
        centerTextPaint.color = accent
        canvas.drawText(eyebrow, banner.centerX(), banner.top + dp(38f), centerTextPaint)

        centerTextPaint.textSize = sp(30f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(title, banner.centerX(), banner.top + dp(84f), centerTextPaint)

        textPaint.textSize = sp(14f)
        textPaint.color = Color.WHITE
        drawMultilineText(canvas, description, banner.left + dp(22f), banner.top + dp(116f), banner.width() - dp(44f), textPaint, dp(20f), Paint.Align.CENTER)

        centerTextPaint.textSize = sp(15f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("Coins $coins", width * 0.5f, height * 0.72f, centerTextPaint)

        drawButton(canvas, primaryButton, primary, if (phase == ScenePhase.LEVEL_CLEAR) Color.parseColor("#A7F231") else Color.parseColor("#F4C63D"), if (phase == ScenePhase.LEVEL_CLEAR) "next" else "restart")
        drawButton(canvas, secondaryButton, secondary, Color.parseColor("#4BA7FF"), "menu")
    }

    private fun drawUpgradeOverlay(canvas: Canvas) {
        fillPaint.color = Color.argb(160, 3, 6, 10)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        centerTextPaint.textAlign = Paint.Align.CENTER
        centerTextPaint.textSize = sp(14f)
        centerTextPaint.color = Color.parseColor("#94E1FF")
        canvas.drawText("MISSION CACHE", width * 0.5f, height * 0.18f, centerTextPaint)
        centerTextPaint.textSize = sp(30f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText("Choose Your Upgrade", width * 0.5f, height * 0.25f, centerTextPaint)

        upgradeCards.forEach { card ->
            cardPaint.shader = LinearGradient(card.rect.left, card.rect.top, card.rect.right, card.rect.bottom, intArrayOf(Color.parseColor("#111B2F"), Color.parseColor("#09111B")), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(card.rect, dp(26f), dp(26f), cardPaint)
            cardPaint.shader = null
            strokePaint.color = adjustAlpha(card.accent, 0.9f)
            strokePaint.strokeWidth = dp(2f)
            canvas.drawRoundRect(card.rect, dp(26f), dp(26f), strokePaint)

            centerTextPaint.textSize = sp(13f)
            centerTextPaint.color = card.accent
            canvas.drawText("UPGRADE", card.rect.centerX(), card.rect.top + dp(34f), centerTextPaint)
            centerTextPaint.textSize = sp(20f)
            centerTextPaint.color = Color.WHITE
            canvas.drawText(card.type.label, card.rect.centerX(), card.rect.top + dp(74f), centerTextPaint)

            textPaint.textSize = sp(13f)
            textPaint.color = Color.parseColor("#D7E7FF")
            drawMultilineText(canvas, card.type.blurb, card.rect.left + dp(18f), card.rect.top + dp(112f), card.rect.width() - dp(36f), textPaint, dp(20f), Paint.Align.CENTER)
            drawButton(canvas, RectF(card.rect.left + dp(18f), card.rect.bottom - dp(72f), card.rect.right - dp(18f), card.rect.bottom - dp(18f)), "Equip", card.accent, "apply")
        }
    }

    private fun drawFeatureStrip(canvas: Canvas, x: Float, y: Float, text: String) {
        fillPaint.color = Color.parseColor("#7CFFCB")
        canvas.drawCircle(x, y - dp(6f), dp(5f), fillPaint)
        this.textPaint.textSize = sp(15f)
        this.textPaint.color = Color.parseColor("#D7E8FF")
        canvas.drawText(text, x + dp(18f), y, this.textPaint)
    }

    private fun drawToggleRow(canvas: Canvas, rect: RectF, label: String, value: String, description: String) {
        cardPaint.color = Color.parseColor("#FFFFFF")
        canvas.drawRoundRect(rect, dp(16f), dp(16f), cardPaint)
        strokePaint.color = Color.parseColor("#1C222D")
        strokePaint.strokeWidth = dp(1.6f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), strokePaint)

        textPaint.textSize = sp(13f)
        textPaint.color = Color.parseColor("#1D212A")
        canvas.drawText(label, rect.left + dp(18f), rect.centerY() + dp(5f), textPaint)

        drawButton(canvas, RectF(rect.right - dp(114f), rect.top + dp(10f), rect.right - dp(12f), rect.bottom - dp(10f)), value, Color.parseColor("#A7F231"), "toggle")
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, accent: Int, subtitle: String) {
        buttonPaint.color = Color.argb(70, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + dp(4f), rect.right, rect.bottom + dp(4f)),
            dp(16f),
            dp(16f),
            buttonPaint,
        )
        buttonPaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(blendWithWhite(accent, 0.16f), accent), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), buttonPaint)
        buttonPaint.shader = null
        fillPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(rect.left + dp(4f), rect.top + dp(4f), rect.right - dp(4f), rect.top + rect.height() * 0.42f),
            dp(12f),
            dp(12f),
            fillPaint,
        )
        strokePaint.color = Color.parseColor("#1E1F25")
        strokePaint.strokeWidth = dp(1.6f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), strokePaint)

        centerTextPaint.textSize = sp(15f)
        centerTextPaint.color = Color.parseColor("#17191F")
        canvas.drawText(label, rect.centerX(), rect.centerY() - dp(2f), centerTextPaint)
        centerTextPaint.textSize = sp(10f)
        centerTextPaint.color = adjustAlpha(Color.parseColor("#17191F"), 0.74f)
        canvas.drawText(subtitle.uppercase(), rect.centerX(), rect.bottom - dp(10f), centerTextPaint)
    }

    private fun drawMissionChip(canvas: Canvas) {
        val chip = RectF(width * 0.39f, dp(18f), width * 0.61f, dp(90f))
        cardPaint.color = Color.parseColor("#F0B640")
        canvas.drawRoundRect(chip, dp(16f), dp(16f), cardPaint)
        fillPaint.color = Color.parseColor("#6D45A6")
        canvas.drawRoundRect(RectF(chip.left + dp(6f), chip.top + dp(6f), chip.right - dp(6f), chip.top + dp(24f)), dp(10f), dp(10f), fillPaint)
        fillPaint.color = Color.parseColor("#F9E3B6")
        canvas.drawRoundRect(RectF(chip.left + dp(10f), chip.top + dp(34f), chip.right - dp(10f), chip.bottom - dp(10f)), dp(10f), dp(10f), fillPaint)
        strokePaint.color = Color.parseColor("#1F2026")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(chip, dp(16f), dp(16f), strokePaint)

        centerTextPaint.color = Color.WHITE
        centerTextPaint.textSize = sp(11f)
        canvas.drawText("MISSION $currentLevel", chip.centerX(), chip.top + dp(17f), centerTextPaint)
        centerTextPaint.color = Color.parseColor("#1C1E26")
        centerTextPaint.textSize = sp(13f)
        canvas.drawText("Shoot all criminals", chip.centerX(), chip.top + dp(57f), centerTextPaint)

        fillPaint.color = Color.parseColor("#7EDBFF")
        canvas.drawRoundRect(RectF(chip.left + dp(10f), chip.top + dp(34f), chip.left + dp(42f), chip.bottom - dp(10f)), dp(10f), dp(10f), fillPaint)
        centerTextPaint.textSize = sp(10f)
        centerTextPaint.color = Color.parseColor("#1B1D24")
        canvas.drawText("${max(enemiesRemaining, 0)}", chip.left + dp(26f), chip.bottom - dp(18f), centerTextPaint)
    }

    private fun drawCoinCounter(canvas: Canvas) {
        val rect = RectF(width - dp(62f), dp(16f), width - dp(16f), dp(48f))
        cardPaint.color = Color.argb(130, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), cardPaint)
        strokePaint.color = Color.parseColor("#F7F8FA")
        strokePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), strokePaint)
        centerTextPaint.textSize = sp(14f)
        centerTextPaint.color = Color.parseColor("#1A1D25")
        canvas.drawText("$coins", rect.centerX(), rect.centerY() + dp(5f), centerTextPaint)
    }

    private fun drawStatusChip(canvas: Canvas) {
        val rect = RectF(width * 0.34f, height - dp(58f), width * 0.66f, height - dp(22f))
        cardPaint.color = Color.argb(144, 19, 24, 34)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), cardPaint)
        strokePaint.color = Color.argb(80, 255, 255, 255)
        strokePaint.strokeWidth = dp(1.4f)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), strokePaint)
        centerTextPaint.textSize = sp(11.5f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(message.take(48), rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawIconButton(canvas: Canvas, rect: RectF, label: String) {
        cardPaint.color = Color.argb(150, 255, 255, 255)
        canvas.drawOval(rect, cardPaint)
        strokePaint.color = Color.parseColor("#17191F")
        strokePaint.strokeWidth = dp(1.8f)
        canvas.drawOval(rect, strokePaint)
        centerTextPaint.textSize = sp(12f)
        centerTextPaint.color = Color.parseColor("#1A1D25")
        canvas.drawText(label, rect.centerX(), rect.centerY() + dp(4f), centerTextPaint)
    }

    private fun drawHudPill(canvas: Canvas, rect: RectF, label: String, value: String, accent: Int) {
        cardPaint.color = Color.argb(150, 8, 15, 28)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), cardPaint)
        strokePaint.color = adjustAlpha(accent, 0.85f)
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), strokePaint)

        centerTextPaint.textAlign = Paint.Align.CENTER
        centerTextPaint.textSize = sp(11f)
        centerTextPaint.color = adjustAlpha(accent, 0.92f)
        canvas.drawText(label, rect.centerX(), rect.top + dp(17f), centerTextPaint)
        centerTextPaint.textSize = sp(20f)
        centerTextPaint.color = Color.WHITE
        canvas.drawText(value, rect.centerX(), rect.bottom - dp(12f), centerTextPaint)
    }

    private fun drawCloud(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        canvas.drawCircle(centerX - radius * 0.65f, centerY, radius * 0.48f, fillPaint)
        canvas.drawCircle(centerX, centerY - radius * 0.12f, radius * 0.6f, fillPaint)
        canvas.drawCircle(centerX + radius * 0.6f, centerY, radius * 0.42f, fillPaint)
        canvas.drawRoundRect(RectF(centerX - radius, centerY, centerX + radius * 0.95f, centerY + radius * 0.45f), radius * 0.24f, radius * 0.24f, fillPaint)
    }

    private fun drawCrate(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val rect = RectF(left, top, left + width, top + height)
        fillPaint.color = Color.parseColor("#C9865C")
        canvas.drawRoundRect(rect, dp(6f), dp(6f), fillPaint)
        strokePaint.color = Color.parseColor("#87543B")
        strokePaint.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), strokePaint)
        strokePaint.strokeWidth = dp(1.6f)
        canvas.drawLine(rect.left + width * 0.25f, rect.top + dp(6f), rect.left + width * 0.25f, rect.bottom - dp(6f), strokePaint)
        canvas.drawLine(rect.left + width * 0.52f, rect.top + dp(6f), rect.left + width * 0.52f, rect.bottom - dp(6f), strokePaint)
        canvas.drawLine(rect.left + width * 0.78f, rect.top + dp(6f), rect.left + width * 0.78f, rect.bottom - dp(6f), strokePaint)
        canvas.drawLine(rect.left + dp(8f), rect.top + height * 0.22f, rect.right - dp(8f), rect.top + height * 0.22f, strokePaint)
    }

    private fun drawMultilineText(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint, lineHeight: Float, align: Paint.Align = Paint.Align.LEFT) {
        paint.textAlign = align
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) {
            lines += current
        }
        lines.forEachIndexed { index, line ->
            val drawX = when (align) {
                Paint.Align.CENTER -> x + maxWidth * 0.5f
                Paint.Align.RIGHT -> x + maxWidth
                else -> x
            }
            canvas.drawText(line, drawX, y + index * lineHeight, paint)
        }
    }

    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun blendWithWhite(color: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        val r = (Color.red(color) + (255 - Color.red(color)) * clamped).toInt()
        val g = (Color.green(color) + (255 - Color.green(color)) * clamped).toInt()
        val b = (Color.blue(color) + (255 - Color.blue(color)) * clamped).toInt()
        return Color.rgb(r, g, b)
    }

    private fun shadeColor(color: Int, factor: Float): Int {
        val clamped = factor.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) * clamped).toInt(),
            (Color.green(color) * clamped).toInt(),
            (Color.blue(color) * clamped).toInt(),
        )
    }

    private fun buildActorPalette(isEnemy: Boolean): List<Int> {
        val shirt = if (isEnemy) enemyShirts.random(random) else civilianShirts.random(random)
        return listOf(
            skinTones.random(random),
            shirt,
            pantColors.random(random),
            hairColors.random(random),
        )
    }
}
