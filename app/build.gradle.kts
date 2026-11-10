plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.mhayden123.linkcleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mhayden123.linkcleaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
