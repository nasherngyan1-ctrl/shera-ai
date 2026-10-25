import java.io.File
import java.util.Base64

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Automatically prepare .env, debug.keystore, and Android SDK licenses on CI / GitHub Actions runners
run {
  val isCi = System.getenv("GITHUB_ACTIONS") == "true" || System.getenv("CI") == "true"

  val envExampleFile = rootProject.file(".env.example")
  if (!envExampleFile.exists()) {
    try {
      envExampleFile.writeText("GEMINI_API_KEY=PLACEHOLDER\n")
    } catch (_: Exception) {}
  }

  val envFile = rootProject.file(".env")
  if (!envFile.exists() && envExampleFile.exists()) {
    try {
      envExampleFile.copyTo(envFile, overwrite = false)
    } catch (_: Exception) {}
  }

  val debugKeystoreFile = rootProject.file("debug.keystore")
  val debugKeystoreBase64File = rootProject.file("debug.keystore.base64")
  val keystoreInvalid = !debugKeystoreFile.exists() ||
    debugKeystoreFile.length() != 2666L ||
    (try { debugKeystoreFile.readBytes().getOrNull(1) != 0x82.toByte() } catch (_: Exception) { true })

  if ((isCi || keystoreInvalid) && debugKeystoreBase64File.exists()) {
    try {
      val base64Content = debugKeystoreBase64File.readText().replace("\\s".toRegex(), "")
      val decodedBytes = Base64.getDecoder().decode(base64Content)
      debugKeystoreFile.writeBytes(decodedBytes)
    } catch (_: Exception) {}
  }

  if (isCi) {
    // Ensure Android SDK licenses are accepted so AGP can auto-install platforms;android-36.1
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
      ?: System.getenv("ANDROID_HOME")
      ?: "/usr/local/lib/android/sdk"
    try {
      val licensesDir = File(sdkRoot, "licenses")
      licensesDir.mkdirs()
      File(licensesDir, "android-sdk-license").writeText(
        "\n8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n84831b9409646a918e30573bab4c9c91346d8abd\n"
      )
      File(licensesDir, "android-sdk-preview-license").writeText(
        "\n84831b9409646a918e30573bab4c9c91346d8abd\n504667f4c0de7af1a06de9f4b1727b84351f2910\n"
      )
    } catch (_: Exception) {}

    // Remove any legacy binary resource directories if left over from an earlier commit
    listOf(
      "app/src/main/res/font",
      "app/src/main/res/mipmap-mdpi",
      "app/src/main/res/mipmap-hdpi",
      "app/src/main/res/mipmap-xhdpi",
      "app/src/main/res/mipmap-xxhdpi",
      "app/src/main/res/mipmap-xxxhdpi"
    ).forEach { relPath ->
      try {
        rootProject.file(relPath).deleteRecursively()
      } catch (_: Exception) {}
    }
  }
}
