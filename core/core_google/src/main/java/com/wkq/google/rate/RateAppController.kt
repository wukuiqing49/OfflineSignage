package com.wkq.google.rate

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * 应用评分/反馈自动提示控制器。
 */
object RateAppController {

    private const val TAG = "RateAppController"
    private const val PREF_NAME = "google_rate_state"
    private const val KEY_PREFIX = "rate_module_"
    private const val DAYS_BETWEEN_PROMPTS = 30L

    fun initFirstUseIfNeeded(context: Context) {
        val key = getPrefKey(context, "first_use_time")
        val prefs = prefs(context)
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        }
    }

    fun shouldShowRateDialog(
        context: Context,
        minDays: Int = 1
    ): Boolean {
        if (isRated(context)) {
            Log.d(TAG, "shouldShowRateDialog: skipped because user has already rated/commented.")
            return false
        }

        if (getCompletedReportCount(context) < 1) {
            Log.d(TAG, "shouldShowRateDialog: skipped because no report has been completed.")
            return false
        }

        initFirstUseIfNeeded(context)
        val prefs = prefs(context)
        val firstUseTime = prefs.getLong(getPrefKey(context, "first_use_time"), System.currentTimeMillis())
        val activeDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstUseTime)
        if (activeDays < minDays) {
            Log.d(TAG, "shouldShowRateDialog: skipped because active days ($activeDays) is less than required ($minDays).")
            return false
        }

        val lastPromptTime = prefs.getLong(getPrefKey(context, "last_prompt_time"), 0L)
        if (lastPromptTime > 0L) {
            val promptDiffDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastPromptTime)
            if (promptDiffDays < DAYS_BETWEEN_PROMPTS) {
                Log.d(TAG, "shouldShowRateDialog: skipped because days since last prompt ($promptDiffDays) is less than required interval ($DAYS_BETWEEN_PROMPTS).")
                return false
            }
        }

        Log.d(TAG, "shouldShowRateDialog: criteria met. Preparing to show rating dialog.")
        return true
    }

    fun markRated(context: Context) {
        prefs(context).edit().putBoolean(getPrefKey(context, "has_rated"), true).apply()
    }

    fun isRated(context: Context): Boolean {
        return prefs(context).getBoolean(getPrefKey(context, "has_rated"), false)
    }

    fun increasePromptCount(context: Context) {
        val currentCount = getPromptCount(context)
        prefs(context).edit()
            .putInt(getPrefKey(context, "prompt_count"), currentCount + 1)
            .putLong(getPrefKey(context, "last_prompt_time"), System.currentTimeMillis())
            .apply()
    }

    fun getPromptCount(context: Context): Int {
        return prefs(context).getInt(getPrefKey(context, "prompt_count"), 0)
    }

    /** 同一报告只记录一次，返回 true 表示这是新的核心完成事件。 */
    fun recordCompletedReport(context: Context, reportId: Long): Boolean {
        if (reportId <= 0L) return false
        val prefs = prefs(context)
        val reportKey = getPrefKey(context, "completed_report_$reportId")
        if (prefs.getBoolean(reportKey, false)) return false
        prefs.edit()
            .putBoolean(reportKey, true)
            .putInt(getPrefKey(context, "completed_report_count"), getCompletedReportCount(context) + 1)
            .apply()
        return true
    }

    fun getCompletedReportCount(context: Context): Int {
        return prefs(context).getInt(getPrefKey(context, "completed_report_count"), 0)
    }

    private fun getPrefKey(context: Context, rawKey: String): String {
        return KEY_PREFIX + context.packageName + "_" + rawKey
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}
