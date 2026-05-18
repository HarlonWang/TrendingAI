package whl.trending.ai.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SubscribeResponse(
    val success: Boolean = false,
    val status: String? = null,
    val message: String? = null,
    val error: String? = null
)

enum class SubscribeStatus {
    NEW,
    ALREADY,
    RESUBSCRIBED,
    CANCELLED,
    NOT_SUBSCRIBED,
    UNKNOWN;

    companion object {
        fun from(value: String?): SubscribeStatus = when (value) {
            "new" -> NEW
            "already" -> ALREADY
            "resubscribed" -> RESUBSCRIBED
            "cancelled" -> CANCELLED
            "not_subscribed" -> NOT_SUBSCRIBED
            else -> UNKNOWN
        }
    }
}
