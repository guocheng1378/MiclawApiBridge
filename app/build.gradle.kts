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
        versionCode = 7
        versionName = "1.6.0"
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
}
