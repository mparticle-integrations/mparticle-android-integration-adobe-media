package com.mparticle.kits

import android.content.Context
import com.mparticle.kits.KitIntegration
import com.mparticle.kits.KitIntegration.AttributeListener
import com.mparticle.kits.KitIntegration.PushListener
import com.mparticle.kits.KitIntegration.ApplicationStateListener
import com.mparticle.kits.AdobeKitBase
import kotlin.Throws
import java.lang.IllegalArgumentException
import com.mparticle.kits.ReportingMessage
import com.mparticle.MParticle.IdentityType
import android.content.Intent
import java.lang.Runnable
import java.net.URL
import java.net.HttpURLConnection
import com.mparticle.internal.MPUtility
import java.io.IOException
import com.mparticle.internal.MPUtility.AdIdInfo
import com.mparticle.kits.AdobeKitBase.UrlBuilder
import com.mparticle.kits.AdobeApi
import com.mparticle.kits.KitUtils
import org.json.JSONException
import android.content.SharedPreferences
import org.json.JSONObject
import java.lang.StringBuilder

abstract class AdobeKitBase : KitIntegration(), AttributeListener, PushListener,
    ApplicationStateListener {
    private val dVer = "2"
    var url: String? = DEFAULT_URL
    private var mOrgId: String? = null
    private var requestInProgress = false
    @Throws(IllegalArgumentException::class)
    override fun onKitCreate(map: Map<String, String>, context: Context): List<ReportingMessage> {
        mOrgId = map[ORG_ID_KEY]
        if (map.containsKey(AUDIENCE_MANAGER_SERVER)) {
            url = map[AUDIENCE_MANAGER_SERVER]
        }
        marketingCloudId
        return emptyList()
    }

    override fun setOptOut(optedOut: Boolean): List<ReportingMessage> = emptyList()

    override fun onApplicationForeground() {
        syncIds()
    }

    override fun onApplicationBackground() {
        syncIds()
    }

    override fun setUserAttribute(s: String, s1: String) {
        syncIds()
    }

    override fun setUserAttributeList(s: String, list: List<String>) {
        syncIds()
    }

    override fun supportsAttributeLists(): Boolean {
        return false
    }

    override fun setAllUserAttributes(map: Map<String, String>, map1: Map<String, List<String>>) {
        syncIds()
    }

    override fun removeUserAttribute(s: String) {
        syncIds()
    }

    override fun setUserIdentity(identityType: IdentityType, s: String) {
        syncIds()
    }

    override fun removeUserIdentity(identityType: IdentityType) {
        syncIds()
    }

    override fun logout(): List<ReportingMessage> = emptyList()


    override fun willHandlePushMessage(intent: Intent): Boolean = false

    override fun onPushMessageReceived(context: Context, intent: Intent) {}
    override fun onPushRegistration(instanceId: String, senderId: String): Boolean {
        syncIds()
        return false
    }

    var deferred = false
    private fun syncIds() {
        if (requestInProgress) {
            deferred = true
        }
        requestInProgress = true
        executeNetworkRequest {
            try {
                val url = URL("https", url, "/id?" + encodeIds())
                val urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.connectTimeout = 2000
                urlConnection.readTimeout = 10000
                if (urlConnection.responseCode in 200..299) {
                    val response = MPUtility.getJsonResponse(urlConnection)
                    parseResponse(response)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            requestInProgress = false
            if (deferred) {
                deferred = false
                syncIds()
            }
        }
    }

    private fun encodeIds(): String {
        var gaid: String? = null
        val adId = MPUtility.getAdIdInfo(context)
        if (adId != null && adId.advertiser == AdIdInfo.Advertiser.GOOGLE) {
            gaid = adId.id
        }
        val pushId = kitManager.pushInstanceId
        val dBlob = dBlob
        val dcsRegion = dcsRegion
        return encodeIds(marketingCloudId, mOrgId, dBlob, dcsRegion, pushId, gaid, userIdentities)
    }

    fun encodeIds(
        marketingCloudId: String?,
        orgId: String?,
        dBlob: String?,
        dcsRegion: String?,
        pushId: String?,
        gaid: String?,
        userIdentities: Map<IdentityType, String>
    ): String {
        val builder = UrlBuilder()
        builder.append(D_MID_KEY, marketingCloudId)
            .append(D_ORIG_ID_KEY, orgId)
            .append(D_BLOB_KEY, dBlob)
            .append(DCS_REGION_KEY, dcsRegion)
            .append(D_PLATFORM_KEY, "android")
            .append(D_VER, dVer)
            .appendCustomIdentity(PUSH_TOKEN_KEY, pushId)
            .appendCustomIdentity(GOOGLE_AD_ID_KEY, gaid)
        for ((key, value) in userIdentities) {
            builder.appendCustomIdentity(getServerString(key), value)
        }
        return builder.toString()
    }

    override fun getInstance(): Any {
        return AdobeApi(marketingCloudId!!)
    }

    private fun parseResponse(jsonObject: JSONObject) {
        try {
            val marketingCloudIdKey = jsonObject.getString(D_MID_KEY)
            val dcsRegion = jsonObject.optString(DCS_REGION_KEY)
            val dBlob = jsonObject.optString(D_BLOB_KEY)
            val existingMarketingCloudId = integrationAttributes[MARKETING_CLOUD_ID_KEY]
            if (KitUtils.isEmpty(existingMarketingCloudId)) {
                marketingCloudId = marketingCloudIdKey
            }
            setDcsRegion(dcsRegion)
            setDBlob(dBlob)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * fetch the MarketingCloudId. If it can't be found in our storage, assume that this
     * user is migrating from the Adobe SDK and try to fetch it from where the Adobe SDK would store it
     */
    private var marketingCloudId: String?
        get() {
            var marketingCloudIdKey = integrationAttributes[MARKETING_CLOUD_ID_KEY]
            if (KitUtils.isEmpty(marketingCloudIdKey)) {
                var adobeSharedPrefs =
                    context.getSharedPreferences("visitorIDServiceDataStore", Context.MODE_PRIVATE)
                marketingCloudIdKey = adobeSharedPrefs.getString("ADOBEMOBILE_PERSISTED_MID", null)
                if (KitUtils.isEmpty(marketingCloudIdKey)) {
                    adobeSharedPrefs =
                        context.getSharedPreferences("APP_MEASUREMENT_CACHE", Context.MODE_PRIVATE)
                    marketingCloudIdKey = adobeSharedPrefs.getString("ADBMOBILE_PERSISTED_MID", null)
                }
                if (!KitUtils.isEmpty(marketingCloudIdKey)) {
                    return marketingCloudIdKey
                }
            }
            return marketingCloudIdKey
        }

        private set(id) {
            val integrationAttributes = integrationAttributes
            integrationAttributes[MARKETING_CLOUD_ID_KEY] = id
            setIntegrationAttributes(integrationAttributes)
        }
    private val dcsRegion: String?
         get() = integrationAttributes[AUDIENCE_MANAGER_LOCATION_HINT]

    private fun setDcsRegion(dcsRegion: String) {
        val attrs = integrationAttributes
        attrs[AUDIENCE_MANAGER_LOCATION_HINT] = dcsRegion
        integrationAttributes = attrs
    }

    private val dBlob: String?
         get() = integrationAttributes[AUDIENCE_MANAGER_BLOB]

    private fun setDBlob(dBlob: String) {
        val attrs = integrationAttributes
        attrs[AUDIENCE_MANAGER_BLOB] = dBlob
        integrationAttributes = attrs
    }

    internal inner class UrlBuilder {
        var builder = StringBuilder()
        var hasValue = false
        fun append(key: String?, value: String?): UrlBuilder {
            if (KitUtils.isEmpty(key) || KitUtils.isEmpty(value)) {
                return this
            }
            if (hasValue) {
                builder.append("&")
            } else {
                hasValue = true
            }
            builder.append(key)
            builder.append("=")
            builder.append(value)
            return this
        }

        fun appendCustomIdentity(key: Int, value: String?): UrlBuilder {
            return append("d_cid", "$key%01$value")
        }

        fun appendCustomIdentity(key: String, value: String): UrlBuilder {
            return append("d_cid_ic", "$key%01$value")
        }

        override fun toString(): String {
            return builder.toString()
        }

    }

    companion object {
        const val MARKETING_CLOUD_ID_KEY = "mid"
        private const val ORG_ID_KEY = "organizationID"
        private const val AUDIENCE_MANAGER_BLOB = "aamb"
        private const val AUDIENCE_MANAGER_LOCATION_HINT = "aamlh"
        const val AUDIENCE_MANAGER_SERVER = "audienceManagerServer"
        private const val D_MID_KEY = "d_mid"
        private const val D_ORIG_ID_KEY = "d_orgid"
        private const val D_BLOB_KEY = "d_blob"
        private const val DCS_REGION_KEY = "dcs_region"
        private const val D_PLATFORM_KEY = "d_ptfm"
        private const val D_VER = "d_ver"
        private const val DEFAULT_URL = "dpm.demdex.net"
        private const val PUSH_TOKEN_KEY = 20919
        private const val GOOGLE_AD_ID_KEY = 20914

        //TODO
        //check if these are actually correct and replace
        private fun getServerString(identityType: IdentityType): String {
            return when (identityType) {
                IdentityType.Other -> "other"
                IdentityType.CustomerId -> "customerid"
                IdentityType.Facebook -> "facebook"
                IdentityType.Twitter -> "twitter"
                IdentityType.Google -> "google"
                IdentityType.Microsoft -> "microsoft"
                IdentityType.Yahoo -> "yahoo"
                IdentityType.Email -> "email"
                IdentityType.Alias -> "alias"
                IdentityType.FacebookCustomAudienceId -> "facebookcustomaudienceid"
                else -> ""
            }
        }
    }
}
