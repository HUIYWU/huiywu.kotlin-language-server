import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("application")
    alias(libs.plugins.com.github.jk1.tcdeps)
    alias(libs.plugins.com.jaredsburrows.license)
    id("kotlin-language-server.publishing-conventions")
    id("kotlin-language-server.distribution-conventions")
    id("kotlin-language-server.kotlin-conventions")
}

val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

val serverMainClassName = "org.javacs.kt.MainKt"
val applicationName = "kotlin-language-server"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Kotlin"
    applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    applicationDistribution.into("bin") {
        filePermissions { unix(755) }
    }
}

repositories {
    maven(url = "https://repo.gradle.org/gradle/libs-releases")
    maven { url = uri("$projectDir/lib") }
    maven(uri("$projectDir/lib"))
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    // dependencies are constrained to versions defined
    // in /platform/build.gradle.kts
    implementation(platform(project(":platform")))
    annotationProcessor(platform(project(":platform")))

    implementation(project(":shared"))

    implementation(libs.org.eclipse.lsp4j.lsp4j)
    implementation(libs.org.eclipse.lsp4j.jsonrpc)

    implementation(kotlin("compiler"))
    implementation(kotlin("scripting-compiler"))
    implementation(kotlin("scripting-jvm-host-unshaded"))
    implementation(kotlin("sam-with-receiver-compiler-plugin"))
    implementation(kotlin("reflect"))
    implementation(libs.org.jetbrains.fernflower)
    implementation(libs.org.jetbrains.exposed.core)
    implementation(libs.org.jetbrains.exposed.dao)
    implementation(libs.org.jetbrains.exposed.jdbc)
    implementation(libs.com.h2database.h2)
    implementation(libs.com.github.fwcd.ktfmt)
    implementation(libs.com.beust.jcommander)
    implementation(libs.org.xerial.sqlite.jdbc)

    testImplementation(libs.hamcrest.all)
    testImplementation(libs.junit.junit)
    testImplementation(libs.org.openjdk.jmh.core)

    // See
    // https://github.com/JetBrains/kotlin/blob/65b0a5f90328f4b9addd3a10c6f24f3037482276/libraries/examples/scripting/jvm-embeddable-host/build.gradle.kts#L8
    compileOnly(kotlin("scripting-jvm-host"))
    testCompileOnly(kotlin("scripting-jvm-host"))

    annotationProcessor(libs.org.openjdk.jmh.generator.annprocess)
}

configurations.forEach { config -> config.resolutionStrategy { preferProjectModules() } }

val androidCompatCommentNeedle = "#       and KOTLIN_LANGUAGE_SERVER_OPTS) rely on word-splitting, this is performed explicitly;\n#       see the in-line comments for details."
val androidCompatCommentReplacement = "#       and KOTLIN_LANGUAGE_SERVER_OPTS) rely on word-splitting, this is performed explicitly;\n#       see the in-line comments for details.\n#\n#       AndroidCodeStudio compatibility note:\n#       If KOTLIN_LANGUAGE_SERVER_PREDEFINED_CLASSPATH or KOTLIN_LSP_CLASSPATH is set,\n#       the launcher prepends matching -DkotlinLanguageServer.* system properties before\n#       evaluating the traditional *_OPTS environment variables."
val androidCompatDefaultJvmMarker = "DEFAULT_JVM_OPTS="
val androidCompatDefaultJvmReplacement = """DEFAULT_JVM_OPTS=""
ANDROID_KLS_OPTS=""

append_android_kls_opt () {
    if [ -z "${'$'}ANDROID_KLS_OPTS" ]; then
        ANDROID_KLS_OPTS="\"${'$'}1\""
    else
        ANDROID_KLS_OPTS="${'$'}ANDROID_KLS_OPTS \"${'$'}1\""
    fi
}

if [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_SKIP_CLASSPATH_RESOLUTION" ]; then
    append_android_kls_opt "-DkotlinLanguageServer.skipClasspathResolution=${'$'}KOTLIN_LANGUAGE_SERVER_SKIP_CLASSPATH_RESOLUTION"
elif [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_PREDEFINED_CLASSPATH" ] || [ -n "${'$'}KOTLIN_LSP_CLASSPATH" ]; then
    append_android_kls_opt "-DkotlinLanguageServer.skipClasspathResolution=true"
fi

if [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_PREDEFINED_CLASSPATH" ]; then
    append_android_kls_opt "-DkotlinLanguageServer.predefinedClasspath=${'$'}KOTLIN_LANGUAGE_SERVER_PREDEFINED_CLASSPATH"
elif [ -n "${'$'}KOTLIN_LSP_CLASSPATH" ]; then
    append_android_kls_opt "-DkotlinLanguageServer.predefinedClasspath=${'$'}KOTLIN_LSP_CLASSPATH"
fi

if [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_PATH" ]; then
    append_android_kls_opt "-Dorg.sqlite.lib.path=${'$'}KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_PATH"
fi

if [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_NAME" ]; then
    append_android_kls_opt "-Dorg.sqlite.lib.name=${'$'}KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_NAME"
fi"""

fun patchAndroidCodeStudioLauncher(scriptFile: File) {
    if (!scriptFile.exists()) return
    var text = scriptFile.readText()

    val marker = "AndroidCodeStudio compatibility note:"
    if (!text.contains(marker)) {
        require(text.contains(androidCompatCommentNeedle)) {
            "Failed to patch kotlin-language-server comment block for AndroidCodeStudio compatibility"
        }
        text = text.replace(androidCompatCommentNeedle, androidCompatCommentReplacement)
    }

    if (!text.contains("ANDROID_KLS_OPTS=\"\"")) {
        val markerIndex = text.indexOf(androidCompatDefaultJvmMarker)
        require(markerIndex >= 0) {
            "Failed to patch kotlin-language-server DEFAULT_JVM_OPTS block for AndroidCodeStudio compatibility"
        }
        val lineStart = text.lastIndexOf('\n', markerIndex).let { if (it >= 0) it + 1 else 0 }
        val lineEnd = text.indexOf('\n', markerIndex).let { if (it >= 0) it else text.length }
        text = text.removeRange(lineStart, lineEnd)
        text = text.substring(0, lineStart) + androidCompatDefaultJvmReplacement + text.substring(lineStart)
    }

    if (!text.contains("-Dorg.sqlite.lib.name=\$KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_NAME")) {
        val sqliteNameBlock = """
if [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_NAME" ]; then
    append_android_kls_opt "-Dorg.sqlite.lib.name=${'$'}KOTLIN_LANGUAGE_SERVER_SQLITE_LIB_NAME"
fi"""
        val sqliteNameIndex = text.indexOf(sqliteNameBlock)
        require(sqliteNameIndex >= 0) {
            "Failed to locate kotlin-language-server sqlite env block while patching AndroidCodeStudio compatibility"
        }
        val insertion = """

if [ -n "${'$'}KOTLIN_LANGUAGE_SERVER_OPTS" ]; then
    KOTLIN_LANGUAGE_SERVER_OPTS=${'$'}(printf '%s' "${'$'}KOTLIN_LANGUAGE_SERVER_OPTS" | tr -d '\\n')
fi"""
        text = text.substring(0, sqliteNameIndex + sqliteNameBlock.length) + insertion + text.substring(sqliteNameIndex + sqliteNameBlock.length)
    }

    scriptFile.writeText(text)
}

tasks.startScripts {
    applicationName = "kotlin-language-server"
    doLast {
        val originalScript = unixScript.readText()
        val debugDir = layout.buildDirectory.dir("tmp/acs-startscripts").get().asFile.apply { mkdirs() }
        File(debugDir, "kotlin-language-server.before-patch.sh").writeText(originalScript)
        patchAndroidCodeStudioLauncher(unixScript)
        File(debugDir, "kotlin-language-server.after-patch.sh").writeText(unixScript.readText())
    }
}

tasks.register<Exec>("fixFilePermissions") {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.

    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine(
            "sh",
            "-c",
            "chmod +x \"${tasks.installDist.get().destinationDir}/bin/kotlin-language-server\""
    )
}

tasks.register<JavaExec>("debugRun") {
    mainClass.set(serverMainClassName)
    classpath(sourceSets.main.get().runtimeClasspath)
    standardInput = System.`in`

    jvmArgs(debugArgs)
    doLast { println("Using debug port $debugPort") }
}

tasks.register<CreateStartScripts>("debugStartScripts") {
    applicationName = "kotlin-language-server"
    mainClass.set(serverMainClassName)
    outputDir = tasks.installDist.get().destinationDir.toPath().resolve("bin").toFile()
    classpath = tasks.startScripts.get().classpath
    defaultJvmOpts = listOf(debugArgs)
}

tasks.register<Sync>("installDebugDist") {
    dependsOn("installDist")
    finalizedBy("debugStartScripts")
}

tasks.withType<Test>() {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.installDist { finalizedBy("fixFilePermissions") }

tasks.build { finalizedBy("installDist") }
