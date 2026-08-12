package io.github.guocheng1378.miclawbridge;

/**
 * 诊断版调试文件记录: 每次关键节点追加写入, 用于定位模块是否被框架加载/注入
 * 写入多个公共位置, 尽量保证至少一处可读
 */
public class DebugLog {
    private static final String[] PATHS = {
        "/data/local/tmp/miclaw_dbg.txt",
        "/sdcard/Download/miclaw_dbg.txt",
        "/sdcard/Android/miclaw_dbg.txt"
    };
    private static boolean inited = false;

    public static synchronized void w(String tag, String msg) {
        try {
            Logger.d("[" + tag + "] " + msg);
        } catch (Throwable ignored) {}
        String line = System.currentTimeMillis() + " [" + tag + "] " + msg + "\n";
        for (String p : PATHS) {
            try {
                java.io.File f = new java.io.File(p);
                java.io.FileWriter fw = new java.io.FileWriter(f, true);
                fw.write(line);
                fw.close();
            } catch (Throwable ignored) {}
        }
    }

    public static void clear() {
        for (String p : PATHS) {
            try {
                java.io.File f = new java.io.File(p);
                if (f.exists()) f.delete();
            } catch (Throwable ignored) {}
        }
    }
}
