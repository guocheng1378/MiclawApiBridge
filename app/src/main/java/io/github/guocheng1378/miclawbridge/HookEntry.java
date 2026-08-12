package io.github.guocheng1378.miclawbridge;

import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LibXposed API 102 模块入口 (v2.0 恢复真实启动)
 *
 * 双保险启动:
 *  1. onPackageLoaded: hook Application.attach, attach 时立即启动 Bridge
 *  2. onPackageReady: 兜底, attach hook 可能错过时反射 ActivityThread.currentApplication 启动
 * 排除 system_server 和模块自身进程, 防止系统崩溃 / UI 闪退
 */
public class HookEntry extends XposedModule {

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        // system_server 加载时不做任何事
        if (param.isSystemServer()) {
            Logger.d("HookEntry: system_server, skip");
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        // system_server: 不做任何事, 防止系统崩溃
        Logger.d("HookEntry: onSystemServerStarting, skip");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // 只注入目标 App; 排除模块自身进程 (避免框架注入自身导致 UI 崩溃)
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        if ("io.github.guocheng1378.miclawbridge".equals(param.getPackageName())) return;
        try {
            Class<?> appClass = Class.forName("android.app.Application", true, param.getDefaultClassLoader());
            Method attach = appClass.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Context ctx = (Context) chain.getArg(0);
                    if (ctx != null) {
                        BridgeStarter.start(ctx.getApplicationContext());
                        Logger.d("MiclawBridge v2.0 started (attach)");
                    }
                } catch (Throwable t) {
                    Logger.e("HookEntry: start via attach failed", t);
                }
                return result;
            });
            Logger.d("HookEntry: hooked Application.attach (onPackageLoaded)");
        } catch (Throwable t) {
            Logger.e("HookEntry: onPackageLoaded hook attach failed", t);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        // 兜底: attach hook 可能错过, 反射 ActivityThread.currentApplication 启动
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        if ("io.github.guocheng1378.miclawbridge".equals(param.getPackageName())) return;
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread", true, param.getClassLoader());
            Method currentApp = atClass.getDeclaredMethod("currentApplication");
            currentApp.setAccessible(true);
            Context ctx = (Context) currentApp.invoke(null);
            if (ctx != null) {
                BridgeStarter.start(ctx.getApplicationContext());
                Logger.d("MiclawBridge v2.0 started (onPackageReady fallback)");
            }
        } catch (Throwable t) {
            Logger.e("HookEntry: onPackageReady fallback failed", t);
        }
    }
}
