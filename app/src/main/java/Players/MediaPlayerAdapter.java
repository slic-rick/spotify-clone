package Players;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class MediaPlayerAdapter extends PlayerAdapter {

    private static final String TAG = "MediaPlayerAdapter";

    private MediaMetadataCompat mCurrentMedia;
    private boolean mCurrentMediaPlayedToCompletion;
    private int mState;                                         // state of the exoPlayer
    private long mStartTime;
    private PlaybackInfoListener mPlaybackInfoListener;

    // ExoPlayer objects
    private SimpleExoPlayer mExoPlayer;
    private TrackSelector mTrackSelector;
    private DefaultRenderersFactory mRenderersFactory;
    private DataSource.Factory mDataSourceFactory;                                                // for creating the source file, the exoplayer plays the source
    private ExoPlayerEventListener mExoPlayerEventListener;

    private final Context mContext;

    public MediaPlayerAdapter(@NonNull Context context,PlaybackInfoListener mPlaybackInfoListener) {
        super(context);
        mContext = context.getApplicationContext();
        this.mPlaybackInfoListener = mPlaybackInfoListener;
    }

    private void initializeExoPlayer(){
        if (mExoPlayer == null) {
            mTrackSelector = new DefaultTrackSelector();
            mRenderersFactory = new DefaultRenderersFactory(mContext);
            mDataSourceFactory = new DefaultDataSourceFactory(mContext, Util.getUserAgent(mContext, "AudioStreamer"));
            mExoPlayer = ExoPlayerFactory.newSimpleInstance(mRenderersFactory, mTrackSelector, new DefaultLoadControl());

            if(mExoPlayerEventListener == null){
                mExoPlayerEventListener = new ExoPlayerEventListener();
            }

//            mExoPlayer.addListener(mExoPlayerEventListener);
        }
    }

    private void release() {
        if (mExoPlayer != null) {
            mExoPlayer.release();
            mExoPlayer = null;
        }
    }

    @Override
    protected void onPlay() {

        //getPlayWhenReady() boolean that checks if there is a file playing or not,true if playing
        if (mExoPlayer != null && !mExoPlayer.getPlayWhenReady()) {
            mExoPlayer.setPlayWhenReady(true);
            setNewState(PlaybackStateCompat.STATE_PLAYING);
        }

    }

    @Override
    protected void onPause() {
        //getPlayWhenReady() boolean that checks if there is a file playing or not

        if (mExoPlayer != null && mExoPlayer.getPlayWhenReady()) {
            mExoPlayer.setPlayWhenReady(false);
            setNewState(PlaybackStateCompat.STATE_PAUSED);
        }
    }

    @Override
    public void playFromMedia(MediaMetadataCompat metadata) {

        //CALLED WHEN THE A SONG IS SELECTED TO BE STREAMED
        startTrackingPlayback();
        playFile(metadata);

    }
    @Override
    public MediaMetadataCompat getCurrentMedia() {
        return mCurrentMedia;  // return the current media
    }

    @Override
    public boolean isPlaying() {
        //true if song is currently playing;
        return mExoPlayer != null && mExoPlayer.getPlayWhenReady();
    }

    @Override
    protected void onStop() {

        // Regardless of whether or not the ExoPlayer has been created / started, the state must
        // be updated, so that MediaNotificationManager can take down the notification.
        Log.d(TAG, "onStop: stopped");
        setNewState(PlaybackStateCompat.STATE_STOPPED);
        release();   // release exoPlayer when stopped playing media
    }

    @Override
    public void seekTo(long position) {
        if (mExoPlayer != null) {
            mExoPlayer.seekTo((int) position);

            // Set the state (to the current state) because the position changed and should
            // be reported to clients.
            setNewState(mState);                    // error prone!!!
        }
    }

    //exoplayer volume
    @Override
    public void setVolume(float volume) {

        if (mExoPlayer != null) {
            mExoPlayer.setVolume(volume);
        }
    }

    // This is the main reducer for the player state machine.
    private void setNewState(@PlaybackStateCompat.State int newPlayerState) {
        mState = newPlayerState;

        // Whether playback goes to completion, or whether it is stopped, the
        // mCurrentMediaPlayedToCompletion is set to true.

        if (mState == PlaybackStateCompat.STATE_STOPPED) {
            mCurrentMediaPlayedToCompletion = true;
        }


        final long reportPosition = mExoPlayer == null ? 0 : mExoPlayer.getCurrentPosition();

        // Send playback state information to service
        publishStateBuilder(reportPosition);
    }

    private void playFile(MediaMetadataCompat metadata) {
        String mediaId = metadata.getDescription().getMediaId();

        // id true, we can play song
        boolean mediaChanged = (mCurrentMedia == null || !mediaId.equals(mCurrentMedia.getDescription().getMediaId()));

        if (mCurrentMediaPlayedToCompletion) {
            // Last audio file was played to completion, the resourceId hasn't changed, but the
            // player was released, so force a reload of the media file for playback.

            // set up play for next play
            mediaChanged = true;   // then we can release exo player
            mCurrentMediaPlayedToCompletion = false;

        }
        if (!mediaChanged) {

            //we clicked the same song
            if (!isPlaying()) {
                // if the song wan't playing, we play it
                play();
            }
            // song is already playing so kill method
            return;
        }
        else {

            // release exoPlayer so we play new song,
            release();
        }

        mCurrentMedia = metadata; //init new song

        initializeExoPlayer();

        try {
            // setting the source to be played
            MediaSource audioSource =
                    new ExtractorMediaSource.Factory(mDataSourceFactory)
                            .createMediaSource(Uri.parse(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))); // turning metaData file in media sawce

            mExoPlayer.prepare(audioSource);                                                        // you have to  prepare the sauce :D :D :D
            Log.d(TAG, "onPlayerStateChanged: PREPARE");

        } catch (Exception e) {

            throw new RuntimeException("Failed to play media uri: "
                    + metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI), e);
        }

        play();

    }

    private void startTrackingPlayback() {

        // UPDATES THE SEEKBAR
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying()) {
                    mPlaybackInfoListener.onSeekTo(
                            mExoPlayer.getContentPosition(), mExoPlayer.getDuration()
                    );
                    handler.postDelayed(this, 100);
                }

                if(mExoPlayer.getContentPosition() >= mExoPlayer.getDuration()
                        && mExoPlayer.getDuration() > 0){

                    //mExoPlayer.getContentPosition() is the current position while mExoPlayer.getDuration() is the max position
                    mPlaybackInfoListener.onPlaybackComplete();
                }
            }
        };

        handler.postDelayed(runnable, 100);
    }

    private void publishStateBuilder(long reportPosition){

        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();         // sends states to the mSession in the session class
        stateBuilder.setActions(getAvailableActions());
        stateBuilder.setState(mState,
                reportPosition,
                1.0f,
                SystemClock.elapsedRealtime());
        mPlaybackInfoListener.onPlaybackStateChange(stateBuilder.build());                          // This method is also called in the session
        mPlaybackInfoListener.updateUI(mCurrentMedia.getDescription().getMediaId());                // sending the id of the currently playing media to the service
    }

    /**
     * Set the current capabilities available on this session. Note: If a capability is not
     * listed in the bitmask of capabilities then the MediaSession will not handle it. For
     * example, if you don't want ACTION_STOP to be handled by the MediaSession, then don't
     * included it in the bitmask that's returned.
     */
    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

        switch (mState) {
            case PlaybackStateCompat.STATE_STOPPED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE;
                break;

                //maybe play next and skip  previous
            case PlaybackStateCompat.STATE_PLAYING:
                actions |= PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_SEEK_TO;
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            default:
                actions |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE
                        | PlaybackStateCompat.ACTION_STOP
                        | PlaybackStateCompat.ACTION_PAUSE;
        }
        return actions;
    }

    // listener for the exoPlayer
    private class ExoPlayerEventListener implements Player.EventListener{

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onLoadingChanged(boolean isLoading) {

        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState){
                case Player.STATE_ENDED:{
                    setNewState(PlaybackStateCompat.STATE_PAUSED);
                    break;
                }
                case Player.STATE_BUFFERING:{
                    Log.d(TAG, "onPlayerStateChanged: BUFFERING");
                    mStartTime = System.currentTimeMillis();
                    break;
                }
                case Player.STATE_IDLE:{

                    break;
                }
                case Player.STATE_READY:{
                    Log.d(TAG, "onPlayerStateChanged: READY");
                    Log.d(TAG, "onPlayerStateChanged: TIME ELAPSED: " + (System.currentTimeMillis() - mStartTime));
                    //shouldn't we reset mStartTime?
                    break;
                }
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {

        }

        @Override
        public void onPositionDiscontinuity(int reason) {
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }

        @Override
        public void onSeekProcessed() {

        }
    }


}
