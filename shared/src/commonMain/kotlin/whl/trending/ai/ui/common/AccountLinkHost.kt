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
import whl.trending.ai.auth.OauthCallback
import whl.trending.ai.auth.OauthCallbackBus
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.AccountLink
import whl.trending.ai.core.platform.trackEvent
import whl.trending.ai.data.repository.UserRepository

/**
 * 绑定 GitHub 身份的回跳宿主。挂在 App 根部（与 [SignInHintHost] 平级），
 * 因为用户从浏览器回来时停在哪一页无法预期。
 *
 * 成功后要做三件事，**顺序不能反**：
 * 1. **fresh 刷身份**——绕开服务端 claims 缓存拿到新的 `github_user_id`；
 * 2. 通知界面重载（[AccountLink.markLinked]）；
 * 3. **补一次 Pro 对账**——用户很可能是「先赞助、后关联」，权益早发好了只是匹配不上，
 *    不补对账他还得自己再点一次升级（这正是 SponsorLinkHost 那条引导的终点）。
 *
 * 失败分派：协议里登录失败与绑定失败都回跳 `?error=`、形状相同，
 * 靠 [AccountLink.consumePending] 区分是不是本流程发起的。
 */
@Composable
fun AccountLinkHost() {
    val repo = remember { UserRepository() }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        OauthCallbackBus.events.collect { cb ->
            when (cb) {
                OauthCallback.Linked -> {
                    OauthCallbackBus.consume()
                    // 身份变了但登录态没变，authState 不会发射，只能靠 markLinked 通知界面
                    globalAuthManager.authorized { token ->
                        repo.syncMe(token, fresh = true)
                        AccountLink.markLinked()
                        repo.refreshPro(token)
                    }
                }

                is OauthCallback.Failed -> {
                    // 只处理由绑定入口发起的失败；登录失败归登录面板
                    if (!AccountLink.consumePending()) return@collect
                    OauthCallbackBus.consume()
                    trackEvent("account_link_error", mapOf("reason" to cb.error))
                    errorText = when (cb.error) {
                        // 后端 onLinked 的两种冲突（见 github-ai-trending-api 的 app-users.js）：
                        // 一律拒绝、绝不改绑——改绑会让 Pro 权益随 GitHub ID 漂移到别人账上
                        "github_in_use" -> getString(Res.string.account_link_github_in_use)
                        "already_linked" -> getString(Res.string.account_link_already_linked)
                        else -> getString(Res.string.account_link_failed)
                    }
                }

                // 登录回跳不归这里管
                is OauthCallback.SignedIn -> Unit
            }
        }
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
