package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.device.DeviceDiagnosticsManager
import com.example.core.security.EncryptedDataStorageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testDeviceDiagnosticsManager() {
        val diagnosticsManager = DeviceDiagnosticsManager(context)
        val diag = diagnosticsManager.getCompleteDiagnostics()

        assertNotNull("Diagnostics object should not be null", diag)
        assertTrue("Total RAM should be greater than 0", diag.ramTotalGb > 0)
        assertNotNull("OS version should not be null", diag.osVersion)
        assertNotNull("Build number should not be null", diag.buildNumber)
        assertNotNull("Thermal status should not be null", diag.thermalStatus)
    }

    @Test
    fun testEncryptedStorageRoundTrip() {
        val cryptoManager = EncryptedDataStorageManager(context)
        val testData = "Subham_Secret_Biometric_Vector_Payload_12345"

        val encrypted = cryptoManager.encryptString(testData)
        assertNotNull("Encrypted string must not be null", encrypted)
        assertNotEquals("Encrypted string must not equal plain text", testData, encrypted)

        val decrypted = cryptoManager.decryptString(encrypted)
        assertEquals("Decrypted string must match original plain text", testData, decrypted)
    }
}
