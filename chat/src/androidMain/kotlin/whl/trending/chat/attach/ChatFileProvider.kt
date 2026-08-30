package whl.trending.chat.attach

import androidx.core.content.FileProvider
import whl.trending.chat.R

/**
 * 聊天模块专属 FileProvider 子类。宿主可能还打包了其他注册
 * `androidx.core.content.FileProvider` 的库（如 kmp-webview），manifest merger 按
 * android:name 合并 provider，同类名必然冲突——子类化让类名唯一。
 *
 * 路径配置仍必须在 manifest 里以 meta-data 声明：静态 [FileProvider.getUriForFile]
 * 只解析 meta-data，构造器资源只对 provider 实例方法生效，二者需指向同一份
 * `chat_file_paths.xml`。
 */
internal class ChatFileProvider : FileProvider(R.xml.chat_file_paths)
