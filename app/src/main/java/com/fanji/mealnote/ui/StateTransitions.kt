package com.fanji.mealnote.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 原子状态跃迁：仅当当前值与读取时快照一致，才写入 [transform] 的结果。
 *
 * 为什么需要它：`update` / `updateAndGet` 返回的是**写入之后**的状态，调用方无法区分
 * 「这次由我把标志位立起来」还是「本来就已被别人立着」。早先的实现据此写了
 * `if (!snapshot.isBusy) return` 这样的重复提交守卫——而返回值里的 `isBusy` 恒为 true，
 * 守卫从来不会生效，两次并发点击会各自启动一次写库 / 导出。
 *
 * 本函数把「判断 + 置位」压进一次 CAS：
 * - 真正完成跃迁的调用拿到**新状态**；
 * - 已在忙碌、或 [transform] 主动返回 null 的调用一律拿到 null，调用方据此提前返回。
 *
 * [transform] 可能因 CAS 失败被重复调用，因此必须是纯函数：不要在里面启动协程、
 * 写日志或修改外部状态。
 */
internal inline fun <T> MutableStateFlow<T>.tryTransition(transform: (T) -> T?): T? {
    while (true) {
        val current = value
        val next = transform(current) ?: return null
        if (compareAndSet(current, next)) return next
    }
}
