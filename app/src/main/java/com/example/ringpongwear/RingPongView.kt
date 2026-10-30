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

    // Separate target/current angles give rotary input a spring-like, eased response.
    private var paddleAngle = (-PI / 2.0).toFloat()
    private var targetPaddleAngle = paddleAngle
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
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(115, 115, 115)
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
    private val ballCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val ballHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dp(1.5f)
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
        contentDescription = "Ring Pong. Rotate the watch bezel to move the paddle. Tap to start."
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
        ballRadius = max(dp(5.5f), minDimension * 0.017f)
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

        val paddleStillMoving = abs(normalizeAngle(targetPaddleAngle - paddleAngle)) > 0.0015f ||
            abs(paddleAngularVelocity) > 0.008f
        if (state == GameState.PLAYING || paddleStillMoving) {
            postInvalidateOnAnimation()
        } else {
            lastFrameNanos = 0L
        }
    }

    private fun drawArena(canvas: Canvas) {
        arenaPaint.strokeWidth = dp(1.35f)
        canvas.drawCircle(cx, cy, arenaRadius - arenaPaint.strokeWidth / 2f, arenaPaint)
        val tickCount = 40
        for (i in 0 until tickCount) {
            val a = (2.0 * PI * i / tickCount).toFloat()
            val length = if (i % 5 == 0) dp(7f) else dp(3.5f)
            val outer = arenaRadius - dp(2f)
            val inner = outer - length
            canvas.drawLine(
                cx + cos(a) * inner,
                cy + sin(a) * inner,
                cx + cos(a) * outer,
                cy + sin(a) * outer,
                tickPaint
            )
        }
    }

    private fun drawPaddle(canvas: Canvas) {
        paddlePaint.strokeWidth = paddleStroke
        val startDegrees = Math.toDegrees((paddleAngle - paddleHalfAngle).toDouble()).toFloat()
        val sweepDegrees = Math.toDegrees((paddleHalfAngle * 2f).toDouble()).toFloat()
        canvas.drawArc(arcRect, startDegrees, sweepDegrees, false, paddlePaint)
    }

    private fun drawBall(canvas: Canvas) {
        // A white ball with a small black center stays distinct from the thick white paddle.
        canvas.drawCircle(ballX, ballY, ballRadius + dp(1.5f), ballHaloPaint)
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
        canvas.drawCircle(ballX, ballY, max(dp(1.6f), ballRadius * 0.27f), ballCorePaint)
    }

    private fun drawHud(canvas: Canvas) {
        titlePaint.textSize = dp(10.5f)
        scorePaint.textSize = dp(31f)
        hintPaint.textSize = dp(10.5f)
        subHintPaint.textSize = dp(9.5f)

        canvas.drawText("RING PONG", cx, cy - arenaRadius * 0.47f, titlePaint)
        canvas.drawText(score.toString(), cx, cy - arenaRadius * 0.265f, scorePaint)
        titlePaint.textSize = dp(8.8f)
        canvas.drawText("BEST  $bestScore", cx, cy - arenaRadius * 0.17f, titlePaint)

        when (state) {
            GameState.READY -> {
                canvas.drawText("ROTATE BEZEL", cx, cy + arenaRadius * 0.49f, hintPaint)
                canvas.drawText("TAP TO START", cx, cy + arenaRadius * 0.61f, subHintPaint)
            }
            GameState.PLAYING -> {
                canvas.drawText("ROTATE TO MOVE", cx, cy + arenaRadius * 0.60f, subHintPaint)
            }
            GameState.GAME_OVER -> {
                canvas.drawText("GAME OVER", cx, cy + arenaRadius * 0.43f, hintPaint)
                canvas.drawText("TAP TO PLAY AGAIN", cx, cy + arenaRadius * 0.57f, subHintPaint)
            }
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
        val error = normalizeAngle(targetPaddleAngle - paddleAngle)
        if (abs(error) < 0.0015f && abs(paddleAngularVelocity) < 0.008f) {
            paddleAngle = targetPaddleAngle
            paddleAngularVelocity = 0f
            return
        }

        // A damped spring gives the paddle a gentle, responsive elastic follow-through.
        val acceleration = error * PADDLE_SPRING - paddleAngularVelocity * PADDLE_DAMPING
        paddleAngularVelocity = (paddleAngularVelocity + acceleration * dt)
            .coerceIn(-MAX_PADDLE_ANGULAR_SPEED, MAX_PADDLE_ANGULAR_SPEED)
        paddleAngle = normalizeAngle(paddleAngle + paddleAngularVelocity * dt)
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
        val speed = min(sqrt(velocityX * velocityX + velocityY * velocityY) * SPEED_MULTIPLIER, maxSpeed)
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

    private fun normalizeAngle(angle: Float): Float = atan2(sin(angle), cos(angle))

    private fun rotatePaddle(scrollAmount: Float) {
        if (!scrollAmount.isFinite() || abs(scrollAmount) < 0.0001f) return
        // Samsung bezel direction is inverted relative to the prior build; reverse it here.
        val angleDelta = (scrollAmount * ROTARY_RADIANS_PER_UNIT)
            .coerceIn(-MAX_ROTARY_STEP, MAX_ROTARY_STEP)
        targetPaddleAngle = normalizeAngle(targetPaddleAngle + angleDelta * ROTARY_DIRECTION)
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
                if (state != GameState.PLAYING) {
                    startGame()
                } else {
                    movePaddleFromTouch(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (state == GameState.PLAYING) movePaddleFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun movePaddleFromTouch(x: Float, y: Float) {
        val dx = x - cx
        val dy = y - cy
        val radius = sqrt(dx * dx + dy * dy)
        // Touch is a fallback; keep central taps available and use the outer ring to aim.
        if (radius >= arenaRadius * 0.52f) {
            targetPaddleAngle = atan2(dy, dx)
            invalidate()
        }
    }

    companion object {
        private const val PREFS = "ring_pong_prefs"
        private const val KEY_BEST = "best_score"
        private const val ROTARY_RADIANS_PER_UNIT = 0.12f
        private const val MAX_ROTARY_STEP = 0.45f
        private const val ROTARY_DIRECTION = -1f
        private const val PADDLE_SPRING = 82f
        private const val PADDLE_DAMPING = 13.5f
        private const val MAX_PADDLE_ANGULAR_SPEED = 7.5f
        private const val MAX_PHYSICS_STEP = 1f / 120f
        private const val SPEED_MULTIPLIER = 1.035f
        private const val CURVE_DECAY = 1.55f
        private const val MAX_CURVE_RATE = 0.68f
        private const val HAPTIC_DURATION_MS = 12L
        private const val HAPTIC_AMPLITUDE = 38
    }
}
