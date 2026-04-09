import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Vectra orchestration API and native bridge scaffolding"

java {
    withSourcesJar()
}

val vectraInvariantReport by tasks.registering {
    group = "verification"
    description = "Gera relatório de conformidade matemática do Vectra."

    dependsOn(tasks.named("test"))

    val xmlResultsDir = layout.buildDirectory.dir("test-results/test")
    val reportFile = layout.buildDirectory.file("reports/vectra/invariants.html")

    inputs.dir(xmlResultsDir)
    outputs.file(reportFile)

    doLast {
        val xmlDir = xmlResultsDir.get().asFile
        val files = xmlDir.listFiles { file -> file.extension == "xml" }?.toList().orEmpty()
        val factory = DocumentBuilderFactory.newInstance()

        var tests = 0
        var failures = 0
        var skipped = 0
        val vectraCases = mutableListOf<Triple<String, String, String>>()

        files.forEach { xml ->
            val doc = factory.newDocumentBuilder().parse(xml)
            val testcases = doc.getElementsByTagName("testcase")
            for (index in 0 until testcases.length) {
                val node = testcases.item(index)
                val className = node.attributes?.getNamedItem("classname")?.nodeValue.orEmpty()
                if (!className.startsWith("org.gradle.vectra")) {
                    continue
                }

                tests += 1
                var status = "PASS"
                val children = node.childNodes
                for (childIndex in 0 until children.length) {
                    when (children.item(childIndex).nodeName) {
                        "failure", "error" -> {
                            failures += 1
                            status = "FAIL"
                        }
                        "skipped" -> {
                            skipped += 1
                            status = "SKIPPED"
                        }
                    }
                }

                val methodName = node.attributes?.getNamedItem("name")?.nodeValue.orEmpty()
                vectraCases += Triple(className, methodName, status)
            }
        }

        val pass = tests - failures - skipped
        val html = buildString {
            appendLine("<!doctype html>")
            appendLine("<html lang=\"pt-BR\"><head><meta charset=\"utf-8\" />")
            appendLine("<title>Vectra Invariants Compliance</title>")
            appendLine("<style>body{font-family:Arial,sans-serif;margin:24px;}table{border-collapse:collapse;width:100%;}th,td{border:1px solid #ccc;padding:8px;text-align:left;} .PASS{color:#0a7d17;} .FAIL{color:#b00020;} .SKIPPED{color:#8a6d1d;}</style>")
            appendLine("</head><body>")
            appendLine("<h1>Relatório de conformidade matemática - Vectra</h1>")
            appendLine("<p>Gerado em: ${Instant.now()}</p>")
            appendLine("<ul>")
            appendLine("<li>Total de verificações: $tests</li>")
            appendLine("<li>Aprovadas: $pass</li>")
            appendLine("<li>Falhas: $failures</li>")
            appendLine("<li>Ignoradas: $skipped</li>")
            appendLine("</ul>")
            appendLine("<h2>Escopo validado</h2>")
            appendLine("<ol><li>Periodicidade de fase</li><li>Limites de coerência/entropia</li><li>Estabilidade de atratores</li><li>Determinismo cross-backend com golden vectors</li><li>Equivalência numérica Q16.16 vs double</li><li>Regressão de serialização toroidal</li></ol>")
            appendLine("<h2>Resultados detalhados</h2>")
            appendLine("<table><thead><tr><th>Classe</th><th>Teste</th><th>Status</th></tr></thead><tbody>")
            vectraCases.sortedWith(compareBy({ it.first }, { it.second })).forEach { (clazz, method, status) ->
                appendLine("<tr><td>$clazz</td><td>$method</td><td class=\"$status\">$status</td></tr>")
            }
            appendLine("</tbody></table>")
            appendLine("</body></html>")
        }

        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(html)
    }
}

tasks.named("check") {
    dependsOn(vectraInvariantReport)
}
