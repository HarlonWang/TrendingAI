package whl.trending.ai.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackPopTest {

    @Test
    fun pop_removes_top_entry() {
        val backStack = mutableListOf<Any>(Home, Settings)
        backStack.safePop()
        assertEquals(listOf<Any>(Home), backStack)
    }

    @Test
    fun double_pop_keeps_root() {
        // 模拟转场动画期间二次点击返回：第二次出栈必须被吞掉，栈底 Home 不可弹出
        val backStack = mutableListOf<Any>(Home, Settings)
        backStack.safePop()
        backStack.safePop()
        assertEquals(listOf<Any>(Home), backStack)
    }

    @Test
    fun pop_on_root_only_is_noop() {
        val backStack = mutableListOf<Any>(Home)
        backStack.safePop()
        assertEquals(listOf<Any>(Home), backStack)
    }

    @Test
    fun pop_deep_stack_removes_only_top() {
        val backStack = mutableListOf<Any>(Home, Settings, Favorites, RepoDetail("a", "b"))
        backStack.safePop()
        assertEquals(listOf<Any>(Home, Settings, Favorites), backStack)
    }
}
