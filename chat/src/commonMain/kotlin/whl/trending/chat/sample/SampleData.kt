package whl.trending.chat.sample

import whl.trending.chat.model.ChatMessage
import whl.trending.chat.model.Role

/** 离线 Demo 用的示例数据：覆盖全部 Markdown 元素，用于重点验收渲染与交互效果。 */
object SampleData {

    /** 一段覆盖标题 / 列表 / 代码块 / 表格 / 引用 / 链接 / 强调的富 Markdown。 */
    val richMarkdown: String = """
        # Kotlin 协程速览

        协程是 Kotlin 的**轻量级并发**方案，核心是 `suspend` 函数与结构化并发。

        ## 关键概念

        1. **挂起函数**：用 `suspend` 标记，可被挂起而不阻塞线程
        2. **作用域**：`CoroutineScope` 管理生命周期
        3. 调度器：`Dispatchers.IO` / `Default` / `Main`

        > 结构化并发保证子协程不会泄漏：父作用域取消时，所有子协程一并取消。

        ## 一个例子

        ```kotlin
        suspend fun loadUser(id: String): User = withContext(Dispatchers.IO) {
            val response = api.fetch(id) // 网络请求
            response.toUser()
        }
        ```

        对比 JavaScript 的 `async/await`：

        ```js
        async function loadUser(id) {
          const res = await api.fetch(id);
          return res.json();
        }
        ```

        ## 调度器对照表

        | 调度器 | 用途 | 线程池 |
        | :--- | :---: | ---: |
        | Main | UI 更新 | 主线程 |
        | IO | 网络 / 磁盘 | 弹性 |
        | Default | CPU 密集 | 核数 |

        更多见 [官方文档](https://kotlinlang.org/docs/coroutines-overview.html)。
    """.trimIndent()

    /** Demo 打开即展示的初始会话。 */
    val messages: List<ChatMessage> = listOf(
        ChatMessage(1, Role.USER, "用一句话介绍 Kotlin 协程，并给个例子"),
        ChatMessage(2, Role.ASSISTANT, richMarkdown),
        ChatMessage(3, Role.USER, "和 JavaScript 的 async/await 有什么区别？"),
        ChatMessage(
            4, Role.ASSISTANT,
            "主要区别在于**结构化并发**：\n\n" +
                "- Kotlin 协程有 `CoroutineScope`，父取消会级联取消子协程\n" +
                "- JS 的 `Promise` 没有内建取消传播\n" +
                "- 协程可切换调度器（`withContext`），JS 单线程事件循环\n\n" +
                "简言之：协程更像*可取消、可调度*的 async/await。",
        ),
        ChatMessage(5, Role.USER, "发几张图看看：独立图、图文混排、还有一张加载失败的"),
        ChatMessage(
            6, Role.ASSISTANT,
            // 头像 CDN 与首页头像同域，模拟器可达；example.com 返回 HTML 触发解码失败
            "独立成段的图片：\n\n" +
                "![Octocat](https://avatars.githubusercontent.com/u/583231?s=600)\n\n" +
                "图文混排：前面有字 ![GitHub 组织头像](https://avatars.githubusercontent.com/u/9919?s=400) 后面也有字。\n\n" +
                "下面这张会加载失败，降级为可点链接：\n\n" +
                "![加载失败的图](https://example.com/broken.png)",
        ),
    )

    /** 末位 id，供 ViewModel 续号。 */
    val lastId: Long = messages.maxOf { it.id }
}
