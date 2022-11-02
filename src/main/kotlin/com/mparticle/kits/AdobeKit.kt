package com.mparticle.kits

import android.app.Application
import android.content.Context
import android.content.Intent
import com.adobe.marketing.mobile.Analytics
import com.adobe.marketing.mobile.Identity
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.Media
import com.adobe.marketing.mobile.MediaConstants.AdMetadataKeys
import com.adobe.marketing.mobile.MediaConstants.VideoMetadataKeys
import com.adobe.marketing.mobile.MediaTracker
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.MobileServices
import com.adobe.marketing.mobile.Signal
import com.adobe.marketing.mobile.UserProfile
import com.mparticle.BaseEvent
import com.mparticle.MPEvent
import com.mparticle.MParticle
import com.mparticle.media.events.ContentType
import com.mparticle.media.events.EventAttributes
import com.mparticle.media.events.MediaAd
import com.mparticle.media.events.MediaAdBreak
import com.mparticle.media.events.MediaAttributeKeys
import com.mparticle.media.events.MediaContent
import com.mparticle.media.events.MediaEvent
import com.mparticle.media.events.MediaEventName
import com.mparticle.media.events.MediaSegment

open class AdobeKit: KitIntegration.EventListener, KitIntegration(),
    KitIntegration.AttributeListener, KitIntegration.PushListener,
    KitIntegration.ApplicationStateListener {

    internal val MARKETING_CLOUD_ID_KEY = "mid"
    internal val LAUNCH_APP_ID: String = "launchAppId"

    var defaultMediaTracker: MediaTracker? = null
    var mediaTrackers: MutableMap<String, MediaTracker> = mutableMapOf()
    var currentPlayheadPosition: Long = 0

    override fun getName() = "Adobe Media"

    public override fun onKitCreate(settings: Map<String, String>, context: Context): List<ReportingMessage> {
        val appId = settings.get(LAUNCH_APP_ID)

        MobileCore.setApplication(context.applicationContext as Application)
        MobileServices.registerExtension()
        Analytics.registerExtension()
        Media.registerExtension()
        UserProfile.registerExtension()
        Identity.registerExtension()
        Lifecycle.registerExtension()
        Signal.registerExtension()
        MobileCore.start {
            MobileCore.configureWithAppID(appId)
            syncIds()
        }
        defaultMediaTracker = Media.createTracker()
        return listOf()
    }

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

    override fun setUserIdentity(identityType: MParticle.IdentityType, s: String) {
        syncIds()
    }

    override fun removeUserIdentity(identityType: MParticle.IdentityType) {
        syncIds()
    }

    override fun logout(): List<ReportingMessage> = emptyList()

    override fun willHandlePushMessage(intent: Intent): Boolean = false

    override fun onPushMessageReceived(context: Context, intent: Intent) {}
    override fun onPushRegistration(instanceId: String, senderId: String): Boolean {
        syncIds()
        return false
    }

    private fun syncIds() {
        Identity.getExperienceCloudId {
            setMarketingCloudId(it)
        }
    }

    override fun getInstance() =
        Identity.getExperienceCloudId {
            setMarketingCloudId(it)
            AdobeApi(it)
        }

    fun setMarketingCloudId(id: String) {
        val integrationAttributes = integrationAttributes
        if(id.length > 0 && !id.equals(integrationAttributes[MARKETING_CLOUD_ID_KEY])) {
            integrationAttributes[MARKETING_CLOUD_ID_KEY] = id
            setIntegrationAttributes(integrationAttributes)
        }
    }

    override fun setOptOut(optout: Boolean): List<ReportingMessage> = emptyList()

    override fun logEvent(p0: MPEvent) = null

    override fun leaveBreadcrumb(p0: String?) = null

    override fun logException(p0: Exception?, p1: MutableMap<String, String>?, p2: String?) = null

    override fun logScreen(p0: String?, p1: MutableMap<String, String>?) = null

    override fun logError(errorString: String?, p1: MutableMap<String, String>?): List<ReportingMessage>? {
        defaultMediaTracker?.trackError(errorString)
        return null
    }

    override fun logBaseEvent(event: BaseEvent): MutableList<ReportingMessage>? {
        if (event is MediaEvent) {
            event.playheadPosition?.let {
                currentPlayheadPosition = it
                mediaTrackers[event.sessionId]?.updateCurrentPlayhead(it.toSeconds())
            }
            when (event.eventName) {
                MediaEventName.SESSION_START -> sessionStart(event)
                MediaEventName.SESSION_END -> sessionEnd(event)
                MediaEventName.PLAY -> play(event)
                MediaEventName.PAUSE -> pause(event)
                MediaEventName.AD_BREAK_END -> adBreakEnd(event)
                MediaEventName.AD_BREAK_START -> adBreakStart(event)
                MediaEventName.AD_START -> adStart(event)
                MediaEventName.AD_SKIP, MediaEventName.AD_END -> adEnd(event)
                MediaEventName.UPDATE_QOS -> updateQos(event)
                MediaEventName.BUFFER_END -> bufferEnd(event)
                MediaEventName.BUFFER_START -> bufferStart(event)
                MediaEventName.SEEK_START -> seekStart(event)
                MediaEventName.SEEK_END -> seekEnd(event)
                MediaEventName.SEGMENT_START -> segmentStart(event)
                MediaEventName.SEGMENT_SKIP -> segmentSkip(event)
                MediaEventName.SEGMENT_END -> segmentEnd(event)
                MediaEventName.UPDATE_PLAYHEAD_POSITION -> {
                    /** already handled */
                }
                MediaEventName.AD_CLICK -> {
                    /** do nothing */
                }
            }
        }
        return null
    }

    private fun sessionStart(mediaEvent: MediaEvent) {
        val sessionId = mediaEvent.sessionId ?: return
        val mediaInfo = mediaEvent.mediaContent.getMediaObject()
        mediaTrackers[sessionId] = Media.createTracker()
        mediaTrackers[sessionId]?.trackSessionStart(mediaInfo, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun sessionEnd(mediaEvent: MediaEvent) {
        val sessionId = mediaEvent.sessionId ?: return
        mediaTrackers[sessionId]?.trackSessionEnd()
        mediaTrackers.remove(sessionId)
    }

    private fun play(mediaEvent: MediaEvent) {
        mediaTrackers[mediaEvent.sessionId]?.trackPlay()
    }

    private fun pause(mediaEvent: MediaEvent) {
        mediaTrackers[mediaEvent.sessionId]?.trackPause()
    }

    private fun updateQos(mediaEvent: MediaEvent) {
        mediaEvent.qos?.let { mediaQos ->
            val qoe = Media.createQoEObject(mediaQos.bitRate?.toLong() ?: 0,
                mediaQos.startupTime?.toSeconds() ?: 0.0,
                mediaQos.fps?.toDouble() ?: 0.0,
                mediaQos.droppedFrames?.toLong() ?: 0)
            mediaTrackers[mediaEvent.sessionId]?.updateQoEObject(qoe)
        }
    }

    private fun adBreakStart(mediaEvent: MediaEvent) {
        val adBreakObject = mediaEvent.adBreak?.getAdBreakObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.AdBreakStart, adBreakObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun adBreakEnd(mediaEvent: MediaEvent) {
        val adBreakObject = mediaEvent.adBreak?.getAdBreakObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.AdBreakComplete, adBreakObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun adStart(mediaEvent: MediaEvent) {
        val adBreakObject = mediaEvent.mediaAd?.getAdObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.AdStart, adBreakObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun adEnd(mediaEvent: MediaEvent) {
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.AdComplete, mediaEvent.mediaAd?.getAdObject(), mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun seekEnd(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.SeekComplete, mediaObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun seekStart(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.SeekStart, mediaObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun bufferEnd(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.BufferComplete, mediaObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun bufferStart(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.BufferStart, mediaObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun segmentEnd(mediaEvent: MediaEvent) {
        val chapterObject = mediaEvent.segment?.getChapterObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.ChapterComplete, chapterObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun segmentSkip(mediaEvent: MediaEvent) {
        val chapterObject = mediaEvent.segment?.getChapterObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.ChapterSkip, chapterObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun segmentStart(mediaEvent: MediaEvent) {
        val chapterObject = mediaEvent.segment?.getChapterObject()
        mediaTrackers[mediaEvent.sessionId]?.trackEvent(Media.Event.ChapterStart, chapterObject, mediaEvent.customAttributes?.toAdobeAttributes())
    }

    private fun MediaSegment.getChapterObject(): Map<String?, Any?> {
        return Media.createChapterObject(title,
            index?.toLong() ?: 0,
            duration?.toDouble() ?: 0.0,
            currentPlayheadPosition.toDouble()
        )
    }

    internal fun MediaContent.getMediaObject(): HashMap<String?, Any?> {
        return Media.createMediaObject(
            name,
            contentId,
            duration?.toSeconds() ?: 0.0,
            streamType,
            getMediaType()
        )
    }

    internal fun MediaAdBreak.getAdBreakObject(): Map<String?, Any?> {
        return Media.createAdBreakObject(
            title,
            1L,
            currentPlayheadPosition.toSeconds()
        )
    }

    internal fun MediaAd.getAdObject(): Map<String?, Any?> {
        return Media.createAdObject(title, id, position?.toLong() ?: 0, duration?.toDouble()
            ?: 0.0)
    }

    internal fun MediaContent.getMediaType(): Media.MediaType? {
        return when (contentType) {
            ContentType.AUDIO -> Media.MediaType.Audio
            ContentType.VIDEO -> Media.MediaType.Video
            else -> null
        }
    }

    internal fun <K: String?, V> Map<K, V>.toAdobeAttributes(): Map<String?, String?> =
        entries.associate { (key, value) ->
            when (key) {
                MediaAttributeKeys.AD_ADVERTISING_ID -> AdMetadataKeys.ADVERTISER
                MediaAttributeKeys.AD_CAMPAIGN -> AdMetadataKeys.CAMPAIGN_ID
                MediaAttributeKeys.AD_CREATIVE -> AdMetadataKeys.CREATIVE_ID
                MediaAttributeKeys.AD_PLACEMENT -> AdMetadataKeys.PLACEMENT_ID
                MediaAttributeKeys.AD_SITE_ID -> AdMetadataKeys.SITE_ID
                EventAttributes.CONTENT_SHOW -> VideoMetadataKeys.SHOW
                EventAttributes.CONTENT_EPISODE -> VideoMetadataKeys.EPISODE
                EventAttributes.CONTENT_ASSET_ID -> VideoMetadataKeys.ASSET_ID
                EventAttributes.CONTENT_GENRE -> VideoMetadataKeys.GENRE
                EventAttributes.CONTENT_FIRST_AIR_DATE -> VideoMetadataKeys.FIRST_AIR_DATE
                EventAttributes.CONTENT_DIGITAL_DATE -> VideoMetadataKeys.FIRST_DIGITAL_DATE
                EventAttributes.CONTENT_RATING -> VideoMetadataKeys.RATING
                EventAttributes.CONTENT_ORIGINATOR -> VideoMetadataKeys.ORIGINATOR
                EventAttributes.CONTENT_NETWORK -> VideoMetadataKeys.NETWORK
                EventAttributes.CONTENT_SHOW_TYPE -> VideoMetadataKeys.SHOW_TYPE
                EventAttributes.CONTENT_MVPD -> VideoMetadataKeys.MVPD
                EventAttributes.CONTENT_AUTHORIZED -> VideoMetadataKeys.AUTHORIZED
                EventAttributes.CONTENT_DAYPART -> VideoMetadataKeys.DAY_PART
                EventAttributes.CONTENT_FEED -> VideoMetadataKeys.FEED
                else -> key
            } to value.toString()
        }

    internal fun Long.toSeconds(): Double {
        return toDouble() / 1000
    }
}
