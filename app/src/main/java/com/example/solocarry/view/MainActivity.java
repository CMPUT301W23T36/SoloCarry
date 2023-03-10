package com.example.solocarry.view;

import static android.content.ContentValues.TAG;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory;
import com.example.solocarry.R;
import com.example.solocarry.controller.UserController;
import com.example.solocarry.model.Code;
import com.example.solocarry.model.User;
import com.example.solocarry.util.AuthUtil;
import com.example.solocarry.util.CustomInfoWindowAdapter;
import com.example.solocarry.util.DatabaseUtil;
import com.example.solocarry.util.MapUtil;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.github.clans.fab.FloatingActionButton;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.StorageReference;
import com.kongzue.dialogx.DialogX;
import com.kongzue.dialogx.dialogs.WaitDialog;
import com.kongzue.dialogx.style.MIUIStyle;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private MapUtil mapUtil;
    private AuthUtil authUtil;
    private Circle circle;
    private int radius = 1000;
    private Color circleColor;
    private com.google.android.material.floatingactionbutton.FloatingActionButton userPhoto;
    private ArrayList<Code> codeList;
    private boolean codeListChanged;
    private HashMap<String, String> images;

    private static final int REQUEST_CAMERA_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseFirestore db = DatabaseUtil.getFirebaseFirestoreInstance();

        //load user info
        authUtil = new AuthUtil();

        //Initialize map fragment
        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        MapStyleOptions style = new MapStyleOptions(getString(R.string.black_golden_map_style));
        assert mapFragment != null;
        mapUtil = new MapUtil(this,mapFragment,style);


        DialogX.init(this);
        DialogX.globalStyle = MIUIStyle.style();
        DialogX.globalTheme = DialogX.THEME.DARK;

        WaitDialog.show("Loading Map...");

        // Set up an OnPreDrawListener to the root view. (if not started drawing, stay at splash screen)
        final View content = findViewById(android.R.id.content);
        circleColor = Color.valueOf(255,232,124);
        codeListChanged = false;

        content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public boolean onPreDraw() {
                        // Check if the initial data is ready.
                        if (mapUtil.isMapReady()) {
                            // The content is ready; start drawing.
                            content.getViewTreeObserver().removeOnPreDrawListener(this);

                            //draw circle
                            mapUtil.getLocationUtil().getFusedLocationProviderClient().requestLocationUpdates(mapUtil.getLocationUtil().getLocationRequest(), new LocationCallback() {
                                @Override
                                public void onLocationResult(@NonNull LocationResult locationResult) {
                                    super.onLocationResult(locationResult);
                                    if (locationResult.getLastLocation() != null) {
                                        if (circle!=null){
                                            circle.remove();
                                        }
                                        circle = mapUtil.getgMap().addCircle(new CircleOptions().center(new LatLng(locationResult.getLastLocation().getLatitude(), locationResult.getLastLocation().getLongitude()))
                                                .radius(radius)
                                                .strokeColor(circleColor.toArgb())
                                                .strokeWidth(35)
                                                .strokePattern(Arrays.asList(new Dash(400), new Gap(300))));

                                        if(codeList==null){
                                            codeList = new ArrayList<>();
                                            images = new HashMap<>();
                                            db.collection("codes")
                                                    .whereEqualTo("showPublic",true)
                                                    .get().addOnSuccessListener(queryDocumentSnapshots -> {
                                                        for (DocumentSnapshot document:
                                                                queryDocumentSnapshots.getDocuments()) {
                                                            Code tempCode = document.toObject(Code.class);
                                                            codeList.add(tempCode);
                                                            images.put(tempCode.getName(),tempCode.getPhoto());
                                                            if (mapUtil.isMapReady()) {
                                                                float[] results = new float[1];
                                                                for (Code code:
                                                                        codeList) {
                                                                    Location.distanceBetween(locationResult.getLastLocation().getLatitude(),locationResult.getLastLocation().getLongitude(),
                                                                            code.getLatitude(),code.getLongitude(), results);
                                                                    if (results[0]<radius){
                                                                        LatLng markerLatLng = new LatLng(code.getLatitude(),code.getLongitude());
                                                                        mapUtil.getgMap().addMarker(new MarkerOptions()
                                                                                .position(markerLatLng)
                                                                                .title(code.getName())
                                                                                .snippet("Worth: "+code.getScore())
                                                                                .icon(BitmapDescriptorFactory.defaultMarker(Code.worthToColor(code.getScore()))));
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        WaitDialog.dismiss();
                                                        //set custom marker info window
                                                        mapUtil.getgMap().setInfoWindowAdapter(new CustomInfoWindowAdapter(MainActivity.this, images));
                                                    });
                                        }else{
                                            if (codeListChanged){
                                                mapUtil.getgMap().clear();
                                                float[] results = new float[1];
                                                for (Code code:
                                                        codeList) {
                                                    Location.distanceBetween(locationResult.getLastLocation().getLatitude(),locationResult.getLastLocation().getLongitude(),
                                                            code.getLatitude(),code.getLongitude(), results);
                                                    if (results[0]<radius){
                                                        LatLng markerLatLng = new LatLng(code.getLatitude(),code.getLongitude());
                                                        mapUtil.getgMap().addMarker(new MarkerOptions().position(markerLatLng).title(code.getName()).snippet("Worth: "+code.getScore()).icon(BitmapDescriptorFactory.defaultMarker(Code.worthToColor(code.getScore()))));
                                                    }
                                                }
                                                //set custom marker info window
                                                mapUtil.getgMap().setInfoWindowAdapter(new CustomInfoWindowAdapter(MainActivity.this, images));

                                                codeListChanged = false;
                                            }
                                        }
                                    }
                                }
                            }, Looper.getMainLooper());

                            return true;
                        } else {
                            // The content is not ready; suspend.
                            return false;
                        }
                    }
                });

        //listen to active upload
        db.collection("codes").whereEqualTo("showPublic", true)
                .addSnapshotListener((value, error) -> {
                    if(error==null&&!value.isEmpty()){
                        codeListChanged = true;
                        if(codeList!=null){
                            codeList.clear();
                            images.clear();
                            for (QueryDocumentSnapshot doc : value) {
                                Code code = doc.toObject(Code.class);
                                codeList.add(code);
                                images.put(code.getName(),code.getPhoto());
                            }
                        }
                    }
                });

        userPhoto = findViewById(R.id.userPhoto);
        DrawableCrossFadeFactory factory =
                new DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build();
        RequestOptions requestOptions = new RequestOptions()
                .placeholder(R.drawable.ic_logo)
                .error(R.drawable.ic_logo)
                .fallback(R.drawable.ic_logo)
                .override(100,100);
        Glide.with(this)
                .load(authUtil.getUser().getPhotoUrl())
                .override(96,96)
                .centerCrop()
                .apply(requestOptions)
                .transform(new CircleCrop())
                .transition(DrawableTransitionOptions.withCrossFade(factory))
                .into(userPhoto);

        FloatingActionButton signOutButton = findViewById(R.id.sign_out_dropdown_item);
        signOutButton.setOnClickListener(view -> {
            AuthUtil.SignOut();
            Intent intent = new Intent(MainActivity.this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(MainActivity.this).toBundle());
        });

        com.google.android.material.floatingactionbutton.FloatingActionButton cameraButton = findViewById(R.id.camera_button);
        cameraButton.setOnClickListener(view -> {
            // Check if camera permission is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // Request camera permission
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                // Camera permission already granted, proceed with camera activity
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });

        com.google.android.material.floatingactionbutton.FloatingActionButton chatButton = findViewById(R.id.chat_button);
        chatButton.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        FloatingActionButton contactButton = findViewById(R.id.contact_dropdown_item);
        contactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this,ContactMenuActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });

        com.google.android.material.floatingactionbutton.FloatingActionButton rank = findViewById(R.id.ranking_button);
        rank.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RankingActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });

        setFloatingActionButtonTransition();
        setCodePanel();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Camera permission granted, proceed with camera activity
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                startActivity(intent);
            } else {
                // Camera permission denied, show error message or take alternative action
                Toast.makeText(this, "Camera permission required to use this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        //stop sensor listener when pause
        mapUtil.getSensorManager().unregisterListener(mapUtil,mapUtil.getSensor());
    }

    @Override
    protected void onResume() {
        super.onResume();
        //re-register sensor listener when resume
        mapUtil.getSensorManager().registerListener(mapUtil,mapUtil.getSensor(), SensorManager.SENSOR_DELAY_NORMAL,SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void setFloatingActionButtonTransition(){
        final FloatingActionMenu fam = (FloatingActionMenu) findViewById(R.id.dropdown_menu);
        AnimatorSet set = new AnimatorSet();

        ObjectAnimator scaleOutX = ObjectAnimator.ofFloat(fam.getMenuIconView(), "scaleX", 1.0f, 0.2f);
        ObjectAnimator scaleOutY = ObjectAnimator.ofFloat(fam.getMenuIconView(), "scaleY", 1.0f, 0.2f);

        ObjectAnimator scaleInX = ObjectAnimator.ofFloat(fam.getMenuIconView(), "scaleX", 0.2f, 1.0f);
        ObjectAnimator scaleInY = ObjectAnimator.ofFloat(fam.getMenuIconView(), "scaleY", 0.2f, 1.0f);

        scaleOutX.setDuration(50);
        scaleOutY.setDuration(50);

        scaleInX.setDuration(150);
        scaleInY.setDuration(150);

        scaleInX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                fam.getMenuIconView().setImageResource(fam.isOpened()
                        ? R.drawable.ic_menu : R.drawable.ic_expand_less);
            }
        });

        set.play(scaleOutX).with(scaleOutY);
        set.play(scaleInX).with(scaleInY).after(scaleOutX);
        set.setInterpolator(new OvershootInterpolator(2));

        fam.setIconToggleAnimatorSet(set);
    }

    private void setCodePanel(){
        final com.google.android.material.floatingactionbutton.FloatingActionButton filterButton = findViewById(R.id.button_filter);
        filterButton.setOnClickListener(v -> {
            FilterFragment dialogFrag = FilterFragment.newInstance();
            dialogFrag.setParentFab(filterButton);
            dialogFrag.show(getSupportFragmentManager(), dialogFrag.getTag());
        });
    }
}