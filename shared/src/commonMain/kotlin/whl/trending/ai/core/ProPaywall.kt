package whl.trending.ai.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.UpsellTarget
import whl.trending.ai.core.analytics.track

/**
 * 订阅页统一入口。所有 Pro 触点（账户页、chat 库的锁定弹窗与触顶卡、设置页语言引导）都经
 * [open] 进订阅页：一处上报 upsell_clicked，导航由根部 App 收集 [requests] 完成——
 * chat 库没有导航栈，只能靠总线。
 */
object ProPaywall {

    /** upsell_clicked(target=pro) 的 source 词汇，宿主侧入口在此登记；chat 库的在 chat host 契约里。 */
    const val SOURCE_PROFILE_PLAN_CARD = "profile_plan_card"
    const val SOURCE_SETTINGS_LANGUAGE = "settings_language"

    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun open(source: String) {
        track(AppEvent.UpsellClicked(source = source, target = UpsellTarget.PRO))
        _requests.tryEmit(Unit)
    }
}
