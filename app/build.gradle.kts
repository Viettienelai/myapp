plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.myapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.myapp"
        minSdk = 34
        targetSdk = 36
        versionCode = 2

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Bật tính năng rút gọn code
            isShrinkResources = true // Xóa các ảnh/tài nguyên thừa không dùng
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    implementation(libs.kotlinx.coroutines.android)
}