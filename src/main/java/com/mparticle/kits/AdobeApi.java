package com.mparticle.kits;

public class AdobeApi {
    private final String adobeMcid;

    public AdobeApi(String adobeMcid) {
        super();
        this.adobeMcid = adobeMcid;
    }

    public String getMarketingCloudID() {
        return adobeMcid;
    }
}
