package com.mparticle.kits;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.mparticle.MParticle;
import com.mparticle.internal.MPUtility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

public abstract class AdobeKitBase extends KitIntegration implements KitIntegration.AttributeListener, KitIntegration.PushListener, KitIntegration.ApplicationStateListener {

    static final String MARKETING_CLOUD_ID_KEY = "mid";
    private static final String ORG_ID_KEY = "organizationID";
    private static final String AUDIENCE_MANAGER_BLOB = "aamb";
    private static final String AUDIENCE_MANAGER_LOCATION_HINT = "aamlh";

    private static final String D_MID_KEY = "d_mid";
    private static final String D_ORIG_ID_KEY = "d_orgid";
    private static final String D_BLOB_KEY = "d_blob";
    private static final String DCS_REGION_KEY = "dcs_region";
    private static final String D_PLATFORM_KEY = "d_ptfm";
    private static final String D_VER = "d_ver";

    private static final String DEFAULT_URL = "dpm.demdex.net";

    private static final Integer PUSH_TOKEN_KEY = 20919;
    private static final Integer GOOGLE_AD_ID_KEY = 20914;

    private final String dVer = "2";
    private String url = DEFAULT_URL;

    private String mOrgId;

    private boolean requestInProgress = false;

    @Override
    protected List<ReportingMessage> onKitCreate(Map<String, String> map, Context context) throws IllegalArgumentException {
        mOrgId = map.get(ORG_ID_KEY);
        getMarketingCloudId();
        return null;
    }

    @Override
    public List<ReportingMessage> setOptOut(boolean optedOut) {
        return null;
    }

    @Override
    public void onApplicationForeground() {
        syncIds();
    }

    @Override
    public void onApplicationBackground() {
        syncIds();
    }

    @Override
    public void setUserAttribute(String s, String s1) {
        syncIds();
    }

    @Override
    public void setUserAttributeList(String s, List<String> list) {
        syncIds();
    }

    @Override
    public boolean supportsAttributeLists() {
        return false;
    }

    @Override
    public void setAllUserAttributes(Map<String, String> map, Map<String, List<String>> map1) {
        syncIds();
    }

    @Override
    public void removeUserAttribute(String s) {
        syncIds();
    }

    @Override
    public void setUserIdentity(MParticle.IdentityType identityType, String s) {
        syncIds();
    }

    @Override
    public void removeUserIdentity(MParticle.IdentityType identityType) {
        syncIds();
    }

    @Override
    public List<ReportingMessage> logout() {
        return null;
    }

    @Override
    public boolean willHandlePushMessage(Intent intent) {
        return false;
    }

    @Override
    public void onPushMessageReceived(Context context, Intent intent) {

    }

    @Override
    public boolean onPushRegistration(String instanceId, String senderId) {
        syncIds();
        return false;
    }

    boolean deferred = false;

    private void syncIds() {
        if (requestInProgress) {
            deferred = true;
        }
        requestInProgress = true;

        executeNetworkRequest(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https", AdobeKitBase.this.url, "/id?" + encodeIds());
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(2000);
                    urlConnection.setReadTimeout(10000);
                    if (urlConnection.getResponseCode() >= 200 && urlConnection.getResponseCode() < 300) {
                        JSONObject response = MPUtility.getJsonResponse(urlConnection);
                        parseResponse(response);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                requestInProgress = false;
                if (deferred) {
                    deferred = false;
                    syncIds();
                }
            }
        });
    }


    private String encodeIds() {
        String gaid = null;
        MPUtility.AndroidAdIdInfo adId = MPUtility.getGoogleAdIdInfo(getContext());
        if (adId != null) {
            gaid = adId.id;
        }
        String pushId = getKitManager().getPushInstanceId();
        String marketingCloudId = getMarketingCloudId();
        String dBlob = getDBlob();
        String dcsRegion = getDcsRegion();
        return encodeIds(marketingCloudId, mOrgId, dBlob, dcsRegion, pushId, gaid, getUserIdentities());
    }

    protected String encodeIds(String marketingCloudId, String orgId, String dBlob, String dcsRegion, String pushId, String gaid, Map<MParticle.IdentityType, String> userIdentities) {

        UrlBuilder builder = new UrlBuilder();
        builder.append(D_MID_KEY, marketingCloudId)
                .append(D_ORIG_ID_KEY, orgId)
                .append(D_BLOB_KEY, dBlob)
                .append(DCS_REGION_KEY, dcsRegion)
                .append(D_PLATFORM_KEY, "android")
                .append(D_VER, dVer)
                .appendCustomIdentity(PUSH_TOKEN_KEY, pushId)
                .appendCustomIdentity(GOOGLE_AD_ID_KEY, gaid);
        for (Map.Entry<MParticle.IdentityType, String> entry : userIdentities.entrySet()) {
            builder.appendCustomIdentity(getServerString(entry.getKey()), entry.getValue());
        }
        return builder.toString();
    }

    @Override
    public Object getInstance() {
        return new AdobeApi(getMarketingCloudId());
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    private void parseResponse(JSONObject jsonObject) {
        try {
            String marketingCloudId = jsonObject.getString(D_MID_KEY);
            String dcsRegion = jsonObject.optString(DCS_REGION_KEY);
            String dBlob = jsonObject.optString(D_BLOB_KEY);
            setMarketingCloudId(marketingCloudId);
            setDcsRegion(dcsRegion);
            setDBlob(dBlob);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * fetch the MarketingCloudId. If it can't be found in our storage, assume that this
     * user is migrating from the Adobe SDK and try to fetch it from where the Adobe SDK would store it
     */
    private String getMarketingCloudId() {
        String marketingCloudId = getIntegrationAttributes().get(MARKETING_CLOUD_ID_KEY);
        if (KitUtils.isEmpty(marketingCloudId)) {
            SharedPreferences adobeSharedPrefs = getContext().getSharedPreferences("APP_MEASUREMENT_CACHE", Context.MODE_PRIVATE);
            marketingCloudId = adobeSharedPrefs.getString("ADBMOBILE_PERSISTED_MID", null);
            if (!KitUtils.isEmpty(marketingCloudId)) {
                setMarketingCloudId(marketingCloudId);
            }
        }
        return marketingCloudId;
    }

    private void setMarketingCloudId(String id) {
        Map<String, String> integrationAttributes = getIntegrationAttributes();
        integrationAttributes.put(MARKETING_CLOUD_ID_KEY, id);
        setIntegrationAttributes(integrationAttributes);
    }

    private String getDcsRegion() {
        return getIntegrationAttributes().get(AUDIENCE_MANAGER_LOCATION_HINT);
    }

    private void setDcsRegion(String dcsRegion) {
        Map<String, String> attrs = getIntegrationAttributes();
        attrs.put(AUDIENCE_MANAGER_LOCATION_HINT, dcsRegion);
        setIntegrationAttributes(attrs);
    }

    private String getDBlob() {
        return getIntegrationAttributes().get(AUDIENCE_MANAGER_BLOB);
    }

    private void setDBlob(String dBlob) {
        Map<String, String> attrs = getIntegrationAttributes();
        attrs.put(AUDIENCE_MANAGER_BLOB, dBlob);
        setIntegrationAttributes(attrs);
    }

    //TODO
    //check if these are actually correct and replace
    private static String getServerString(MParticle.IdentityType identityType) {
        switch (identityType) {
            case Other:
                return "other";
            case CustomerId:
                return "customerid";
            case Facebook:
                return "facebook";
            case Twitter:
                return "twitter";
            case Google:
                return "google";
            case Microsoft:
                return "microsoft";
            case Yahoo:
                return "yahoo";
            case Email:
                return "email";
            case Alias:
                return "alias";
            case FacebookCustomAudienceId:
                return "facebookcustomaudienceid";
            default:
                return "";
        }
    }

    class UrlBuilder {
        StringBuilder builder;
        boolean hasValue = false;

        UrlBuilder() {
            builder = new StringBuilder();
        }

        UrlBuilder append(String key, String value) {
            if (KitUtils.isEmpty(key) || KitUtils.isEmpty(value)) {
                return this;
            }
            if (hasValue) {
                builder.append("&");
            } else {
                hasValue = true;
            }
            builder.append(key);
            builder.append("=");
            builder.append(value);
            return this;
        }

        UrlBuilder appendCustomIdentity(Integer key, String value) {
            return append("d_cid", key + "%01" + value);
        }

        UrlBuilder appendCustomIdentity(String key, String value) {
            return append("d_cid_ic", key + "%01" + value);
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
