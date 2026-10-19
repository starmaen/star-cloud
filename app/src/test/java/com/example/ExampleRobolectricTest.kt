package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ContactItem
import com.example.service.ContactsBackupHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context matches Star Cloud`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Star Cloud", appName)
  }

  @Test
  fun `verify vCard export formatting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper = ContactsBackupHelper(context)
    val contacts = listOf(
      ContactItem("1", "Ahmad Syria", "+963944111222", "starsyria2500@gmail.com", true)
    )
    val vcard = helper.exportToVCard(contacts)
    assertTrue(vcard.contains("BEGIN:VCARD"))
    assertTrue(vcard.contains("FN:Ahmad Syria"))
    assertTrue(vcard.contains("TEL;TYPE=CELL:+963944111222"))
    assertTrue(vcard.contains("END:VCARD"))
  }
}
