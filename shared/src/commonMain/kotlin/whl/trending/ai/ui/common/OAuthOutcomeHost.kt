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
import wang.harlon.eventbase.Eventbase
import wang.harlon.loginbase.OAuthOutcome
import whl.trending.ai.auth.GithubAuthResult
import whl.trending.ai.auth.LoginSheetBus
import whl.trending.ai.auth.LoginbaseAuthManager
import whl.trending.ai.auth.OAuthResultGuard
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.AccountLink
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.AuthAction
import whl.trending.ai.core.analytics.AuthOutcome
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.repository.UserRepository

/**
 * OAuth 回跳结果的**唯一常驻消费者**，挂 App 根部——回跳可能是冷启动，登录面板早已不存在，
 * 若由面板消费 `client.oauthResults`（replay=1）就没有消费者：结果滞留 replay，
 * 下次面板刚弹出就会被旧结果自关/顶红字，还多报假埋点。面板只读 [LoginSheetBus.githubResult]。
 *
 * 绑定成功后三步**顺序不能反**：fresh 刷身份（绕开服务端 claims 缓存拿新 `github_user_id`）
 * → 通知界面重载（[AccountLink.markLinked]）→ 补一次 Pro 对账（用户很可能先赞助后关联）。
 */
@Composable
fun OAuthOutcomeHost() {
    val repo = remember { UserRepository() }
    var errorText by remember { mutableStateOf<String?>(null) }

    val client = (globalAuthManager as? LoginbaseAuthManager)?.client

    LaunchedEffect(client) {
        client?.oauthResults?.collect { outcome ->
            // 回跳重建 Activity 时新旧 composition 短暂并存、同一次投递会被消费两次，
            // 靠 [OAuthResultGuard] 拦掉
            if (!OAuthResultGuard.shouldHandle(outcome)) return@collect
            // 每种结果都要消费掉：漏一种就滞留到下一次面板打开
            client.consumeOauthResult()
            when (outcome) {
                is OAuthOutcome.SignedIn -> {
                    // 冷启动路径面板已不在，source 记成 cold_start 而不是不报——漏斗少终态更难查
                    track(
                        AppEvent.AuthFinished(
                            AuthAction.SIGN_IN,
                            AuthOutcome.SUCCESS,
                            method = "github",
                            source = LoginSheetBus.request.value ?: "cold_start",
                            isNew = outcome.session.isNewUser == true,
                        ),
                        Eventbase.currentFlow(),
                    )
                    LoginSheetBus.clear()
                }

                is OAuthOutcome.Linked -> {
                    val linkSource = AccountLink.consumePendingSource()
                    // markLinked 必须留在重试块**外**：authorized 撞 401 会重跑整个 block，
                    // 放块内会重复上报 auth_finished
                    var synced = false
                    globalAuthManager.authorized { token ->
                        repo.syncMe(token, fresh = true)
                        synced = true
                    }
                    if (synced) {
                        // 身份变了但登录态没变，authState 不会发射，只能靠 markLinked 通知界面
                        AccountLink.markLinked(linkSource)
                        globalAuthManager.authorized { token -> repo.refreshPro(token) }
                    }
                }

                // 登录失败与绑定失败回跳形状相同，靠落盘标记区分是哪条流程发起的
                is OAuthOutcome.Failed -> {
                    val linkSource = AccountLink.consumePendingSource()
                    if (linkSource != null) {
                        track(
                            AppEvent.AuthFinished(
                                AuthAction.LINK,
                                AuthOutcome.ERROR,
                                method = "github",
                                source = linkSource,
                                reason = outcome.reason,
                            ),
                            Eventbase.currentFlow(),
                        )
                        errorText = when (outcome.reason) {
                            // 后端 app-users.js 的两种冲突：一律拒绝、绝不改绑——
                            // 改绑会让 Pro 权益随 GitHub ID 漂移到别人账上
                            "github_in_use" -> getString(Res.string.account_link_github_in_use)
                            "already_linked" -> getString(Res.string.account_link_already_linked)
                            else -> getString(Res.string.account_link_failed)
                        }
                    } else {
                        track(
                            AppEvent.AuthFinished(
                                AuthAction.SIGN_IN,
                                AuthOutcome.ERROR,
                                method = "github",
                                source = LoginSheetBus.request.value ?: "cold_start",
                                reason = outcome.reason,
                            ),
                            Eventbase.currentFlow(),
                        )
                        LoginSheetBus.reportGithubResult(GithubAuthResult.FAILED)
                    }
                }

                OAuthOutcome.Cancelled -> {
                    // 清掉落盘的绑定标记，防它把下一次登录失败错认成绑定失败
                    val linkSource = AccountLink.consumePendingSource()
                    // 取消必须有终态事件，否则「主动放弃」和「流程中途断了」再也分不开
                    track(
                        AppEvent.AuthFinished(
                            if (linkSource != null) AuthAction.LINK else AuthAction.SIGN_IN,
                            AuthOutcome.CANCELED,
                            method = "github",
                            source = linkSource ?: LoginSheetBus.request.value ?: "cold_start",
                        ),
                        Eventbase.currentFlow(),
                    )
                    LoginSheetBus.reportGithubResult(GithubAuthResult.CANCELED)
                }

                // 配置类异常输入（详见库文档），开发期问题，不打扰用户
                is OAuthOutcome.Unrecognized -> Unit
            }
        }
    }

    // 发起阶段的失败到不了 oauthResults（浏览器没开起来），用同一个提示收口
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
