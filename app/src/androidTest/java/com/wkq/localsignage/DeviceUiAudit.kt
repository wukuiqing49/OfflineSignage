package com.wkq.localsignage

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.feature.app.model.SignageSettings
import androidx.core.content.edit
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
    private var originalSettings: SignageSettings? = null
    private var originalWelcome = false
    @Volatile private var resumedActivity: Activity? = null

    override fun onCreate(arguments: Bundle?) {
        args = arguments ?: Bundle()
        start()
    }

    override fun callActivityOnCreate(activity: Activity, icicle: Bundle?) {
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
                originalSettings = SignageRuntime.settings()
                args.getString("preference")?.let { preference ->
                    SignageRuntime.setSettings(requireNotNull(originalSettings).copy(orientation = preference))
                }
                originalLocales = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    LocaleListCompat.wrap(targetContext.getSystemService(android.app.LocaleManager::class.java).applicationLocales)
                } else AppCompatDelegate.getApplicationLocales()
                setAuditLocales(LocaleListCompat.forLanguageTags(args.getString("locale", "en")))
            }
            waitForIdleSync()
            SystemClock.sleep(500)
            if (args.getString("mode") == "billing") {
                auditBillingPlans()
                restoreConfiguration()
                finish(Activity.RESULT_OK, Bundle().apply { putString("result", "Audited ${pages.length()} billing states (test fixtures)") })
                return
            }
            if (args.getString("welcome") == "true") {
                check(SignageRuntime.resources().isEmpty()) { "Onboarding audit requires an empty test device" }
                val preferences = targetContext.getSharedPreferences("local_signage_onboarding", 0)
                originalWelcome = preferences.getBoolean("welcome_completed", false)
                preferences.edit { putBoolean("welcome_completed", false) }
                val welcome = launch(Intent(targetContext, WelcomeActivity::class.java))
                capture(welcome, "welcome")
                scrollAndCapture(welcome, "welcome-bottom")
                runOnMainSync { welcome.finish() }
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
            runOnMainSync {
                val retry = billing.findViewById<View>(com.wkq.localsignage.feature.app.R.id.retry_catalog_button)
                if (retry.isShown) {
                    retry.performClick()
                    check(billing.findViewById<View>(com.wkq.localsignage.feature.app.R.id.content_scroll).visibility == View.VISIBLE)
                }
            }
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
            restoreConfiguration()
            finish(Activity.RESULT_OK, Bundle().apply { putString("result", "Audited ${pages.length()} page states") })
        } catch (failure: Throwable) {
            File(output, "failure.txt").writeText(failure.stackTraceToString())
            restoreConfiguration()
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", failure.toString()) })
        }
    }

    /** 仅测试 APK 注入页面状态，不替换生产商品、不发起真实购买。 */
    private fun auditBillingPlans() {
        val activity = launch(Intent(targetContext, BillingActivity::class.java))
        SystemClock.sleep(5000)
        val month = com.wkq.google.billing.GoogleProduct(
            productId = "pro_subscription:pro-month", productType = "subs", title = "TEST monthly",
            description = "UI fixture", formattedPrice = "$4.99", offerToken = "test-month", basePlanId = "pro-month", billingPeriod = "P1M"
        )
        val year = month.copy(productId = "pro_subscription:pro-yearly", basePlanId = "pro-yearly", billingPeriod = "P1Y", formattedPrice = "$39.99", offerToken = "test-year")
        val lifetime = month.copy(productId = "pro_lifetime", baseProductId = "pro_lifetime", productType = "inapp", basePlanId = "", billingPeriod = "", formattedPrice = "$99.99", offerToken = "test-life")
        val render = BillingActivity::class.java.getDeclaredMethod("render", com.wkq.localsignage.monetization.MonetizationUiState::class.java).apply { isAccessible = true }
        val fixtures = listOf(
            Triple("all", com.wkq.google.billing.GoogleBillingCatalog(listOf(month, year), listOf(lifetime)), listOf(true, true, true)),
            Triple("partial", com.wkq.google.billing.GoogleBillingCatalog(oneTimeProducts = listOf(lifetime)), listOf(false, false, true)),
            Triple("unavailable", com.wkq.google.billing.GoogleBillingCatalog(), listOf(false, false, false))
        )
        val buttons = listOf(com.wkq.localsignage.feature.app.R.id.monthly_button, com.wkq.localsignage.feature.app.R.id.subscription_button, com.wkq.localsignage.feature.app.R.id.lifetime_button)
        val panels = listOf(com.wkq.localsignage.feature.app.R.id.monthly_panel, com.wkq.localsignage.feature.app.R.id.subscription_panel, com.wkq.localsignage.feature.app.R.id.lifetime_panel)
        for ((name, catalog, enabled) in fixtures) {
            runOnMainSync {
                render.invoke(activity, com.wkq.localsignage.monetization.MonetizationUiState(
                    entitlement = com.wkq.localsignage.monetization.EntitlementState(com.wkq.localsignage.monetization.EntitlementType.TRIAL_ACTIVE, 0L, System.currentTimeMillis() + 86400000L),
                    catalog = catalog, catalogLoaded = true, errorMessage = if (name == "unavailable") "Test unavailable" else ""
                ))
                panels.forEach { check(activity.findViewById<View>(it).visibility == View.VISIBLE) }
                buttons.forEachIndexed { index, id -> check(activity.findViewById<View>(id).isEnabled == enabled[index]) { "Wrong purchase availability: $name/$index" } }
                activity.findViewById<ScrollView>(com.wkq.localsignage.feature.app.R.id.content_scroll).scrollTo(0, 0)
            }
            capture(activity, "billing-fixture-$name")
            scrollAndCapture(activity, "billing-fixture-$name-bottom")
        }
        runOnMainSync { activity.finish() }
    }

    private fun restoreConfiguration() {
        runOnMainSync {
            originalSettings?.let(SignageRuntime::setSettings)
            if (args.getString("welcome") == "true") {
                targetContext.getSharedPreferences("local_signage_onboarding", 0).edit {
                    putBoolean("welcome_completed", originalWelcome)
                }
            }
            setAuditLocales(originalLocales)
        }
    }

    private fun setAuditLocales(locales: LocaleListCompat) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            targetContext.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                android.os.LocaleList.forLanguageTags(locales.toLanguageTags())
        } else AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun launch(intent: Intent): Activity {
        val launched = startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForIdleSync()
        SystemClock.sleep(1100)
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            val current = resumedActivity?.takeIf { it.javaClass == launched.javaClass } ?: launched
            var ready = false
            runOnMainSync { ready = !current.isDestroyed && current.window.decorView.hasWindowFocus() }
            if (ready) return current
            SystemClock.sleep(100)
        }
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
        val actualLocale = activity.resources.configuration.locales[0]
        val expectedLocale = java.util.Locale.forLanguageTag(args.getString("locale", "en"))
        // Android 可补全 zh-Hans-CN 等脚本信息，按请求的语言/地区验证。
        check(actualLocale.language == expectedLocale.language &&
            (expectedLocale.country.isEmpty() || actualLocale.country == expectedLocale.country)) {
            "Unexpected locale ${actualLocale.toLanguageTag()}, expected $expectedLocale on $name"
        }
        check(kotlin.math.abs(activity.resources.configuration.fontScale - args.getString("fontScale", "1.0").toFloat()) < 0.01f) {
            "Unexpected font scale on $name"
        }
        runOnMainSync {
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
        args.getString("expectedOrientation")?.toInt()?.let { expected ->
            check(activity.resources.configuration.orientation == expected) { "Unexpected orientation on $name" }
        }
        pages.put(JSONObject().put("page", name).put("views", views)
            .put("orientation", activity.resources.configuration.orientation))
        File(output, "report.json").writeText(pages.toString(2))
        // 包含配对凭据的原始截图仅写应用私有测试目录，拉取后同样只保留在忽略的 build 目录。
        uiAutomation.takeScreenshot()?.let { bitmap ->
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
