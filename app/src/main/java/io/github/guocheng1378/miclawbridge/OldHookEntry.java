package io.github.guocheng1378.miclawbridge;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 老 Xposed API 入口 (兼容只支持 API 82 的框架) */
public class OldHookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        // 排除模块自身进程 (避免框架注入自身导致 UI 崩溃)
        if (!"com.aios.osbot".equals(lpparam.packageName)) return;
        if ("io.github.guocheng1378.miclawbridge".equals(lpparam.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader,
                "attach", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context ctx = (Context) param.args[0];
                        if (ctx != null) {
                            BridgeStarter.start(ctx.getApplicationContext());
                        }
                    }
                });
            Logger.d("OldHookEntry: hooked Application.attach");
        } catch (Throwable t) {
            Logger.e("OldHookEntry: hook attach failed", t);
            try {
                Context ctx = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                    "currentApplication");
                if (ctx != null) BridgeStarter.start(ctx.getApplicationContext());
            } catch (Throwable t2) {
                Logger.e("OldHookEntry: fallback failed", t2);
            }
        }
    }
}
