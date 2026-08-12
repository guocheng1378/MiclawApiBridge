package io.github.guocheng1378.miclawbridge;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LibXposed API 102 模块入口
 *
 * 三重保险启动:
 *  1. onPackageLoaded: hook Application.attach           -> 最早, 拿到 Context 立即启动
 *  2. onPackageReady:   hook Instrumentation.callApplicationOnCreate
 *                        -> attach 错过时, onCreate 前必然走到 (最可靠)
 *  3. onPackageReady:   反射 ActivityThread.currentApplication 兜底启动
 * 排除 system_server 和模块自身进程, 防止系统崩溃 / UI 闪退
 */
public class HookEntry extends XposedModule {

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        if (param.isSystemServer()) {
            Logger.d("HookEntry: system_server, skip");
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        Logger.d("HookEntry: onSystemServerStarting, skip");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
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
                        Logger.d("HookEntry: started via attach");
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
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        if ("io.github.guocheng1378.miclawbridge".equals(param.getPackageName())) return;

        // 保险 2: hook Instrumentation.callApplicationOnCreate
        // onCreate 前必然经过这里, Application 已完整可用, 是最可靠的启动时机
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
                        BridgeStarter.start(((Application) app).getApplicationContext());
                        Logger.d("HookEntry: started via callApplicationOnCreate");
                    }
                } catch (Throwable t) {
                    Logger.e("HookEntry: start via callApplicationOnCreate failed", t);
                }
                return result;
            });
            Logger.d("HookEntry: hooked Instrumentation.callApplicationOnCreate");
        } catch (Throwable t) {
            Logger.e("HookEntry: hook callApplicationOnCreate failed", t);
        }

        // 保险 3: 反射 ActivityThread.currentApplication 兜底
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread", true, param.getClassLoader());
            Method currentApp = atClass.getDeclaredMethod("currentApplication");
            currentApp.setAccessible(true);
            Context ctx = (Context) currentApp.invoke(null);
            if (ctx != null) {
                BridgeStarter.start(ctx.getApplicationContext());
                Logger.d("HookEntry: started via currentApplication fallback");
            }
        } catch (Throwable t) {
            Logger.e("HookEntry: currentApplication fallback failed", t);
        }
    }
}
