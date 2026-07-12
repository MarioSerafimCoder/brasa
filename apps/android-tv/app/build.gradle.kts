plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization) }

android {
    namespace = "com.brasa.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.brasa.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(platform(libs.compose.bom)); androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling); debugImplementation(libs.compose.test.manifest)
    implementation(libs.activity.compose); implementation(libs.lifecycle.runtime); implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose); implementation(libs.tv.material)
    implementation(libs.media3.exoplayer); implementation(libs.media3.ui); implementation(libs.media3.session); implementation(libs.media3.okhttp)
    implementation(libs.datastore); implementation(libs.coroutines.android); implementation(libs.serialization.json)
    implementation(libs.okhttp); implementation(libs.okhttp.logging); implementation(libs.coil.compose); implementation(libs.coil.network)
    testImplementation(libs.junit); testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.espresso.core); androidTestImplementation(libs.compose.test.junit4)
}
