package whl.trending.chat.attach

import androidx.compose.runtime.Composable

/**
 * 附件采集的平台缝：相册多选与拍照。产出为**已压缩、剥净 EXIF** 的本地 JPEG 路径
 * （预处理规格见 Android 侧 ChatImages），失败反馈由各平台以自己的惯用形态就地给出。
 */
interface ChatImagePicker {
    /** 平台是否有拍照入口；false 时 UI 不渲染相机项。 */
    val canCapture: Boolean

    fun pickFromAlbum()

    fun capture()
}

/**
 * @param maxImages 单条消息附图上限（选择器的多选上限）
 * @param remaining 发起时刻的剩余名额（随缩略图条与处理中数量实时变化，故为函数）
 * @param onProcessingChange 处理中数量增减（+1 进入压缩，-1 完成或失败），驱动占位 loading
 * @param onImageReady 单张产物就绪（路径指向进程私有缓存）
 */
@Composable
expect fun rememberChatImagePicker(
    maxImages: Int,
    remaining: () -> Int,
    onProcessingChange: (Int) -> Unit,
    onImageReady: (String) -> Unit,
): ChatImagePicker
