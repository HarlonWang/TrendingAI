package whl.trending.chat.attach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// TODO(attach-step5)：接 PHPickerViewController（相册）与 UIImagePickerController（拍照），
// 压缩/旋正走 UIImage + ImageIO，对齐 Android 侧 ChatImages 的产物规格。
// 当前占位仅保证编译，chat UI 在 iOS 尚不可达。
@Composable
actual fun rememberChatImagePicker(
    maxImages: Int,
    remaining: () -> Int,
    onProcessingChange: (Int) -> Unit,
    onImageReady: (String) -> Unit,
): ChatImagePicker = remember {
    object : ChatImagePicker {
        override val canCapture = false
        override fun pickFromAlbum() {}
        override fun capture() {}
    }
}
