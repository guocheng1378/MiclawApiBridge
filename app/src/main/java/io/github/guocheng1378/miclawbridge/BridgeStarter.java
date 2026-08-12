package io.github.guocheng1378.miclawbridge;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/** 统一启动器: 双入口共享, 防重复启动 */
public class BridgeStarter {
    private static final AtomicBoolean started = new AtomicBoolean(false);

    public static void start(Context context) {
        if (!started.compareAndSet(false, true)) {
            DebugLog.w("BridgeStarter", "already started, skip");
            return;
        }
        try {
            DebugLog.w("BridgeStarter", "start() called, loading config...");
            Config.loadFrom(context.getApplicationContext());
            HttpServer server = new HttpServer(context);
            server.start();
            DebugLog.w("BridgeStarter", "Miclaw API Bridge started");
        } catch (Throwable t) {
            DebugLog.w("BridgeStarter", "Bridge start FAILED: " + t);
            started.set(false);
        }
    }
}
