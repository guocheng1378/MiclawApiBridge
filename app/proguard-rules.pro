-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-keep,allowoptimization,allowobfuscation class io.github.guocheng1378.miclawbridge.OldHookEntry { *; }
-keep public class io.github.guocheng1378.miclawbridge.HookEntry { *; }
