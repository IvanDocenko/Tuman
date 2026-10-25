plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.tuman.browser"
    compileSdk = 37
    compileSdkMinor = 2
    defaultConfig {
        applicationId = "com.tuman.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = 20104
        versionName = "2.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    lint { abortOnError = true; checkReleaseBuilds = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}
// Fail resolution if a future dependency introduces a known analytics SDK.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        val forbidden = listOf("com.google.firebase", "com.google.android.gms:play-services-measurement",
            "com.crashlytics", "io.fabric", "com.amplitude", "com.mixpanel", "io.sentry")
        val coordinate = "${requested.group}:${requested.name}"
        check(forbidden.none { coordinate.startsWith(it) }) {
            "Tuman privacy policy rejects dependency $coordinate"
        }
    }
}
dependencies {
    implementation("org.mozilla.geckoview:geckoview-nightly:160.0.20261022084508")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.instrumentation.transformClassesWith(
            com.tuman.build.GeckoPermissionVisitorFactory::class.java,
            com.android.build.api.instrumentation.InstrumentationScope.ALL
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            com.android.build.api.instrumentation.FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )
    }
}
