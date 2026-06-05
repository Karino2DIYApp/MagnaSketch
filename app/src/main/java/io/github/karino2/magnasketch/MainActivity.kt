package io.github.karino2.magnasketch

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import androidx.core.graphics.createBitmap
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    val surface: SurfaceView by lazy { findViewById(R.id.canvas)}

    private val penWidth = 3f
    val penPaint = Paint().apply {
        isAntiAlias = true
        // isDither = true
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        // strokeJoin = Paint.Join.ROUND
        // strokeCap = Paint.Cap.ROUND
        strokeWidth = penWidth
    }

    val bitmap by lazy { createBitmap(surface.width, surface.height)}
    val bmpCanvas by lazy { Canvas(bitmap) }

    fun drawScribbleToBitmap(points: List<TouchPoint>) {
        if (points.isEmpty()) return
        val path = Path()
        val prePoint = PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            // skip strange jump point.
            if (abs(prePoint.y - point.y) >= 30)
                continue
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }
        bmpCanvas.drawPath(path, penPaint)
    }

    private val inputCallback : RawInputCallback = object: RawInputCallback() {
        override fun onBeginRawDrawing(
            p0: Boolean,
            p1: TouchPoint?
        ) {
        }

        override fun onEndRawDrawing(
            p0: Boolean,
            p1: TouchPoint?
        ) {
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {

        }

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            drawScribbleToBitmap(plist.points)
        }

        override fun onBeginRawErasing(
            p0: Boolean,
            p1: TouchPoint?
        ) {
        }

        override fun onEndRawErasing(
            p0: Boolean,
            p1: TouchPoint?
        ) {
        }

        override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {
        }

        override fun onRawErasingTouchPointListReceived(p0: TouchPointList?) {
        }
    }
    private var isRawDrawingOpened = false

    private val layoutChangedListener : View.OnLayoutChangeListener by lazy {
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (clearSurfaceView()) {
                surface.removeOnLayoutChangeListener(layoutChangedListener)
            }
            setupRawDrawing()
        }
    }

    private fun setupRawDrawing() {
        if (isRawDrawingOpened) {
            val limit = surfaceVisibleRect()
            touchHelper.setLimitRect(limit, emptyList<Rect>())
            return
        }

        val limit = surfaceVisibleRect()
        touchHelper.setStrokeWidth(penWidth)
            .setStrokeColor(Color.BLACK)
            .setLimitRect(limit, emptyList<Rect>())
            .openRawDrawing()
        touchHelper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
        isRawDrawingOpened = true
    }

    private fun surfaceVisibleRect(): Rect {
        val limit = Rect()
        surface.getLocalVisibleRect(limit)

        // I don't know the reason, but this geometry seems to low for 40px.
        // offset here.
        limit.offset(0, -40)
        return limit
    }

    private val surfaceCallback : SurfaceHolder.Callback by lazy {
        object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder,
                p1: Int,
                p2: Int,
                p3: Int
            ) {
            }

            override fun surfaceCreated(holder: SurfaceHolder) {
                clearSurfaceView()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                holder.removeCallback(surfaceCallback)
            }
        }
    }

    val touchHelper by lazy {
        val helper = TouchHelper.create(surface, inputCallback)
        surface.addOnLayoutChangeListener(layoutChangedListener)
        surface.holder.addCallback(surfaceCallback)
        helper
    }

    fun clearSurfaceView(): Boolean {
        val holder = surface.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        touchHelper
    }

    override fun onResume() {
        super.onResume()
        surface.post {
            setupRawDrawing()
            touchHelper.setRawDrawingEnabled(true)
            touchHelper.isRawDrawingRenderEnabled = true
        }
    }

    override fun onPause() {
        touchHelper.isRawDrawingRenderEnabled = false
        touchHelper.setRawDrawingEnabled(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (isRawDrawingOpened) {
            touchHelper.closeRawDrawing()
            isRawDrawingOpened = false
        }
        super.onDestroy()
    }
}
