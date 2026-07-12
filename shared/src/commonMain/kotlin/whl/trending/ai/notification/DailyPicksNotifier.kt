package whl.trending.ai.notification

/**
 * 每日 Picks 本地通知的平台能力接口（仿 [whl.trending.ai.update.UpdateChecker] 的接口反转）：
 * shared 只感知接口，Android 实现在 androidLibrary/notifier（WorkManager 本地定时，
 * 不依赖 GMS/FCM，F-Droid 等无 Play 服务设备同样可用）；iOS 保持 NoOp，设置页隐藏开关。
 */
interface DailyPicksNotifier {
    /** 平台是否支持本地每日通知；false（iOS / 未注入）时设置页不显示开关 */
    val isSupported: Boolean

    /**
     * 开启每日通知：负责申请通知权限（Android 13+）并调度每日任务。
     * @return 是否真正开启；权限被拒时返回 false，调用方不应把开关落为 true。
     */
    suspend fun enable(): Boolean

    /** 关闭每日通知：取消已调度的任务。 */
    fun disable()
}

object NoOpDailyPicksNotifier : DailyPicksNotifier {
    override val isSupported = false
    override suspend fun enable() = false
    override fun disable() {}
}

var globalDailyPicksNotifier: DailyPicksNotifier = NoOpDailyPicksNotifier
