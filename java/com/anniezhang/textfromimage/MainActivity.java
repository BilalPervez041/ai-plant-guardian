package com.anniezhang.textfromimage;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    ImageView imageView2;
    TextView placeholderText;
    View chose, btnCapture, btnHistory;

    private Uri photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chose = findViewById(R.id.chose);
        imageView2 = findViewById(R.id.imageView2);
        btnCapture = findViewById(R.id.btnCapture);
        btnHistory = findViewById(R.id.btnHistory);
        placeholderText = findViewById(R.id.placeholderText);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }

        if (chose != null) {
            chose.setOnClickListener(v -> openImageSelector());
        }
        
        if (btnCapture != null) {
            btnCapture.setOnClickListener(v -> takePhoto());
        }

        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(intent);
            });
        }
    }

    private void openImageSelector() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        someActivityResultLauncher.launch(Intent.createChooser(intent, "Choose an image"));
    }

    private final ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && data.getData() != null) {
                            Uri imageUri = data.getData();
                            placeholderText.setVisibility(View.GONE);
                            imageView2.setImageURI(imageUri);
                            
                            // Save a copy of the gallery image for history persistence
                            Uri savedUri = null;
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                                savedUri = saveImage(bitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // Navigate to ResultActivity
                            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                            intent.putExtra("imageUri", (savedUri != null ? savedUri : imageUri).toString());
                            startActivity(intent);
                        }
                    }
                }
            }
    );

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        if (photoFile != null) {
            photoUri = androidx.core.content.FileProvider.getUriForFile(this,
                    "com.anniezhang.textfromimage.fileprovider",
                    photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            cameraLauncher.launch(intent);
        }
    }

    private File createImageFile() throws IOException {
        String imageFileName = "JPEG_" + System.currentTimeMillis() + "_";
        File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (photoUri != null) {
                            placeholderText.setVisibility(View.GONE);
                            imageView2.setImageURI(photoUri);

                            // Navigate to ResultActivity
                            Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                            intent.putExtra("imageUri", photoUri.toString());
                            startActivity(intent);
                        }
                    }
                }
            }
    );

    private Uri saveImage(Bitmap bitmap) {
        File imagesFolder = new File(getCacheDir(), "images");
        if (!imagesFolder.exists()) imagesFolder.mkdirs();
        File file = new File(imagesFolder, "captured_image_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            return Uri.fromFile(file);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
