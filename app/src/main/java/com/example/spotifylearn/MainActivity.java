package com.example.spotifylearn;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

import Models.Artist;
import client.MediaBrowserHelper;
import client.MediaBrowserHelperCallback;
import services.MediaService;
import util.MainActivityFragmentManager;
import util.MyPreferenceManager;

import static util.Constants.MEDIA_QUEUE_POSITION;
import static util.Constants.QUEUE_NEW_PLAYLIST;
import static util.Constants.SEEK_BAR_MAX;
import static util.Constants.SEEK_BAR_PROGRESS;

public class MainActivity extends AppCompatActivity  implements IMainActivity, MediaBrowserHelperCallback {


    private static final String TAG = "MainActivity";
    private ProgressBar mProgressBar;
    private MediaBrowserHelper mMediaBrowserHelper;

    //broadcast receivers
    private SeekBarBroadcastReceiver mSeekbarBroadcastReceiver;
    private UpdateUIBroadcastReceiver mUpdateUIBroadcastReceiver;

    private MyApplication mMyApplication;
    private MyPreferenceManager mMyPrefManager;
    //booleans
    boolean isPlaying;
    private boolean mOnAppOpen;                                                                     // checks if the open was open before
    private boolean mWasConfigurationChange = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mProgressBar = findViewById(R.id.progress_bar);
        mMyPrefManager = new MyPreferenceManager(this);
        mMediaBrowserHelper = new MediaBrowserHelper(this, MediaService.class);
        mMediaBrowserHelper.setMediaBrowserHelperCallback(this);                                    // init the MediaBrowserHelperCallback in the Media browser class
        mMyApplication = MyApplication.getInstance();


        if(savedInstanceState == null){           //create only if no fragment has been init already
            loadFragment(HomeFragment.newInstance(),true);
        }

    }

    private MediaControllerFragment getMediaControllerFragment(){
        MediaControllerFragment mediaControllerFragment = (MediaControllerFragment)getSupportFragmentManager()
                .findFragmentById(R.id.media_controller);
        if(mediaControllerFragment != null){
            return mediaControllerFragment;
        }
        return null;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mWasConfigurationChange = true;
    }


    @Override
    public void playPause() {
        if(mOnAppOpen){
            if (isPlaying) {
                mMediaBrowserHelper.getTransportControls().pause();
            }
            else {
                mMediaBrowserHelper.getTransportControls().play();
            }
        }
        else{
            if(!getMyPreferenceManager().getPlaylistId().equals("")){
                onMediaSelected(
                        getMyPreferenceManager().getPlaylistId(),
                        mMyApplication.getMediaItem(getMyPreferenceManager().getLastPlayedMedia()),
                        getMyPreferenceManager().getQueuePosition()
                );
            }
            else{
                Toast.makeText(this, "select something to play", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public MyApplication getMyApplicationInstance() {
        return mMyApplication;
    }

    @Override
    public MyPreferenceManager getMyPreferenceManager() {
        return mMyPrefManager;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(!getMyPreferenceManager().getPlaylistId().equals("")){                                   // there is data saved in the saved pref, load it
            prepareLastPlayedMedia();
        }
        else{
            mMediaBrowserHelper.onStart(mWasConfigurationChange);                                   // how we connect client to service
        }
    }

    /**
     * In a production app you'd want to get this data from a cache.
     */
    private void prepareLastPlayedMedia(){

        Log.d(TAG, "prepareLastPlayedMedia: METHOD CALLED");
        showProgressBar();

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Query query  = firestore
                .collection("Audio")
                .document("avY6kRCpfAilLjIRB1Gd")
                .collection(getMyPreferenceManager().getLastCategory())
                .document(getMyPreferenceManager().getLastPlayedArtist())
                .collection(getString(R.string.collection_content))
                .orderBy(getString(R.string.field_date_added), Query.Direction.ASCENDING);

        Log.d(TAG, "prepareLastPlayedMedia: CATEGORY "+getMyPreferenceManager().getLastCategory());
        Log.d(TAG, "prepareLastPlayedMedia: LAST PLAYED ARTIST "+getMyPreferenceManager().getLastPlayedArtist());

        final List<MediaMetadataCompat> mediaItems = new ArrayList<>();                                                                                   // storing playlist media from cloud
        query.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                Log.d(TAG, "onComplete: METHOD CALLED");
                if (task.isSuccessful()) {
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        MediaMetadataCompat mediaItem = addToMediaList(document);
                        mediaItems.add(mediaItem);
                        Log.d(TAG, "onComplete: LAST PLAYED MEDIA "+getMyPreferenceManager().getLastPlayedMedia());
                        if(mediaItem.getDescription().getMediaId().equals(getMyPreferenceManager().getLastPlayedMedia())){                              // if this media was the one playing, set the title in the media controller

                            Log.d(TAG, "onComplete: CONDITION CALLED MEDIA CONTROLLER TITLE SHOULD GET SET");
                            getMediaControllerFragment().setMediaTitle(mediaItem);
                        }
                    }

                } else {
                    Log.d(TAG, "Error getting documents: ", task.getException());
                    Toast.makeText(mMyApplication, "Error getting documents:"+ task.getException(), Toast.LENGTH_SHORT).show();
                }
                onFinishedGettingPreviousSessionData(mediaItems);                                   //saving
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: AN ERROR OCCURED "+e.getMessage());
                Toast.makeText(mMyApplication, "An error occurred "+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onFinishedGettingPreviousSessionData(List<MediaMetadataCompat> mediaItems) {
        Log.d(TAG, "onFinishedGettingPreviousSessionData: METHOD CALLED");
        mMyApplication.setMediaItems(mediaItems);
        mMediaBrowserHelper.onStart(mWasConfigurationChange);
        hideProgressBar();
    }

    /**
     * Translate the Firestore data into something the MediaBrowserService can deal with (MediaMetaDataCompat objects)
     * @param document
     */
    private MediaMetadataCompat addToMediaList(QueryDocumentSnapshot document) {

        Log.d(TAG, "addToMediaList: METHOD CALLED");
        //deconstructing the received document from cloud
        MediaMetadataCompat media = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, document.getString(getString(R.string.field_media_id)))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, document.getString(getString(R.string.field_artist)))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, document.getString(getString(R.string.field_title)))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, document.getString(getString(R.string.field_media_url)))
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, document.getString(getString(R.string.field_description)))
                .putString(MediaMetadataCompat.METADATA_KEY_DATE, document.getDate(getString(R.string.field_date_added)).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, getMyPreferenceManager().getLastPlayedArtistImage())
                .build();

        Log.d(TAG, "addToMediaList: GET ARTIST IMAGE "+ getMyPreferenceManager().getLastPlayedArtistImage());
        return media;

    }

    @Override
    public void onMediaSelected(String playlistId,MediaMetadataCompat mediaItem,int queuePosition) {

        if (mediaItem != null) {
            Log.d(TAG, "onMediaSelected: CALLED: " + mediaItem.getDescription().getMediaId());

            String currentPlaylistId = getMyPreferenceManager().getPlaylistId();                                                            // getting playlist id from shared pref

            Bundle bundle = new Bundle();
            bundle.putInt(MEDIA_QUEUE_POSITION, queuePosition);                                                                             // sending the que index of the selected song to the fragment playlist

            if (playlistId.equals(currentPlaylistId)) {
                mMediaBrowserHelper.getTransportControls().playFromMediaId(mediaItem.getDescription().getMediaId(),bundle);                   // sending the selected media and index in the bundle to service class
                mMediaBrowserHelper.subscribeToNewPlaylist(playlistId);
            }

        else {
                bundle.putBoolean(QUEUE_NEW_PLAYLIST, true);                                                                                // let the player know this is a new playlist
                mMediaBrowserHelper.subscribeToNewPlaylist(playlistId);
                mMediaBrowserHelper.getTransportControls().playFromMediaId(mediaItem.getDescription().getMediaId(),null);
            }

            mOnAppOpen = true;                                                                                                              // open was opened and a media was played

        } else {
            Toast.makeText(this, "select something to play", Toast.LENGTH_SHORT).show();
        }
    }
        @Override
    protected void onStop() {
        super.onStop();
        mMediaBrowserHelper.onStop();
            getMediaControllerFragment().getMediaSeekBar().disconnectController();                // disconnecting the seekbar from the media controller
    }

    @Override
    protected void onPause() {
        super.onPause();

        //unregistering the broadcast receiver when state paused
        if(mSeekbarBroadcastReceiver != null)
        {
            unregisterReceiver(mSeekbarBroadcastReceiver);
        }

        //unregistering the broadcast receiver when state paused
        if(mUpdateUIBroadcastReceiver != null)
        {
            unregisterReceiver(mUpdateUIBroadcastReceiver);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        initUpdateUIBroadcastReceiver();
        initSeekBarBroadcastReceiver();

    }

    private void loadFragment(Fragment fragment, boolean lateralMovement){
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        //for the animation of fragments
        if(lateralMovement){
            transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left);
        }

        String tag = "";                                                                            // like an id for the fragment
        if(fragment instanceof HomeFragment){

            tag = getString(R.string.fragment_home);
        }
        else if(fragment instanceof CategoryFragment){
            tag = getString(R.string.fragment_category);
            transaction.addToBackStack(tag);                    // manually adding fragment to backStack
        }
        else if(fragment instanceof PlaylistFragment){
            tag = getString(R.string.fragment_playlist);
            transaction.addToBackStack(tag);
        }

        transaction.add(R.id.container, fragment, tag);                                             //adding fragment to transaction
        transaction.commit();

        MainActivityFragmentManager.getInstance().addFragment(fragment);                            // adding fragment to list fragment, when newly created
    }

    //when going backwards
    private void showFragment(Fragment fragment, boolean backwardsMovement){
        // Show selected fragment
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if(backwardsMovement){
            transaction.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right);
        }
        transaction.show(fragment);                                                                 // show fragment when back button pressed
        transaction.commit();

        // hide the others fragments
        for(Fragment f: MainActivityFragmentManager.getInstance().getFragments()){
            if(f != null){
                if(!f.getTag().equals(fragment.getTag())){
                    FragmentTransaction t = getSupportFragmentManager().beginTransaction();
                    t.hide(f); // will not destroy fragment
                    t.commit();
                }
            }
        }
    }

    private static final String KEY_ = "";

    @Override
    public void onBackPressed()  {


        ArrayList<Fragment> fragments = new ArrayList<>(MainActivityFragmentManager.getInstance().getFragments());                      //getting  all fragments

        if(fragments.size() > 1){
            FragmentTransaction t = getSupportFragmentManager().beginTransaction();
            t.remove(fragments.get(fragments.size() - 1));                                               // destroy fragment currently in view
            t.commit();
            MainActivityFragmentManager.getInstance().removeFragment(fragments.size() - 1);     //
            showFragment(fragments.get(fragments.size() - 2), true);
        }
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("active_fragments", MainActivityFragmentManager.getInstance().getFragments().size());
    }

    @Override
    public void hideProgressBar() {
        Log.d(TAG, "hideProgressBar: METHOD CALLED");
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void showProgressBar() {
        Log.d(TAG, "showProgressBar: METHOD CALLED");
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCategorySelected(String category) {
        loadFragment(CategoryFragment.newInstance(category),true);
    }

    @Override
    public void onArtistSelected(String category, Artist artist) {
    loadFragment(PlaylistFragment.newInstance(category,artist),true);
    }

    @Override
    public void setActionBarTitle(String title) {
        getSupportActionBar().setTitle(title);
    }

    @Override
    public void onMetadataChanged(MediaMetadataCompat metadata) {
        Log.d(TAG, "onMetadataChanged: called");
        if(metadata == null){
            return;
        }

        // Do stuff with new Metadata, setting the title of the song in the media Controller
        if(getMediaControllerFragment() != null){
            getMediaControllerFragment().setMediaTitle(metadata);
        }

    }

    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state) {

        isPlaying = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;         // true if state is playing

        // getting the media controller and updating UI
        if(getMediaControllerFragment() != null){
            getMediaControllerFragment().setIsPlaying(isPlaying);
        }


    }

    @Override
    public void onMediaControllerConnected(MediaControllerCompat mediaController) {
        //method called when the client and the service connected successfully
        getMediaControllerFragment().getMediaSeekBar().setMediaController(mediaController);         // linking the seek bar to the media player
    }

    //init the broadcast receiver
    private void initSeekBarBroadcastReceiver(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getString(R.string.broadcast_seekbar_update));
        mSeekbarBroadcastReceiver = new SeekBarBroadcastReceiver();
        registerReceiver(mSeekbarBroadcastReceiver, intentFilter);
    }

    //init the UI broadcast receiver
    private void initUpdateUIBroadcastReceiver(){
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(getString(R.string.broadcast_update_ui));
        mUpdateUIBroadcastReceiver = new UpdateUIBroadcastReceiver();
        registerReceiver(mUpdateUIBroadcastReceiver, intentFilter);
    }

    private class UpdateUIBroadcastReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            String newMediaId = intent.getStringExtra(getString(R.string.broadcast_new_media_id));
            Log.d(TAG, "onReceive: CALLED: " + newMediaId);
            if(getPlaylistFragment() != null){
                Log.d(TAG, "onReceive: " + mMyApplication.getMediaItem(newMediaId).getDescription().getMediaId());
                getPlaylistFragment().updateUI(mMyApplication.getMediaItem(newMediaId));
            }
        }
    }

    private PlaylistFragment getPlaylistFragment(){

        PlaylistFragment playlistFragment = (PlaylistFragment)getSupportFragmentManager()
                .findFragmentByTag(getString(R.string.fragment_playlist));                          // initialising the playlist fragment, so we update it
        if(playlistFragment != null){
            return playlistFragment;
        }
        return null;
    }

    private class SeekBarBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            long seekProgress = intent.getLongExtra(SEEK_BAR_PROGRESS, 0);
            long seekMax = intent.getLongExtra(SEEK_BAR_MAX, 0);

            // if the user is actively sliding the seekbar condition
//            if(!getMediaControllerFragment().getMediaSeekBar().isTracking()){
//
//                getMediaControllerFragment().getMediaSeekBar().setProgress((int)seekProgress);      // set the current time of the playing media
//                getMediaControllerFragment().getMediaSeekBar().setMax((int)seekMax);                // set the max time of the media
//
//            }

            getMediaControllerFragment().getMediaSeekBar().setProgress((int)seekProgress);          // set the current time of the playing media
            getMediaControllerFragment().getMediaSeekBar().setMax((int)seekMax);                    // set the max time of the media
        }
    }


}
