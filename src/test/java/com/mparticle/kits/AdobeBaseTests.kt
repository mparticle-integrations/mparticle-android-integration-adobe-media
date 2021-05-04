package com.mparticle.kits

import android.app.Application
import android.content.Context
import com.mparticle.MParticle
import junit.framework.Assert
import org.junit.Test
import org.mockito.Mockito
import java.util.*
import kotlin.jvm.Throws

class AdobeBaseTests {

    private fun getKit() = AdobeKit()

    @Test
    @Throws(Exception::class)
    fun testBuildUrl() {
        val url = getKit().encodeIds("<MCID>", "<ORG ID>", "<BLOB>", "<REGION>", "<PUSH TOKEN>", "<GAID>", HashMap<MParticle.IdentityType, String>())
        val testUrl1 = "d_mid=<MCID>&d_ver=2&d_orgid=<ORG ID>&d_cid=20914%01<GAID>&d_cid=20919%01<PUSH TOKEN>&dcs_region=<REGION>&d_blob=<BLOB>&d_ptfm=android"
        assertEqualUnorderedUrlParams(url, testUrl1)

        val userIdentities = HashMap<MParticle.IdentityType, String>()
        userIdentities[MParticle.IdentityType.CustomerId] = "<CUSTOMER ID>"
        userIdentities[MParticle.IdentityType.Email] = "<EMAIL>"
        val url2 = getKit().encodeIds("<MCID>", "<ORG ID>", "<BLOB>", "<REGION>", "<PUSH TOKEN>", "<GAID>", userIdentities)
        val testUrls2 = "d_mid=<MCID>&d_ver=2&d_orgid=<ORG ID>&d_cid=20914%01<GAID>&d_cid=20919%01<PUSH TOKEN>&dcs_region=<REGION>&d_blob=<BLOB>&d_ptfm=android&d_cid_ic=customerid%01<CUSTOMER ID>&d_cid_ic=email%01<EMAIL>"
        assertEqualUnorderedUrlParams(url2, testUrls2)
    }

    @Test
    @Throws(java.lang.Exception::class)
    fun testGetUrlInstance() {
        val kit = getKit()
        kit.kitManager = Mockito.mock(KitManagerImpl::class.java)
        val integrationAttributes: MutableMap<String, String> = HashMap()
        integrationAttributes[AdobeKitBase.MARKETING_CLOUD_ID_KEY] = "foo"
        Mockito.`when`(kit.kitManager.getIntegrationAttributes(Mockito.any(KitIntegration::class.java))).thenReturn(integrationAttributes)
        val settings = mutableMapOf<String, String>()
        settings[AdobeKitBase.AUDIENCE_MANAGER_SERVER] = "some.random.url"
        settings[kit.LAUNCH_APP_ID] = "bar"
        val context = Mockito.mock(Application::class.java);
        Mockito.`when`(context.applicationContext).thenReturn(context)
        kit.onKitCreate(settings, context)
        val url = kit.url
        org.junit.Assert.assertEquals(url, "some.random.url")
    }

    private fun assertEqualUnorderedUrlParams(url1: String?, url2: String?) {
        if (url1 == null && url2 == null) {
            return
        }
        val url1Split = Arrays.asList(*url1!!.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        val url2Split = Arrays.asList(*url2!!.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        Assert.assertEquals(url1Split.size.toLong(), url2Split.size.toLong())
        Collections.sort(url1Split)
        Collections.sort(url2Split)
        for (i in url1Split.indices) {
            if (url1Split[i] != url2Split[i])
                Assert.assertTrue(false)
        }
    }
}