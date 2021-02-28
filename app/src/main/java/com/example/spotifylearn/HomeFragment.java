package com.example.spotifylearn;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;

import Adapters.HomeRecyclerAdapter;

public class HomeFragment extends Fragment implements HomeRecyclerAdapter.IHomeSelector
{
// implementing the interface from adapter
    private static final String TAG = "HomeFragment";

    // UI Components
    private RecyclerView mRecyclerView;
    private IMainActivity mIMainActivity;

    // Vars
    private HomeRecyclerAdapter mAdapter;
    private ArrayList<String> mCategories = new ArrayList<>();


    public static HomeFragment newInstance(){
        return new HomeFragment();
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mIMainActivity = (IMainActivity) getActivity();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);                    // prevends the instance state from getting messed up during config changes
    }

    @Override
    public void onHiddenChanged(boolean hidden) {

        if(!hidden){
            mIMainActivity.setActionBarTitle(getString(R.string.categories));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initRecyclerView(view);
        mIMainActivity.setActionBarTitle(getString(R.string.categories));
    }

    private void initRecyclerView(View view){
        Log.d(TAG, "initRecyclerView: METHOD CALLED");
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new HomeRecyclerAdapter(mCategories, getActivity(),  this);
        mRecyclerView.setAdapter(mAdapter);


        if(mCategories.size() == 0){
            Log.d(TAG, "initRecyclerView: mCATEGORIES SIZE == 0");
            retrieveCategories();
            
        }

    }

    @Override
    public void onCategorySelected(int postion) {

        mIMainActivity.onCategorySelected(mCategories.get(postion));
        Log.d(TAG, "onCategorySelected: View clicked");
    }

    private void retrieveCategories(){

        mIMainActivity.showProgressBar();
        Log.d(TAG, "retrieveCategories: METHOD CALLED");
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        DocumentReference ref = firestore
                .collection("Audio")
                .document("avY6kRCpfAilLjIRB1Gd");

        ref.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.isSuccessful()){
                    DocumentSnapshot doc = task.getResult();
                    Log.d(TAG, "GOT DATA FROM DATABASE: " + doc);
                    HashMap<String, String> categoriesMap = (HashMap)doc.getData().get("Categories");
                    mCategories.addAll(categoriesMap.keySet());
                    Log.d(TAG, "onComplete: mCATEGORIES SET");
                }
                updateDataSet();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(getActivity(), "ERROR OCCURRED"+ e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onFailure: AN ERROR OCCURRED");
            }
        });
    }

    private void updateDataSet(){
        mIMainActivity.hideProgressBar();
        mAdapter.notifyDataSetChanged();
    }
}
