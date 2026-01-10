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

    signingConfigs {
        create("release") {
            // Quan trọng: dùng file() để lấy đường dẫn tương đối từ thư mục app
            storeFile = file("my-release-key.jks")
            storePassword = "123456" // Pass bạn đặt ở bước 1
            keyAlias = "key0"        // Alias bạn đặt ở bước 1
            keyPassword = "123456"   // Pass bạn đặt ở bước 1
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
}