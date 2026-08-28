import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.linkshare.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.linkshare.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        val configuredSupabaseUrl = providers.gradleProperty("LINKO_SUPABASE_URL").orElse("https://pbnvssbtshvesqwhckfa.supabase.co").get()
        val configuredControlPlane = providers.gradleProperty("LINKO_CONTROL_PLANE_URL").orElse(configuredSupabaseUrl).get()
        val configuredSupabaseKey = providers.gradleProperty("LINKO_SUPABASE_PUBLISHABLE_KEY").orElse("sb_publishable_lUMjChFhCBKATMQzEpD5vg_ZdSc6Fw9").get()
        getByName("debug") {
            buildConfigField("String", "LINKO_CONTROL_PLANE_URL", "\"${configuredControlPlane.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "LINKO_SUPABASE_URL", "\"${configuredSupabaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "LINKO_SUPABASE_PUBLISHABLE_KEY", "\"${configuredSupabaseKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        getByName("release") {
            buildConfigField("String", "LINKO_CONTROL_PLANE_URL", "\"${configuredControlPlane.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "LINKO_SUPABASE_URL", "\"${configuredSupabaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            buildConfigField("String", "LINKO_SUPABASE_PUBLISHABLE_KEY", "\"${configuredSupabaseKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.16.0")
        force("androidx.core:core-ktx:1.16.0")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core:1.16.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.5.1")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.zaneschepke:hevtunnel:1.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}