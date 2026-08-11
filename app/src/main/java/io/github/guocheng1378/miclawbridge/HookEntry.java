package io.github.guocheng1378.miclawbridge;

import android.content.Context;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * LibXposed API 102 模块入口 (最新 LSPosed 框架)
 */
public class HookEntry extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // 早期回调: 默认 ClassLoader 就绪 (API 29+), 这里不做重活
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!"com.aios.osbot".equals(param.getPackageName())) return;
        try {
            // Hook Application.attach 获取 Context, 然后启动 HTTP Bridge
            Class<?> appClass = Class.forName("android.app.Application", true, param.getClassLoader());
            Method attach = appClass.getDeclaredMethod("attach", Context.class);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                Context ctx = (Context) chain.getArg(0);
                if (ctx != null) {
                    startBridge(ctx.getApplicationContext());
                }
                return result;
            });
            Logger.d("HookEntry: hooked Application.attach for com.aios.osbot");
        } catch (Throwable t) {
            Logger.e("HookEntry: failed to hook Application.attach", t);
        }
    }

    private void startBridge(Context context) {
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
