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
        fun onClipboard()
        fun onSettings()
        fun onPicker()
    }

    enum class Shift { OFF, ON, CAPS }
    private data class Slot(val key: Key, val rect: RectF, val row: Int)

    private val d = resources.displayMetrics.density
    private val toolbarHeight = 43f * d
    private val rowHeight = 51f * d
    private val padH = 3f * d
    private val padV = 5f * d
    private val gap = 4f * d
    private val keyRadius = 7f * d
    private val bg = Color.rgb(211, 216, 225)
    private val keyColor = Color.rgb(250, 251, 255)
    private val specialColor = Color.rgb(185, 195, 210)
    private val actionColor = Color.rgb(174, 198, 238)
    private val pressedColor = Color.rgb(224, 232, 246)
    private val ink = Color.rgb(32, 39, 49)
    private val mutedInk = Color.rgb(86, 98, 115)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var layer = Layer.LETTERS
    private var shift = Shift.OFF
    private var slots: List<Slot> = emptyList()
    private var pressed: Slot? = null
    private var pressedTool = -1
    private var lastShiftTap = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val repeat = object : Runnable {
        override fun run() {
            listener.onDelete()
            handler.postDelayed(this, 48)
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val rows = Layouts.rows(layer).size
        val wanted = toolbarHeight + rows * rowHeight + padV * 2
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(wanted.toInt(), heightSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) = buildSlots()

    private fun buildSlots() {
        val list = ArrayList<Slot>()
        val usableW = (width - 2 * padH).coerceAtLeast(1f)
        Layouts.rows(layer).forEachIndexed { rowIndex, row ->
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = usableW / total
            val top = toolbarHeight + padV + rowIndex * rowHeight
            var x = padH
            row.forEach { key ->
                val w = unit * key.weight
                if (key.type != KeyType.SPACER) {
                    list.add(Slot(key, RectF(x + gap / 2, top + gap / 2, x + w - gap / 2, top + rowHeight - gap / 2), rowIndex))
                }
                x += w
            }
        }
        slots = list
        invalidate()
    }

    fun setLayer(next: Layer) {
        layer = next
        buildSlots()
        requestLayout()
    }

    fun reset() {
        layer = Layer.LETTERS
        shift = Shift.OFF
        buildSlots()
        requestLayout()
    }

    fun setAutoShift(on: Boolean) {
        if (shift == Shift.CAPS) return
        val next = if (on) Shift.ON else Shift.OFF
        if (next != shift) {
            shift = next
            invalidate()
        }
    }

    private fun labelFor(key: Key): String = when (key.type) {
        KeyType.CHAR -> if (layer == Layer.LETTERS && shift != Shift.OFF) key.label.uppercase() else key.label
        KeyType.SHIFT -> if (shift == Shift.CAPS) "⬆" else "⇧"
        KeyType.DEL -> "⌫"
        KeyType.ENTER -> "➜"
        KeyType.MODE_SYM -> "?123"
        KeyType.MODE_ABC -> "ABC"
        KeyType.EMOJI -> "☺"
        KeyType.SPACE -> ""
        KeyType.SPACER -> ""
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        drawToolbar(canvas)
        slots.forEach { slot ->
            val key = slot.key
            val isPressed = slot === pressed
            paint.color = when {
                isPressed -> pressedColor
                key.type == KeyType.ENTER -> actionColor
                key.type == KeyType.SHIFT && shift == Shift.CAPS -> actionColor
                key.type.special -> specialColor
                else -> keyColor
            }
            paint.setShadowLayer(1.2f * d, 0f, 1.4f * d, Color.argb(38, 25, 35, 50))
            canvas.drawRoundRect(slot.rect, keyRadius, keyRadius, paint)
            paint.clearShadowLayer()

            val label = labelFor(key)
            if (label.isNotEmpty()) {
                textPaint.color = if (key.type == KeyType.ENTER) ink else ink
                textPaint.textSize = when (key.type) {
                    KeyType.MODE_SYM, KeyType.MODE_ABC -> 14f * d
                    KeyType.SHIFT, KeyType.DEL, KeyType.ENTER -> 23f * d
                    else -> 22f * d
                }
                val baseline = slot.rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(label, slot.rect.centerX(), baseline, textPaint)
            }

            if (layer == Layer.LETTERS && slot.row == 0 && key.type == KeyType.CHAR) {
                val number = when (key.label.lowercase()) {
                    "q" -> "1"; "w" -> "2"; "e" -> "3"; "r" -> "4"; "t" -> "5"
                    "y" -> "6"; "u" -> "7"; "i" -> "8"; "o" -> "9"; "p" -> "0"
                    else -> ""
                }
                textPaint.color = mutedInk
                textPaint.textSize = 8.5f * d
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(number, slot.rect.right - 4f * d, slot.rect.top + 12f * d, textPaint)
                textPaint.textAlign = Paint.Align.CENTER
            }
            if (key.type == KeyType.SPACE) {
                textPaint.color = mutedInk
                textPaint.textSize = 10f * d
                val baseline = slot.rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText("English (US)", slot.rect.centerX(), baseline, textPaint)
            }
        }
    }

    private fun drawToolbar(canvas: Canvas) {
        val labels = listOf("☺", "▣", "⚙", "⌨")
        val cell = width / 4f
        labels.forEachIndexed { index, label ->
            val cx = cell * (index + 0.5f)
            paint.color = if (pressedTool == index) pressedColor else Color.TRANSPARENT
            canvas.drawRoundRect(
                RectF(cx - 17f * d, 4f * d, cx + 17f * d, toolbarHeight - 3f * d),
                18f * d, 18f * d, paint
            )
            textPaint.color = mutedInk
            textPaint.textSize = if (index == 1) 19f * d else 21f * d
            val baseline = toolbarHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, cx, baseline, textPaint)
        }
    }

    private fun hit(x: Float, y: Float): Slot? =
        slots.firstOrNull { x >= it.rect.left - gap / 2 && x <= it.rect.right + gap / 2 &&
            y >= it.rect.top - gap / 2 && y <= it.rect.bottom + gap / 2 }

    private fun haptic() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.y < toolbarHeight) {
                    pressedTool = (event.x / (width / 4f)).toInt().coerceIn(0, 3)
                    pressed = null
                } else {
                    pressedTool = -1
                    pressed = hit(event.x, event.y)
                }
                invalidate()
                if (pressed != null) {
                    haptic()
                    if (pressed?.key?.type == KeyType.DEL) {
                        listener.onDelete()
                        handler.postDelayed(repeat, 420)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressedTool >= 0) {
                    val next = if (event.y < toolbarHeight) (event.x / (width / 4f)).toInt().coerceIn(0, 3) else -1
                    if (next != pressedTool) {
                        pressedTool = next
                        invalidate()
                    }
                } else {
                    val next = hit(event.x, event.y)
                    if (next !== pressed) {
                        handler.removeCallbacks(repeat)
                        pressed = next
                        invalidate()
                        if (next != null) haptic()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeat)
                val tool = pressedTool
                val key = pressed?.key
                pressedTool = -1
                pressed = null
                invalidate()
                if (tool >= 0 && event.y < toolbarHeight) {
                    when (tool) {
                        0 -> listener.onText("😊")
                        1 -> listener.onClipboard()
                        2 -> listener.onSettings()
                        3 -> listener.onPicker()
                    }
                } else if (key != null && key.type != KeyType.DEL) {
                    fire(key)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeat)
                pressed = null
                pressedTool = -1
                invalidate()
                return true
            }
        }
        return true
    }

    private fun fire(key: Key) {
        when (key.type) {
            KeyType.CHAR -> {
                val value = if (layer == Layer.LETTERS && shift != Shift.OFF) key.label.uppercase() else key.label
                if (layer == Layer.LETTERS && shift == Shift.ON) {
                    shift = Shift.OFF
                    invalidate()
                }
                listener.onText(value)
            }
            KeyType.SPACE -> listener.onText(" ")
            KeyType.EMOJI -> listener.onText("😊")
            KeyType.ENTER -> listener.onEnter()
            KeyType.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == Shift.CAPS -> Shift.OFF
                    shift == Shift.ON && now - lastShiftTap < 360 -> Shift.CAPS
                    shift == Shift.ON -> Shift.OFF
                    else -> Shift.ON
                }
                lastShiftTap = now
                invalidate()
            }
            KeyType.MODE_SYM -> setLayer(Layer.SYMBOLS)
            KeyType.MODE_ABC -> setLayer(Layer.LETTERS)
            else -> Unit
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(repeat)
        super.onDetachedFromWindow()
    }
}
