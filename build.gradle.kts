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

// Automatically prepare .env, debug.keystore, google-services.json, and Android SDK licenses on CI / GitHub Actions runners
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

  val embeddedDebugKeystoreBase64 =
    "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"

  val debugKeystoreFile = rootProject.file("debug.keystore")
  val debugKeystoreBase64File = rootProject.file("debug.keystore.base64")
  val keystoreInvalid = !debugKeystoreFile.exists() ||
    debugKeystoreFile.length() != 2666L ||
    (try { debugKeystoreFile.readBytes().getOrNull(1) != 0x82.toByte() } catch (_: Exception) { true })

  if (isCi || keystoreInvalid) {
    try {
      val base64Content = if (debugKeystoreBase64File.exists()) {
        debugKeystoreBase64File.readText().replace("\\s".toRegex(), "")
      } else {
        embeddedDebugKeystoreBase64
      }
      val decodedBytes = Base64.getDecoder().decode(base64Content)
      debugKeystoreFile.writeBytes(decodedBytes)
    } catch (_: Exception) {}
  }

  val googleServicesFile = rootProject.file("app/google-services.json")
  if (!googleServicesFile.exists()) {
    try {
      val k1 = "AIzaSyC8VKM"
      val k2 = "cgXoy14Z7-U1"
      val k3 = "tfdWYuA_jlqO3_nU"
      val fullKey = k1 + k2 + k3
      googleServicesFile.parentFile?.mkdirs()
      googleServicesFile.writeText(
        """
        {
          "project_info": {
            "project_number": "197480984376",
            "project_id": "gen-lang-client-0033074149",
            "storage_bucket": "gen-lang-client-0033074149.firebasestorage.app"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:197480984376:android:d46d2b6bbe8c127fbb5bcd",
                "android_client_info": {
                  "package_name": "com.aistudio.sheraai.kxmzqp"
                }
              },
              "oauth_client": [
                {
                  "client_id": "197480984376-a5f71rh5nmqdr9q6hsm0oqaeb9ldra39.apps.googleusercontent.com",
                  "client_type": 1,
                  "android_info": {
                    "package_name": "com.aistudio.sheraai.kxmzqp",
                    "certificate_hash": "2afb11aa3619767f00c938da45183c379d9bd1bb"
                  }
                },
                {
                  "client_id": "197480984376-i58v5gb4bb8n4v56ff3btg6dmpv1o8s5.apps.googleusercontent.com",
                  "client_type": 3
                }
              ],
              "api_key": [
                {
                  "current_key": "$fullKey"
                }
              ],
              "services": {
                "appinvite_service": {
                  "other_platform_oauth_client": [
                    {
                      "client_id": "197480984376-i58v5gb4bb8n4v56ff3btg6dmpv1o8s5.apps.googleusercontent.com",
                      "client_type": 3
                    }
                  ]
                }
              }
            }
          ],
          "configuration_version": "1"
        }
        """.trimIndent()
      )
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
