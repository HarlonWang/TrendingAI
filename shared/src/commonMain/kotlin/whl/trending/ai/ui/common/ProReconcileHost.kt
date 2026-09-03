package whl.trending.ai.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import whl.trending.ai.auth.AuthState
import whl.trending.ai.auth.globalAuthManager
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.ProSponsor
import whl.trending.ai.core.ReconcileAction
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.CheckoutStepKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.repository.UserRepository

/**
 * 回前台对账 Pro：赞助（[ProSponsor]）与 Paddle 购买（[ProCheckout]）两个窗口各查各的。
 * 只在「已登录、非 Pro、且处于对账窗口内」才查，窗口外零请求——pro-refresh 每次都消耗
 * 服务端的 GitHub PAT 配额，不能拿 resume 当轮询点。用户付完款从浏览器回来时停在哪一页
 * 无法预期，所以挂在 App 根部而不是订阅页。
 */
@Composable
fun ProReconcileHost() {
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        if (globalAuthManager.authState.value is AuthState.LoggedIn) {
            if (!globalSettingsManager.currentIsPro() && ProSponsor.shouldReconcile()) {
                scope.launch { reconcileSponsor() }
            }
            if (!globalSettingsManager.currentIsPro() && ProCheckout.shouldReconcile()) {
                scope.launch { reconcileCheckout() }
            }
        }
        onPauseOrDispose { }
    }
}

private suspend fun reconcileSponsor() {
    val result = UserRepository().refreshPro()
    when (ProSponsor.reconcileAction(result)) {
        ReconcileAction.MARK_PRO -> ProSponsor.markReconciled()

        // 钱付了但没关联 GitHub：一并结束对账窗口，补对账交给关联流程——
        // 不结束的话每次回前台都会再弹，比不提示还烦。
        ReconcileAction.GUIDE_LINK -> {
            ProSponsor.markReconciled()
            ProSponsor.signalNeedsGithubLink()
        }

        // 窗口留着，下次回前台再试
        ReconcileAction.STAY_SILENT -> Unit
    }
}

// 走 /api/me（纯 D1 查询），不烧 pro/refresh 的 GitHub PAT 配额，因此可以重试多轮——
// 权益要等 webhook 落库，用户完全可能比 webhook 先回到 App，查一次就放弃会把「已付款」显示成免费档。
private suspend fun reconcileCheckout() {
    val repository = UserRepository()
    val attempt = ProCheckout.reconcile {
        // syncMe 会把 isPro 落到本地设置，UI 各处（模型选择器、配额卡）随之解锁
        repository.syncMe() != null && globalSettingsManager.currentIsPro()
    }
    // attempt 区分「秒到」与「等满 26 秒」，是判断 webhook 时延是否需要调窗口的依据
    if (attempt != null) {
        ProCheckout.markActivated()
        track(AppEvent.CheckoutStep(CheckoutStepKind.RECONCILED, attempt = attempt))
    }
}
