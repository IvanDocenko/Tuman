// Plugin versions are pinned in buildSrc/build.gradle.kts, shared with Gecko instrumentation.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
}
