package io.github.guocheng1378.miclawbridge;

import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LibXposed API 102 模块入口 (最新 LSPosed 框架)
 */
public class HookEntry extends XposedModule {

    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // 早期 hook Application.attach (onPackageReady 时 attach 可能已发生, 会错过!)
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        try {
            Class<?> appClass = Class.forName("android.app.Application", true, param.getDefaultClassLoader());
            Method attach = appClass.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                Context ctx = (Context) chain.getArg(0);
                if (ctx != null) {
                    startBridge(ctx.getApplicationContext());
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
        // 兜底: attach hook 可能错过, 直接反射拿当前 Application
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread", true, param.getClassLoader());
            Method currentApp = atClass.getDeclaredMethod("currentApplication");
            currentApp.setAccessible(true);
            Context ctx = (Context) currentApp.invoke(null);
            if (ctx != null) {
                Logger.d("HookEntry: onPackageReady fallback, starting bridge");
                startBridge(ctx.getApplicationContext());
            }
        } catch (Throwable t) {
            Logger.e("HookEntry: onPackageReady fallback failed", t);
        }
    }

    private void startBridge(Context context) {
        // 防重入: 双保险只启动一次
        if (!started.compareAndSet(false, true)) {
            Logger.d("HookEntry: bridge already started, skip");
            return;
        }
        try {
            Config.loadFrom(context.getApplicationContext());
            Logger.d("Config loaded: port=" + Config.HTTP_PORT
                + " llmKey=" + (Config.LLM_API_KEY.isEmpty() ? "empty" : "set")
                + " llmProxy=" + Config.LLM_PROXY_ENABLED);
            HttpServer server = new HttpServer(context);
            server.start();
            Logger.d("Miclaw API Bridge started on 127.0.0.1:8787");
        } catch (Throwable t) {
            Logger.e("Miclaw API Bridge start failed", t);
        }
    }
}
