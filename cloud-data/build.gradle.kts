plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
}

android {
    namespace = "io.homeassistant.companion.android.cloud.data"
}

dependencies {
    // LocalStorage and its SharedPreferences implementation come from :common; the session
    // repository persists through them so cloud storage behaves like every other storage here.
    implementation(project(":common"))

    implementation(platform(libs.retrofit.bom))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.android)

    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
}
