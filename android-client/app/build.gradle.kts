plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}
val portalUrl = providers.gradleProperty("portalUrl").orElse("https://bird-theatre-h7xq.onrender.com")
android {
  namespace = "ca.gfh.birdtheatre"
  compileSdk = 36
  defaultConfig {
    applicationId = "ca.gfh.birdtheatre"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    buildConfigField("String", "PORTAL_URL", "\"${portalUrl.get()}\"")
  }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}
dependencies {
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.compose.material3:material3:1.4.0")
  implementation("androidx.compose.ui:ui:1.10.4")
  implementation("androidx.compose.ui:ui-tooling-preview:1.10.4")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  debugImplementation("androidx.compose.ui:ui-tooling:1.10.4")
}
