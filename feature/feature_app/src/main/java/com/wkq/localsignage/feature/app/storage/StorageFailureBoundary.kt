package com.wkq.localsignage.feature.app.storage

import android.database.sqlite.SQLiteException
import java.io.IOException

/** 只接管可识别的存储故障；编程错误继续向上抛出。 */
internal inline fun storageAttempt(onFailure: (Exception) -> Unit, block: () -> Unit): Boolean {
    return try {
        block()
        true
    } catch (error: Exception) {
        if (error !is SQLiteException && error !is IOException &&
            !(error is IllegalStateException && error.message == "METADATA_WRITE_FAILED")
        ) throw error
        onFailure(error)
        false
    }
}
