import java.util.Properties
import java.io.File

plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization) }

val versionFile=rootProject.file("version.properties")
val appVersion=Properties().apply{require(versionFile.isFile){"version.properties não encontrado."};versionFile.inputStream().use(::load)}
val configuredVersionCode=appVersion.getProperty("VERSION_CODE")?.toIntOrNull()?.takeIf{it>0}?:error("VERSION_CODE deve ser inteiro positivo.")
val configuredVersionName=appVersion.getProperty("VERSION_NAME")?.takeIf{it.matches(Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$"))}?:error("VERSION_NAME deve seguir versionamento semântico.")
val signingValues=listOf("BRASA_TV_KEYSTORE_PATH","BRASA_TV_KEYSTORE_PASSWORD","BRASA_TV_KEY_ALIAS","BRASA_TV_KEY_PASSWORD").associateWith{System.getenv(it).orEmpty()}
val releaseSigningReady=signingValues.values.all(String::isNotBlank)
val publicCertificateFingerprint=rootProject.file("release-certificate.sha256").takeIf{it.isFile}?.readText()?.trim()?.uppercase().orEmpty()

android {
    namespace = "com.brasa.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.brasa.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs { if(releaseSigningReady)create("release"){storeFile=file(signingValues.getValue("BRASA_TV_KEYSTORE_PATH"));storePassword=signingValues.getValue("BRASA_TV_KEYSTORE_PASSWORD");keyAlias=signingValues.getValue("BRASA_TV_KEY_ALIAS");keyPassword=signingValues.getValue("BRASA_TV_KEY_PASSWORD");enableV1Signing=true;enableV2Signing=true;enableV3Signing=true} }
    buildTypes {
        debug { buildConfigField("String","RELEASE_CERTIFICATE_SHA256","\"\"") }
        release { isMinifyEnabled = true; isShrinkResources=true;proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro");if(releaseSigningReady)signingConfig=signingConfigs.getByName("release");buildConfigField("String","RELEASE_CERTIFICATE_SHA256","\"$publicCertificateFingerprint\"") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

val validateReleaseSigning=tasks.register("validateReleaseSigning"){
    val signingReady=releaseSigningReady
    val keyStorePath=signingValues.getValue("BRASA_TV_KEYSTORE_PATH")
    val certificateFingerprint=publicCertificateFingerprint
    doLast{require(signingReady){"Build release exige BRASA_TV_KEYSTORE_PATH, BRASA_TV_KEYSTORE_PASSWORD, BRASA_TV_KEY_ALIAS e BRASA_TV_KEY_PASSWORD."};require(File(keyStorePath).isFile){"Keystore release não encontrado."};require(certificateFingerprint.matches(Regex("^[A-F0-9]{64}$"))){"release-certificate.sha256 deve conter o fingerprint SHA-256 público sem separadores."}}
}
tasks.matching{it.name=="preReleaseBuild"}.configureEach{dependsOn(validateReleaseSigning)}

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
