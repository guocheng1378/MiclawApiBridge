package io.github.guocheng1378.miclawbridge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Root 权限检测与执行 (su)
 *
 * 模块代码注入到 com.aios.osbot 进程, su 请求以 osbot 身份发出,
 * 需在 Magisk/KernelSU 授权列表中允许 com.aios.osbot (首次会弹窗).
 */
public class RootUtil {

    /** 检测当前进程是否具有 root 权限 */
    public static boolean isRootAvailable() {
        String out = exec("id");
        return out != null && out.contains("uid=0");
    }

    /** 以 root 执行命令, 返回合并输出 (stdout+stderr); 无 root 返回 null */
    public static String exec(String command) {
        return exec(command, 10);
    }

    /** 以 root 执行命令并返回输出 */
    public static String exec(String command, int timeoutSec) {
        String[] suPaths = {"su", "/system/bin/su", "/system/xbin/su", "/vendor/bin/su"};
        for (String su : suPaths) {
            try {
                Process p = new ProcessBuilder(su, "-c", command)
                        .redirectErrorStream(true)
                        .start();
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(line);
                }
                if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                    p.destroy();
                }
                return sb.toString();
            } catch (Exception e) {
                // 尝试下一个 su 路径
            }
        }
        return null;
    }
}
