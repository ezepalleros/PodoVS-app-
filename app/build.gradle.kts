plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.podovs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.podovs"
        minSdk = 26                 // ⬅️ Health Connect requiere 26+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ⬅️ Activo ViewBinding (recomendado)
    buildFeatures {
        viewBinding = true
    }

    // ⬅️ Java 17 para usar cómodamente java.time y APIs modernas
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Health Connect (dejé tu versión más nueva)
    implementation("androidx.health.connect:connect-client:1.2.0-alpha01")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
