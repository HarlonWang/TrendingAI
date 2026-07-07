package whl.trending.ai.core

/** 与 Worker 端 subscribe.js 的 isValidEmail 同口径；此前四处入口各存一份正则，改档需四处齐改。 */
private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** 邮箱格式校验（先 trim 再匹配）。反馈 / 订阅 / waitlist / 语言采集共用。 */
fun isValidEmail(value: String): Boolean = EMAIL_REGEX.matches(value.trim())
