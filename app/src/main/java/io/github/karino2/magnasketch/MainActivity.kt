package io.github.karino2.magnasketch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import java.io.File
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    val surface: SurfaceView by lazy { findViewById(R.id.canvas)}

    private val penWidth = 5f
    val penPaint = Paint().apply {
        isAntiAlias = true
        // isDither = true
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        // strokeJoin = Paint.Join.ROUND
        // strokeCap = Paint.Cap.ROUND
        strokeWidth = penWidth
    }

    val bmpPaint = Paint(Paint.DITHER_FLAG)

    val bitmap by lazy {
        val bmp = createBitmap(surface.width, surface.height)
        val bcanvas = Canvas(bmp)
        try {
            val sbmp = openFileInput(fileName).use {
                BitmapFactory.decodeStream(it)
            }
            bcanvas.drawBitmap(sbmp, 0f, 0f, bmpPaint)
        }catch(_: java.io.FileNotFoundException)
        {
            bcanvas.drawColor(Color.WHITE)
        }
        bmp
    }

    val bmpCanvas by lazy { Canvas(bitmap) }

    var isDirty = false

    fun drawScribbleToBitmap(points: List<TouchPoint>) {
        if (points.isEmpty()) return
        isDirty = true
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

    private var lastOpenedLimit: Rect? = null

    private fun applyRawDrawingSettings() {
        val limit = surfaceVisibleRect()
        if (!surface.holder.surface.isValid || limit.width() <= 0 || limit.height() <= 0) return

        // サイズが変わっていたら、バッファ不整合を防ぐため開き直す（黒画面対策の核心）
        if (isRawRenderingBecomesStale(limit)) {
            ensureCloseRawRendering()
        }

        if (!touchHelper.isRawDrawingRenderEnabled) {
            clearSurfaceView(bitmap)
            touchHelper.setStrokeWidth(penWidth)
                .setStrokeColor(Color.BLACK)
                .setLimitRect(limit, emptyList<Rect>())
                .setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                .openRawDrawing()
            touchHelper.setRawDrawingEnabled(true)
            touchHelper.isRawDrawingRenderEnabled = true
            lastOpenedLimit = Rect(limit)
        } else {
            touchHelper.setLimitRect(limit, emptyList<Rect>())
        }
    }

    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        touchHelper.isRawDrawingRenderEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())

    private fun surfaceVisibleRect(): Rect {
        val limit = Rect()
        surface.getLocalVisibleRect(limit)

        return limit
    }

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            applyRawDrawingSettings()
        }
        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    val touchHelper by lazy {
        val helper = TouchHelper.create(surface, inputCallback)
        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyRawDrawingSettings() }
        surface.holder.addCallback(surfaceCallback)
        helper
    }

    fun clearSurfaceView(bmp: Bitmap? = null): Boolean {
        val holder = surface.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        bmp?.let {
            canvas.drawBitmap(it, 0f, 0f, bmpPaint)
        }
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.let {
            it.setDisplayShowHomeEnabled(true)
            it.setIcon(R.mipmap.ic_launcher)
            it.title = ""
        }
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
        applyRawDrawingSettings()
    }

    private fun updateBmpToSurface() {
        surface.holder?.let { holder->
            holder.lockCanvas()?.let { lockCanvas ->
                lockCanvas.drawColor(Color.WHITE)
                lockCanvas.drawBitmap(bitmap, 0f, 0f, bmpPaint)
                holder.unlockCanvasAndPost(lockCanvas)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyRawDrawingSettings()
    }

    override fun onPause() {
        ensureCloseRawRendering()
        ensureSave()
        updateBmpToSurface()
        super.onPause()
    }

    override fun onDestroy() {
        ensureCloseRawRendering()
        super.onDestroy()
    }

    private fun ensureCloseRawRendering() {
        if (touchHelper.isRawDrawingRenderEnabled) {
            touchHelper.setRawDrawingEnabled(false)
            touchHelper.isRawDrawingRenderEnabled = false
            touchHelper.closeRawDrawing()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    val fileName = "canvas.png"

    fun ensureSave() {
        if(!isDirty) return

        doSave()
        isDirty = false
    }

    private fun doSave() {
        openFileOutput(fileName, MODE_PRIVATE).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.flush()
        }
    }

    fun withTempNoRawRendering(f: ()->Unit) {
        val isRawRendering = touchHelper.isRawDrawingRenderEnabled
        if (isRawRendering)
        {
            touchHelper.setRawDrawingEnabled(false)
        }
        f()
        if (isRawRendering)
        {
            touchHelper.setRawDrawingEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_clear -> {
                withTempNoRawRendering {
                    clearSurfaceView()
                    bmpCanvas.drawColor(Color.WHITE)
                    doSave()
                }
                isDirty = false
                return true
            }
            R.id.action_share-> {
                withTempNoRawRendering {
                    ensureSave()

                    val path = File.createTempFile("magna_sketch_share_", ".png", cacheDir)
                    openFileInput(fileName).use { src->
                        path.outputStream().use { dest ->
                            src.copyTo(dest)
                            dest.flush()
                        }
                    }
                    val uri = FileProvider.getUriForFile(this, applicationContext.packageName+".provider", path)
                    ShareCompat.IntentBuilder(this).apply {
                        setChooserTitle("Sharing MagnaSketch")
                        setStream(uri)
                        setType("image/png")
                    }.startChooser()
                }
            }

        }
        return super.onOptionsItemSelected(item)
    }

}
