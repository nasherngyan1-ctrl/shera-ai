import java.util.Base64

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Automatically prepare .env and debug.keystore on CI / GitHub Actions runners
// where gitignored files are not present in a fresh checkout.
run {
  val envFile = rootProject.file(".env")
  val envExampleFile = rootProject.file(".env.example")
  if (!envFile.exists() && envExampleFile.exists()) {
    envExampleFile.copyTo(envFile, overwrite = false)
  }

  val debugKeystoreFile = rootProject.file("debug.keystore")
  val debugKeystoreBase64File = rootProject.file("debug.keystore.base64")
  if (!debugKeystoreFile.exists() && debugKeystoreBase64File.exists()) {
    try {
      val base64Content = debugKeystoreBase64File.readText().replace("\\s".toRegex(), "")
      val decodedBytes = Base64.getDecoder().decode(base64Content)
      debugKeystoreFile.writeBytes(decodedBytes)
    } catch (_: Exception) {
    }
  }
}

