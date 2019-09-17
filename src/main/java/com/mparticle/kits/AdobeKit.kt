package com.mparticle.kits

import android.app.Application
import android.content.Context
import com.adobe.marketing.mobile.*
import com.mparticle.BaseEvent
import com.mparticle.MPEvent
import com.mparticle.events.*
import com.mparticle.internal.Logger
import java.lang.Exception

class AdobeKit: AdobeKitBase(), KitIntegration.EventListener {

    internal val LAUNCH_APP_ID: String = "launchAppId"

    internal lateinit var mediaTracker: MediaTracker
    private var currentPlayheadPosition: Long = 0
    private var sessionStarted = false

    override fun getName() = "Adobe Media"

    public override fun onKitCreate(settings: MutableMap<String, String>?, context: Context): List<ReportingMessage> {
        super.onKitCreate(settings, context)
        val appId = settings?.get(LAUNCH_APP_ID)

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
        }

        Media.createTracker { tracker ->
            mediaTracker = tracker
        }
        return listOf()
    }

    override fun setOptOut(optout: Boolean) = null

    override fun logEvent(p0: MPEvent?) = null

    override fun leaveBreadcrumb(p0: String?) = null

    override fun logException(p0: Exception?, p1: MutableMap<String, String>?, p2: String?) = null

    override fun logScreen(p0: String?, p1: MutableMap<String, String>?) = null

    override fun logError(errorString: String?, p1: MutableMap<String, String>?): List<ReportingMessage>? {
        mediaTracker.trackError(errorString)
        return null
    }

    override fun logEvent(event: BaseEvent): MutableList<ReportingMessage> {
        if (event is MediaEvent) {
            event.playheadPosition?.let {
                currentPlayheadPosition = it
                mediaTracker.updateCurrentPlayhead(it.toSeconds())
            }
            when (event.type) {
                MediaEventType.SessionStart -> sessionStart(event)
                MediaEventType.SessionEnd -> sessionEnd()
                MediaEventType.Play -> play()
                MediaEventType.Pause -> pause()
                MediaEventType.AdBreakEnd -> adBreakEnd(event)
                MediaEventType.AdBreakStart -> adBreakStart(event)
                MediaEventType.AdStart -> adStart(event)
                MediaEventType.AdSkip, MediaEventType.AdEnd -> adEnd(event)
                MediaEventType.UpdateQoS -> updateQos(event)
                MediaEventType.BufferEnd -> bufferEnd(event)
                MediaEventType.BufferStart -> bufferStart(event)
                MediaEventType.SeekStart -> seekStart(event)
                MediaEventType.SeekEnd -> seekEnd(event)
                MediaEventType.SegmentStart -> segmentStart(event)
                MediaEventType.SegmentSkip -> segmentSkip(event)
                MediaEventType.SegmentEnd -> segmentEnd(event)
                MediaEventType.UpdatePlayheadPosition -> {
                    /**already handled*/
                }
                MediaEventType.AdClick -> return mutableListOf()
                else -> return mutableListOf()
            }
            val message = ReportingMessage.fromEvent(this, event)
            (event.type as? MediaEventType)?.name?.let {
                message.eventName = it
            }
            return mutableListOf(message)
        } else {
            return mutableListOf()
        }
    }

    private fun sessionStart(mediaEvent: MediaEvent) {
        if (!sessionStarted) {
            val mediaInfo = mediaEvent.mediaContent.getMediaObject()
            mediaTracker.trackSessionStart(mediaInfo, mediaEvent.customAttributes)
            sessionStarted = true
        }
    }

    private fun sessionEnd() {
        mediaTracker.trackSessionEnd()
    }

    private fun play() {
        mediaTracker.trackPlay()
    }

    private fun pause() {
        mediaTracker.trackPause()
    }

    private fun updateQos(mediaEvent: MediaEvent) {
        mediaEvent.qos?.let { mediaQos ->
            val qoe = Media.createQoEObject(mediaQos.bitRate?.toLong() ?: 0,
                    mediaQos.startupTime?.toSeconds() ?: 0.0,
                    mediaQos.fps?.toDouble() ?: 0.0,
                    mediaQos.droppedFrames.toLong())
            mediaTracker.updateQoEObject(qoe)
        }
    }

    private fun adBreakStart(mediaEvent: MediaEvent) {
        val adBreakObject = mediaEvent.adBreak?.getAdBreakObject()
        mediaTracker.trackEvent(Media.Event.AdBreakStart, adBreakObject, mediaEvent.customAttributes)
    }

    private fun adBreakEnd(mediaEvent: MediaEvent) {
        val adBreakObject = mediaEvent.adBreak?.getAdBreakObject()
        mediaTracker.trackEvent(Media.Event.AdBreakComplete, adBreakObject, mediaEvent.customAttributes)
    }

    private fun adStart(mediaEvent: MediaEvent) {
        val adBreakObject = mediaEvent.mediaAd?.getAdObject()
        mediaTracker.trackEvent(Media.Event.AdStart, adBreakObject, mediaEvent.customAttributes)
    }

    private fun adEnd(mediaEvent: MediaEvent) {
        mediaTracker.trackEvent(Media.Event.AdComplete, mediaEvent.mediaAd?.getAdObject(), mediaEvent.customAttributes)
    }

    private fun seekEnd(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTracker.trackEvent(Media.Event.SeekComplete, mediaObject, mediaEvent.customAttributes)
    }

    private fun seekStart(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTracker.trackEvent(Media.Event.SeekStart, mediaObject, mediaEvent.customAttributes)
    }

    private fun bufferEnd(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTracker.trackEvent(Media.Event.BufferComplete, mediaObject, mediaEvent.customAttributes)
    }

    private fun bufferStart(mediaEvent: MediaEvent) {
        val mediaObject = mediaEvent.mediaContent.getMediaObject()
        mediaTracker.trackEvent(Media.Event.BufferStart, mediaObject, mediaEvent.customAttributes)
    }

    private fun segmentEnd(mediaEvent: MediaEvent) {
        val chapterObject = mediaEvent.segment?.getChapterObject()
        mediaTracker.trackEvent(Media.Event.ChapterComplete, chapterObject, mediaEvent.customAttributes)
    }

    private fun segmentSkip(mediaEvent: MediaEvent) {
        val chapterObject = mediaEvent.segment?.getChapterObject()
        mediaTracker.trackEvent(Media.Event.ChapterSkip, chapterObject, mediaEvent.customAttributes)
    }

    private fun segmentStart(mediaEvent: MediaEvent) {
        val chapterObject = mediaEvent.segment?.getChapterObject()
        mediaTracker.trackEvent(Media.Event.ChapterStart, chapterObject, mediaEvent.customAttributes)
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
        val currentTime = currentPlaybackTime ?: currentPlayheadPosition
        return Media.createAdBreakObject(
                tag,
                1L,
                currentTime.toSeconds()
        )
    }

    internal fun MediaAd.getAdObject(): Map<String?, Any?> {
        return Media.createAdObject(title, id, placement?.toLong() ?: 0, length.toDouble())
    }

    internal fun MediaContent.getMediaType(): Media.MediaType? {
        return when (contentType) {
            ContentType.AUDIO -> Media.MediaType.Audio
            ContentType.VIDEO -> Media.MediaType.Video
            else -> null
        }
    }

    internal fun Long.toSeconds(): Double {
        return toDouble() / 1000
    }

}