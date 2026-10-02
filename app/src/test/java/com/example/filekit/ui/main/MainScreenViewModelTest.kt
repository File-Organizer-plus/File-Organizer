package fileorganizer.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormattingTest {

    @Test
    fun largeFilesFormatter_formatsCommonSizes() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("1 MB", formatFileSize(1024L * 1024L))
    }

    @Test
    fun duplicateFilesFormatter_formatsCommonSizes() {
        assertEquals("0 B", formatFileSizeForDuplicates(0))
        assertEquals("1 KB", formatFileSizeForDuplicates(1024))
        assertEquals("1 MB", formatFileSizeForDuplicates(1024L * 1024L))
    }
}
