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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Xposed API 不在 Maven Central, 使用本地 jar
    compileOnly(files("libs/api-82.jar"))
}
