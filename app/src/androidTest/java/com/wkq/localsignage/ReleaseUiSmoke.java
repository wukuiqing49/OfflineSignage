package com.wkq.localsignage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** 仅使用平台类型巡检正式包，避免测试链接被R8改名的业务类或Kotlin运行库。 */
public class ReleaseUiSmoke extends Instrumentation {
    private Bundle args;
    private final List<Activity> opened = new ArrayList<>();

    @Override public void onCreate(Bundle arguments) {
        args = arguments == null ? new Bundle() : arguments;
        start();
    }

    @Override public void onStart() {
        SharedPreferences preferences = getTargetContext().getSharedPreferences("local_signage_onboarding", 0);
        boolean hadWelcome = preferences.contains("welcome_completed");
        boolean originalWelcome = preferences.getBoolean("welcome_completed", false);
        boolean restoreWelcome = "true".equals(args.getString("welcome"));
        int count = 0;
        int resultCode = Activity.RESULT_OK;
        Bundle result = new Bundle();
        try {
            if (restoreWelcome) {
                // 调用方先确认空素材库，只暂时改变引导标记，不清理业务数据。
                preferences.edit().putBoolean("welcome_completed", false).apply();
                Activity welcome = launch("WelcomeActivity", "welcome_root");
                count++;
                onMain(welcome::finish);
            }
            Activity main = launch("MainActivity", "playback_root");
            onMain(() -> view(main, "show_pairing_button").performClick());
            checkVisible(main, "open_billing_row");
            count++;
            Activity billing = launch("BillingActivity", "billing_root");
            onMain(() -> {
                require(view(billing, "plan_section_title").isShown(), "Plan heading missing");
                View toggle = view(billing, "plan_details_toggle");
                View details = view(billing, "plan_details");
                require(details.getVisibility() == View.GONE, "Details initially expanded");
                require(toggle.performClick(), "Toggle click failed");
                require(details.getVisibility() == View.VISIBLE, "Details did not expand");
                require(toggle.performClick(), "Toggle second click failed");
                require(details.getVisibility() == View.GONE, "Details did not collapse");
            });
            count++;
            onMain(billing::finish);
            Activity help = launch("HelpActivity", "help_root");
            count++;
            onMain(help::finish);
            Activity legal = launch("LegalCenterActivity", "legal_center_root");
            count++;
            onMain(legal::finish);
            result.putString("result", "Release smoke passed: " + count + " pages and details toggle");
        } catch (Throwable error) {
            resultCode = Activity.RESULT_CANCELED;
            result.putString("failure", error.toString());
        } finally {
            onMain(() -> {
                for (int i = opened.size() - 1; i >= 0; i--) {
                    Activity activity = opened.get(i);
                    if (!activity.isDestroyed()) activity.finish();
                }
            });
            if (restoreWelcome) {
                SharedPreferences.Editor editor = preferences.edit();
                if (hadWelcome) editor.putBoolean("welcome_completed", originalWelcome);
                else editor.remove("welcome_completed");
                editor.apply();
            }
        }
        finish(resultCode, result);
    }

    /** 显式入口只使用字符串，测试APK不静态链接任何业务类。 */
    private Activity launch(String name, String root) {
        Activity activity = startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),
            "com.wkq.localsignage." + name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        opened.add(activity);
        waitForIdleSync();
        SystemClock.sleep(1200);
        checkVisible(activity, root);
        return activity;
    }

    private void checkVisible(Activity activity, String name) {
        onMain(() -> {
            require(!activity.isDestroyed() && activity.getWindow().getDecorView().hasWindowFocus(), "Page lost focus: " + name);
            require(view(activity, name).isShown(), "Page root missing: " + name);
        });
    }

    /** 将断言带回测试线程，保证失败时仍执行配置恢复，不使目标主线程崩溃。 */
    private void onMain(Runnable action) {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    @SuppressLint("DiscouragedApi") // 测试按名称读取正式包ID，不能链接Debug变体的R常量。
    private View view(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", getTargetContext().getPackageName());
        require(id != 0, "Release resource missing: " + name);
        View view = activity.findViewById(id);
        require(view != null, "Release view missing: " + name);
        return view;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
