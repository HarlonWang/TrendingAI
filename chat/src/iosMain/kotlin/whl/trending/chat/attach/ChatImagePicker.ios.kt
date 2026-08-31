package whl.trending.chat.attach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIGraphicsImageRenderer
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.darwin.NSObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import whl.trending.chat.core.logWarn
import whl.trending.chat.ui.topViewController

/**
 * iOS 实现：相册走 PHPicker（零权限弹窗），拍照走 UIImagePickerController.camera。
 * 产物规格对齐 Android 侧 ChatImages：重绘归一方向、长边 ≤1568、JPEG 质量阶梯压到
 * 预算内、重编码天然剥净 EXIF，写进 Caches/chat_images。
 */
@Composable
actual fun rememberChatImagePicker(
    maxImages: Int,
    remaining: () -> Int,
    onProcessingChange: (Int) -> Unit,
    onImageReady: (String) -> Unit,
): ChatImagePicker {
    val scope = rememberCoroutineScope()
    val currentRemaining by rememberUpdatedState(remaining)
    val currentProcessing by rememberUpdatedState(onProcessingChange)
    val currentReady by rememberUpdatedState(onImageReady)

    return remember {
        IosChatImagePicker(
            scope = scope,
            maxImages = maxImages,
            remaining = { currentRemaining() },
            onProcessingChange = { currentProcessing(it) },
            onImageReady = { currentReady(it) },
        )
    }
}

private class IosChatImagePicker(
    private val scope: CoroutineScope,
    private val maxImages: Int,
    private val remaining: () -> Int,
    private val onProcessingChange: (Int) -> Unit,
    private val onImageReady: (String) -> Unit,
) : ChatImagePicker {

    // delegate 必须强持有：present 后无人引用会被立刻回收，回调静默丢失
    private var pickerDelegate: PickerDelegate? = null
    private var cameraDelegate: CameraDelegate? = null

    override val canCapture: Boolean
        get() = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )

    override fun pickFromAlbum() {
        val config = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = remaining().coerceAtLeast(0).toLong()
            selection = PHPickerConfigurationSelectionOrdered
        }
        val delegate = PickerDelegate { results ->
            pickerDelegate = null
            results.take(remaining().coerceAtLeast(0)).forEach { ingestProvider(it.itemProvider) }
        }
        pickerDelegate = delegate
        val controller = PHPickerViewController(configuration = config)
        controller.delegate = delegate
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }

    override fun capture() {
        val delegate = CameraDelegate { image ->
            cameraDelegate = null
            image?.let { ingestImage(it) }
        }
        cameraDelegate = delegate
        val controller = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModePhoto
            this.delegate = delegate
        }
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }

    private fun ingestProvider(provider: NSItemProvider) {
        if (!provider.hasItemConformingToTypeIdentifier(IMAGE_TYPE)) return
        onProcessingChange(+1)
        provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, error ->
            scope.launch {
                val image = data?.let { UIImage(data = it) }
                if (image == null) {
                    logWarn(TAG, "album load failed: $error")
                    onProcessingChange(-1)
                } else {
                    finishIngest(image)
                }
            }
        }
    }

    private fun ingestImage(image: UIImage) {
        onProcessingChange(+1)
        scope.launch { finishIngest(image) }
    }

    private suspend fun finishIngest(image: UIImage) {
        val path = withContext(Dispatchers.IO) { compressToStore(image) }
        onProcessingChange(-1)
        if (path != null) onImageReady(path) else logWarn(TAG, "compress failed")
    }

    private companion object {
        const val TAG = "ChatImagePicker"
        const val IMAGE_TYPE = "public.image"
    }
}

private const val MAX_EDGE = 1568.0

@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
private fun compressToStore(image: UIImage): String? {
    // UIGraphicsImageRenderer 重绘：方向归一进像素、缩到长边 ≤1568，重编码即剥 EXIF
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, MAX_EDGE / maxOf(width, height))
    val targetW = (width * scale).coerceAtLeast(1.0)
    val targetH = (height * scale).coerceAtLeast(1.0)
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetW, targetH))
    val normalized = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(0.0, 0.0, targetW, targetH))
    }

    val budgetBytes = whl.trending.chat.host.chatHost.imagesPerImageJpegKb() * 1024
    var data: platform.Foundation.NSData? = null
    for (quality in doubleArrayOf(0.8, 0.65, 0.5)) {
        data = UIImageJPEGRepresentation(normalized, quality) ?: continue
        if (data.length.toLong() <= budgetBytes) break
    }
    val bytes = data ?: return null

    val cachesDir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    val dir = "$cachesDir/chat_images"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    val path = "$dir/${Uuid.random()}.jpg"
    return if (bytes.writeToFile(path, atomically = true)) path else null
}

private class PickerDelegate(
    private val onPicked: (List<PHPickerResult>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onPicked(didFinishPicking.filterIsInstance<PHPickerResult>())
    }
}

private class CameraDelegate(
    private val onCaptured: (UIImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onCaptured(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onCaptured(null)
    }
}
