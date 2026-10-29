package com.example.ringpongwear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A dependency-free Canvas arcade game designed for round Wear OS displays.
 * Rotary input is received through the standard Android rotary encoder axis.
 */
class RingPongView(context: Context) : View(context) {
    private enum class GameState { READY, PLAYING, GAME_OVER }

    private var state = GameState.READY
    private var score = 0
    private var bestScore = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_BEST, 0)

    private val density = resources.displayMetrics.density
    private var cx = 0f
    private var cy = 0f
    private var minDimension = 0f
    private var arenaRadius = 0f
    private var ballRadius = 0f
    private var wallRadius = 0f
    private var paddleStroke = 0f
    private var paddleRadius = 0f
    private val paddleHalfAngle = (25.0 * PI / 180.0).toFloat()
    private var paddleAngle = (-PI / 2.0).toFloat()

    private var ballX = 0f
    private var ballY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var initialSpeed = 0f
    private var maxSpeed = 0f
    private var lastFrameNanos = 0L

    private val arenaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.rgb(45, 61, 63)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(62, 82, 84)
    }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ACCENT
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val ballHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ACCENT
        strokeWidth = dp(1.5f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(125, 151, 151)
        textAlign = Paint.Align.CENTER
        textSize = dp(10f)
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(27f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT
        textAlign = Paint.Align.CENTER
        textSize = dp(10f)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val subHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(121, 141, 142)
        textAlign = Paint.Align.CENTER
        textSize = dp(9f)
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
        arenaRadius = minDimension * 0.415f
        ballRadius = max(dp(4.5f), minDimension * 0.014f)
        wallRadius = arenaRadius - ballRadius
        paddleStroke = max(dp(7f), minDimension * 0.024f)
        paddleRadius = wallRadius - paddleStroke * 0.48f
        arcRect.set(cx - paddleRadius, cy - paddleRadius, cx + paddleRadius, cy + paddleRadius)
        ballX = cx
        ballY = cy
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        if (minDimension <= 0f) return

        if (state == GameState.PLAYING) {
            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) {
                0f
            } else {
                ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.033f)
            }
            lastFrameNanos = now
            updateGame(dt)
        } else {
            lastFrameNanos = 0L
        }

        drawArena(canvas)
        drawPaddle(canvas)
        drawBall(canvas)
        drawHud(canvas)

        if (state == GameState.PLAYING) postInvalidateOnAnimation()
    }

    private fun drawArena(canvas: Canvas) {
        arenaPaint.strokeWidth = dp(1.2f)
        canvas.drawCircle(cx, cy, arenaRadius - arenaPaint.strokeWidth / 2f, arenaPaint)
        val tickCount = 32
        for (i in 0 until tickCount) {
            val a = (2.0 * PI * i / tickCount).toFloat()
            val length = if (i % 4 == 0) dp(7f) else dp(3.5f)
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
        canvas.drawCircle(ballX, ballY, ballRadius + dp(2f), ballHaloPaint)
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
    }

    private fun drawHud(canvas: Canvas) {
        titlePaint.textSize = dp(9.5f)
        scorePaint.textSize = dp(26f)
        hintPaint.textSize = dp(10f)
        subHintPaint.textSize = dp(9f)

        canvas.drawText("R I N G   P O N G", cx, cy - arenaRadius * 0.49f, titlePaint)
        canvas.drawText(score.toString(), cx, cy - arenaRadius * 0.29f, scorePaint)
        titlePaint.textSize = dp(8f)
        canvas.drawText("BEST  $bestScore", cx, cy - arenaRadius * 0.20f, titlePaint)

        when (state) {
            GameState.READY -> {
                canvas.drawText("ROTATE BEZEL TO AIM", cx, cy + arenaRadius * 0.49f, hintPaint)
                canvas.drawText("TAP TO START", cx, cy + arenaRadius * 0.62f, subHintPaint)
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
        maxSpeed = minDimension * 0.96f
        // Aim initially toward the current paddle so the first exchange is fair.
        val startAngle = paddleAngle
        velocityX = cos(startAngle) * initialSpeed
        velocityY = sin(startAngle) * initialSpeed
        state = GameState.PLAYING
        lastFrameNanos = 0L
        invalidate()
    }

    private fun updateGame(dt: Float) {
        if (dt <= 0f) return
        ballX += velocityX * dt
        ballY += velocityY * dt

        val dx = ballX - cx
        val dy = ballY - cy
        val distance = sqrt(dx * dx + dy * dy)
        if (distance < wallRadius) return

        val safeDistance = max(distance, 0.001f)
        val nx = dx / safeDistance
        val ny = dy / safeDistance
        val impactAngle = atan2(ny, nx)
        val angleDelta = normalizeAngle(impactAngle - paddleAngle)

        // Keep the ball just inside the circular wall to prevent repeat collisions.
        ballX = cx + nx * (wallRadius - dp(0.25f))
        ballY = cy + ny * (wallRadius - dp(0.25f))

        if (abs(angleDelta) > paddleHalfAngle) {
            state = GameState.GAME_OVER
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
        val speed = min(sqrt(velocityX * velocityX + velocityY * velocityY) * 1.035f, maxSpeed)
        val offset = (angleDelta / paddleHalfAngle).coerceIn(-1f, 1f)
        // Return inward with a small tangential deflection based on where the ball hit.
        val tx = -ny
        val ty = nx
        var outX = -nx + tx * offset * 0.48f
        var outY = -ny + ty * offset * 0.48f
        val outLength = sqrt(outX * outX + outY * outY).coerceAtLeast(0.001f)
        outX /= outLength
        outY /= outLength
        velocityX = outX * speed
        velocityY = outY * speed
    }

    private fun normalizeAngle(angle: Float): Float = atan2(sin(angle), cos(angle))

    private fun rotatePaddle(scrollAmount: Float) {
        if (!scrollAmount.isFinite() || abs(scrollAmount) < 0.0001f) return
        // Most rotary sources report detents; some report larger pixel-like deltas.
        // Clamp each event so either source remains controllable on a small round screen.
        val angleDelta = (scrollAmount * ROTARY_RADIANS_PER_UNIT).coerceIn(-MAX_ROTARY_STEP, MAX_ROTARY_STEP)
        paddleAngle = normalizeAngle(paddleAngle + angleDelta * ROTARY_DIRECTION)
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
            paddleAngle = atan2(dy, dx)
            invalidate()
        }
    }

    companion object {
        private const val PREFS = "ring_pong_prefs"
        private const val KEY_BEST = "best_score"
        private const val ROTARY_RADIANS_PER_UNIT = 0.12f
        private const val MAX_ROTARY_STEP = 0.45f
        // Change to -1f if your watch reports the opposite direction than expected.
        private const val ROTARY_DIRECTION = 1f
        private const val ACCENT = 0xFF52F6C2.toInt()
    }
}
