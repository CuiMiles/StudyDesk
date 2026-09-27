plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose"); kotlin("plugin.serialization") }
android {
    namespace = "io.github.cuimiles.studydesk"
    compileSdk = 36
    defaultConfig { applicationId = "io.github.cuimiles.studydesk"; minSdk = 26; targetSdk = 36; versionCode = 8; versionName = "1.3.0-lan" }
    val lanKeystore = System.getenv("STUDYDESK_SIGNING_STORE_FILE")
    if (!lanKeystore.isNullOrBlank()) {
        signingConfigs.create("lanRelease") {
            storeFile = file(lanKeystore)
            storePassword = System.getenv("STUDYDESK_SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("STUDYDESK_SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("STUDYDESK_SIGNING_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("lanRelease")
    }
    buildFeatures { compose = true }
    androidResources { ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:CVS:thumbs.db:picasa.ini:*~:*.db-shm:*.db-wal" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.16")
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
