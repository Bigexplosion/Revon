package com.example.qstart

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TrackPreviewView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var onPositionChanged: ((Double) -> Unit)? = null

    private val pathPaint = Paint().apply {
        color = Color.parseColor("#55FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val markerAPaint = Paint().apply { color = Color.parseColor("#00E5FF"); style = Paint.Style.FILL; isAntiAlias = true }
    private val markerBPaint = Paint().apply { color = Color.parseColor("#FF5252"); style = Paint.Style.FILL; isAntiAlias = true }

    private var trackPoints: List<TrackPoint> = listOf()
    private var posA: PointF? = null
    private var posB: PointF? = null
    
    private val trackPath = Path()
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()

    fun setTrack(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        this.trackPoints = points
        post {
            calculatePath()
            invalidate()
        }
    }

    fun updatePositions(distA: Double, distB: Double) {
        if (trackPoints.isEmpty()) return
        posA = getPointAtDist(distA)
        posB = getPointAtDist(distB)
        invalidate()
    }

    private fun getPointAtDist(dist: Double): PointF? {
        val p = trackPoints.minByOrNull { Math.abs(it.distance - dist) } ?: return null
        val pts = floatArrayOf(p.lng.toFloat(), p.lat.toFloat())
        matrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
    }

    private fun calculatePath() {
        if (trackPoints.isEmpty() || width <= 0 || height <= 0) return
        trackPath.reset()
        val minLat = trackPoints.minOf { it.lat }; val maxLat = trackPoints.maxOf { it.lat }
        val minLng = trackPoints.minOf { it.lng }; val maxLng = trackPoints.maxOf { it.lng }
        val avgLat = (minLat + maxLat) / 2.0
        val latCos = Math.cos(Math.toRadians(avgLat))
        val latRange = maxLat - minLat; val lngRangeNormalized = (maxLng - minLng) * latCos
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        val scale = Math.min(w / lngRangeNormalized.toFloat(), h / latRange.toFloat()) * 0.9f
        
        matrix.reset()
        matrix.postScale(latCos.toFloat(), 1.0f)
        matrix.postTranslate((-minLng * latCos).toFloat(), (-maxLat).toFloat())
        matrix.postScale(scale, -scale)
        val offsetX = paddingLeft + (w - lngRangeNormalized.toFloat() * scale) / 2f
        val offsetY = paddingTop + (h - latRange.toFloat() * scale) / 2f
        matrix.postTranslate(offsetX, offsetY)
        matrix.invert(inverseMatrix)

        trackPoints.forEachIndexed { i, p ->
            val pts = floatArrayOf(p.lng.toFloat(), p.lat.toFloat())
            matrix.mapPoints(pts)
            if (i == 0) trackPath.moveTo(pts[0], pts[1]) else trackPath.lineTo(pts[0], pts[1])
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (trackPoints.isEmpty()) return false
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val touchPts = floatArrayOf(event.x, event.y)
            inverseMatrix.mapPoints(touchPts)
            // 找出距離觸碰 GPS 座標最近的點 (注意矩陣轉換回來的順序是 [lng, lat])
            val closest = trackPoints.minByOrNull { p ->
                val dx = p.lng - touchPts[0]
                val dy = p.lat - touchPts[1]
                dx * dx + dy * dy
            }
            closest?.let { onPositionChanged?.invoke(it.distance) }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(trackPath, pathPaint)
        posA?.let { canvas.drawCircle(it.x, it.y, 16f, markerAPaint) }
        posB?.let { canvas.drawCircle(it.x, it.y, 16f, markerBPaint) }
    }
}
