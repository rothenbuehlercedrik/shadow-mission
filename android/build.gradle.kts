plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val natives: Configuration by configurations.creating

android {
    namespace = "com.shadowmission.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shadowmission.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = project.version.toString()
    }

    // A fixed debug key, committed on purpose, so each new CI build installs over the last one on your phone.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.srcDirs("res")
            assets.srcDirs("../assets")
            jniLibs.srcDirs("libs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
    }
}

// libGDX ships its native .so files inside jars; unpack them where Android expects them.
val copyAndroidNatives by tasks.registering {
    val nativeJars = natives
    val libsDir = file("libs")
    inputs.files(nativeJars)
    outputs.dir(libsDir)
    doLast {
        nativeJars.files.forEach { jar ->
            val abi = jar.name.substringAfter("natives-").removeSuffix(".jar")
            copy {
                from(zipTree(jar))
                into(File(libsDir, abi))
                include("*.so")
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn(copyAndroidNatives)
}
