plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sarrafi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sarrafi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // کلید امضای ثابت (اختیاری): اگر در گیت‌هاب ساخته شود، نسخه‌های بعدی روی نسخه‌ی قبلی نصب می‌شوند و اطلاعات پاک نمی‌شود
    val ksFile: String? = System.getenv("KS_FILE")
    if (ksFile != null) {
        signingConfigs {
            create("fixed") {
                storeFile = file(ksFile)
                storePassword = "sarrafi-ks-2026"
                keyAlias = "sarrafi"
                keyPassword = "sarrafi-ks-2026"
                storeType = "pkcs12"
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("fixed")
            }
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
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
