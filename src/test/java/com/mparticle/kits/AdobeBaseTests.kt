package com.mparticle.kits

import com.mparticle.MParticle
import junit.framework.Assert
import org.junit.Test
import java.util.*

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