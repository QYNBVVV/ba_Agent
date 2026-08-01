package com.baam.mobile.engine.cv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.baam.mobile.engine.task.MatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect as CvRect
import org.opencv.imgproc.Imgproc
import timber.log.Timber
import java.io.IOException

/**
 * 模板匹配封装：与开源 BAAS 同算法（TM_CCOEFF_NORMED），模板 PNG 可直接复用。
 *
 * 设计要点：
 * 1. 模板从 assets 加载，按路径缓存为灰度 Mat，避免重复解码。
 * 2. 截图也转灰度匹配，提速且对色彩扰动更鲁棒。
 * 3. 支持 [Rect] 限定匹配区域（参考坐标系），提速 + 减少误判。
 * 4. 多模板任一命中即返回（场景多态模板）。
 *
 * 线程安全：内部用 [matchMutex] 串行化 OpenCV 调用（OpenCV 单帧匹配本身较快，
 * 串行可避免 Mat 生命周期错乱；如需并行可后续用对象池）。
 */
class TemplateMatcher(
    private val context: Context,
) {
    private val matchMutex = Mutex()
    private val templateCache = HashMap<String, Mat>()

    suspend fun match(
        screenshot: Bitmap,
        templatePaths: List<String>,
        threshold: Float,
        region: Rect? = null,
    ): MatchResult = withContext(Dispatchers.Default) {
        OpenCVInitializer.ensureInitialized()
        if (templatePaths.isEmpty()) return@withContext MatchResult.NOT_FOUND

        matchMutex.withLock {
            val sceneMat = bitmapToGray(screenshot)
            try {
                for (path in templatePaths) {
                    val tpl = loadTemplate(path) ?: continue
                    // 模板比截图（或裁剪后）还大则跳过
                    val target = clipRegion(sceneMat, region)
                    if (tpl.cols() > target.cols() || tpl.rows() > target.rows()) {
                        Timber.w("template $path larger than target, skip")
                        continue
                    }
                    val result = Mat()
                    Imgproc.matchTemplate(target, tpl, result, Imgproc.TM_CCOEFF_NORMED)
                    val mmr = Core.minMaxLoc(result)
                    result.release()

                    if (mmr.maxVal >= threshold) {
                        // 命中坐标补回 region 偏移，得到参考系全图坐标
                        val cx = (mmr.maxLoc.x + tpl.cols() / 2.0 +
                            (region?.left?.toDouble() ?: 0.0)).toInt()
                        val cy = (mmr.maxLoc.y + tpl.rows() / 2.0 +
                            (region?.top?.toDouble() ?: 0.0)).toInt()
                        return@withLock MatchResult(true, cx, cy, mmr.maxVal.toFloat())
                    }
                }
                MatchResult.NOT_FOUND
            } finally {
                sceneMat.release()
            }
        }
    }

    /** 预热：在 App 启动或任务开始前批量加载模板到缓存 */
    suspend fun preload(paths: List<String>) = withContext(Dispatchers.Default) {
        OpenCVInitializer.ensureInitialized()
        matchMutex.withLock {
            paths.forEach { loadTemplate(it) }
        }
    }

    private fun loadTemplate(path: String): Mat? {
        templateCache[path]?.let { return it }
        return try {
            val bmp = context.assets.open(path).use { decodePngStream(it) }
            val mat = bitmapToGray(bmp)
            bmp.recycle()
            templateCache[path] = mat
            mat
        } catch (e: IOException) {
            Timber.w("template not found: $path (${e.message})")
            null
        }
    }

    private fun bitmapToGray(bmp: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(bmp, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gray
    }

    private fun clipRegion(src: Mat, region: Rect?): Mat {
        if (region == null) return src
        val rect = CvRect(
            region.left.coerceIn(0, src.cols() - 1),
            region.top.coerceIn(0, src.rows() - 1),
            region.width().coerceAtMost(src.cols() - region.left),
            region.height().coerceAtMost(src.rows() - region.top),
        )
        return Mat(src, rect)
    }

    private fun decodePngStream(stream: java.io.InputStream): Bitmap {
        val bytes = stream.readBytes()
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("decode png failed")
    }
}
