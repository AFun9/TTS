import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ONNX Runtime：作为库依赖自动下载，供 JNI 链接与运行时加载
val ONNX_VERSION = "1.23.2"
val onnxBuildDir = layout.buildDirectory.dir("onnxruntime").get().asFile
val stableEspeakDir = rootProject.file("third_party/espeak-ng")
val stablePiperDir = rootProject.file("third_party/piper-phonemize")
val targetAbis = (project.findProperty("sherpa.tts.abis") as? String)
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.ifEmpty { null }
    ?: listOf("arm64-v8a")

val prepareOnnxRuntime by tasks.registering {
    group = "build"
    description = "从 AAR 解出 .so 并下载头文件，供 CMake 链接 ONNX Runtime"
    outputs.dir(onnxBuildDir)
    doLast {
        onnxBuildDir.mkdirs()
        val onnxPath = onnxBuildDir.absolutePath
        val libDir = file("$onnxPath/lib")
        val includeDir = file("$onnxPath/include")

        // 1) 从 Maven 依赖的 AAR 解出 jni/*.so -> lib/<abi>/
        val config = configurations.detachedConfiguration(
            dependencies.create("com.microsoft.onnxruntime:onnxruntime-android:$ONNX_VERSION")
        )
        config.isTransitive = false
        val aar = config.resolve().single()
        copy {
            from(zipTree(aar)) {
                include("jni/**")
            }
            into(onnxBuildDir)
        }
        file("$onnxPath/jni").renameTo(libDir)

        // 2) 头文件：未设置 onnxruntime.include.root 时才下载并解压；已设置则用你手动放的目录，不碰 zip
        if (project.findProperty("onnxruntime.include.root") == null &&
            (!includeDir.exists() || includeDir.list()?.isEmpty() != false)) {
            val zipFile = file("$buildDir/onnxruntime-$ONNX_VERSION.zip")
            if (!zipFile.exists() || zipFile.length() < 4 || zipFile.readBytes().take(2) != listOf(0x50.toByte(), 0x4B.toByte())) {
                if (zipFile.exists()) zipFile.delete()
                val urls = listOf(
                    "https://codeload.github.com/microsoft/onnxruntime/zip/refs/tags/v$ONNX_VERSION",
                    "https://github.com/microsoft/onnxruntime/archive/refs/tags/v$ONNX_VERSION.zip"
                )
                var ok = false
                for (zipUrl in urls) {
                    try {
                        (URI(zipUrl).toURL().openConnection() as HttpURLConnection).apply {
                            setRequestProperty("User-Agent", "Gradle-ONNX")
                            connectTimeout = 30_000
                            readTimeout = 60_000
                            instanceFollowRedirects = true
                            connect()
                            if (responseCode in 200..299) {
                                inputStream.use { input ->
                                    zipFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                if (zipFile.length() >= 4 && zipFile.readBytes().take(2) == listOf(0x50.toByte(), 0x4B.toByte())) {
                                    ok = true
                                }
                            }
                        }
                    } catch (_: Exception) { }
                    if (ok) break
                    if (zipFile.exists()) zipFile.delete()
                }
                if (!ok || !zipFile.exists() || zipFile.readBytes().take(2) != listOf(0x50.toByte(), 0x4B.toByte())) {
                    throw GradleException("下载 ONNX Runtime 源码 zip 失败。可设置 onnxruntime.include.root 指向本地 include（如 build/onnxruntime-linux-x64-1.23.2）跳过下载。")
                }
            }
            // 解压前再次校验，避免残留的无效文件导致 zipTree 报错
            if (zipFile.readBytes().take(2) != listOf(0x50.toByte(), 0x4B.toByte())) {
                zipFile.delete()
                throw GradleException("$zipFile 不是有效 ZIP。请删除该文件后重试，或在 gradle.properties 中设置 onnxruntime.include.root=../build/onnxruntime-linux-x64-1.23.2 使用本地头文件。")
            }
            copy {
                from(zipTree(zipFile)) {
                    include("*/include/**")
                    includeEmptyDirs = true
                    eachFile { path = path.substringAfter('/') }
                }
                into(onnxBuildDir)
            }
        }
    }
}

val freezePhonemizeDeps by tasks.registering {
    group = "build setup"
    description = "将 .cxx 中已下载的 espeak-ng/piper-phonemize 固化到 android/third_party"
    doLast {
        fun latestDepSource(depName: String): File {
            val marker = fileTree(project.file(".cxx")) {
                include("**/_deps/$depName/CMakeLists.txt")
            }.files.maxByOrNull { it.lastModified() }
                ?: throw GradleException("未在 app/.cxx 中找到 $depName。请先成功执行一次 :app:assembleDebug。")
            return marker.parentFile
        }

        fun copyTree(src: File, dst: File) {
            if (!src.exists()) {
                throw GradleException("目录不存在: ${src.absolutePath}")
            }
            if (dst.exists()) dst.deleteRecursively()
            dst.parentFile?.mkdirs()
            copy {
                from(src)
                into(dst)
            }
        }

        val espeakSrc = latestDepSource("espeak_ng-src")
        val piperSrc = latestDepSource("piper_phonemize-src")

        copyTree(espeakSrc, stableEspeakDir)
        copyTree(piperSrc, stablePiperDir)

        logger.lifecycle("Frozen espeak-ng => ${stableEspeakDir.absolutePath}")
        logger.lifecycle("Frozen piper-phonemize => ${stablePiperDir.absolutePath}")
        logger.lifecycle("建议在 gradle.properties 配置:")
        logger.lifecycle("sherpa.tts.espeakNg.sourceDir=../third_party/espeak-ng")
        logger.lifecycle("sherpa.tts.piperPhonemize.sourceDir=../third_party/piper-phonemize")
    }
}

android {
    namespace = "com.k2fsa.sherpa.tts"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.k2fsa.sherpa.tts"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                if (project.findProperty("sherpa.tts.enableEspeakNg") == "true") {
                    arguments += "-DSHERPA_TTS_ENABLE_ESPEAK_NG=ON"
                }
                val espeakNgSourceDir = project.findProperty("sherpa.tts.espeakNg.sourceDir") as? String
                val espeakNgZip = project.findProperty("sherpa.tts.espeakNg.zip") as? String
                val piperSourceDir = project.findProperty("sherpa.tts.piperPhonemize.sourceDir") as? String
                val piperZip = project.findProperty("sherpa.tts.piperPhonemize.zip") as? String
                if (!espeakNgSourceDir.isNullOrBlank()) {
                    arguments += "-DSHERPA_TTS_ESPEAK_NG_SOURCE_DIR=${project.file(espeakNgSourceDir).absolutePath}"
                } else if (stableEspeakDir.exists()) {
                    arguments += "-DSHERPA_TTS_ESPEAK_NG_SOURCE_DIR=${stableEspeakDir.absolutePath}"
                }
                if (!espeakNgZip.isNullOrBlank()) {
                    arguments += "-DSHERPA_TTS_ESPEAK_NG_ARCHIVE=${project.file(espeakNgZip).absolutePath}"
                }
                if (!piperSourceDir.isNullOrBlank()) {
                    arguments += "-DSHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR=${project.file(piperSourceDir).absolutePath}"
                } else if (stablePiperDir.exists()) {
                    arguments += "-DSHERPA_TTS_PIPER_PHONEMIZE_SOURCE_DIR=${stablePiperDir.absolutePath}"
                }
                if (!piperZip.isNullOrBlank()) {
                    arguments += "-DSHERPA_TTS_PIPER_PHONEMIZE_ARCHIVE=${project.file(piperZip).absolutePath}"
                }
                // 头文件：onnxruntime.include.root（可选，如手动放的 Linux 预编译包路径）或自动准备的目录
                // 库：始终用 AAR 解出的 Android .so（在 onnxBuildDir/lib），不用 Linux 的 .so
                val includeRoot = project.findProperty("onnxruntime.include.root") as? String
                val root = project.findProperty("onnxruntime.root") as? String
                if (!includeRoot.isNullOrBlank()) {
                    val includeDir = project.file(includeRoot).absolutePath
                    arguments += "-DONNXRUNTIME_INCLUDE_DIR=$includeDir/include"
                    arguments += "-DONNXRUNTIME_LIB_DIR=${onnxBuildDir.absolutePath}/lib"
                } else if (!root.isNullOrBlank()) {
                    arguments += "-DONNXRUNTIME_ROOT=${project.file(root).absolutePath}"
                } else {
                    arguments += "-DONNXRUNTIME_ROOT=${onnxBuildDir.absolutePath}"
                }
            }
        }
        ndk {
            abiFilters += targetAbis
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../../models")
            // 未配置 onnxruntime.root 时，.so 由 implementation 的 AAR 提供；配置了则用本地 lib
            val onnxRoot = project.findProperty("onnxruntime.root") as? String
            if (!onnxRoot.isNullOrBlank()) {
                val onnxLib = File(onnxRoot, "lib")
                if (onnxLib.exists()) jniLibs.srcDirs(onnxLib.absolutePath)
            }
        }
    }
}

// 未配置 onnxruntime.root 时，先准备好 ONNX 再跑 CMake（实际执行的是 buildCMakeDebug 等）
tasks.configureEach {
    if ((name.startsWith("externalNativeBuild") || name.startsWith("buildCMake")) &&
        project.findProperty("onnxruntime.root") == null) {
        dependsOn(prepareOnnxRuntime)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // ONNX Runtime：Gradle 自动下载 AAR，.so 会打进 APK；prepareOnnxRuntime 提供头文件与 lib 供 CMake 链接
    implementation("com.microsoft.onnxruntime:onnxruntime-android:$ONNX_VERSION")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
}
