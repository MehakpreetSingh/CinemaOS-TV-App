plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sentry.android.gradle)
}

import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask
import java.io.File
import java.util.Properties

// Copies the per-API dex metadata (.dm) files AGP builds next to the binary ART profile into the APK's assets, so the
// in-app updater can install the next version together with its profile and have it compiled at install time.
abstract class EmbedUpdateDexMetadataTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dexMetadataDirectory: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun embed() {
        val target = outputDirectory.get().asFile.resolve("updater_dm")
        target.deleteRecursively()
        target.mkdirs()
        val source = dexMetadataDirectory.files.firstOrNull { it.resolve("dex-metadata-map.properties").exists() } ?: return
        val apiToFile = Properties().apply { source.resolve("dex-metadata-map.properties").inputStream().use(::load) }
        // Written by hand instead of Properties.store, which adds a timestamp and would make the APK non-reproducible.
        val indexLines = apiToFile.stringPropertyNames().sortedBy { it.toInt() }.map { api ->
            val relativePath = apiToFile.getProperty(api)
            val fileName = relativePath.substringBefore('/') + ".dm"
            source.resolve(relativePath).copyTo(target.resolve(fileName), overwrite = true)
            "$api=$fileName"
        }
        target.resolve("index.properties").writeText(indexLines.joinToString("\n", postfix = "\n"))
    }
}

fun parseBooleanProperty(value: String?): Boolean {
    val normalized = value?.trim()?.lowercase() ?: return false
    return normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on"
}

fun resolveProperty(dev: Properties, local: Properties, key: String, fallback: String = ""): String {
    return dev.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: local.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: fallback
}

fun buildConfigString(value: String): String {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun cmakePath(path: String): String {
    if (path.isBlank()) return ""
    val file = File(path)
    val resolved = if (file.isAbsolute) file else rootProject.file(path)
    return resolved.absolutePath.replace("\\", "/")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

val devProperties = Properties().apply {
    val devPropertiesFile = rootProject.file("local.dev.properties")
    if (devPropertiesFile.exists()) {
        load(devPropertiesFile.inputStream())
    }
}

val enableDoviNative = parseBooleanProperty(
    resolveProperty(devProperties, localProperties, "DOVI_NATIVE_ENABLED")
)
val doviExtractorHookReady = parseBooleanProperty(
    resolveProperty(devProperties, localProperties, "DOVI_EXTRACTOR_HOOK_READY")
)
val doviEnableRealLink = parseBooleanProperty(
    resolveProperty(devProperties, localProperties, "DOVI_ENABLE_REAL_LINK")
)
val doviStaticLibPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_STATIC_LIB")
val doviIncludeDirPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_INCLUDE_DIR")
val doviPrebuiltRootPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_PREBUILT_ROOT")
val sponsorNames = resolveProperty(devProperties, localProperties, "SPONSOR_NAMES", "ragmehos.")
val sentryDsn = providers.environmentVariable("SENTRY_DSN").orNull?.trim()?.takeIf { it.isNotBlank() }
    ?: resolveProperty(devProperties, localProperties, "SENTRY_DSN")
val sentryAuthToken = providers.environmentVariable("SENTRY_AUTH_TOKEN").orNull?.trim()?.takeIf { it.isNotBlank() }
    ?: resolveProperty(devProperties, localProperties, "SENTRY_AUTH_TOKEN").takeIf { it.isNotBlank() }
val sentryOrg = providers.environmentVariable("SENTRY_ORG").orNull?.trim()?.takeIf { it.isNotBlank() }
    ?: resolveProperty(devProperties, localProperties, "SENTRY_ORG").takeIf { it.isNotBlank() }
val sentryProject = providers.environmentVariable("SENTRY_PROJECT").orNull?.trim()?.takeIf { it.isNotBlank() }
    ?: resolveProperty(devProperties, localProperties, "SENTRY_PROJECT").takeIf { it.isNotBlank() }
val sentryMappingUploadEnabled = sentryAuthToken != null && sentryOrg != null && sentryProject != null

fun env(name: String): String? = providers.environmentVariable(name).orNull

fun truthy(value: String?): Boolean {
    return value.equals("true", ignoreCase = true) ||
        value.equals("1", ignoreCase = true) ||
        value.equals("yes", ignoreCase = true)
}

val buildingAppBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
val useDebugReleaseSigning = env("CI_USE_DEBUG_SIGNING").equals("true", ignoreCase = true)
val useLocalFfmpegDecoder = truthy(
    providers.gradleProperty("useLocalFfmpegDecoder").orNull
        ?: env("USE_LOCAL_FFMPEG_DECODER")
        ?: localProperties.getProperty("USE_LOCAL_FFMPEG_DECODER")
)
val releaseStoreFilePath = env("NUVIO_RELEASE_STORE_FILE")
    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_FILE")
val releaseKeyAliasValue = env("NUVIO_RELEASE_KEY_ALIAS")
    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_ALIAS", "nuviotv")
val releaseKeyPasswordValue = env("NUVIO_RELEASE_KEY_PASSWORD")
    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_PASSWORD", "815787")
val releaseStorePasswordValue = env("NUVIO_RELEASE_STORE_PASSWORD")
    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_PASSWORD", "815787")

android {
    namespace = "com.nuvio.tv"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.nuvio.tv"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 24
        targetSdk = 36
