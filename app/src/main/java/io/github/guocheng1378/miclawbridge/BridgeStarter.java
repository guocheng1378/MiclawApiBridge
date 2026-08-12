package io.github.guocheng1378.miclawbridge;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/** 统一启动器: LibXposed 和老 Xposed 双入口共享, 防重复启动 */
public class BridgeStarter {
    private static final AtomicBoolean started = new AtomicBoolean(false);

    public static void start(Context context) {
        if (!started.compareAndSet(false, true)) {
            Logger.d("BridgeStarter: already started, skip");
            return;
        }
        try {
            Config.loadFrom(context.getApplicationContext());
            HttpServer server = new HttpServer(context);
            server.start();
            Logger.d("Miclaw API Bridge started");
        } catch (Throwable t) {
            Logger.e("Bridge start failed", t);
            started.set(false);
        }
    }
}
