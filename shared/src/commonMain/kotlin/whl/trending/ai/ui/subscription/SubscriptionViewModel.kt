package whl.trending.ai.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import whl.trending.ai.core.ProCheckout
import whl.trending.ai.core.analytics.AppEvent
import whl.trending.ai.core.analytics.CheckoutStepKind
import whl.trending.ai.core.analytics.track
import whl.trending.ai.core.platform.getSystemLanguage
import whl.trending.ai.data.local.globalSettingsManager
import whl.trending.ai.data.model.PricesResponse
import whl.trending.ai.data.model.ProBenefitRow
import whl.trending.ai.data.repository.BillingRepository

sealed interface SubscriptionEvent {
    /** 下单失败（创建交易没成功），UI 提示重试。已开出收银台的失败不在此列。 */
    data object CheckoutFailed : SubscriptionEvent
}

/** 权益表一行，已按 UI 语言选好文案 */
data class BenefitRowText(val label: String, val free: String, val pro: String)

/**
 * @param prices 服务端算好的两档价格；[PricesResponse.available] 为 false 时整页不报价，
 *   把定价交给收银台呈现——只报一半或报错的价格比不报更伤信任。
 * @param benefitRows 权益对比行，读冷启动拉取的 app-config 缓存（本页不发请求）；空表示从未拉到，UI 显示一句兜底。
 */
data class SubscriptionUiState(
    val loading: Boolean = true,
    val prices: PricesResponse? = null,
    val benefitRows: List<BenefitRowText> = emptyList(),
    val selectedPlan: String = ProCheckout.PLAN_ANNUAL,
    val checkingOut: Boolean = false,
)

/**
 * 订阅页状态。
 *
 * 默认选中年付是**唯一**体现「主推年付」的地方（定价拍板：双档并列、月付不得折叠，
 * 协议层无默认值）。用户改选月付后照常下单，不做任何挽留。
 */
class SubscriptionViewModel(
    private val repository: BillingRepository = BillingRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private val _events = Channel<SubscriptionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val prices = repository.fetchPrices()
            val rows = resolveBenefitRows(globalSettingsManager.proBenefitRows(), uiLanguage())
            _uiState.update {
                it.copy(loading = false, prices = prices, benefitRows = rows)
            }
        }
    }

    fun selectPlan(plan: String) {
        if (_uiState.value.selectedPlan == plan) return
        track(AppEvent.CheckoutStep(CheckoutStepKind.PLAN_SELECTED, plan = plan))
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    /**
     * 下单：创建交易 → 外跳收银台。权益以 webhook 为准，回前台由
     * [ProCheckout.reconcile] 对账，本页不等待、不轮询。
     */
    fun startCheckout() {
        if (_uiState.value.checkingOut) return
        val plan = _uiState.value.selectedPlan
        viewModelScope.launch {
            _uiState.update { it.copy(checkingOut = true) }
            val checkout = repository.createCheckout(plan)
            _uiState.update { it.copy(checkingOut = false) }
            if (checkout == null) {
                _events.send(SubscriptionEvent.CheckoutFailed)
                return@launch
            }
            ProCheckout.openCheckout(checkout.url, plan)
        }
    }
}

private fun uiLanguage(): String {
    val lang = globalSettingsManager.currentAppLanguage().isoCode ?: getSystemLanguage()
    return if (lang.startsWith("zh")) "zh" else "en"
}

/** 按语言取每格文案；任一格取不到的行整行跳过，宁可少一行也不显示半行 */
internal fun resolveBenefitRows(rows: List<ProBenefitRow>, lang: String): List<BenefitRowText> =
    rows.mapNotNull { row ->
        val label = row.label.forLang(lang) ?: return@mapNotNull null
        val free = row.free.forLang(lang) ?: return@mapNotNull null
        val pro = row.pro.forLang(lang) ?: return@mapNotNull null
        BenefitRowText(label, free, pro)
    }
