package com.monitor.app.diag

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DiagnosticLoggerTest {

    @Rule
    @JvmField
    val tmpDir = TemporaryFolder()

    @Test
    fun `log creates file and writes line`() {
        val context = mock<Context>()
        val filesDir = tmpDir.newFolder("files")
        whenever(context.filesDir).thenReturn(filesDir)

        val logger = DiagnosticLogger(context)
        logger.log("test_event", """{"key":"value"}""")

        val logFiles = logger.getLogFiles()
        assertEquals(1, logFiles.size)
        val content = logFiles[0].readText()
        assertTrue(content.contains("test_event"))
        assertTrue(content.contains("\"key\":\"value\""))
    }
}
