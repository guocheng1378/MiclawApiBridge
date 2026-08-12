package io.github.guocheng1378.miclawbridge;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LibXposed API 102 模块入口 (v2.0.3 诊断版)
 *
 * 三重保险启动 + 文件诊断:
 *  1. onPackageLoaded: hook Application.attach
 *  2. onPackageReady:   hook Instrumentation.callApplicationOnCreate
 *  3. onPackageReady:   反射 ActivityThread.currentApplication 兜底
 *
 * 每个关键节点同时写入 /data/local/tmp/miclaw_dbg.txt 与 /sdcard/Download/miclaw_dbg.txt,
 * 用于确认模块是否被框架加载/注入 (LSPosed 日志页可能不显示模块 Log)
 */
public class HookEntry extends XposedModule {

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        if (param.isSystemServer()) {
            DebugLog.w("HookEntry", "onModuleLoaded: system_server, skip");
            return;
        }
        DebugLog.w("HookEntry", "onModuleLoaded: 模块已被框架加载! process=" + param.getProcessName());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        DebugLog.w("HookEntry", "onSystemServerStarting: skip");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        DebugLog.w("HookEntry", "onPackageLoaded: pkg=" + param.getPackageName());
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        if ("io.github.guocheng1378.miclawbridge".equals(param.getPackageName())) return;
        DebugLog.w("HookEntry", "onPackageLoaded: TARGET com.aios.osbot, hooking Application.attach");
        try {
            Class<?> appClass = Class.forName("android.app.Application", true, param.getDefaultClassLoader());
            Method attach = appClass.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Context ctx = (Context) chain.getArg(0);
                    if (ctx != null) {
                        DebugLog.w("HookEntry", "ATTACH fired, starting bridge");
                        BridgeStarter.start(ctx.getApplicationContext());
                    }
                } catch (Throwable t) {
                    DebugLog.w("HookEntry", "attach start error: " + t);
                }
                return result;
            });
            DebugLog.w("HookEntry", "attach hook registered");
        } catch (Throwable t) {
            DebugLog.w("HookEntry", "attach hook register FAILED: " + t);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        DebugLog.w("HookEntry", "onPackageReady: pkg=" + param.getPackageName());
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        if ("io.github.guocheng1378.miclawbridge".equals(param.getPackageName())) return;

        // 保险 2: hook Instrumentation.callApplicationOnCreate
        try {
            Class<?> instrClass = Class.forName("android.app.Instrumentation", true, param.getClassLoader());
            Method callAppCreate = instrClass.getDeclaredMethod(
                    "callApplicationOnCreate", android.app.Application.class);
            callAppCreate.setAccessible(true);
            hook(callAppCreate).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object app = chain.getArg(0);
                    if (app instanceof Application) {
                        DebugLog.w("HookEntry", "callApplicationOnCreate fired, starting bridge");
                        BridgeStarter.start(((Application) app).getApplicationContext());
                    }
                } catch (Throwable t) {
                    DebugLog.w("HookEntry", "callAppCreate start error: " + t);
                }
                return result;
            });
            DebugLog.w("HookEntry", "callApplicationOnCreate hook registered");
        } catch (Throwable t) {
            DebugLog.w("HookEntry", "callAppCreate hook register FAILED: " + t);
        }

        // 保险 3: currentApplication 兜底
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread", true, param.getClassLoader());
            Method currentApp = atClass.getDeclaredMethod("currentApplication");
            currentApp.setAccessible(true);
            Context ctx = (Context) currentApp.invoke(null);
            if (ctx != null) {
                DebugLog.w("HookEntry", "currentApplication fallback: starting bridge");
                BridgeStarter.start(ctx.getApplicationContext());
            } else {
                DebugLog.w("HookEntry", "currentApplication fallback: ctx is null (too early)");
            }
        } catch (Throwable t) {
            DebugLog.w("HookEntry", "currentApplication fallback FAILED: " + t);
        }
    }
}
