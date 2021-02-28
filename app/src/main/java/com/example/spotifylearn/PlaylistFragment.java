package com.example.spotifylearn;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

import Adapters.PlaylistRecyclerAdapter;
import Models.Artist;

public class PlaylistFragment extends Fragment implements PlaylistRecyclerAdapter.IMediaSelector{

    private static final String TAG = "PlaylistFragment";

    // UI Components
    private RecyclerView mRecyclerView;

    // Vars
    private PlaylistRecyclerAdapter mAdapter;
    private ArrayList<MediaMetadataCompat> mMediaList = new ArrayList<>();                          // all playlist media
    private IMainActivity mIMainActivity;
    private String mSelectedCategory;
    private Artist mSelectArtist;
    private MediaMetadataCompat mSelectedMedia;                                                     // media that's playing


    public static PlaylistFragment newInstance(String category, Artist artist){                     // creating a fragment with
        PlaylistFragment playlistFragment = new PlaylistFragment();
        Bundle args = new Bundle();
        args.putString("category", category);
        args.putParcelable("artist", artist);
        playlistFragment.setArguments(args);
        return playlistFragment;
    }

    public void updateUI(MediaMetadataCompat mediaItem){
        //setting the index of the newly selected media in the list
        mAdapter.setSelectedIndex(mAdapter.getIndexOfItem(mediaItem));
        mSelectedMedia = mediaItem;
        saveLastPlayedSongProperties();
    }

//    @Override
//    public void onHiddenChanged(boolean hidden) {
//        if(!hidden){
//            mIMainActivity.setActionBarTitle(mSelectArtist.getTitle());
//        }
//    }

    @Override
    public void onHiddenChanged(boolean hidden) {

        // called when we press back, setting the title bar title
        if(!hidden){
            mIMainActivity.setActionBarTitle(mSelectArtist.getTitle());
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // getting arguments from other fragments
        super.onCreate(savedInstanceState);
        if(getArguments() != null){
            mSelectedCategory = getArguments().getString("category");
            mSelectArtist = getArguments().getParcelable("artist");
        }
        setRetainInstance(true);                 // ???
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initRecyclerView(view);
        mIMainActivity.setActionBarTitle(mSelectArtist.getTitle());                                 // setting action bar title

        // if we saved a Bundle outState before then we re access it
        if(savedInstanceState != null){
            mAdapter.setSelectedIndex(savedInstanceState.getInt("selected_index"));
        }

    }


/*
  *      Method for highlighting the right media item in the playlist,media that
  *      is currently in the media controller
 */


    private void getSelectedMediaItem(String mediaId){
        for(MediaMetadataCompat mediaItem: mMediaList){
            if(mediaItem.getDescription().getMediaId().equals(mediaId)){
                mSelectedMedia = mediaItem;
                mAdapter.setSelectedIndex(mAdapter.getIndexOfItem(mSelectedMedia));
                break;
            }
        }
    }

    private void retrieveMedia(){

        // getting doc from fireStore
        mIMainActivity.showProgressBar();

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Query query = firestore
                .collection("Audio")
                .document("avY6kRCpfAilLjIRB1Gd")
                .collection(mSelectedCategory)
                .document(mSelectArtist.getArtist_id())
                .collection("Content").orderBy("date_added", Query.Direction.ASCENDING);

        query.get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if(task.isSuccessful()){
                    for(QueryDocumentSnapshot document: task.getResult()){
                        Log.d(TAG, "onComplete: GOT DOC FROM BD");
                        addToMediaList(document);
                    }
                }
                else{
                    Log.d(TAG, "onComplete: error getting documents: " + task.getException());
                }
                updateDataSet();
            }


        });
    }

    private void addToMediaList(QueryDocumentSnapshot document) {
        //creating mediaMetaData
        Log.d(TAG, "addToMediaList: METHOD CALLED");
        MediaMetadataCompat media = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, document.getString(getString(R.string.field_media_id)))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, document.getString(getString(R.string.field_artist)))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, document.getString(getString(R.string.field_title)))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, document.getString(getString(R.string.field_media_url)))
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, document.getString(getString(R.string.field_description)))
                .putString(MediaMetadataCompat.METADATA_KEY_DATE, document.getDate(getString(R.string.field_date_added)).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, mSelectArtist.getImage())
                .build();


        mMediaList.add(media);
    }

    private void updateDataSet(){

        mIMainActivity.hideProgressBar();
        mAdapter.notifyDataSetChanged();

          //setting and highlighting the index of the song that is currently playing in the media controller
        if(mIMainActivity.getMyPreferenceManager().getLastPlayedArtist().equals(mSelectArtist.getArtist_id())){
            getSelectedMediaItem(mIMainActivity.getMyPreferenceManager().getLastPlayedMedia());
        }


    }

    private void initRecyclerView(View view){
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new PlaylistRecyclerAdapter(getActivity(), mMediaList, this);
        mRecyclerView.setAdapter(mAdapter);

        if(mMediaList.size() == 0){
            retrieveMedia();
        }
    }

    @Override
    public void onAttach(Context context) {

        super.onAttach(context);
        mIMainActivity = (IMainActivity) getActivity();

    }

    @Override
    public void onMediaSelected(int position) {

        //@position ,,, position of the selected  item in the list

        // adding the selected playlist to myApplication class list
        mIMainActivity.getMyApplicationInstance().setMediaItems(mMediaList);                        // store selected playlist to , myApplication
        mSelectedMedia = mMediaList.get(position);                                                  //setting the selected media in the playlist
        mAdapter.setSelectedIndex(position);                                                        // tell the adapter what has been selected


        mIMainActivity.onMediaSelected(mSelectArtist.getArtist_id(),mSelectedMedia,position);       // calling method from mainActivity
        saveLastPlayedSongProperties();

    }

//    @Override
//    public void onMediaSelected(int position) {
//        mIMainActivity.getMyApplicationInstance().setMediaItems(mMediaList);
//        mSelectedMedia = mMediaList.get(position);
//        mAdapter.setSelectedIndex(position);
//        mIMainActivity.onMediaSelected(
//                mSelectArtist.getArtist_id(), // playlist_id = artist_id
//                mMediaList.get(position),
//                position);
//        saveLastPlayedSongProperties();
//    }

//    public void updateUI(MediaMetadataCompat mediaItem){
//        mAdapter.setSelectedIndex(mAdapter.getIndexOfItem(mediaItem));
//        mSelectedMedia = mediaItem;
//        saveLastPlayedSongProperties();
//    }

    private void saveLastPlayedSongProperties(){

        // Save some properties for next time the app opens
        // NOTE: Normally you'd do this with a cache

        mIMainActivity.getMyPreferenceManager().savePlaylistId(mSelectArtist.getArtist_id());                            // playlist id is same as an artist id from bd
        mIMainActivity.getMyPreferenceManager().saveLastPlayedArtist(mSelectArtist.getArtist_id());
        mIMainActivity.getMyPreferenceManager().saveLastPlayedCategory(mSelectedCategory);
        mIMainActivity.getMyPreferenceManager().saveLastPlayedArtistImage(mSelectArtist.getImage());
        mIMainActivity.getMyPreferenceManager().saveLastPlayedMedia(mSelectedMedia.getDescription().getMediaId());

        Log.d(TAG, "saveLastPlayedSongProperties: SAVED ARTIST "+mSelectArtist.getArtist_id());
        Log.d(TAG, "saveLastPlayedSongProperties: SAVED CATEGORY "+mSelectedCategory);
        Log.d(TAG, "saveLastPlayedSongProperties: SAVED ARTIST IMAGE "+mSelectArtist.getImage());
        Log.d(TAG, "saveLastPlayedSongProperties: SAVED LAST PLAYED MEDIA "+mSelectedMedia.getDescription().getMediaId());


    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        //saving the selected index of the currenly subscribed playlist
        super.onSaveInstanceState(outState);
        outState.putInt("selected_index", mAdapter.getSelectedIndex());
    }
}