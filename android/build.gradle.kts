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
        versionCode = 1
        versionName = "0.1.0-p0"
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
val copyNatives by tasks.registering {
    val nativeFiles = natives
    val outDir: File = layout.projectDirectory.dir("src/main/jniLibs").asFile
    inputs.files(nativeFiles)
    outputs.dir(outDir)
    doLast {
        for (jar in nativeFiles.files) {
            val name = jar.name
            val abi: String? = when {
                name.contains("arm64-v8a") -> "arm64-v8a"
                name.contains("armeabi-v7a") -> "armeabi-v7a"
                else -> null
            }
            if (abi != null) {
                val target = File(outDir, abi)
                target.mkdirs()
                val zip = ZipFile(jar)
                val entry = zip.getEntry("libgdx.so")
                if (entry != null) {
                    val input = zip.getInputStream(entry)
                    val output = File(target, "libgdx.so").outputStream()
                    input.copyTo(output)
                    output.close()
                    input.close()
                }
                zip.close()
            }
        }
    }
}

tasks.preBuild {
    dependsOn(copyNatives)
}
