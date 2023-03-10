package com.example.solocarry.view;

import static android.content.ContentValues.TAG;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.Toast;

import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.solocarry.R;
import com.example.solocarry.controller.CodeController;
import com.example.solocarry.controller.UserController;
import com.example.solocarry.model.Code;
import com.example.solocarry.model.User;
import com.example.solocarry.util.AuthUtil;
import com.example.solocarry.util.DatabaseUtil;
import com.example.solocarry.util.LocationUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.type.DateTime;
import com.kongzue.dialogx.DialogX;
import com.kongzue.dialogx.dialogs.MessageDialog;
import com.kongzue.dialogx.dialogs.TipDialog;
import com.kongzue.dialogx.dialogs.WaitDialog;
import com.kongzue.dialogx.style.MIUIStyle;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Date;

public class CodePreferenceActivity extends AppCompatActivity{

    private Button confirmButton;
    private EditText editTextCodeName;
    private EditText editTextCodeComment;
    private Switch codeImagePreference;
    private ImageView imageView, cancelButton;
    private Bitmap randomBitmap;
    private Bitmap customBitmap;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_preference);

        DialogX.init(this);
        DialogX.globalStyle = MIUIStyle.style();
        DialogX.globalTheme = DialogX.THEME.DARK;

        Intent intent = getIntent();
        String SHA256 = intent.getStringExtra("hash");
        FirebaseFirestore db = DatabaseUtil.getFirebaseFirestoreInstance();
        String uid = AuthUtil.currentUser.getUid();

        codeImagePreference = findViewById(R.id.switch_code_thmbnail);
        confirmButton = findViewById(R.id.btn_upload_code);
        cancelButton = findViewById(R.id.button_back);
        imageView = findViewById(R.id.imageView_code);
        editTextCodeComment = findViewById(R.id.editText_code_comment);
        editTextCodeName = findViewById(R.id.editText_code_name);

        codeImagePreference.setOnCheckedChangeListener((compoundButton, b) -> {
            if(b){
                compoundButton.setText("Random");
                WaitDialog.show("Loading...");
                Picasso.get().load("https://robohash.org/"+SHA256).transform(new CircleTransform()).into(imageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        WaitDialog.dismiss();
                        TipDialog.show("Loading Success!", WaitDialog.TYPE.SUCCESS);
                        randomBitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
                    }

                    @Override
                    public void onError(Exception e) {
                        WaitDialog.dismiss();
                        TipDialog.show("Loading Error!", WaitDialog.TYPE.ERROR);
                    }
                });
            }else{
                compoundButton.setText("Custom");
                if (customBitmap!=null){
                    imageView.setImageBitmap(customBitmap);
                }else{
                    imageView.setImageResource(R.mipmap.user_default);
                }
            }
        });

        imageView.setOnClickListener(view -> {
            if(!codeImagePreference.isChecked()){
                //if custom, take picture
                Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); //IMAGE CAPTURE CODE
                startActivityForResult(captureIntent, 100);
            }else{
                Picasso.get().load("https://picsum.photos/200").into(imageView);
            }
        });

        confirmButton.setOnClickListener(view -> {
            WaitDialog.show("Uploading...");
            if(editTextCodeName.getText().toString().trim().length()<2){
                WaitDialog.dismiss();
                TipDialog.show("Input at least 2 characters for code name!", WaitDialog.TYPE.WARNING);
            }else if(!codeImagePreference.isChecked()&&customBitmap==null){
                WaitDialog.dismiss();
                TipDialog.show("No custom image uploaded!", WaitDialog.TYPE.WARNING);
            }else if(codeImagePreference.isChecked()&&randomBitmap==null){
                WaitDialog.dismiss();
                TipDialog.show("No random image loaded!", WaitDialog.TYPE.WARNING);
            }else{
                //see if code name unique
                DocumentReference userDocRef = UserController.getUser(uid);
                db.collection("codes").whereEqualTo("name",editTextCodeName.getText().toString().trim()).count()
                        .get(AggregateSource.SERVER).addOnSuccessListener(task -> {
                            if(task.getCount()==0){
                                //get location
                                LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                                @SuppressLint("MissingPermission") Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                                double longitude = location.getLongitude();
                                double latitude = location.getLatitude();

                                FirebaseStorage storage = DatabaseUtil.getFirebaseStorageInstance();
                                StorageReference storageReference = storage.getReferenceFromUrl("gs://solocarry-8bf9e.appspot.com/");
                                String storagePath = uid+ LocalDateTime.now().toString()+".jpg";
                                StorageReference codeRef = storageReference.child(storagePath);

                                Code code = new Code(SHA256, editTextCodeName.getText().toString().trim(), true);
                                code.setLocation((float) latitude, (float) longitude);
                                code.setComment(editTextCodeComment.getText().toString());
                                code.updateScore(Code.hashCodeToScore(SHA256));
                                code.setPhoto(storagePath);

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                Bitmap bitmap = codeImagePreference.isChecked()?randomBitmap:customBitmap;
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                                byte[] data = baos.toByteArray();
                                UploadTask uploadTask = codeRef.putBytes(data);
                                uploadTask.addOnSuccessListener(taskSnapshot -> {
                                            //add to user collection
                                            db.collection("users").document(uid)
                                                    .collection("codes").document(SHA256)
                                                    .set(code)
                                                    .addOnSuccessListener(unused -> {
                                                        //add score
                                                        userDocRef.get().addOnSuccessListener(documentSnapshot1 -> {
                                                            User user = UserController.transformUser(documentSnapshot1);
                                                            user.addScore(Code.hashCodeToScore(SHA256));
                                                            UserController.updateUser(user);
                                                            //add to code
                                                            CodeController.addCode(code,uid);
                                                            WaitDialog.dismiss();
                                                            TipDialog.show("Code Added!", WaitDialog.TYPE.SUCCESS);
                                                            MessageDialog.build()
                                                                    .setCancelButton("No, back to map")
                                                                    .setCancelButton((dialog, v) -> {
                                                                        dialog.dismiss();
                                                                        Intent homeIntent = new Intent(getApplicationContext(), MainActivity.class);
                                                                        homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                                                        startActivity(homeIntent);
                                                                        return false;
                                                                    })
                                                                    .setOkButton("Yes, back to camera")
                                                                    .setOkButton((dialog, v) -> {
                                                                        dialog.dismiss();
                                                                        finish();
                                                                        return false;
                                                                    })
                                                                    .setTitle("More code?")
                                                                    .setMessage("The code has been added to your collection, do you want to upload more code?")
                                                                    .setCancelable(false)
                                                                    .show();
                                                        });
                                                    });

                                        }
                                );
                            }else{
                                TipDialog.show("The code name should be unique!", WaitDialog.TYPE.WARNING);
                            }
                });
            }
        });

        cancelButton.setOnClickListener(view -> finish());

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            Rect rect = new Rect(-50, -50, 150, 150);
            assert(rect.left < rect.right && rect.top < rect.bottom);
            customBitmap = (Bitmap)data.getExtras().get("data");
            Bitmap resultBmp = Bitmap.createBitmap(rect.right-rect.left, rect.bottom-rect.top, Bitmap.Config.ARGB_8888);
            new Canvas(resultBmp).drawBitmap(customBitmap, -rect.left, -rect.top, null);
            imageView.setImageBitmap(customBitmap);
        }
    }

    class CircleTransform implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            int size = Math.min(source.getWidth(), source.getHeight());

            int x = (source.getWidth() - size) / 2;
            int y = (source.getHeight() - size) / 2;

            Bitmap squaredBitmap = Bitmap.createBitmap(source, x, y, size, size);
            if (squaredBitmap != source) {
                source.recycle();
            }

            Bitmap bitmap = Bitmap.createBitmap(size, size, source.getConfig());

            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            BitmapShader shader = new BitmapShader(squaredBitmap,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
            paint.setAntiAlias(true);

            float r = size / 2f;
            canvas.drawCircle(r, r, r, paint);

            squaredBitmap.recycle();
            return bitmap;
        }

        @Override
        public String key() {
            return "circle";
        }
    }
}