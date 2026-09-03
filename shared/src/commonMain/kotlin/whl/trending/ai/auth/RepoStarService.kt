package whl.trending.ai.auth

import whl.trending.ai.data.remote.GithubApi

/**
 * GitHub 仓库 star / unstar 的共享业务逻辑：Trending 列表页与 README 详情页复用。
 * 统一处理「取 GitHub token → 鉴权 → 调 API」，把结果归一为 [Result] 交由 UI 映射文案。
 * token 经 [GithubTokenProvider] 从自家后端取回（`app_users.gh_token_enc`，2026-08 起取代
 * Logto Secret Vault），需含 `public_repo` scope 才能写 star。
 */
class RepoStarService(
    private val githubApi: GithubApi = GithubApi(),
    private val tokenProvider: GithubTokenProvider = GithubTokenProvider.shared,
    private val authManager: () -> AuthManager = { globalAuthManager },
) {
    enum class Result { STARRED, UNSTARRED, NEED_LOGIN, NEED_GITHUB_LINK, FAILED }

    /** 查询是否已 star；无 token（未登录/取不到）或查询失败时返回 null，UI 据此显示未点亮且不报错。 */
    suspend fun isStarred(owner: String, repo: String): Boolean? {
        val token = tokenProvider.get() ?: return null
        return try {
            githubApi.isStarred(token, owner, repo)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun star(owner: String, repo: String): Result = run(owner, repo, star = true)

    suspend fun unstar(owner: String, repo: String): Result = run(owner, repo, star = false)

    private suspend fun run(owner: String, repo: String, star: Boolean): Result {
        val loggedIn = authManager().authState.value is AuthState.LoggedIn
        val token = when (val lookup = tokenProvider.lookup()) {
            is GithubTokenLookup.Available -> lookup.token
            // 已登录但服务端明确没存 token（纯邮箱账号 / vault 被清空）→ 引导去关联；网络问题 → 普通失败
            GithubTokenLookup.Missing -> return if (loggedIn) Result.NEED_GITHUB_LINK else Result.NEED_LOGIN
            GithubTokenLookup.Failed -> return if (loggedIn) Result.FAILED else Result.NEED_LOGIN
        }
        return try {
            if (star) githubApi.starRepo(token, owner, repo)
            else githubApi.unstarRepo(token, owner, repo)
            if (star) Result.STARRED else Result.UNSTARRED
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.FAILED
        }
    }

    companion object {
        val shared = RepoStarService()
    }
}
