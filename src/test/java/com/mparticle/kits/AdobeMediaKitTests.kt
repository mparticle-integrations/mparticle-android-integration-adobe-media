package com.mparticle.kits


import android.app.Application
import android.content.Context
import com.adobe.marketing.mobile.Media
import com.adobe.marketing.mobile.MediaTracker
import com.adobe.marketing.mobile.MobileCore
import com.mparticle.media.events.ContentType
import com.mparticle.media.events.MediaContent
import junit.framework.Assert.*
import org.junit.Test
import org.mockito.Mockito
import java.util.*

class AdobeMediaKitTests {

    private fun getKit() = object: AdobeKit()  {
        val tracker: MediaTracker?
            get() { return super.mediaTracker }
    }

    @Test
    fun testGetName() {
        val name = getKit().getName()
        assertTrue(name != null && name.length > 0)
    }

    /**
     * Kit *should* throw an exception when they're initialized with the wrong settings.
     *
     */
    @Test
    fun testOnKitCreate() {
        var e: Exception? = null
        try {
            val kit = getKit()
            val settings = HashMap<String, String>()
            settings.put("fake setting", "fake")
            kit.onKitCreate(settings, Mockito.mock(Context::class.java))
        }catch (ex: Exception) {
            e = ex
        }
        assertNotNull(e)
    }

    @Test
    fun testClassName() {
        val factory = KitIntegrationFactory()
        val integrations = factory.getKnownIntegrations()
        val className = AdobeKit()::class.java.getName()
        assertEquals("$className not found as a known integration.",1, integrations.filterValues { it == className }.count())
    }

    @Test
    fun testMediaConfig() {
        val kit = getKit()
        val trackingServer = "launch app idea"
        val settings = mutableMapOf(AdobeKit().LAUNCH_APP_ID to trackingServer)
        val context= Mockito.mock(Context::class.java)
        Mockito.`when`(context.applicationContext).thenReturn(Mockito.mock(Application::class.java))

        kit.setKitManager(Mockito.mock(KitManagerImpl::class.java))
        Mockito.`when`(kit.kitManager.getIntegrationAttributes(Mockito.any(KitIntegration::class.java))).thenReturn(
                mapOf(AdobeKitBase.MARKETING_CLOUD_ID_KEY to "not nothing")
        )
        kit.onKitCreate(settings, context)

        assertEquals(trackingServer, MobileCore.configKey)
        assertNotNull(kit.tracker)
    }

    @Test
    fun toSecondsTest() {
        getKit().apply {
            assertEquals(1.001, 1001L.toSeconds())
        }
    }

    @Test
    fun getMediaTypeTest() {
        var mediaContent = MediaContent().apply {
            contentType = ContentType.AUDIO
        }
        getKit().apply {
            assertEquals(Media.MediaType.Audio, mediaContent.getMediaType())
        }
        mediaContent = MediaContent().apply {
            contentType = ContentType.VIDEO
        }
        getKit().apply {
            assertEquals(Media.MediaType.Video, mediaContent.getMediaType())
        }
    }

}