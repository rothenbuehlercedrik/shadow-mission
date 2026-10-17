// Plugins live on the root build classpath so the Kotlin plugin can see the Android plugin's
// classes. The Android plugin is only loaded when the android module is part of the build.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
        if (findProject(":android") != null) {
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}

allprojects {
    version = "0.1.0"
}
