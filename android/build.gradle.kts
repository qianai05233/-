import java.io.File
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion = "1.13.1"

val natives by configurations.creating

android {
    namespace = "com.mistbound.relics"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mistbound.relics"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.2-p1-fix"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        // Android 6+ 兼容：部分设备需要 legacy 打包才能正确加载 libgdx.so
        jniLibs.useLegacyPackaging = true
    }

    // 关键修复：默认 assets 目录是 src/main/assets，但本项目实际放在 android/assets
    // 之前未配置导致 APK 里完全没有 game/frames.json 等资源，启动直接 GdxRuntimeException 闪退
    sourceSets {
        getByName("main") {
            assets.srcDirs("assets")
            // jniLibs 默认就是 src/main/jniLibs，显式声明防止 AGP 版本差异
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")

    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
}

// 将 libGDX 原生库从 maven jar 解包到 jniLibs/<abi>/libgdx.so
// 修复：增加更健壮的解压逻辑 + 支持更多 ABI + 日志输出，方便排查闪退
val copyNatives by tasks.registering {
    val nativeFiles = natives
    val outDir: File = layout.projectDirectory.dir("src/main/jniLibs").asFile
    inputs.files(nativeFiles)
    outputs.dir(outDir)
    doLast {
        println(">> copyNatives: outDir=$outDir, jars=${nativeFiles.files.map { it.name }}")
        for (jar in nativeFiles.files) {
            val name = jar.name
            val abi: String? = when {
                name.contains("arm64-v8a") -> "arm64-v8a"
                name.contains("armeabi-v7a") -> "armeabi-v7a"
                name.contains("x86_64") -> "x86_64"
                name.contains("x86") -> "x86"
                else -> null
            }
            if (abi != null) {
                val targetDir = File(outDir, abi)
                targetDir.mkdirs()
                ZipFile(jar).use { zip ->
                    // gdx-platform jar 里通常直接是 libgdx.so 在根目录，也可能是带 abi 前缀
                    val candidates = listOf("libgdx.so", "$abi/libgdx.so", "lib/$abi/libgdx.so")
                    var found = false
                    for (cand in candidates) {
                        val entry = zip.getEntry(cand) ?: continue
                        zip.getInputStream(entry).use { input ->
                            File(targetDir, "libgdx.so").outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        println(">> copyNatives: extracted $cand -> $abi/libgdx.so from $name")
                        found = true
                        break
                    }
                    if (!found) {
                        // 兜底：遍历所有 .so
                        for (e in zip.entries()) {
                            if (e.name.endsWith(".so")) {
                                println(">> copyNatives: found entry ${e.name} in $name")
                                if (e.name.contains("gdx")) {
                                    zip.getInputStream(e).use { input ->
                                        File(targetDir, "libgdx.so").outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    found = true
                                    break
                                }
                            }
                        }
                    }
                    if (!found) {
                        println("!! copyNatives: WARN no libgdx.so found in $name")
                    }
                }
            } else {
                println("!! copyNatives: skip jar $name (unknown abi)")
            }
        }
        // 列出结果
        if (outDir.exists()) {
            outDir.walkTopDown().forEach { println(">> jniLibs: $it") }
        }
    }
}

tasks.preBuild {
    dependsOn(copyNatives)
}
// 确保 merge 阶段一定在 copy 之后
tasks.matching { name.contains("merge") && name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyNatives)
}
