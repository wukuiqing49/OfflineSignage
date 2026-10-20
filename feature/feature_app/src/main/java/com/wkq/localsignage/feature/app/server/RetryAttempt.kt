package com.wkq.localsignage.feature.app.server

import kotlinx.coroutines.CancellationException

/** 一轮失败不终止常驻同步；服务停止时的取消必须继续传播。 */
internal suspend fun retryAttempt(onError: (Exception) -> Unit, block: suspend () -> Unit): Boolean =
    try {
        block()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onError(error)
        false
    }
