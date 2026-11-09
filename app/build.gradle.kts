plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bcw.classeviva"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bcw.classeviva"
        minSdk = 26
        targetSdk = 36
        versionCode = 34
        versionName = "3.3.1"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firma di debug, così la build di release si installa anche senza una chiave propria.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Le prove dell'interfaccia girano con Robolectric e hanno bisogno delle risorse (caratteri, stringhe).
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.materialkolor.mcu)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

tasks.withType<Test>().configureEach {
    jvmArgs("-Duser.timezone=Europe/Rome", "-Duser.language=it", "-Duser.country=IT")
}

// `./gradlew launchDebug`: installa la build debug e la avvia. La debug ha l'ID `com.bcw.classeviva.debug`
// (vedi applicationIdSuffix), non `com.bcw.classeviva` che è quello della release: con l'ID sbagliato
// `am start` risponde "Activity class ... does not exist".
val debugActivity = "com.bcw.classeviva.debug/com.bcw.classeviva.MainActivity"
tasks.register<Exec>("launchDebug") {
    group = "application"
    description = "Installa la build debug sul dispositivo e la avvia."
    dependsOn("installDebug")
    // sdk.dir in local.properties è scritto con i ':' e i '\' escapati (es. C\:\\Users\\...).
    val sdkDir = rootProject.file("local.properties").readLines()
        .first { it.startsWith("sdk.dir=") }.substringAfter("=")
        .replace("\\:", ":").replace("\\\\", "\\")
    val adbName = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
    commandLine(rootProject.file("$sdkDir/platform-tools/$adbName").absolutePath, "shell", "am", "start", "-n", debugActivity)
}
