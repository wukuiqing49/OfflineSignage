package com.wkq.localsignage

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** 真机页面巡检。覆盖应用语言并读取当前系统字体，不修改用户素材或购买状态。 */
class DeviceUiAudit : Instrumentation() {
    private lateinit var args: Bundle
    private lateinit var output: File
    private val pages = JSONArray()
    private var originalLocales = LocaleListCompat.getEmptyLocaleList()
    @Volatile private var resumedActivity: Activity? = null

    override fun onCreate(arguments: Bundle?) {
        args = arguments ?: Bundle()
        start()
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
        if (args.getString("orientation") == "portrait" && activity !is MainActivity) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        super.callActivityOnCreate(activity, icicle)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun callActivityOnResume(activity: Activity) {
        super.callActivityOnResume(activity)
        resumedActivity = activity
    }

    override fun onStart() {
        output = File(targetContext.getExternalFilesDir(null), "device-ui-audit/" +
            args.getString("locale", "en") + "-" + args.getString("fontScale", "1.0") +
            "-" + args.getString("orientation", "landscape")).apply { mkdirs() }
        try {
            runOnMainSync {
                originalLocales = AppCompatDelegate.getApplicationLocales()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(args.getString("locale", "en")))
            }
            val main = launch(Intent(targetContext, MainActivity::class.java))
            runOnMainSync {
                main.findViewById<View>(com.wkq.localsignage.R.id.show_pairing_button).performClick()
            }
            capture(main, "pairing")
            scrollAndCapture(main, "pairing-bottom")
            val help = launch(Intent(targetContext, HelpActivity::class.java))
            capture(help, "help")
            scrollAndCapture(help, "help-bottom")
            runOnMainSync { help.finish() }
            val articles = if (args.getString("mode") == "compact") listOf("autostart", "enterprise_deployment")
                else listOf("quick_start", "content", "html", "playback", "multi_device",
                    "autostart", "troubleshooting", "purchase", "enterprise_deployment")
            for (article in articles) {
                val activity = launch(HelpArticleActivity.intent(targetContext, article))
                capture(activity, "article-$article")
                scrollAndCapture(activity, "article-$article-bottom")
                runOnMainSync { activity.finish() }
            }
            val billing = launch(Intent(targetContext, BillingActivity::class.java))
            SystemClock.sleep(2500)
            capture(billing, "billing")
            scrollAndCapture(billing, "billing-bottom")
            runOnMainSync { billing.finish() }
            val legal = launch(Intent(targetContext, LegalCenterActivity::class.java))
            capture(legal, "legal")
            scrollAndCapture(legal, "legal-bottom")
            runOnMainSync { legal.finish() }
            val documents = if (args.getString("mode") == "compact") listOf("privacy")
                else listOf("privacy", "terms", "subscription", "data_deletion")
            for (document in documents) {
                val activity = launch(LegalDocumentActivity.intent(targetContext, document))
                capture(activity, "legal-$document")
                scrollAndCapture(activity, "legal-$document-bottom")
                runOnMainSync { activity.finish() }
            }
            runOnMainSync { main.finish() }
            File(output, "report.json").writeText(pages.toString(2))
            runOnMainSync { AppCompatDelegate.setApplicationLocales(originalLocales) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "Audited ${pages.length()} page states") })
        } catch (failure: Throwable) {
            File(output, "failure.txt").writeText(failure.stackTraceToString())
            runOnMainSync { AppCompatDelegate.setApplicationLocales(originalLocales) }
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", failure.toString()) })
        }
    }

    private fun launch(intent: Intent): Activity {
        val launched = startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForIdleSync()
        SystemClock.sleep(1100)
        return resumedActivity?.takeIf { it.javaClass == launched.javaClass } ?: launched
    }

    private fun scrollAndCapture(activity: Activity, name: String) {
        runOnMainSync {
            fun scroll(view: View) {
                if (view is ScrollView && view.isShown) view.fullScroll(View.FOCUS_DOWN)
                if (view is ViewGroup) for (i in 0 until view.childCount) scroll(view.getChildAt(i))
            }
            scroll(activity.window.decorView)
        }
        capture(activity, name)
    }

    private fun capture(activity: Activity, name: String) {
        waitForIdleSync()
        SystemClock.sleep(400)
        val activePackage = uiAutomation.rootInActiveWindow?.packageName?.toString()
        var focused = false
        runOnMainSync { focused = activity.window.decorView.hasWindowFocus() }
        check(focused && (activePackage == null || activePackage == targetContext.packageName)) {
            uiAutomation.takeScreenshot()?.let { bitmap ->
                File(output, "obscured.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            "Application is obscured by $activePackage; unlock device and close system panels before running the audit"
        }
        val views = JSONArray()
        runOnMainSync {
            check(activity.resources.configuration.locales[0].toLanguageTag() == args.getString("locale", "en"))
            check(kotlin.math.abs(activity.resources.configuration.fontScale - args.getString("fontScale", "1.0").toFloat()) < 0.01f)
            fun inspect(view: View) {
                if (!view.isShown) return
                if (view is TextView && view.text.isNotEmpty()) {
                    val id = if (view.id == View.NO_ID) "" else runCatching {
                        activity.resources.getResourceEntryName(view.id)
                    }.getOrDefault("")
                    val layout = view.layout
                    val sensitive = id in setOf("pairing_access_code", "pairing_address", "pairing_hotspot_info")
                    val rect = Rect()
                    val visible = view.getGlobalVisibleRect(rect)
                    val ellipsis = layout?.let { l -> (0 until l.lineCount).sumOf(l::getEllipsisCount) } ?: 0
                    val overflow = layout != null && layout.height > view.height - view.compoundPaddingTop - view.compoundPaddingBottom + 2
                    views.put(JSONObject().put("id", id).put("text", if (sensitive) "[REDACTED]" else view.text)
                        .put("width", view.width).put("height", view.height).put("visible", visible)
                        .put("bounds", rect.toShortString()).put("ellipsis", ellipsis).put("verticalOverflow", overflow)
                        .put("fontScale", activity.resources.configuration.fontScale)
                        .put("locale", activity.resources.configuration.locales.toLanguageTags()))
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i))
            }
            inspect(activity.window.decorView)
        }
        pages.put(JSONObject().put("page", name).put("views", views)
            .put("orientation", activity.resources.configuration.orientation))
        // 包含配对凭据的原始截图仅写应用私有测试目录，拉取后同样只保留在忽略的 build 目录。
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
