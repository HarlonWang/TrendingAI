package whl.trending.chat.attach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import whl.trending.ai.data.local.globalSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聊天图片的采集与预处理。
 *
 * [ingest]：任意来源 Uri（相册 Photo Picker / 拍照 FileProvider）→ 采样解码（防大图 OOM）
 * → 按 EXIF Orientation 旋正像素 → 长边缩至 ≤[MAX_EDGE] → JPEG 重编码写入私有缓存。
 * 重编码产物不含任何 EXIF（GPS/时间/设备随之剥除）；Orientation 是唯一先消费再丢弃的字段。
 *
 * 单张产物必须不超预算（app-config 下发，服务端 KV 单源；未拉到用与服务端一致的默认）：
 * 质量沿 [QUALITY_LADDER] 递降直到达标。预算 = 服务端图片总闸摊到单条消息张数上限——
 * 文字密集的书页/截图在固定 q80 下可达 400KB/张，攒满一条消息会撞闸。
 */
object ChatImages {

    private const val TAG = "ChatImages"
    private const val MAX_EDGE = 1568
    private val QUALITY_LADDER = intArrayOf(80, 65, 50)
    private const val DIR = "chat_images"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    /** 与宿主 FileProvider 区隔的专属 authority 后缀（见模块 manifest） */
    private const val AUTHORITY_SUFFIX = ".chat.fileprovider"

    private var cleanedUp = false

    /** 压缩 + EXIF 归一化后写入私有缓存，返回文件路径；失败返回 null。 */
    suspend fun ingest(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        cleanupOld(context)
        runCatching {
            val resolver = context.contentResolver

            // 注意：inJustDecodeBounds 模式下 decodeStream 恒返回 null（只回填 bounds），
            // 不能用它的返回值判空，须以 bounds 尺寸判断解码是否成功
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: return@runCatching null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_EDGE) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null

            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

            val upright = applyOrientation(decoded, orientation)
            val scaled = scaleDown(upright)

            // 阶梯走完仍超预算时用最低档结果兜底（1568px q50 实际到不了默认 280KB，防御性）
            val budgetBytes = globalSettingsManager.chatImagesPerImageJpegKb() * 1024
            var bytes: ByteArray? = null
            for (quality in QUALITY_LADDER) {
                val buf = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, buf)
                bytes = buf.toByteArray()
                if (bytes.size <= budgetBytes) break
            }
            val out = File(imageDir(context), "${UUID.randomUUID()}.jpg")
            out.writeBytes(bytes!!)
            if (scaled !== upright) upright.recycle()
            scaled.recycle()
            out.absolutePath
        }
            .onFailure { Log.w(TAG, "ingest failed: uri=$uri", it) }
            .getOrNull()
            .also { if (it == null) Log.w(TAG, "ingest returned null: uri=$uri") }
    }

    /** 为系统相机 TakePicture 生成输出目标：私有缓存文件 + 专属 FileProvider Uri。 */
    fun newCaptureTarget(context: Context): Pair<Uri, File> {
        val file = File(imageDir(context), "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        return uri to file
    }

    private fun imageDir(context: Context): File =
        File(context.cacheDir, DIR).apply { mkdirs() }

    /** 清理超过 24h 的缓存图（会话是内存级的，进程重启后旧图必然不再被引用）。每进程执行一次。 */
    private fun cleanupOld(context: Context) {
        if (cleanedUp) return
        cleanedUp = true
        runCatching {
            val cutoff = System.currentTimeMillis() - MAX_AGE_MS
            imageDir(context).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / edge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
