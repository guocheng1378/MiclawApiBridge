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

    signingConfigs {
        // 固定签名: 保证所有构建(本地/GitHub Actions)签名一致, 可覆盖安装
        // 密钥文件已入库 (keystore/miclaw.jks), 个人模块使用; 生产项目建议改用 GitHub Secrets
        create("release") {
            storeFile = file("keystore/miclaw.jks")
            storePassword = "miclaw123"
            keyAlias = "miclaw"
            keyPassword = "miclaw123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
