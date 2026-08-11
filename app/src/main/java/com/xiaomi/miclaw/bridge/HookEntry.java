package com.xiaomi.miclaw.bridge;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed 入口: 注入 com.aios.osbot, 启动 HTTP Bridge
 */
public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.aios.osbot".equals(lpparam.packageName)) {
            return;
        }

        Logger.d("HookEntry: injected into " + lpparam.packageName);

        // 等 Application 创建后拿 Context 启动服务
        XposedHelpers.findAndHookMethod(
            "android.app.Application", lpparam.classLoader,
            "attach", Context.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context appContext = (Context) param.args[0];
                    Logger.d("Application attached, starting bridge...");
                    startBridge(appContext.getApplicationContext());
                }
            });
    }

    private void startBridge(Context context) {
        try {
            HttpServer server = new HttpServer(context);
            server.start();
            Logger.d("Miclaw API Bridge started on 127.0.0.1:8787");
        } catch (Throwable t) {
            Logger.e("Bridge start failed: " + t.getMessage(), t);
        }
    }
}
