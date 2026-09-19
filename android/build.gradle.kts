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
    val outDir = layout.projectDirectory.dir("src/main/jniLibs")
    inputs.files(nativeFiles)
    outputs.dir(outDir)
    doLast {
        nativeFiles.files.forEach { jar ->
            val abi = when {
                jar.name.contains("arm64-v8a") -> "arm64-v8a"
                jar.name.contains("armeabi-v7a") -> "armeabi-v7a"
                else -> null
            }
            if (abi != null) {
                val target = outDir.dir(abi).asFile
                target.mkdirs()
                ZipFile(jar).use { zip ->
                    zip.getEntry("libgdx.so")?.let { entry ->
                        zip.getInputStream(entry).use { input ->
                            java.io.File(target, "libgdx.so").outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
    }
}

tasks.preBuild {
    dependsOn(copyNatives)
}
