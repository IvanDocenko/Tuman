plugins { java }
repositories { google(); mavenCentral() }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    implementation("com.android.tools.build:gradle:9.4.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10")
    implementation("org.ow2.asm:asm:9.8")
    testImplementation("junit:junit:4.13.2")
}
