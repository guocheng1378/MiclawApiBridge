package io.github.guocheng1378.miclawbridge;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 老 Xposed API 82 入口 (兼容只支持 API 82 的框架, 如旧版 LSPosed / EdXposed)
 *
 * 三重保险启动 (与 LibXposed HookEntry 对齐):
 *  1. hook Application.attach
 *  2. hook Instrumentation.callApplicationOnCreate (最可靠)
 *  3. 反射 ActivityThread.currentApplication 兜底
 */
public class OldHookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.aios.osbot".equals(lpparam.packageName)) return;
        if ("io.github.guocheng1378.miclawbridge".equals(lpparam.packageName)) return;

        // 1. hook Application.attach (最早时机)
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
                            Logger.d("OldHookEntry: started via attach");
                        }
                    }
                });
            Logger.d("OldHookEntry: hooked Application.attach");
        } catch (Throwable t) {
            Logger.e("OldHookEntry: attach hook failed", t);
        }

        // 2. hook Instrumentation.callApplicationOnCreate (最可靠)
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Instrumentation", lpparam.classLoader,
                "callApplicationOnCreate", Application.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object app = param.args[0];
                        if (app instanceof Application) {
                            BridgeStarter.start(((Application) app).getApplicationContext());
                            Logger.d("OldHookEntry: started via callApplicationOnCreate");
                        }
                    }
                });
            Logger.d("OldHookEntry: hooked Instrumentation.callApplicationOnCreate");
        } catch (Throwable t) {
            Logger.e("OldHookEntry: callApplicationOnCreate hook failed", t);
        }

        // 3. 反射 currentApplication 兜底
        try {
            Context ctx = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                "currentApplication");
            if (ctx != null) {
                BridgeStarter.start(ctx.getApplicationContext());
                Logger.d("OldHookEntry: started via currentApplication fallback");
            }
        } catch (Throwable t2) {
            Logger.e("OldHookEntry: fallback failed", t2);
        }
    }
}
