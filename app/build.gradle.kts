plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val veloraApiBaseUrl = providers.gradleProperty("VELORA_API_BASE_URL")
    .orElse(providers.environmentVariable("VELORA_API_BASE_URL"))
    .orElse("http://10.0.2.2:8080/")
    .get()
    .let { if (it.endsWith('/')) it else "$it/" }

fun configuredValue(name: String): String = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orElse("")
    .get()

android {
    namespace = "app.velora"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.velora"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"${veloraApiBaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${configuredValue("GOOGLE_WEB_CLIENT_ID").replace("\"", "\\\"")}\"")
        buildConfigField("String", "VK_CLIENT_ID", "\"${configuredValue("VK_CLIENT_ID").replace("\"", "\\\"")}\"")
        buildConfigField("boolean", "VK_CONFIGURED", (configuredValue("VK_CLIENT_ID").isNotBlank() && configuredValue("VK_ANDROID_CLIENT_SECRET").isNotBlank()).toString())
        manifestPlaceholders["VKIDClientID"] = configuredValue("VK_CLIENT_ID")
        // VK Android SDK mobile key; never reuse the backend OAuth client secret here.
        manifestPlaceholders["VKIDClientSecret"] = configuredValue("VK_ANDROID_CLIENT_SECRET")
        manifestPlaceholders["VKIDRedirectHost"] = "vk.ru"
        manifestPlaceholders["VKIDRedirectScheme"] = "vk${configuredValue("VK_CLIENT_ID").ifBlank { "0" }}"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-compiler:2.55")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.vk.id:vkid:2.2.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt { correctErrorTypes = true }
