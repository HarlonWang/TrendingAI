package whl.trending.chat.markdown

// TODO(markdown-step4): cinterop 绑定 apple/swift-cmark 后替换为真实解析。
// 当前占位实现只保证 iOS 目标可编译——chat UI 在 iOS 尚不可达，
// commonTest 方言套件在 iOS 侧以 @IgnoreIos 跳过，接入真解析时一并摘除。
actual fun parseMarkdown(markdown: String): MdDocument =
    MdDocument(listOf(MdBlock.Paragraph(listOf(MdInline.Text(markdown)))))
