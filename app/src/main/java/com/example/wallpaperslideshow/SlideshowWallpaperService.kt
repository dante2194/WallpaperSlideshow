package com.example.wallpaperslideshow

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.preference.PreferenceManager

/**
 * Live wallpaper that cycles through every picture of a chosen folder.
 *
 * Duration / transition / shuffle are read live from SharedPreferences,
 * so changing them in Settings takes effect immediately.
 */
class SlideshowWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SlideshowEngine()

    private inner class SlideshowEngine :
        Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val appCtx get() = this@SlideshowWallpaperService

        private val handler = Handler(Looper.getMainLooper())
        private val prefs by lazy {
            PreferenceManager.getDefaultSharedPreferences(appCtx)
        }

        // ---- surface state -------------------------------------------------
        private var surfaceReady = false
        private var viewW = 0
        private var viewH = 0

        // ---- slideshow state ----------------------------------------------
        private var images: List<Uri> = emptyList()
        private var order: MutableList<Int> = mutableListOf()
        private var position = 0
        private var needsReload = true
        private var loading = false

        private var current: Bitmap? = null
        private var previous: Bitmap? = null

        // ---- transition state ---------------------------------------------
        private var animating = false
        private var progress = 1f
        private var transitionStart = 0L
        private var transitionDuration = 0L

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val background = Paint().apply { color = Color.BLACK }

        private val advanceRunnable = Runnable { advance() }

        private val frameRunnable = object : Runnable {
            override fun run() {
                tick()
                if (animating) handler.postDelayed(this, FRAME_MS)
            }
        }

        // ===================================================================
        //  Engine lifecycle
        // ===================================================================
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setOffsetNotificationsEnabled(false)
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            try {
                prefs.unregisterOnSharedPreferenceChangeListener(this)
            } catch (_: Throwable) {
            }
            handler.removeCallbacksAndMessages(null)
            releaseBitmaps()
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            viewW = w
            viewH = h
            surfaceReady = true
            reloadIfNeeded()
            if (images.isNotEmpty()) {
                if (current == null) current = loadBitmap(currentUri())
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(frameRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            handler.removeCallbacks(advanceRunnable)
            handler.removeCallbacks(frameRunnable)
            if (visible) {
                reloadIfNeeded()
                drawFrame()
                scheduleNext()
            }
        }

        // ===================================================================
        //  Preferences
        // ===================================================================
        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            when (key) {
                Prefs.KEY_FOLDER -> {
                    needsReload = true
                    if (isVisible) reloadIfNeeded()
                }
                Prefs.KEY_SHUFFLE -> {
                    rebuildOrder(keepCurrent = true)
                    scheduleNext()
                }
                Prefs.KEY_DURATION -> scheduleNext()
                Prefs.KEY_TRANSITION -> {
                    if (isVisible) drawFrame()
                }
            }
        }

        // ===================================================================
        //  Folder loading
        // ===================================================================
        private fun reloadIfNeeded() {
            if (!needsReload || loading) return
            needsReload = false
            loading = true

            val treeUri = Prefs.folderUri(appCtx)?.let { Uri.parse(it) }

            Thread {
                val list = ImageRepository.listImages(appCtx, treeUri)
                handler.post {
                    loading = false
                    images = list
                    position = 0
                    rebuildOrder(keepCurrent = false)
                    previous = null
                    current = if (viewW > 0) loadBitmap(currentUri()) else null
                    drawFrame()
                    scheduleNext()
                }
            }.start()
        }

        private fun rebuildOrder(keepCurrent: Boolean) {
            val oldUri = if (keepCurrent) currentUri() else null
            order = if (Prefs.shuffle(appCtx)) {
                images.indices.shuffled().toMutableList()
            } else {
                images.indices.toMutableList()
            }
            if (oldUri != null) {
                val idx = images.indexOf(oldUri)
                val pos = order.indexOf(idx)
                if (pos >= 0) position = pos
            }
            if (order.isEmpty()) position = 0
            else position = position.coerceIn(0, order.size - 1)
        }

        private fun currentUri(): Uri? {
            if (images.isEmpty() || order.isEmpty()) return null
            val idx = order[position.coerceIn(0, order.size - 1)]
            return images.getOrNull(idx)
        }

        // ===================================================================
        //  Advancing / scheduling
        // ===================================================================
        private fun scheduleNext() {
            handler.removeCallbacks(advanceRunnable)
            if (!isVisible) return
            val delayMs = Prefs.durationSeconds(appCtx) * 1000L
            handler.postDelayed(advanceRunnable, delayMs)
        }

        private fun advance() {
            if (isVisible && images.isNotEmpty()) {
                previous = current
                position = (position + 1) % order.size
                current = loadBitmap(currentUri())
                startTransition()
            }
            scheduleNext()
        }

        // ===================================================================
        //  Transitions
        // ===================================================================
        private fun startTransition() {
            val type = Prefs.transition(appCtx)
            val duration = Prefs.transitionMs(appCtx)

            if (previous == null || current == null || type == "none" || duration <= 0L) {
                animating = false
                progress = 1f
                previous = null
                drawFrame()
                return
            }

            transitionDuration = duration
            transitionStart = SystemClock.uptimeMillis()
            progress = 0f
            animating = true
            handler.removeCallbacks(frameRunnable)
            handler.post(frameRunnable)
        }

        private fun tick() {
            if (!animating) return
            val elapsed = SystemClock.uptimeMillis() - transitionStart
            progress = (elapsed.toFloat() / transitionDuration.toFloat()).coerceIn(0f, 1f)
            if (progress >= 1f) {
                animating = false
                previous = null
                handler.removeCallbacks(frameRunnable)
            }
            drawFrame()
        }

        // ===================================================================
        //  Drawing
        // ===================================================================
        private fun drawFrame() {
            if (!surfaceReady) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) render(canvas)
            } catch (_: Throwable) {
                // surface went away - ignore
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        private fun render(canvas: Canvas) {
            canvas.drawRect(0f, 0f, viewW.toFloat(), viewH.toFloat(), background)

            val cur = current ?: return
            val prev = previous
            val type = Prefs.transition(appCtx)
            val t = progress

            // 1. old picture underneath
            if (prev != null && t < 1f) {
                canvas.drawBitmap(prev, null, destRect(prev), paint)
            }

            // 2. new picture with the chosen effect
            when {
                prev == null || t >= 1f -> {
                    canvas.drawBitmap(cur, null, destRect(cur), paint)
                }

                type == "slide_left" -> {
                    canvas.save()
                    canvas.translate((1f - t) * viewW, 0f)
                    canvas.drawBitmap(cur, null, destRect(cur), paint)
                    canvas.restore()
                }

                type == "slide_up" -> {
                    canvas.save()
                    canvas.translate(0f, (1f - t) * viewH)
                    canvas.drawBitmap(cur, null, destRect(cur), paint)
                    canvas.restore()
                }

                type == "zoom" -> {
                    val scale = 1.15f - 0.15f * t
                    canvas.save()
                    canvas.scale(scale, scale, viewW / 2f, viewH / 2f)
                    paint.alpha = (t * 255f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(cur, null, destRect(cur), paint)
                    paint.alpha = 255
                    canvas.restore()
                }

                // "fade" and anything unknown
                else -> {
                    paint.alpha = (t * 255f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(cur, null, destRect(cur), paint)
                    paint.alpha = 255
                }
            }
        }

        /** Center-crop destination rectangle so the picture fills the screen. */
        private fun destRect(bmp: Bitmap): Rect {
            if (viewW <= 0 || viewH <= 0) return Rect(0, 0, bmp.width, bmp.height)
            val scale = maxOf(
                viewW.toFloat() / bmp.width.toFloat(),
                viewH.toFloat() / bmp.height.toFloat()
            )
            val w = (bmp.width * scale).toInt()
            val h = (bmp.height * scale).toInt()
            val left = (viewW - w) / 2
            val top = (viewH - h) / 2
            return Rect(left, top, left + w, top + h)
        }

        // ===================================================================
        //  Bitmap loading (downsampled to the screen size)
        // ===================================================================
        private fun loadBitmap(uri: Uri?): Bitmap? {
            if (uri == null || viewW <= 0 || viewH <= 0) return null
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                appCtx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sample = 1
                while (bounds.outWidth / (sample * 2) >= viewW &&
                    bounds.outHeight / (sample * 2) >= viewH
                ) {
                    sample *= 2
                }

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                appCtx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun releaseBitmaps() {
            try {
                current?.recycle()
                previous?.recycle()
            } catch (_: Throwable) {
            }
            current = null
            previous = null
        }

    }
}

// Moved to file scope — inner classes cannot have companions.
private const val FRAME_MS = 16L
