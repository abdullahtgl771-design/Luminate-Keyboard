package com.luminate.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class KeyboardView(context: Context, private val listener: Listener) : View(context) {
    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
    }

    enum class Shift { OFF, ON, CAPS }
    private class Slot(val key: Key, val rect: RectF)

    private val d = resources.displayMetrics.density
    private val rowHeight = 54 * d
    private val padH = 3 * d
    private val padV = 6 * d
    private val gap = 4 * d
    private val radius = 8 * d
    private val cBg = Color.parseColor("#202124")
    private val cKey = Color.parseColor("#3C4043")
    private val cSpecial = Color.parseColor("#2F3033")
    private val cPressed = Color.parseColor("#5F6368")
    private val cAccent = Color.parseColor("#8AB4F8")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var layer = Layer.LETTERS
    private var shift = Shift.OFF
    private var slots: List<Slot> = emptyList()
    private var pressed: Slot? = null
    private var lastShiftTap = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val repeat = object : Runnable {
        override fun run() {
            listener.onDelete()
            handler.postDelayed(this, 45)
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), (4 * rowHeight + 2 * padV).toInt())
    }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = buildSlots()

    private fun buildSlots() {
        val list = ArrayList<Slot>()
        val usableW = width - 2 * padH
        Layouts.rows(layer).forEachIndexed { r, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = usableW / total
            val top = padV + r * rowHeight
            var x = padH
            for (k in row) {
                val w = unit * k.weight
                if (k.type != KeyType.SPACER) {
                    list.add(Slot(k, RectF(x + gap / 2, top + gap / 2, x + w - gap / 2, top + rowHeight - gap / 2)))
                }
                x += w
            }
        }
        slots = list
    }

    fun setLayer(l: Layer) {
        layer = l
        buildSlots()
        invalidate()
    }
    fun reset() = setLayer(Layer.LETTERS)
    fun setAutoShift(on: Boolean) {
        if (shift == Shift.CAPS) return
        val n = if (on) Shift.ON else Shift.OFF
        if (n != shift) { shift = n; invalidate() }
    }

    private fun labelFor(k: Key): String? = when (k.type) {
        KeyType.CHAR -> if (layer == Layer.LETTERS && shift != Shift.OFF) k.label.uppercase() else k.label
        KeyType.SHIFT -> "\u21E7"
        KeyType.DEL -> "\u232B"
        KeyType.ENTER -> "\u21B5"
        KeyType.MODE_SYM -> "?123"
        KeyType.MODE_ABC -> "ABC"
        else -> null
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBg)
        for (s in slots) {
            val k = s.key
            paint.color = when {
                s === pressed -> cPressed
                k.type == KeyType.ENTER -> cAccent
                k.type == KeyType.SHIFT && shift == Shift.CAPS -> cAccent
                k.type.special -> cSpecial
                else -> cKey
            }
            canvas.drawRoundRect(s.rect, radius, radius, paint)
            val label = labelFor(k) ?: continue
            textPaint.color = when {
                k.type == KeyType.ENTER || (k.type == KeyType.SHIFT && shift == Shift.CAPS) -> cBg
                k.type == KeyType.SHIFT && shift == Shift.ON -> cAccent
                else -> Color.WHITE
            }
            textPaint.textSize = if (k.type == KeyType.MODE_SYM || k.type == KeyType.MODE_ABC) 15 * d else 22 * d
            val cy = s.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(label, s.rect.centerX(), cy, textPaint)
        }
    }

    private fun hit(x: Float, y: Float): Slot? {
        val h = gap / 2
        return slots.firstOrNull {
            x >= it.rect.left - h && x <= it.rect.right + h &&
                y >= it.rect.top - h && y <= it.rect.bottom + h
        }
    }
    private fun haptic() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit(e.x, e.y)
                invalidate()
                pressed?.let {
                    haptic()
                    if (it.key.type == KeyType.DEL) {
                        listener.onDelete()
                        handler.postDelayed(repeat, 400)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val h = hit(e.x, e.y)
                if (h !== pressed) {
                    handler.removeCallbacks(repeat)
                    pressed = h
                    invalidate()
                    if (h != null) haptic()
                }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeat)
                val p = pressed
                pressed = null
                invalidate()
                if (p != null && p.key.type != KeyType.DEL) fire(p.key)
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeat)
                pressed = null
                invalidate()
            }
        }
        return true
    }

    private fun fire(k: Key) {
        when (k.type) {
            KeyType.CHAR -> {
                val s = if (layer == Layer.LETTERS && shift != Shift.OFF) k.label.uppercase() else k.label
                if (layer == Layer.LETTERS && shift == Shift.ON) { shift = Shift.OFF; invalidate() }
                listener.onText(s)
            }
            KeyType.SPACE -> listener.onText(" ")
            KeyType.ENTER -> listener.onEnter()
            KeyType.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == Shift.CAPS -> Shift.OFF
                    shift == Shift.ON && now - lastShiftTap < 350 -> Shift.CAPS
                    shift == Shift.ON -> Shift.OFF
                    else -> Shift.ON
                }
                lastShiftTap = now
                invalidate()
            }
            KeyType.MODE_SYM -> setLayer(Layer.SYMBOLS)
            KeyType.MODE_ABC -> setLayer(Layer.LETTERS)
            else -> {}
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(repeat)
        super.onDetachedFromWindow()
    }
}
