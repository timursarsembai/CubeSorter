package com.timursarsembayev.cubesorter

import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class SortType {
    NUMBERS,   // Сортировка чисел 0-9
    COLORS,    // Сортировка цветов
    EMOJIS     // Сортировка смайликов
}

class SorterGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object { const val MAX_LEVEL = 40 }

    var onMovesChanged: ((moves: Int) -> Unit)? = null
    var onRoundChanged: ((round: Int, targets: IntArray) -> Unit)? = null
    var onRoundCompleted: ((round: Int, moves: Int) -> Unit)? = null
    // Новый колбэк при завершении всех уровней
    var onAllCompleted: (() -> Unit)? = null

    private var cols = 4
    private var rows = 7

    private var cellSize = 0f
    private var gridLeft = 0f
    private var gridTop = 0f
    private var labelArea = 0f

    private var stacks: MutableList<MutableList<Int>> = MutableList(cols) { mutableListOf() }

    private var moves = 0
    private var round = 1
    private var targets: IntArray = IntArray(maxOf(1, cols - 1)) { it }
    // Свойство для получения текущего уровня (используется в активности)
    val currentRound: Int get() = round

    // Текущий тип сортировки
    private var currentSortType: SortType = SortType.NUMBERS

    private var dragging = false
    private var dragFromCol = -1
    private var dragDigit = -1
    private var dragX = 0f
    private var dragY = 0f

    private var hoverCol: Int = -1
    private var hoverRow: Int = -1
    private var hoverValid: Boolean = false

    private var rejectCol: Int = -1
    private var rejectAnimStart: Long = 0L
    private val rejectAnimDuration = 250L

    private var acceptAnimActive: Boolean = false
    private var acceptAnimStart: Long = 0L
    private val acceptAnimDuration = 350L

    private var tone: ToneGenerator? = null

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#22000000")
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10FFFFFF")
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(6f * resources.displayMetrics.density, BlurMaskFilter.Blur.NORMAL)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#263238")
    }

    // Цвета для чисел
    private val digitColors = intArrayOf(
        Color.parseColor("#FF7043"), // 0 - оранжевый
        Color.parseColor("#42A5F5"), // 1 - голубой
        Color.parseColor("#66BB6A"), // 2 - зеленый
        Color.parseColor("#FFA726"), // 3 - желтый
        Color.parseColor("#AB47BC"), // 4 - фиолетовый
        Color.parseColor("#26C6DA"), // 5 - бирюзовый
        Color.parseColor("#EC407A"), // 6 - розовый
        Color.parseColor("#7E57C2"), // 7 - сиреневый
        Color.parseColor("#8D6E63"), // 8 - коричневый
        Color.parseColor("#FFCA28")  // 9 - золотой
    )

    // Цвета для сортировки по цветам
    private val sortColors = intArrayOf(
        Color.parseColor("#F44336"), // 0 - красный
        Color.parseColor("#FF9800"), // 1 - оранжевый
        Color.parseColor("#FFEB3B"), // 2 - желтый
        Color.parseColor("#4CAF50"), // 3 - зеленый
        Color.parseColor("#2196F3"), // 4 - синий
        Color.parseColor("#9C27B0"), // 5 - фиолетовый
        Color.parseColor("#E91E63"), // 6 - розовый
        Color.parseColor("#795548"), // 7 - коричневый
        Color.parseColor("#607D8B"), // 8 - серый
        Color.parseColor("#000000")  // 9 - черный
    )

    // Смайлики для сортировки
    private val emojiList = arrayOf(
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊"
    )

    init { isClickable = true; resetAll() }

    fun resetAll() { moves = 0; round = 1; startRound(); notifyMoves() }
    fun nextRound() {
        // Если текущий раунд меньше максимального – идем дальше, иначе уведомляем о полном завершении
        if (round < MAX_LEVEL) {
            round += 1
            startRound()
        } else {
            onAllCompleted?.invoke()
        }
    }

    // Определяем тип сортировки для текущего раунда
    private fun getSortTypeForRound(round: Int): SortType {
        return when ((round - 1) % 3) {
            0 -> SortType.NUMBERS
            1 -> SortType.COLORS
            2 -> SortType.EMOJIS
            else -> SortType.NUMBERS
        }
    }

    // Переход на указанный уровень (для режима администратора)
    fun jumpToLevel(level: Int) {
        val target = level.coerceIn(1, MAX_LEVEL)
        round = target
        startRound()
    }

    private fun computeGridForRound() {
        val step = (round - 1) / 5
        cols = 4 + step
        rows = 7 + step
        if (cols < 3) cols = 3
        if (rows < 5) rows = 5
    }

    private fun resizeStacks(newCols: Int) {
        if (stacks.size == newCols) { stacks.forEach { it.clear() }; return }
        stacks = MutableList(newCols) { mutableListOf() }
    }

    private fun recalcMetrics(contentW: Int, contentH: Int) {
        val tentativeCell = min(contentW / cols.toFloat(), contentH / rows.toFloat())
        labelPaint.textSize = tentativeCell * 0.5f
        labelArea = labelPaint.textSize * 1.6f
        cellSize = min(contentW / cols.toFloat(), (contentH - labelArea) / rows.toFloat())
        val gridW = cellSize * cols
        val gridH = cellSize * rows
        gridLeft = paddingLeft + (contentW - gridW) / 2f
        gridTop = paddingTop + (contentH - (gridH + labelArea)) / 2f
        textPaint.textSize = cellSize * 0.55f
        labelPaint.textSize = cellSize * 0.5f
    }

    private fun recalcMetricsFromView() {
        val contentW = width - paddingLeft - paddingRight
        val contentH = height - paddingTop - paddingBottom
        if (contentW > 0 && contentH > 0) recalcMetrics(contentW, contentH)
    }

    private fun startRound() {
        // Определяем тип сортировки для текущего раунда
        currentSortType = getSortTypeForRound(round)

        computeGridForRound()
        resizeStacks(cols)
        recalcMetricsFromView()
        moves = 0; notifyMoves()
        val targetCount = maxOf(1, cols - 1)
        targets = IntArray(targetCount)
        val pool = (0..9).shuffled(Random(System.currentTimeMillis()))
        for (i in 0 until targetCount) targets[i] = pool[i]
        val perColumn = (rows - 1).coerceAtLeast(1)
        val bricks = mutableListOf<Int>()
        for (i in 0 until targetCount) repeat(perColumn) { bricks.add(targets[i]) }
        bricks.shuffle(Random(System.currentTimeMillis()))
        var idx = 0
        for (c in 0 until targetCount) {
            val col = stacks[c]
            col.clear(); repeat(perColumn) { col.add(bricks[idx++]) }
        }
        stacks[targetCount].clear()
        onRoundChanged?.invoke(round, targets.copyOf())
        invalidate()
    }

    private fun notifyMoves() { onMovesChanged?.invoke(moves) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcMetrics(w - paddingLeft - paddingRight, h - paddingTop - paddingBottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bufferColIdx = cols - 1
        val bl = gridLeft + bufferColIdx * cellSize
        val bt = gridTop
        val br = bl + cellSize
        val bb = gridTop + rows * cellSize
        overlayPaint.shader = LinearGradient(
            bl, bt, br, bb,
            Color.argb(28, 0, 200, 83),
            Color.argb(12, 0, 200, 83),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(bl, bt, br, bb, overlayPaint)
        overlayPaint.shader = null

        // Рисуем сетку
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val l = gridLeft + c * cellSize
                val t = gridTop + r * cellSize
                canvas.drawRect(l, t, l + cellSize, t + cellSize, cellBgPaint)
                canvas.drawRect(l, t, l + cellSize, t + cellSize, gridPaint)
            }
        }

        // Рисуем блоки
        for (c in 0 until cols) {
            val stack = stacks[c]
            for (i in stack.indices) {
                if (dragging && c == dragFromCol && i == stack.lastIndex) continue
                val digit = stack[i]
                val r = rows - 1 - i
                drawBlock(canvas, c, r, digit)
            }
        }

        // Рисуем перетаскиваемый блок
        if (dragging && dragDigit >= 0) drawFloatingBlock(canvas, dragX, dragY, dragDigit)

        // Подсветка места для размещения
        if (dragging && hoverCol >= 0 && hoverRow >= 0 && hoverValid) {
            val l = gridLeft + hoverCol * cellSize
            val t = gridTop + hoverRow * cellSize
            overlayPaint.color = Color.argb(80, 56, 142, 60)
            canvas.drawRect(l, t, l + cellSize, t + cellSize, overlayPaint)
        }

        // Анимация отклонения
        if (rejectCol in 0 until cols) {
            val elapsed = SystemClock.uptimeMillis() - rejectAnimStart
            val p = (elapsed.toFloat() / rejectAnimDuration).coerceIn(0f, 1f)
            if (p < 1f) {
                val alpha = ((1f - p) * 100).toInt().coerceIn(0, 100)
                val l = gridLeft + rejectCol * cellSize
                val t = gridTop
                overlayPaint.color = Color.argb(alpha, 244, 67, 54)
                canvas.drawRect(l, t, l + cellSize, t + cellSize, overlayPaint)
                val shake = (sin(p * Math.PI * 4).toFloat() * cellSize * 0.02f)
                canvas.drawRect(l + shake, t, l + cellSize + shake, t + cellSize, gridPaint)
                postInvalidateOnAnimation()
            } else { rejectCol = -1 }
        }

        // Анимация принятия
        if (acceptAnimActive) {
            val elapsed = SystemClock.uptimeMillis() - acceptAnimStart
            val p = (elapsed.toFloat() / acceptAnimDuration).coerceIn(0f, 1f)
            if (p < 1f) {
                val l = gridLeft + bufferColIdx * cellSize
                val t = gridTop
                val sweepH = cellSize * p
                overlayPaint.shader = LinearGradient(
                    l, t, l, t + sweepH,
                    Color.argb(120, 76, 175, 80), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                canvas.drawRect(l, t, l + cellSize, t + sweepH, overlayPaint)
                overlayPaint.shader = null
                postInvalidateOnAnimation()
            } else { acceptAnimActive = false }
        }

        // Рисуем метки целей внизу
        drawTargetLabels(canvas)
    }

    private fun drawBlock(canvas: Canvas, col: Int, row: Int, digit: Int) {
        val cx = gridLeft + col * cellSize + cellSize / 2f
        val cy = gridTop + row * cellSize + cellSize / 2f
        val size = cellSize * 0.9f
        val half = size / 2f
        val left = cx - half
        val top = cy - half
        val right = cx + half
        val bottom = cy + half
        val round = cellSize * 0.18f

        // Выбираем цвет в зависимости от типа сортировки
        val color = when (currentSortType) {
            SortType.NUMBERS -> digitColors[digit]
            SortType.COLORS -> sortColors[digit]
            SortType.EMOJIS -> digitColors[digit] // Для смайликов используем базовые цвета
        }

        shadowPaint.color = color
        canvas.drawRoundRect(left + 3f, top + 3f, right + 3f, bottom + 3f, round, round, shadowPaint)
        blockPaint.shader = LinearGradient(
            left, top, right, bottom,
            lighten(color, 0.25f), darken(color, 0.15f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(left, top, right, bottom, round, round, blockPaint)

        // Выбираем содержимое блока в зависимости от типа сортировки
        when (currentSortType) {
            SortType.NUMBERS -> {
                textPaint.color = if (isColorDark(color)) Color.WHITE else Color.parseColor("#212121")
                val fm = textPaint.fontMetrics
                val ty = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(digit.toString(), cx, ty, textPaint)
            }
            SortType.COLORS -> {
                // Для цветов показываем только цветной блок без текста
            }
            SortType.EMOJIS -> {
                textPaint.color = Color.parseColor("#212121")
                val originalSize = textPaint.textSize
                textPaint.textSize = cellSize * 0.6f // Увеличиваем размер для смайликов
                val fm = textPaint.fontMetrics
                val ty = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(emojiList[digit], cx, ty, textPaint)
                textPaint.textSize = originalSize // Возвращаем обычный размер
            }
        }
    }

    private fun drawFloatingBlock(canvas: Canvas, x: Float, y: Float, digit: Int) {
        val size = cellSize * 0.9f
        val half = size / 2f
        val left = x - half
        val top = y - half
        val right = x + half
        val bottom = y + half
        val round = cellSize * 0.18f

        // Выбираем цвет в зависимости от типа сортировки
        val color = when (currentSortType) {
            SortType.NUMBERS -> digitColors[digit]
            SortType.COLORS -> sortColors[digit]
            SortType.EMOJIS -> digitColors[digit] // Для смайликов используем базовые цвета
        }

        shadowPaint.color = color
        canvas.drawRoundRect(left + 3f, top + 3f, right + 3f, bottom + 3f, round, round, shadowPaint)
        blockPaint.shader = LinearGradient(
            left, top, right, bottom,
            lighten(color, 0.25f), darken(color, 0.15f), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(left, top, right, bottom, round, round, blockPaint)

        // Выбираем содержимое блока в зависимости от типа сортировки
        when (currentSortType) {
            SortType.NUMBERS -> {
                textPaint.color = if (isColorDark(color)) Color.WHITE else Color.parseColor("#212121")
                val fm = textPaint.fontMetrics
                val ty = y - (fm.ascent + fm.descent) / 2f
                canvas.drawText(digit.toString(), x, ty, textPaint)
            }
            SortType.COLORS -> {
                // Для цветов показываем только цветной блок без текста
            }
            SortType.EMOJIS -> {
                textPaint.color = Color.parseColor("#212121")
                val originalSize = textPaint.textSize
                textPaint.textSize = cellSize * 0.6f // Увеличиваем размер для смайликов
                val fm = textPaint.fontMetrics
                val ty = y - (fm.ascent + fm.descent) / 2f
                canvas.drawText(emojiList[digit], x, ty, textPaint)
                textPaint.textSize = originalSize // Возвращаем обычный размер
            }
        }
    }

    private fun drawTargetLabels(canvas: Canvas) {
        val baseY = gridTop + rows * cellSize + labelArea * 0.65f
        val fm = labelPaint.fontMetrics
        val baseline = baseY - (fm.ascent + fm.descent) / 2f

        for (c in targets.indices) {
            val cx = gridLeft + c * cellSize + cellSize / 2f

            // Отображаем метки в зависимости от типа сортировки
            when (currentSortType) {
                SortType.NUMBERS -> {
                    canvas.drawText(targets[c].toString(), cx, baseline, labelPaint)
                }
                SortType.COLORS -> {
                    // Для цветов рисуем маленький цветной квадратик
                    val color = sortColors[targets[c]]
                    val size = labelPaint.textSize * 0.8f
                    val half = size / 2f
                    val rect = RectF(cx - half, baseY - half, cx + half, baseY + half)
                    val paint = Paint().apply {
                        this.color = color
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                }
                SortType.EMOJIS -> {
                    val originalSize = labelPaint.textSize
                    labelPaint.textSize = labelPaint.textSize * 1.2f // Увеличиваем размер для смайликов
                    canvas.drawText(emojiList[targets[c]], cx, baseline, labelPaint)
                    labelPaint.textSize = originalSize // Возвращаем обычный размер
                }
            }
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val col = colAt(event.x, event.y) ?: return false
                if (stacks[col].isEmpty()) return false
                val touchedRow = rowAt(event.y) ?: return false
                val topRowInCol = rows - 1 - stacks[col].lastIndex
                if (touchedRow != topRowInCol) return false
                dragFromCol = col
                dragDigit = stacks[col].last()
                dragging = true
                dragX = event.x; dragY = event.y
                hoverCol = -1; hoverRow = -1; hoverValid = false
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                dragX = event.x; dragY = event.y
                val col = colAt(event.x, event.y)
                val row = rowAt(event.y)
                hoverCol = -1; hoverRow = -1; hoverValid = false
                if (col != null && row != null) {
                    val bufferColIdx = cols - 1
                    val cap = if (col == bufferColIdx) rows else (rows - 1).coerceAtLeast(1)
                    val size = stacks[col].size
                    if (size < cap) {
                        val allowedRow = rows - 1 - size
                        if (row == allowedRow) { hoverCol = col; hoverRow = row; hoverValid = true }
                    }
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) { performClick(); return false }
                val targetCol = colAt(event.x, event.y)
                var placed = false
                if (targetCol != null) {
                    val bufferColIdx = cols - 1
                    val cap = if (targetCol == bufferColIdx) rows else (rows - 1).coerceAtLeast(1)
                    if (stacks[targetCol].size < cap) {
                        val from = dragFromCol
                        if (from >= 0 && stacks[from].isNotEmpty() && stacks[from].last() == dragDigit) {
                            stacks[from].removeAt(stacks[from].lastIndex)
                            stacks[targetCol].add(dragDigit)
                            moves++; notifyMoves(); placed = true
                            if (targetCol == bufferColIdx) {
                                acceptAnimActive = true; acceptAnimStart = SystemClock.uptimeMillis(); postInvalidateOnAnimation()
                            }
                            checkRoundComplete()
                        }
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                        rejectCol = targetCol; rejectAnimStart = SystemClock.uptimeMillis(); postInvalidateOnAnimation()
                    }
                }
                dragging = false
                dragFromCol = -1; dragDigit = -1; hoverCol = -1; hoverRow = -1; hoverValid = false
                invalidate()
                if (!placed) performClick(); return placed
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() { tone?.release(); tone = null; super.onDetachedFromWindow() }

    private fun checkRoundComplete() {
        val perColumn = (rows - 1).coerceAtLeast(1)
        for (c in targets.indices) {
            val st = stacks[c]
            if (st.size != perColumn) return
            val need = targets[c]
            if (st.any { it != need }) return
        }
        if (stacks[cols - 1].isNotEmpty()) return
        onRoundCompleted?.invoke(round, moves)
        // Автоматический переход отключён – решает активити (кнопка "далее")
    }
        // Переход на следующий уровень теперь инициируется извне (через кнопку "далее")
    private fun colAt(x: Float, y: Float): Int? {
        val withinX = x >= gridLeft && x < gridLeft + cols * cellSize
        val withinY = y >= gridTop && y < gridTop + rows * cellSize
        if (!withinX || !withinY) return null
        val col = floor((x - gridLeft) / cellSize).toInt()
        return col.coerceIn(0, cols - 1)
    }

    private fun rowAt(y: Float): Int? {
        val withinY = y >= gridTop && y < gridTop + rows * cellSize
        if (!withinY) return null
        val row = floor((y - gridTop) / cellSize).toInt()
        return row.coerceIn(0, rows - 1)
    }

    private fun lighten(color: Int, amount: Float): Int {
        val a = Color.alpha(color)
        var r = Color.red(color)
        var g = Color.green(color)
        var b = Color.blue(color)
        r = (r + (255 - r) * amount).toInt()
        g = (g + (255 - g) * amount).toInt()
        b = (b + (255 - b) * amount).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun darken(color: Int, amount: Float): Int {
        val a = Color.alpha(color)
        var r = Color.red(color)
        var g = Color.green(color)
        var b = Color.blue(color)
        r = (r * (1f - amount)).toInt()
        g = (g * (1f - amount)).toInt()
        b = (b * (1f - amount)).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness >= 0.5
    }
}
