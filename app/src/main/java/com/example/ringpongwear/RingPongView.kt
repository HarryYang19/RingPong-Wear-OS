package com.example.ringpongwear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A dependency-free Canvas arcade game designed for round Wear OS displays.
 * Samsung's digital bezel is handled using Android rotary-encoder input.
 */
class RingPongView(context: Context) : View(context) {
    private enum class GameState { READY, PLAYING, GAME_OVER }

    private var state = GameState.READY
    private var score = 0
    private var bestScore = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_BEST, 0)

    private val density = resources.displayMetrics.density
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    private var cx = 0f
    private var cy = 0f
    private var minDimension = 0f
    private var arenaRadius = 0f
    private var ballRadius = 0f
    private var paddleStroke = 0f
    private var paddleRadius = 0f
    private var collisionRadius = 0f
    private val paddleHalfAngle = (25.0 * PI / 180.0).toFloat()

    // Rotary impulses create a little forward carry: the paddle coasts in the
    // direction of the last turn and is slowed only by drag (never pulled backward).
    private var paddleAngle = (-PI / 2.0).toFloat()
    private var paddleAngularVelocity = 0f

    private var ballX = 0f
    private var ballY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var curveRate = 0f
    private var initialSpeed = 0f
    private var maxSpeed = 0f
    private var lastFrameNanos = 0L

    private val arenaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.rgb(190, 190, 190)
    }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 190, 190)
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(31f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val subHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(170, 170, 170)
        textAlign = Paint.Align.CENTER
        textSize = dp(10f)
    }
    private val arcRect = RectF()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Ring Pong. Rotate the watch bezel to move the paddle. Tap the center to start."
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun dp(value: Float): Float = value * density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        minDimension = min(w, h).toFloat()
        cx = w / 2f
        cy = h / 2f

        // Use nearly all of the circular display while keeping a safe margin from its edge.
        arenaRadius = minDimension * 0.46f
        ballRadius = max(dp(6.2f), minDimension * 0.019f)
        paddleStroke = max(dp(11f), minDimension * 0.042f)
        // Keep the outside of the thick paddle close to the arena rim.
        paddleRadius = arenaRadius - paddleStroke / 2f - max(dp(2f), ballRadius * 0.55f)
        // Collision is against the paddle's INNER face, including the ball radius; this
        // prevents the ball from visibly travelling through the paddle before bouncing.
        collisionRadius = paddleRadius - paddleStroke / 2f - ballRadius - dp(0.5f)
        arcRect.set(cx - paddleRadius, cy - paddleRadius, cx + paddleRadius, cy + paddleRadius)
        ballX = cx
        ballY = cy
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        if (minDimension <= 0f) return

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.033f)
        }
        lastFrameNanos = now

        updatePaddle(dt)
        if (state == GameState.PLAYING) updateGame(dt)

        drawArena(canvas)
        drawPaddle(canvas)
        drawBall(canvas)
        drawHud(canvas)

        val paddleStillMoving = abs(paddleAngularVelocity) > 0.012f
        if (state == GameState.PLAYING || paddleStillMoving) {
            postInvalidateOnAnimation()
        } else {
            lastFrameNanos = 0L
        }
    }

    private fun drawArena(canvas: Canvas) {
        arenaPaint.strokeWidth = dp(1.35f)
        canvas.drawCircle(cx, cy, arenaRadius - arenaPaint.strokeWidth / 2f, arenaPaint)
    }

    private fun drawPaddle(canvas: Canvas) {
        paddlePaint.strokeWidth = paddleStroke
        val startDegrees = Math.toDegrees((paddleAngle - paddleHalfAngle).toDouble()).toFloat()
        val sweepDegrees = Math.toDegrees((paddleHalfAngle * 2f).toDouble()).toFloat()
        canvas.drawArc(arcRect, startDegrees, sweepDegrees, false, paddlePaint)
    }

    private fun drawBall(canvas: Canvas) {
        // One slightly enlarged solid-white disk for an unambiguous ball silhouette.
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
    }

    private fun drawHud(canvas: Canvas) {
        titlePaint.textSize = dp(10.5f)
        scorePaint.textSize = dp(31f)
        hintPaint.textSize = dp(10.5f)
        subHintPaint.textSize = dp(9.5f)

        if (state == GameState.PLAYING) {
            // Once a rally starts, the score is the only text left on screen.
            canvas.drawText(score.toString(), cx, cy - arenaRadius * 0.43f, scorePaint)
            return
        }

        canvas.drawText("RING PONG", cx, cy - arenaRadius * 0.42f, titlePaint)
        titlePaint.textSize = dp(9.5f)
        canvas.drawText("BEST  $bestScore", cx, cy - arenaRadius * 0.22f, titlePaint)
        when (state) {
            GameState.READY -> {
                canvas.drawText("ROTATE TO MOVE", cx, cy + arenaRadius * 0.48f, hintPaint)
                canvas.drawText("TAP CENTER TO START", cx, cy + arenaRadius * 0.60f, subHintPaint)
            }
            GameState.GAME_OVER -> {
                canvas.drawText("GAME OVER", cx, cy + arenaRadius * 0.43f, hintPaint)
                canvas.drawText("TAP CENTER TO RETRY", cx, cy + arenaRadius * 0.57f, subHintPaint)
            }
            GameState.PLAYING -> Unit
        }
    }

    private fun startGame() {
        score = 0
        ballX = cx
        ballY = cy
        initialSpeed = minDimension * 0.29f
        maxSpeed = minDimension * 1.04f
        curveRate = 0f
        // Start toward the paddle's visible/current position for a fair opening rally.
        val startAngle = paddleAngle
        velocityX = cos(startAngle) * initialSpeed
        velocityY = sin(startAngle) * initialSpeed
        state = GameState.PLAYING
        lastFrameNanos = 0L
        invalidate()
    }

    private fun updatePaddle(dt: Float) {
        if (dt <= 0f) return
        // Exponential drag lets the paddle coast forward, then settle naturally.
        // There is no target spring, so drag alone can never reverse its direction.
        paddleAngle += paddleAngularVelocity * dt
        paddleAngle = wrapAngle(paddleAngle)
        paddleAngularVelocity *= exp(-PADDLE_DRAG * dt)
        if (abs(paddleAngularVelocity) < 0.012f) paddleAngularVelocity = 0f
    }

    private fun normalizeAngle(angle: Float): Float = atan2(sin(angle), cos(angle))

    private fun wrapAngle(angle: Float): Float {
        var wrapped = angle
        while (wrapped > PI.toFloat()) wrapped -= (2.0 * PI).toFloat()
        while (wrapped < -PI.toFloat()) wrapped += (2.0 * PI).toFloat()
        return wrapped
    }

    private fun updateGame(dt: Float) {
        if (dt <= 0f) return
        // Small physics substeps preserve smooth curved motion and reduce tunnelling at high speed.
        var remaining = dt
        while (remaining > 0.00001f && state == GameState.PLAYING) {
            val step = min(remaining, MAX_PHYSICS_STEP)
            updateBallStep(step)
            remaining -= step
        }
    }

    private fun updateBallStep(dt: Float) {
        // Add a subtle, decaying curve after each hit instead of perfectly straight segments.
        if (abs(curveRate) > 0.001f) {
            val turn = curveRate * dt
            val c = cos(turn)
            val s = sin(turn)
            val vx = velocityX * c - velocityY * s
            val vy = velocityX * s + velocityY * c
            velocityX = vx
            velocityY = vy
            curveRate *= exp(-CURVE_DECAY * dt)
        }

        ballX += velocityX * dt
        ballY += velocityY * dt

        val dx = ballX - cx
        val dy = ballY - cy
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < collisionRadius) return

        val safeDistance = max(distance, 0.001f)
        val nx = dx / safeDistance
        val ny = dy / safeDistance
        val impactAngle = atan2(ny, nx)
        val angleDelta = normalizeAngle(impactAngle - paddleAngle)
        // The round ends of a thick stroke enlarge its true hit area slightly.
        val endCapAllowance = atan2(ballRadius + paddleStroke * 0.5f, max(paddleRadius, 1f))
        val effectiveHalfAngle = paddleHalfAngle + endCapAllowance

        // Put the ball at the contact point before resolving the hit/miss.
        val contactDistance = max(ballRadius, collisionRadius - dp(0.7f))
        ballX = cx + nx * contactDistance
        ballY = cy + ny * contactDistance

        if (abs(angleDelta) > effectiveHalfAngle) {
            state = GameState.GAME_OVER
            curveRate = 0f
            lastFrameNanos = 0L
            if (score > bestScore) {
                bestScore = score
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_BEST, bestScore)
                    .apply()
            }
            invalidate()
            return
        }

        score++
        triggerCollisionFeedback()
        val speedMultiplier = when {
            score <= SPEED_EASE_SCORE -> SPEED_MULTIPLIER_EARLY
            score <= SPEED_EASE_SCORE_2 -> SPEED_MULTIPLIER_MIDDLE
            else -> SPEED_MULTIPLIER_LATE
        }
        val speed = min(sqrt(velocityX * velocityX + velocityY * velocityY) * speedMultiplier, maxSpeed)
        val offset = (angleDelta / paddleHalfAngle).coerceIn(-1f, 1f)
        // Send the ball back inward and make the impact point affect both angle and spin.
        val tx = -ny
        val ty = nx
        var outX = -nx + tx * offset * 0.56f
        var outY = -ny + ty * offset * 0.56f
        val outLength = sqrt(outX * outX + outY * outY).coerceAtLeast(0.001f)
        outX /= outLength
        outY /= outLength
        velocityX = outX * speed
        velocityY = outY * speed
        val impactCurve = if (abs(offset) < 0.10f) {
            // Even a centered return gets a very small alternating curve, so rallies
            // never settle into perfectly straight, mechanical-looking segments.
            if (score % 2 == 0) 0.18f else -0.18f
        } else {
            offset * 0.48f
        }
        curveRate = (impactCurve + paddleAngularVelocity * 0.025f)
            .coerceIn(-MAX_CURVE_RATE, MAX_CURVE_RATE)
    }

    private fun triggerCollisionFeedback() {
        try {
            val motor = vibrator
            if (motor?.hasVibrator() == true) {
                // Use a truly low amplitude when supported; otherwise use the platform's
                // short, device-tuned tick instead of an amplitude that may become full strength.
                val effect = if (motor.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(HAPTIC_DURATION_MS, HAPTIC_AMPLITUDE)
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                }
                motor.vibrate(effect)
            }
        } catch (_: RuntimeException) {
            // Haptics are optional; gameplay must continue if the device blocks vibration.
        }
    }

    private fun rotatePaddle(scrollAmount: Float) {
        if (!scrollAmount.isFinite() || abs(scrollAmount) < 0.0001f) return
        // Add angular momentum in the direction of rotation. Once input stops, drag
        // carries the paddle forward briefly and smoothly; there is no spring recoil.
        val units = scrollAmount.coerceIn(-MAX_ROTARY_UNITS_PER_EVENT, MAX_ROTARY_UNITS_PER_EVENT)
        val angularImpulse = units * ROTARY_RADIANS_PER_UNIT * ROTARY_INERTIA_GAIN * ROTARY_DIRECTION
        paddleAngularVelocity = (paddleAngularVelocity + angularImpulse)
            .coerceIn(-MAX_PADDLE_ANGULAR_SPEED, MAX_PADDLE_ANGULAR_SPEED)
        invalidate()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val rotaryValue = event.getAxisValue(MotionEvent.AXIS_SCROLL)
            val rotarySource = event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
            if (abs(rotaryValue) > 0.0001f && (rotarySource || event.source != InputDevice.SOURCE_TOUCHSCREEN)) {
                rotatePaddle(rotaryValue)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The outer annulus is the virtual-bezel zone: touch there is always
                // consumed and never steers the paddle. Only an inner tap starts/restarts.
                if (state != GameState.PLAYING && isInsideTouchStartZone(event.x, event.y)) {
                    startGame()
                }
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun isInsideTouchStartZone(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        val radiusSquared = dx * dx + dy * dy
        val startRadius = arenaRadius * TOUCH_START_RADIUS_FRACTION
        return radiusSquared < startRadius * startRadius
    }

    companion object {
        private const val PREFS = "ring_pong_prefs"
        private const val KEY_BEST = "best_score"
        private const val ROTARY_RADIANS_PER_UNIT = 0.12f
        private const val MAX_ROTARY_UNITS_PER_EVENT = 2.0f
        private const val ROTARY_DIRECTION = -1f
        private const val ROTARY_INERTIA_GAIN = 12.5f
        private const val PADDLE_DRAG = 11.5f
        private const val MAX_PADDLE_ANGULAR_SPEED = 11.5f
        private const val TOUCH_START_RADIUS_FRACTION = 0.70f
        private const val MAX_PHYSICS_STEP = 1f / 120f
        // Preserve a lively opening, then taper the growth so 20–50 point rallies
        // remain achievable. The rate tapers again beyond 50 for longer games.
        private const val SPEED_EASE_SCORE = 20
        private const val SPEED_EASE_SCORE_2 = 50
        private const val SPEED_MULTIPLIER_EARLY = 1.035f
        private const val SPEED_MULTIPLIER_MIDDLE = 1.008f
        private const val SPEED_MULTIPLIER_LATE = 1.0045f
        private const val CURVE_DECAY = 1.55f
        private const val MAX_CURVE_RATE = 0.68f
        private const val HAPTIC_DURATION_MS = 12L
        private const val HAPTIC_AMPLITUDE = 38
    }
}
