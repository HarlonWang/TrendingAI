package whl.trending.ai.core

object Constants {
    const val OFFICIAL_WEBSITE_URL = "https://trendingai.cn/app"
    const val FEEDBACK_URL = "https://github.com/HarlonWang/TrendingAI/issues"
    const val RELEASES_URL = "https://github.com/HarlonWang/TrendingAI/releases/latest"
    const val CHANGELOG_URL = "https://github.com/HarlonWang/TrendingAI/releases"
    const val PRIVACY_POLICY_URL = "https://trendingai.cn/privacy/"
    const val GITHUB_SPONSORS_URL = "https://github.com/sponsors/HarlonWang"
    const val ALIPAY_ACCOUNT = "15865268560@163.com"
    const val AUTHOR_EMAIL = "81813780@qq.com"

    /**
     * Logto 账户中心的 GitHub 连接器 ID（后台「连接器 → GitHub」页的 ID，随租户固定）。
     * 用于拼「关联社交身份」的预构建页地址，见 [accountLinkGithubUrl]。
     */
    private const val LOGTO_GITHUB_CONNECTOR_ID = "0xb17od4fhlnc4z1wmo8m"

    /**
     * Logto 预构建的「关联 GitHub」单任务流程页。Logto 自己完成身份验证（邮箱验证码）
     * 与 GitHub 授权，我们只需在外部浏览器打开它。
     *
     * 刻意不传 `redirect=`：app 的 `cn.trendingai://` scheme 由 Logto SDK 的授权 Activity
     * 独占，回跳会被当成 OAuth 登录回调解析。改用 `show_success=true` 让成功页停留，
     * 用户手动返回 app，由 ON_RESUME 触发身份刷新（与赞助页回来对账同一套机制）。
     *
     * `ui_locales` 必传——但只有账户中心需要。Logto 后台「登录与账户 → 内容 → 语言」
     * 的自动检测只覆盖登录体验（所以 SDK 的 signIn 从来不用传语言参数，登录页自动是
     * 中文），账户中心的 account 系列页面不走那条路径，不传就按默认语言 English 渲染。
     */
    fun accountLinkGithubUrl(endpoint: String, uiLocale: String): String =
        "$endpoint/account/social/$LOGTO_GITHUB_CONNECTOR_ID?show_success=true&ui_locales=$uiLocale"
}
