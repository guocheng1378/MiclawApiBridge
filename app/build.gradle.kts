plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.guocheng1378.miclawbridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.guocheng1378.miclawbridge"
        minSdk = 26
        targetSdk = 34
        versionCode = 16
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // LibXposed API 102 (最新 LSPosed 框架 API, Maven Central)
    compileOnly("io.github.libxposed:api:102.0.0")
    // 老 Xposed API 82 (双兼容入口: 适配只支持 API 82 的框架, 仅编译期)
    compileOnly(files("libs/api-82.jar"))
}
