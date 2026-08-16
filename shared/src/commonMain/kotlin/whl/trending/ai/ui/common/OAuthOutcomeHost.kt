package whl.trending.ai.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import trendingai.shared.generated.resources.Res
import trendingai.shared.generated.resources.account_link_already_linked
import trendingai.shared.generated.resources.account_link_failed
import trendingai.shared.generated.resources.account_link_github
import trendingai.shared.generated.resources.account_link_github_in_use
import trendingai.shared.generated.resources.sponsor_link_needed_later
import wang.harlon.loginbase.OAuthOutcome
import whl.trending.ai.auth.GithubAuthResult
import whl.trending.ai.auth.LoginSheetBus
import whl.trending.ai.auth.LoginbaseAuthManager
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.AccountLink
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.repository.UserRepository

/**
 * OAuth 回跳结果的**唯一常驻消费者**。挂在 App 根部（与 [SignInHintHost] 平级），
 * 因为用户从浏览器回来时停在哪一页无法预期。
 *
 * **为什么必须常驻**：`client.oauthResults` 是 `replay = 1` 的 SharedFlow，结果要有人
 * 调 `consumeOauthResult()` 才清。授权要跳出 App，期间进程随时可能被回收——回跳时是
 * 冷启动（库把 URL 停泊起来、由 `restore()` 排空），那时登录面板早已不存在
 * （`LoginSheetBus` 是内存态），若由面板消费就没有消费者了。后果有二：
 * - 登录成功却不上报 `sign_in_success`，漏斗里与「停在授权页直接杀进程」的真实流失混同；
 * - 那条结果一直卡在 replay 里（登出也不清），用户下次打开登录面板的瞬间就会收到它——
 *   旧的成功会让面板刚弹出就自己关掉，旧的失败会让面板一开就顶着红字报错，
 *   两者还各自多上报一次假埋点。
 *
 * 面板因此不再订阅 `oauthResults`，只读 [LoginSheetBus.githubResult]。
 *
 * 绑定成功后要做三件事，**顺序不能反**：
 * 1. **fresh 刷身份**——绕开服务端 claims 缓存拿到新的 `github_user_id`；
 * 2. 通知界面重载（[AccountLink.markLinked]）；
 * 3. **补一次 Pro 对账**——用户很可能是「先赞助、后关联」，权益早发好了只是匹配不上，
 *    不补对账他还得自己再点一次升级（这正是 SponsorLinkHost 那条引导的终点）。
 */
@Composable
fun OAuthOutcomeHost() {
    val repo = remember { UserRepository() }
    var errorText by remember { mutableStateOf<String?>(null) }

    val client = (globalAuthManager as? LoginbaseAuthManager)?.client

    LaunchedEffect(client) {
        client?.oauthResults?.collect { outcome ->
            // 无论哪一种结果都在这里消费掉：漏一种，它就会滞留到下一次面板打开
            client.consumeOauthResult()
            when (outcome) {
                is OAuthOutcome.SignedIn -> {
                    // source 取当前登录请求；冷启动路径上面板已不在、取不到，
                    // 记成 cold_start 而不是不报——漏斗少一个终态比来源不精确更难查
                    trackEvent(
                        "sign_in_success",
                        mapOf(
                            "source" to (LoginSheetBus.request.value ?: "cold_start"),
                            "method" to "github",
                            "is_new" to (outcome.session.isNewUser == true),
                        ),
                    )
                    // 面板还开着就关掉；已经不在了这步是空操作
                    LoginSheetBus.clear()
                }

                is OAuthOutcome.Linked -> {
                    AccountLink.consumePending() // 本流程收尾，标记别留给下一次登录失败
                    // markLinked 带埋点和界面通知，必须留在重试块**外**：authorized 撞 401
                    // 会把整个 block 重跑一遍，放块内就会重复上报 account_link_success
                    var synced = false
                    globalAuthManager.authorized { token ->
                        repo.syncMe(token, fresh = true)
                        synced = true
                    }
                    if (synced) {
                        // 身份变了但登录态没变，authState 不会发射，只能靠 markLinked 通知界面
                        AccountLink.markLinked()
                        globalAuthManager.authorized { token -> repo.refreshPro(token) }
                    }
                }

                // 协议里登录失败与绑定失败都回跳 `?error=`、形状相同，
                // 靠这个落盘标记区分是哪条流程发起的
                is OAuthOutcome.Failed -> if (AccountLink.consumePending()) {
                    trackEvent("account_link_error", mapOf("reason" to outcome.reason))
                    errorText = when (outcome.reason) {
                        // 后端 onLinked 的两种冲突（见 github-ai-trending-api 的 app-users.js）：
                        // 一律拒绝、绝不改绑——改绑会让 Pro 权益随 GitHub ID 漂移到别人账上
                        "github_in_use" -> getString(Res.string.account_link_github_in_use)
                        "already_linked" -> getString(Res.string.account_link_already_linked)
                        else -> getString(Res.string.account_link_failed)
                    }
                } else {
                    trackEvent(
                        "sign_in_error",
                        mapOf("source" to (LoginSheetBus.request.value ?: "cold_start"), "method" to "github"),
                    )
                    LoginSheetBus.reportGithubResult(GithubAuthResult.FAILED)
                }

                OAuthOutcome.Cancelled -> {
                    // 用户放弃：清掉落盘的绑定标记，防它把下一次登录失败错认成绑定失败
                    AccountLink.consumePending()
                    LoginSheetBus.reportGithubResult(GithubAuthResult.CANCELED)
                }

                // 配置类异常输入（详见库文档），开发期问题，不打扰用户
                is OAuthOutcome.Unrecognized -> Unit
            }
        }
    }

    // 发起阶段就失败（未初始化 / 无宿主 Activity）：浏览器没开起来，到不了 oauthResults，
    // 但对用户来说同样是「点了关联却没成」，用同一个提示收口
    val launchFailedText = stringResource(Res.string.account_link_failed)
    LaunchedEffect(Unit) {
        AccountLink.launchFailed.collect { errorText = launchFailedText }
    }

    errorText?.let { text ->
        AlertDialog(
            onDismissRequest = { errorText = null },
            title = { Text(stringResource(Res.string.account_link_github)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { errorText = null }) {
                    Text(stringResource(Res.string.sponsor_link_needed_later))
                }
            },
        )
    }
}
