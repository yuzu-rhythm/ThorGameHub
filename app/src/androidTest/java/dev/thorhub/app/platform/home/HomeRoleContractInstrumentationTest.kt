package dev.thorhub.app.platform.home

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRoleContractInstrumentationTest {
    @Test
    fun homeRoleRequestIsHandledByAndroidSystemUi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        assumeTrue("ROLE_HOME is unavailable on this Android build.", roleManager.isRoleAvailable(RoleManager.ROLE_HOME))

        val request = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        val handler = context.packageManager.resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY)

        assertNotNull("Android system UI must handle the HOME role request intent.", handler)
    }
}
