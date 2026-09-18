package com.wkq.localsignage.feature.app.storage

import java.util.concurrent.Executor

/** 串行写入并合并积压快照，慢磁盘不会产生无界任务队列。 */
internal class CoalescingWriter<T : Any>(
    private val executor: Executor,
    private val write: (T) -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val lock = Any()
    private var pending: T? = null
    private var running = false

    fun submit(value: T) {
        synchronized(lock) {
            pending = value
            if (running) return
            running = true
        }
        try {
            executor.execute(::drain)
        } catch (error: Exception) {
            synchronized(lock) { running = false }
            onError(error)
        }
    }

    private fun drain() {
        while (true) {
            val value = synchronized(lock) {
                pending?.also { pending = null } ?: run { running = false; return }
            }
            try { write(value) } catch (error: Exception) { onError(error) }
        }
    }
}
