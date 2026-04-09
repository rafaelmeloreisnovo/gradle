import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import java.util.Locale

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Vectra orchestration API and native bridge scaffolding"

java {
    withSourcesJar()
}

val sourceSets = the<SourceSetContainer>()
val performanceTest by sourceSets.creating

configurations[performanceTest.implementationConfigurationName].extendsFrom(configurations["testImplementation"])
configurations[performanceTest.runtimeOnlyConfigurationName].extendsFrom(configurations["testRuntimeOnly"])

val nativeBuildDir = layout.buildDirectory.dir("vectra-native")

fun platformKey(): String {
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
    val osKey = when {
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("win") -> "windows"
        else -> os.replace(' ', '-')
    }
    val archKey = when (arch) {
        "x86_64", "amd64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> arch.replace(' ', '-')
    }
    return "$osKey-$archKey"
}

val compileVectraPerfNativeC = tasks.register<Exec>("compileVectraPerfNativeC") {
    val out = nativeBuildDir.map { it.file("vectra-perf-c") }
    outputs.file(out)
    commandLine(
        "cc",
        "-std=c11",
        "-O3",
        "-I", "src/main/c/include",
        "src/main/c/vectra_core.c",
        "src/performanceTest/c/vectra_pulse_mix_c.c",
        "src/performanceTest/c/vectra_perf_native.c",
        "-o", out.get().asFile.absolutePath
    )
    doFirst {
        out.get().asFile.parentFile.mkdirs()
    }
}

val compileVectraPerfNativeAsm = tasks.register<Exec>("compileVectraPerfNativeAsm") {
    val out = nativeBuildDir.map { it.file("vectra-perf-asm") }
    outputs.file(out)
    commandLine(
        "cc",
        "-std=c11",
        "-O3",
        "-I", "src/main/c/include",
        "src/main/c/vectra_core.c",
        "src/main/asm/vectra_pulse.S",
        "src/performanceTest/c/vectra_perf_native.c",
        "-o", out.get().asFile.absolutePath
    )
    doFirst {
        out.get().asFile.parentFile.mkdirs()
    }
}

val vectraPerfBenchmark = tasks.register<JavaExec>("vectraPerfBenchmark") {
    group = "verification"
    description = "Executa benchmark micro/macro do Vectra e gera relatórios versionados."

    dependsOn(compileVectraPerfNativeC, compileVectraPerfNativeAsm, performanceTest.classesTaskName)

    mainClass.set("org.gradle.vectra.perf.VectraPerfRunner")
    classpath = performanceTest.runtimeClasspath

    val platform = platformKey()
    val reportsDir = layout.buildDirectory.dir("reports/vectra")
    val reportJson = reportsDir.map { it.file("perf-report-v1-$platform.json") }
    val summaryMd = reportsDir.map { it.file("perf-summary.md") }

    inputs.file(nativeBuildDir.map { it.file("vectra-perf-c") })
    inputs.file(nativeBuildDir.map { it.file("vectra-perf-asm") })
    outputs.file(reportJson)
    outputs.file(summaryMd)

    args(
        "--native-c=${nativeBuildDir.get().file("vectra-perf-c").asFile.absolutePath}",
        "--native-asm=${nativeBuildDir.get().file("vectra-perf-asm").asFile.absolutePath}",
        "--report=${reportJson.get().asFile.absolutePath}",
        "--summary=${summaryMd.get().asFile.absolutePath}",
        "--platform=$platform"
    )
}

val vectraPerfGate = tasks.register("vectraPerfGate") {
    group = "verification"
    description = "Falha o build quando há regressão relevante contra baseline por plataforma."

    dependsOn(vectraPerfBenchmark)

    doLast {
        val platform = platformKey()
        val report = layout.buildDirectory.file("reports/vectra/perf-report-v1-$platform.json").get().asFile
        val baseline = project.file("src/performanceTest/resources/baseline/$platform.properties")
        require(report.isFile) { "Relatório não encontrado: ${report.absolutePath}" }
        require(baseline.isFile) { "Baseline não encontrado para $platform: ${baseline.absolutePath}" }

        val reportText = report.readText()
        val baselineProps = java.util.Properties().apply {
            baseline.inputStream().use { load(it) }
        }

        val regressions = mutableListOf<String>()
        baselineProps.forEach { k, v ->
            val key = k.toString()
            val max = v.toString().toDouble()
            val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)")
            val actual = regex.find(reportText)?.groupValues?.get(1)?.toDouble()
                ?: error("Métrica '$key' ausente no relatório ${report.name}")
            if (actual > max) {
                regressions += "$key: actual=$actual > baseline=$max"
            }
        }

        if (regressions.isNotEmpty()) {
            throw GradleException("Regressão de performance detectada:\n${regressions.joinToString("\n")}")
        }
    }
}

tasks.named("check") {
    dependsOn(vectraPerfGate)
}