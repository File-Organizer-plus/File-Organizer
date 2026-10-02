package fileorganizer.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @Test
    fun applicationPackageName_isCurrentPackage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("fileorganizer.app", context.packageName)
    }
}
