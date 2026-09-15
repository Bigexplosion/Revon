package com.example.qstart

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * 曬單軌跡圖 View
 * 以速度色彩繪製 GPS 軌跡，支援雙指縮放與拖曳移動，雙擊重置視角。
 */
class TrackPathView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 軌跡點資料，設定後自動重繪 */
    var points: List<TrackPoint> = emptyList()
        set(value) { field = value; pathBitmap = null; invalidate() }

    /** 速度顏色模式：GpsConfig.SPEED_COLOR_FIXED 或 SPEED_COLOR_DYNAMIC */
    var colorMode: Int = GpsConfig.SPEED_COLOR_FIXED
        set(value) { field = value; pathBitmap = null; invalidate() }

    /** 動態速域：由呼叫方預先計算並傳入 */
    var dynamicMin: Double = 0.0
    var dynamicMax: Double = 120.0

    /** 主題強調色（用於未來擴充描邊等） */
    var accentColor: Int = Color.parseColor("#00E5FF")

    // ─── 縮放 & 拖曳狀態 ────────────────────────────────────────────────────
    private var scaleValue = 1f
    private var translateX = 0f
    private var translateY = 0f

    // 快取 Bitmap，避免每幀重建路徑
    private var pathBitmap: Bitmap? = null

    // ─── 手勢偵測器 ──────────────────────────────────────────────────────────
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = (scaleValue * factor).coerceIn(0.25f, 10f)
                // 以縮放焦點為中心縮放
                translateX = detector.focusX - (detector.focusX - translateX) * (newScale / scaleValue)
                translateY = detector.focusY - (detector.focusY - translateY) * (newScale / scaleValue)
                scaleValue = newScale
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent,
                distX: Float, distY: Float
            ): Boolean {
                translateX -= distX
                translateY -= distY
                invalidate()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetView()
                return true
            }
        })

    /** 重置到初始視角（雙擊觸發） */
    fun resetView() {
        scaleValue = 1f
        translateX = 0f
        translateY = 0f
        invalidate()
    }

    // ─── 速度色彩計算 ────────────────────────────────────────────────────────
    private fun getSpeedColor(speed: Double): Int {
        val minS = if (colorMode == GpsConfig.SPEED_COLOR_DYNAMIC) dynamicMin else 0.0
        val maxS = if (colorMode == GpsConfig.SPEED_COLOR_DYNAMIC) dynamicMax else 120.0
        val range = (maxS - minS).coerceAtLeast(1.0)
        val ratio = ((speed - minS) / range).coerceIn(0.0, 1.0).toFloat()
        val hue = 240f - (ratio * 240f)   // 藍(240) → 綠(120) → 黃(60) → 紅(0)
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }

    // ─── 路徑 Bitmap 建立 ────────────────────────────────────────────────────
    private fun buildPathBitmap(w: Int, h: Int): Bitmap {
        val pts = points
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (pts.size < 2) return bmp

        val canvas = Canvas(bmp)

        // 計算邊界
        val minLat = pts.minOf { it.lat }
        val maxLat = pts.maxOf { it.lat }
        val minLng = pts.minOf { it.lng }
        val maxLng = pts.maxOf { it.lng }
        val latRange = (maxLat - minLat).coerceAtLeast(0.00005)
        val lngRange = (maxLng - minLng).coerceAtLeast(0.00005)

        val padding = w * 0.1f
        val drawW = w - padding * 2
        val drawH = h - padding * 2
        val drawScale = minOf(drawW / lngRange.toFloat(), drawH / latRange.toFloat())

        val offsetX = padding + (drawW - lngRange.toFloat() * drawScale) / 2f
        val offsetY = padding + (drawH - latRange.toFloat() * drawScale) / 2f

        fun toX(lng: Double) = offsetX + ((lng - minLng) * drawScale).toFloat()
        fun toY(lat: Double) = offsetY + ((maxLat - lat) * drawScale).toFloat()

        // 動態速域計算
        if (colorMode == GpsConfig.SPEED_COLOR_DYNAMIC) {
            dynamicMin = pts.minOf { it.speed }
            dynamicMax = pts.maxOf { it.speed }.coerceAtLeast(dynamicMin + 1.0)
        }

        val paint = Paint().apply {
            strokeWidth = (w / 160f).coerceIn(2f, 6f)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            paint.color = getSpeedColor(curr.speed)
            canvas.drawLine(toX(prev.lng), toY(prev.lat), toX(curr.lng), toY(curr.lat), paint)
        }

        return bmp
    }

    // ─── View 生命週期 ───────────────────────────────────────────────────────
    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        pathBitmap = null   // 尺寸變更時重建
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return
        val w = width; val h = height
        if (w == 0 || h == 0) return

        if (pathBitmap == null) {
            pathBitmap = buildPathBitmap(w, h)
        }
        val bmp = pathBitmap ?: return

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleValue, scaleValue)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var consumed = scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            consumed = gestureDetector.onTouchEvent(event) || consumed
        }
        // 確保 parent 不攔截觸控（在 ScrollView 中）
        parent?.requestDisallowInterceptTouchEvent(true)
        return consumed || super.onTouchEvent(event)
    }
}
