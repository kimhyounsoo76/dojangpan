plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.dojangpan.bokseup"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.dojangpan.bokseup"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // 항상 같은 열쇠로 서명해야 새 APK를 덮어 설치할 수 있다
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore.jks")
            storePassword = "dojangpan"
            keyAlias = "dojangpan"
            keyPassword = "dojangpan"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
