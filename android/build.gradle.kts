plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Declared but not applied here. The app module applies it conditionally,
    // because the plugin hard-fails when google-services.json is absent and we
    // want the project to still build before Firebase is set up.
    alias(libs.plugins.google.services) apply false
}
