package services;

import android.app.Notification;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.spotifylearn.MyApplication;
import com.example.spotifylearn.R;
import com.google.api.LogDescriptor;
import com.google.common.util.concurrent.ServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import Players.MediaPlayerAdapter;
import Players.PlaybackInfoListener;
import notifications.MediaNotificationManager;
import util.MediaLibrary;
import util.MyPreferenceManager;

import static util.Constants.MEDIA_QUEUE_POSITION;
import static util.Constants.QUEUE_NEW_PLAYLIST;
import static util.Constants.SEEK_BAR_MAX;
import static util.Constants.SEEK_BAR_PROGRESS;

public class MediaService extends MediaBrowserServiceCompat {

    private static final String TAG = "MediaService";

    private MediaSessionCompat mSession;                  // connects with the Media Controller obj
    private MediaPlayerAdapter mPlayback;                 // exoPlayer obj,
    private MyApplication mMyApplication;                 // has the list of the selected list
    private MyPreferenceManager mMyPrefManager;
    private MediaNotificationManager mMediaNotificationManager;
    private boolean mIsServiceStarted;                                                              //determines if the service is running or not


    @Override
    public void onCreate() {
        super.onCreate();

        mMyApplication = MyApplication.getInstance();
        mMyPrefManager = new MyPreferenceManager(this);

        //Build the MediaSession
        mSession = new MediaSessionCompat(this, TAG);

        // Media buttons on the device,
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                // https://developer.android.com/guide/topics/media-apps/mediabuttons#mediabuttons-and-active-mediasessions
                // (handles the PendingIntents for MediaButtonReceiver.buildMediaButtonPendingIntent)

                MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS); // Control the items in the queue (aka playlist)
        // See https://developer.android.com/guide/topics/media-apps/mediabuttons for more info on flags

        mSession.setCallback(new MediaSessionCallback());                                           //callback methods

        // A token that can be used to create a MediaController for this session
        setSessionToken(mSession.getSessionToken());
        mPlayback = new MediaPlayerAdapter(this,new MediaPlayerListener());
        mMediaNotificationManager = new MediaNotificationManager(this);

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        stopSelf();                         // stop the service when task is removed
        mPlayback.stop();                   // stopping exoPlayer
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSession.release();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {

        // checks if the right client is the one accessing the media service
        Log.d(TAG, "onGetRoot: called. ");
        if(clientPackageName.equals(getApplicationContext().getPackageName())){
            // Allowed to browse media
            return new BrowserRoot("some_real_playlist", null);                       // return the browser root media
        }

        return new BrowserRoot("empty_media", null); // return no media
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {

       //receives id of browser from BrowserRoot, checks if the right person is the one authenticated, then send them the media Items

        Log.d(TAG, "onLoadChildren: called: " + parentId + ", " + result);

        //  Browsing not allowed
        if (TextUtils.equals("empty_media", parentId)) {
            result.sendResult(null);
            return;
        }

        result.sendResult(mMyApplication.getMediaItems());                                            // sending the mediaItems in mMyApplication to the application class

    }

    public class MediaSessionCallback extends MediaSessionCompat.Callback {

        private final List<MediaSessionCompat.QueueItem> mPlaylist = new ArrayList<>();             // similar to mediaMetadata, playlist for the songs
        private int mQueueIndex = -1;                                                               // keeps track of which item is currently playing in the playlist
        private MediaMetadataCompat mPreparedMedia;                                                 // currently being played by exo player

        private void resetPlaylist()

        {
            //clearing the playlist and setting the index to -1
            mPlaylist.clear();
            mQueueIndex = -1;
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG, "onPlayFromMediaId: CALLED.");
            // if we are not playing the 1st playlist, then clear the previous
            if(extras.getBoolean(QUEUE_NEW_PLAYLIST, false)){
                resetPlaylist();
            }

            mPreparedMedia = mMyApplication.getTreeMap().get(mediaId);                              // init the Current media with, media from mMyAplicationlist
            mSession.setMetadata(mPreparedMedia);                                                   // setting the currently played media to mSession
            if (!mSession.isActive()) {
                mSession.setActive(true);
            }

            mPlayback.playFromMedia(mPreparedMedia);                                                // playing media in exoPlayer

            int newQueuePosition = extras.getInt(MEDIA_QUEUE_POSITION, -1);             // init the index of the selected song in the que/ playlist
            if(newQueuePosition == -1){
                mQueueIndex++;
            }
            else{
                mQueueIndex = extras.getInt(MEDIA_QUEUE_POSITION);
            }

            mMyPrefManager.saveQueuePosition(mQueueIndex);                                          // saving the index of the played playlist
            mMyPrefManager.saveLastPlayedMedia(mPreparedMedia.getDescription().getMediaId());       // saving the current media metadata

        }



        @Override
        public void onAddQueueItem(MediaDescriptionCompat description)
        {
            //adding to the playlist, media songs
            Log.d(TAG, "onAddQueueItem: CALLED: position in list: " + mPlaylist.size());
            mPlaylist.add(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mQueueIndex == -1) ? 0 : mQueueIndex;
            mSession.setQueue(mPlaylist);                                                           // connecting the playlist with the mediaSession
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description)

        {
            mPlaylist.remove(new MediaSessionCompat.QueueItem(description, description.hashCode()));
            mQueueIndex = (mPlaylist.isEmpty()) ? -1 : mQueueIndex;                                 // condition
            mSession.setQueue(mPlaylist);                                                           // session needs to know the que
        }

        @Override
        public void onPrepare() {

            if (mQueueIndex < 0 && mPlaylist.isEmpty()) {
                // Nothing to play.
                return;  //break
            }

            String mediaId = mPlaylist.get(mQueueIndex).getDescription().getMediaId();
            mPreparedMedia = mMyApplication.getMediaItem(mediaId);                                  // prepare media item for playing
            mSession.setMetadata(mPreparedMedia);                                                   // keep the mSession informed

            if (!mSession.isActive()) {
                mSession.setActive(true);                                                           // so that the mediaSession can receive button events (on clicks) from user
            }

        }

        @Override
        public void onPlay() {

            if (!isReadyToPlay()) {
                // playlist is empty, Nothing to play.
                return;
            }

            if (mPreparedMedia == null) {
                onPrepare();
            }

            mPlayback.playFromMedia(mPreparedMedia);                                                // exo player

            mMyPrefManager.saveQueuePosition(mQueueIndex);
            mMyPrefManager.saveLastPlayedMedia(mPreparedMedia.getDescription().getMediaId());
        }

        @Override
        public void onPause() {
            mPlayback.pause();
        }

        @Override
        public void onStop() {

            mPlayback.stop();
            mSession.setActive(false);

        }

        @Override
        public void onSkipToNext() {

            Log.d(TAG, "onSkipToNext: SKIP TO NEXT");

            // increment and then check using modulus
            mQueueIndex = (++mQueueIndex % mPlaylist.size());
            mPreparedMedia = null;
            onPlay();

        }

        @Override
        public void onSkipToPrevious() {

            Log.d(TAG, "onSkipToPrevious: SKIP TO PREVIOUS");
            mQueueIndex = mQueueIndex > 0 ? mQueueIndex - 1 : mPlaylist.size() - 1;                 // if que is < 0 then play the last song in the playlist
            mPreparedMedia = null;
            onPlay();
        }

        @Override
        public void onSeekTo(long pos) {
            mPlayback.seekTo(pos);
        }

        private boolean isReadyToPlay() {
            return (!mPlaylist.isEmpty());                                                          // true if song is ready to play
        }
    }


    public class MediaPlayerListener implements PlaybackInfoListener {

        private final ServiceManager mServiceManager;                                               // class that managers the notification

        public MediaPlayerListener() {
            mServiceManager = new ServiceManager();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onPlaybackStateChange(PlaybackStateCompat state) {
            mSession.setPlaybackState(state);                                                       // sending the state from EXOplayer to the Msession

            // Manage the started state of this service.
            switch (state.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                    mServiceManager.updateNotification(state,mPlayback.getCurrentMedia()
                            .getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI));
                    break;

                case PlaybackStateCompat.STATE_PAUSED:
                    mServiceManager.updateNotification(state,mPlayback.getCurrentMedia()
                            .getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI));
                    break;

                case PlaybackStateCompat.STATE_STOPPED:
                    Log.d(TAG, "onPlaybackStateChange: STOPPED.");
                    mServiceManager.moveServiceOutOfStartedState();                                 // can destroy the service
                    break;
            }

        }

        @Override
        public void onSeekTo(long progress, long max) {
                                                                                                    // sending broadcast to the ui
            Intent intent = new Intent();
            intent.setAction(getString(R.string.broadcast_seekbar_update));
            intent.putExtra(SEEK_BAR_PROGRESS, progress);
            intent.putExtra(SEEK_BAR_MAX, max);
            sendBroadcast(intent);
        }

        @Override
        public void onPlaybackComplete() {
            Log.d(TAG, "onPlaybackComplete: SKIPPING TO NEXT.");
            //media completed playing so we play next
            mSession.getController().getTransportControls().skipToNext();
        }

        @Override
        public void updateUI(String newMediaId) {
            // sending broadcast of the currenly playing song to the ui/ mainActivity
            Log.d(TAG, "updateUI: CALLED: " + newMediaId);
            Intent intent = new Intent();
            intent.setAction(getString(R.string.broadcast_update_ui));
            intent.putExtra(getString(R.string.broadcast_new_media_id), newMediaId);
            sendBroadcast(intent);

        }
    }

    class ServiceManager implements ICallback {

        private String mDisplayImageUri;
        private Bitmap mCurrentArtistBitmap;
        private PlaybackStateCompat mState;
        private GetArtistBitmapAsyncTask mAsyncTask;

        public ServiceManager() {
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        public void updateNotification(PlaybackStateCompat state, String displayImageUri){
            Log.d(TAG, "updateNotification: METHOD CALLED");
            mState = state;

            if(!displayImageUri.equals(mDisplayImageUri)){
                // download new bitmap

            //Process of downloading the a bitmap
                mAsyncTask = new GetArtistBitmapAsyncTask(
                        Glide.with(MediaService.this)
                                .asBitmap()
                                .load(displayImageUri)
                                .listener(new RequestListener<Bitmap>() {
                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                        return false;
                                    }

                                    @Override
                                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                        return false;
                                    }
                                }).submit(), this);

                mAsyncTask.execute();

                mDisplayImageUri = displayImageUri;
            }

            else{
                // bitmap already downloaded, just load it
                displayNotification(mCurrentArtistBitmap);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        public void displayNotification(Bitmap bitmap){

            Log.d(TAG, "displayNotification: METHOD CALLED");
            // Manage the started state of this service.
            // make new notification everytime the state changes
            Notification notification = null;
            switch (mState.getState()) {

                case PlaybackStateCompat.STATE_PLAYING:
                    notification =
                            mMediaNotificationManager.buildNotification(
                                    mState, getSessionToken(), mPlayback.getCurrentMedia().getDescription(), bitmap);

                    if (!mIsServiceStarted) {
                        ContextCompat.startForegroundService(
                                MediaService.this,
                                new Intent(MediaService.this, MediaService.class));
                        mIsServiceStarted = true;
                    }

                    startForeground(MediaNotificationManager.NOTIFICATION_ID, notification);
                    break;

                case PlaybackStateCompat.STATE_PAUSED:
                    stopForeground(false);
                    notification =
                            mMediaNotificationManager.buildNotification(
                                    mState, getSessionToken(), mPlayback.getCurrentMedia().getDescription(), bitmap);
                    mMediaNotificationManager.getNotificationManager()
                            .notify(MediaNotificationManager.NOTIFICATION_ID, notification);
                    break;
            }
        }

        private void moveServiceOutOfStartedState() {

            Log.d(TAG, "moveServiceOutOfStartedState: METHOD CALLED");
            stopForeground(true);                                                  // stop the foreground service
            stopSelf();                                                                             // kill the service
            mIsServiceStarted = false;
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void done(Bitmap bitmap) {
            Log.d(TAG, "done: METHOD CALLED");
            mCurrentArtistBitmap = bitmap;
            displayNotification(mCurrentArtistBitmap);
        }
    }

    static class GetArtistBitmapAsyncTask extends AsyncTask<Void, Void, Bitmap> {

        private FutureTarget<Bitmap> mBitmap;                                                       // var for downloading bitmap
        private ICallback mICallback;


        public GetArtistBitmapAsyncTask(FutureTarget<Bitmap> bitmap, ICallback iCallback) {
            Log.d(TAG, "GetArtistBitmapAsyncTask: METHOD CALLED");
            this.mBitmap = bitmap;
            this.mICallback = iCallback;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            Log.d(TAG, "onPostExecute: METHOD CALLED");
            //called when the bitmap is available, when it's downloaded
            super.onPostExecute(bitmap);
            mICallback.done(bitmap);
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            Log.d(TAG, "doInBackground: METHOD CALLED");
            // method will return bitmap in onPostExecute
            try {
                return mBitmap.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

}




