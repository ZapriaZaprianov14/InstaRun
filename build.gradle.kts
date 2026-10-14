import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Code that runs inside the target project's JVM: Java 8 bytecode and no runtime dependencies.
val bootstrap = sourceSets.create("bootstrap")
val bootstrapTest = sourceSets.create("bootstrapTest") {
    compileClasspath += bootstrap.output
    runtimeClasspath += bootstrap.output
}

tasks.named<JavaCompile>(bootstrap.compileJavaTaskName) {
    options.release = 8
    // JDK 21 warns that release 8 is obsolete; targeting it is the point.
    options.compilerArgs.add("-Xlint:-options")
}

val bootstrapJar = tasks.register<Jar>("bootstrapJar") {
    archiveBaseName = "instarun-bootstrap"
    from(bootstrap.output)
}

val bootstrapTestTask = tasks.register<Test>("bootstrapTest") {
    description = "Runs the bootstrap tests. They need no IDE."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = bootstrapTest.output.classesDirs
    classpath = bootstrapTest.runtimeClasspath
}

tasks.check {
    dependsOn(bootstrapTestTask)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    // The plugin compiles against the bootstrap sources but ships them as their own jar in lib/,
    // because that jar is exactly what gets put on the launched JVM's classpath.
    compileOnly(bootstrap.output)
    runtimeOnly(files(bootstrapJar))

    testImplementation(libs.junit)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    "bootstrapCompileOnly"(libs.lombok)
    "bootstrapAnnotationProcessor"(libs.lombok)
    "bootstrapCompileOnly"(libs.annotations)
    "bootstrapTestImplementation"(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("com.intellij.java")
    }
}