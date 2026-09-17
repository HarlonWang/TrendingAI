package whl.trending.ai.tinyui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import trendingai.shared.generated.resources.Res
import wang.harlon.tinyui.BuildManifest
import wang.harlon.tinyui.PageError
import wang.harlon.tinyui.PageModule
import wang.harlon.tinyui.PageSink
import wang.harlon.tinyui.RuntimeBundle
import wang.harlon.tinyui.SourceMaps
import wang.harlon.tinyui.components.registerBuiltins
import wang.harlon.tinyui.schema.ComponentRegistry
import whl.trending.ai.tinyui.generated.HostSchemas

/**
 * App-level side of the TinyUI pages: the component registry, the bytecode shipped under
 * `composeResources/files/tinyui/` (written by `pnpm sync` in ../trendingai-tinyui), and where page errors go.
 */
object TinyUIHost {
    /** Built-ins plus the `ta.*` components whose schemas ../trendingai-tinyui generates into [HostSchemas]. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val registry: ComponentRegistry = ComponentRegistry().registerBuiltins().apply {
        register(HostSchemas.TaIcon) { scope ->
            Icon(
                benefitIcon(scope["name"]),
                contentDescription = null,
                tint = scope.color("tint") ?: LocalContentColor.current,
                modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp),
            )
        }
        register(HostSchemas.TaLoading) { scope ->
            LoadingIndicator(
                modifier = scope.modifier().size(scope.get<Dp>("size") ?: 24.dp),
                color = scope.color("color") ?: LoadingIndicatorDefaults.indicatorColor,
            )
        }
    }

    val sink: PageSink = object : PageSink {
        override fun error(error: PageError) = println("TinyUI $error")
        override fun log(line: String) = println("TinyUI $line")
    }

    class Page(val runtime: RuntimeBundle, val module: PageModule, val maps: SourceMaps)

    private val lock = Mutex()
    private var manifest: BuildManifest? = null
    private var runtime: RuntimeBundle? = null
    private val maps = mutableMapOf<String, String>()

    /** Loads `pages/<name>` with the runtime it was built against; the runtime is read once per process. */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun page(name: String): Page = lock.withLock {
        val manifest = manifest ?: BuildManifest.parse(read("manifest.json").decodeToString()).also { manifest = it }
        val runtime = runtime ?: RuntimeBundle(core = read("runtime/core.bin"), native = read("runtime/native.bin")).also {
            runtime = it
            for (module in manifest.runtime) map("runtime/" + module.removePrefix("@tiny-ui/"))?.let { maps[module] = it }
        }
        map(name)?.let { maps[name] = it }
        Page(runtime, PageModule(name, read("$name.bin"), manifest.buildId(name)), SourceMaps(maps.toMap()))
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun read(file: String): ByteArray = Res.readBytes("files/tinyui/$file")

    private suspend fun map(file: String): String? = runCatching { read("$file.js.map").decodeToString() }.getOrNull()
}

/** key 与后端 lib/pro-paywall.js 对应；认不出的 key 用通用勾选，新行不必等客户端发版 */
private fun benefitIcon(key: String?): ImageVector = when (key) {
    "quota" -> Icons.Outlined.Bolt
    "models" -> Icons.Outlined.AutoAwesome
    "voice" -> Icons.Outlined.Mic
    "image_generation" -> Icons.Outlined.Brush
    else -> Icons.Outlined.CheckCircle
}
