package com.github.blueboxgarage.unifyfileviewer.editor

import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertTrue

class DecisionTableXlsxExportTest {
    @Test
    fun `writes decision table data to xlsx`() {
        val table = DecisionTable(
            cols = mutableListOf(
                DTColumn(name = "country", type = "EVALUATE", dataType = "STRING"),
                DTColumn(name = "status", type = "EVALUATE", dataType = "STRING"),
            ),
            rows = mutableListOf(
                DTRow(cols = mutableListOf(
                    DTCell(name = "country", value = "US"),
                    DTCell(name = "status", value = "active"),
                )),
                DTRow(cols = mutableListOf(
                    DTCell(name = "country", value = "CA"),
                    DTCell(name = "status", value = "inactive"),
                )),
            ),
        )

        val model = DecisionTableTableModel(table)
        val output = kotlin.io.path.createTempFile(prefix = "decision-table-", suffix = ".xlsx").toFile()

        try {
            model.writeToXlsx(output)
            assertTrue(output.exists())
            assertTrue(output.length() > 0)

            ZipFile(output).use { zip ->
                val xmlPayload = zip.entries().asSequence()
                    .filter { it.name.startsWith("xl/") && it.name.endsWith(".xml") }
                    .joinToString("\n") { entry ->
                        zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    }

                assertTrue(xmlPayload.contains("country"))
                assertTrue(xmlPayload.contains("status"))
                assertTrue(xmlPayload.contains("US"))
                assertTrue(xmlPayload.contains("active"))
                assertTrue(xmlPayload.contains("CA"))
                assertTrue(xmlPayload.contains("inactive"))
            }
        } finally {
            assertTrue(output.delete() || !output.exists())
        }
    }
}

