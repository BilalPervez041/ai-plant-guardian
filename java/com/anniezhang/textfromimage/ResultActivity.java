package com.anniezhang.textfromimage;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.automl.FirebaseAutoMLLocalModel;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ResultActivity extends AppCompatActivity {

    private ImageView resultImageView;
    private TextView resultTv, weatherTv, appTitleTv, timeTv;
    private Button btnBack, btnShare;
    private FirebaseVisionImageLabeler labeler;
    private FirebaseVisionImageLabeler genericLabeler;
    private FusedLocationProviderClient fusedLocationClient;
    private String plantName = "Unknown";
    private boolean isRainySoon = false;
    private String currentImageUri = "";

    // Professional Weather API Config (Updated with active key)
    private final String WEATHER_API_KEY = "1e0c6554f4874a53fe990bcbafcf7091";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        resultImageView = findViewById(R.id.resultImageView);
        resultTv = findViewById(R.id.resultTv);
        weatherTv = findViewById(R.id.textWeather); 
        timeTv = findViewById(R.id.textTime);
        appTitleTv = findViewById(R.id.textAppTitle);
        btnBack = findViewById(R.id.btnBack);
        btnShare = findViewById(R.id.btnShare);

        if (btnShare != null) {
            btnShare.setOnClickListener(v -> shareCurrentResult());
        }

        if (appTitleTv != null) {
            appTitleTv.setText(R.string.app_name);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        setupAutoMLModel();
        fetchLocationAndWeather();

        boolean fromHistory = getIntent().getBooleanExtra("fromHistory", false);
        if (fromHistory) {
            String fullResult = getIntent().getStringExtra("fullResult");
            String imageUriStr = getIntent().getStringExtra("imageUri");
            if (imageUriStr != null && !imageUriStr.isEmpty()) {
                resultImageView.setImageURI(Uri.parse(imageUriStr));
            }
            resultTv.setText(fullResult);
            return;
        }

        String imageUriStr = getIntent().getStringExtra("imageUri");
        Bitmap bitmap = getIntent().getParcelableExtra("bitmap");

        if (imageUriStr != null) {
            currentImageUri = imageUriStr;
            Uri imageUri = Uri.parse(imageUriStr);
            resultImageView.setImageURI(imageUri);
            processImage(imageUri);
        } else if (bitmap != null) {
            resultImageView.setImageBitmap(bitmap);
            processBitmap(bitmap);
        }

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupAutoMLModel() {
        // 1. Setup specialized Plant Disease Model
        FirebaseAutoMLLocalModel localModel = new FirebaseAutoMLLocalModel.Builder()
                .setAssetFilePath("Files/manifest.json")
                .build();
        try {
            FirebaseVisionOnDeviceAutoMLImageLabelerOptions options = new FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder(localModel)
                    .setConfidenceThreshold(0.25f) // Industrial standard threshold
                    .build();
            labeler = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(options);
        } catch (FirebaseMLException e) {
            e.printStackTrace();
        }

        // 2. Setup Generic Labeler to validate if input is actually a plant
        genericLabeler = FirebaseVision.getInstance().getOnDeviceImageLabeler();
    }

    private void fetchLocationAndWeather() {
        if (!isOnline()) {
            weatherTv.setText("Weather: Offline");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            weatherTv.setText("Weather: Syncing Location...");
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    getWeatherData(location.getLatitude(), location.getLongitude());
                } else {
                    // If last location is null, try to get current location (more accurate but slower)
                    weatherTv.setText("Weather: Locating...");
                    // Using default for Delhi only if everything else fails, or better yet, just show generic error
                    getWeatherData(28.6139, 77.2090); 
                }
            });
        } else {
            weatherTv.setText("Weather: Permission Required");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndWeather();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnected();
    }

    private void getWeatherData(double lat, double lon) {
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + WEATHER_API_KEY + "&units=metric";
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        String cityName = response.getString("name");
                        String weatherDescription = response.getJSONArray("weather").getJSONObject(0).getString("main");
                        double temp = response.getJSONObject("main").getDouble("temp");
                        isRainySoon = weatherDescription.toLowerCase().contains("rain") || weatherDescription.toLowerCase().contains("drizzle");
                        weatherTv.setText(cityName + ": " + (int)temp + "°C (" + weatherDescription + ")");
                        
                        // Set current local time
                        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                        if (timeTv != null) {
                            timeTv.setText("Local Time: " + currentTime);
                        }
                        
                        if (!plantName.equals("Unknown")) {
                            updateResultUI();
                        }
                    } catch (JSONException e) { 
                        weatherTv.setText("Weather: Data Format Error");
                    }
                }, error -> {
                    if (error.networkResponse != null) {
                        int code = error.networkResponse.statusCode;
                        if (code == 401) weatherTv.setText("Weather: API Key Expired");
                        else weatherTv.setText("Weather: HTTP " + code);
                    } else {
                        weatherTv.setText("Weather: Network Problem");
                    }
                });
        queue.add(jsonObjectRequest);
    }

    private void processImage(Uri imageUri) {
        try {
            FirebaseVisionImage image = FirebaseVisionImage.fromFilePath(this, imageUri);
            validateAndProcess(image);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void processBitmap(Bitmap bitmap) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        validateAndProcess(image);
    }

    private void validateAndProcess(FirebaseVisionImage image) {
        if (genericLabeler == null) {
            if (labeler != null) labeler.processImage(image).addOnSuccessListener(this::handleSuccess);
            return;
        }

        genericLabeler.processImage(image).addOnSuccessListener(labels -> {
            boolean isPlantRelated = false;
            for (FirebaseVisionImageLabel label : labels) {
                String text = label.getText().toLowerCase();
                if (text.contains("plant") || text.contains("leaf") || text.contains("tree") || 
                    text.contains("vegetable") || text.contains("fruit") || text.contains("flower") ||
                    text.contains("agriculture") || text.contains("nature") || text.contains("branch") ||
                    text.contains("shrub") || text.contains("flora") || text.contains("crop") ||
                    text.contains("houseplant") || text.contains("produce")) {
                    isPlantRelated = true;
                    break;
                }
            }

            if (isPlantRelated) {
                if (labeler != null) labeler.processImage(image).addOnSuccessListener(this::handleSuccess);
            } else {
                resultTv.setText("■ ANALYSIS NOTIFICATION:\n\n" +
                        "The system could not detect a valid plant or leaf specimen in this image.\n\n" +
                        "To provide a professional-grade diagnosis, please ensure that:\n" +
                        "1. The image clearly features a plant leaf or crop.\n" +
                        "2. There is adequate lighting and focus on the specimen.\n" +
                        "3. The background is neutral and free from non-botanical objects.\n\n" +
                        "Please capture or upload a new image of the leaf for analysis.");
            }
        }).addOnFailureListener(e -> {
            if (labeler != null) labeler.processImage(image).addOnSuccessListener(this::handleSuccess);
        });
    }

    private void handleSuccess(List<FirebaseVisionImageLabel> labels) {
        if (!isOnline()) {
            resultTv.setText("Status: Online Required\n\nInternet connection is needed to provide diagnosis and professional care advice.");
            return;
        }

        if (labels == null || labels.isEmpty()) {
            resultTv.setText("Diagnosis: Not Recognized\n\nSuggestions:\n1. Ensure the leaf is centered.\n2. Use better lighting.\n3. Keep the background simple.");
            return;
        }

        FirebaseVisionImageLabel best = labels.get(0);
        plantName = best.getText();
        currentConfidence = best.getConfidence();

        updateResultUI();
    }

    private float currentConfidence = 0.0f;

    private void updateResultUI() {
        String diagnosis = plantName.replace("__", " ");
        String recommendation = getDescription(plantName);
        String watering = getSmartWateringAdvice(plantName);

        DecimalFormat df = new DecimalFormat("##.#");
        String resultText = "Analysis Results:\n" +
                        "Species/Condition: " + diagnosis + "\n" +
                        "Confidence Level: " + df.format(currentConfidence * 100) + "%\n\n" +
                        "■ Expert Recommendation:\n" + recommendation + "\n\n" +
                        "■ Irrigation & Care Protocol:\n" + watering;
        
        resultTv.setText(resultText);
        saveToHistory(diagnosis, resultText, currentImageUri);
    }

    private void saveToHistory(String name, String result, String uri) {
        SharedPreferences prefs = getSharedPreferences("PlantHistory", MODE_PRIVATE);
        String historyJson = prefs.getString("data", "[]");
        try {
            JSONArray array = new JSONArray(historyJson);
            
            // Check if this result is already the last one to avoid duplicates
            if (array.length() > 0) {
                JSONObject last = array.getJSONObject(0);
                if (last.getString("result").equals(result)) return;
            }

            JSONObject newItem = new JSONObject();
            newItem.put("plantName", name);
            newItem.put("result", result);
            newItem.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            newItem.put("imageUri", uri);
            
            JSONArray newArray = new JSONArray();
            newArray.put(newItem);
            for (int i = 0; i < array.length() && i < 9; i++) {
                newArray.put(array.get(i));
            }
            prefs.edit().putString("data", newArray.toString()).apply();
        } catch (JSONException e) { e.printStackTrace(); }
    }

    private void shareCurrentResult() {
        String diagnosisText = resultTv.getText().toString();
        if (diagnosisText.isEmpty() || diagnosisText.contains("Processing")) {
            Toast.makeText(this, "Please wait for diagnosis", Toast.LENGTH_SHORT).show();
            return;
        }

        String shareBody = "AI_PlantGuardian Diagnosis Report\n\n" +
                diagnosisText + "\n\n" +
                "Shared via AI_PlantGuardian App";

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Plant Disease Diagnosis: " + plantName);
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    private String getSmartWateringAdvice(String label) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isNight = (hour < 6 || hour > 18);
        
        if (isRainySoon) {
            return "STATUS: DO NOT WATER. Rain is forecasted. Natural rain will provide sufficient moisture.";
        }

        String lowerLabel = label.toLowerCase();
        boolean isHealthy = lowerLabel.contains("healthy");
        
        if (lowerLabel.contains("spider_mites")) {
            return "STATUS: INCREASE HUMIDITY. Spider mites thrive in dry conditions. Consider misting the plant during the day, but keep the soil moisture balanced.";
        }

        boolean isPathogen = lowerLabel.contains("blight") || lowerLabel.contains("rot") || 
                             lowerLabel.contains("spot") || lowerLabel.contains("mildew") || 
                             lowerLabel.contains("mold") || lowerLabel.contains("scab") || 
                             lowerLabel.contains("rust") || lowerLabel.contains("virus") ||
                             lowerLabel.contains("esca") || lowerLabel.contains("scorch");

        if (isHealthy) {
            if (isNight) {
                return "STATUS: WATER IN MORNING. The plant is healthy, but night watering can cause root fungus. Please water at 7:00 AM.";
            } else {
                return "STATUS: WATER NOW. It is daytime, the plant can absorb water efficiently. Apply at the base.";
            }
        } else if (isPathogen) {
            if (isNight) {
                return "STATUS: DO NOT WATER NOW. This plant has signs of infection/stress. Night moisture will make it worse. Wait for sunlight.";
            } else {
                return "STATUS: CAREFUL SOIL WATERING. Water ONLY at the soil level. Do not let water touch the leaves, as it spreads pathogens.";
            }
        } else {
            return isNight ? "STATUS: WAIT UNTIL MORNING." : "STATUS: WATER NOW.";
        }
    }

    private String getDescription(String label) {
        switch (label) {
            case "Apple__Apple_Scab":
                return "Fungal disease. Remove fallen leaves and apply fungicides like sulfur or copper-based sprays in early spring.";
            case "Apple__Black_Rot":
                return "Prune infected branches and remove diseased fruit. Use fungicides and maintain good orchard hygiene.";
            case "Apple__Cedar_Apple_Rust":
                return "Remove nearby juniper trees if possible. Apply preventative fungicides during the rainy spring season.";
            case "Apple__Healthy":
                return "Your apple tree looks healthy! Continue regular watering and mulching.";
            case "Blueberry__Healthy":
                return "Blueberry plant is healthy. Maintain acidic soil (pH 4.5-5.5) and consistent moisture.";
            case "Cherry__Healthy":
                return "Cherry tree is healthy. Ensure good air circulation and monitor for pests.";
            case "Cherry__Powdery_Mildew":
                return "Improve air flow by pruning. Apply sulfur-based fungicides or neem oil to control the white coating.";
            case "Corn__Cercospora_Leaf_Spot":
                return "Rotate crops and use resistant varieties. Apply foliar fungicides if the infection spreads upward.";
            case "Corn__Common_Rust":
                return "Usually not fatal. For severe cases, apply fungicides. Planting resistant hybrids is the best prevention.";
            case "Corn__Northern_Leaf_Blight":
                return "Fungal disease. Use foliar fungicides and avoid overhead irrigation to keep leaves dry.";
            case "Corn__Healthy":
                return "Corn is healthy. Ensure adequate nitrogen levels and consistent watering.";
            case "Grape__Black_Rot":
                return "Destroy mummified grapes. Apply fungicides from bud break until about 4 weeks after bloom.";
            case "Grape__Esca":
                return "Complex disease. Prune infected wood during dry weather and protect pruning wounds with sealant.";
            case "Grape__Leaf_Blight":
                return "Remove infected leaves. Apply copper-based fungicides to prevent further spread.";
            case "Grape__Healthy":
                return "Grapes are healthy! Ensure proper trellising and sunlight exposure.";
            case "Orange__Haunglongbing":
                return "Also known as Citrus Greening. No cure exists; remove infected trees to prevent spreading to others.";
            case "Peach__Bacterial_Spot":
                return "Use copper sprays in late fall and early spring. Avoid excessive nitrogen fertilization.";
            case "Peach__Healthy":
                return "Peach tree is healthy. Thin out fruit to prevent branch breakage and improve size.";
            case "Bell_Pepper__Bacterial_Spot":
                return "Avoid overhead watering. Apply copper-based bactericides and practice crop rotation.";
            case "Bell_Pepper__Healthy":
                return "Peppers are healthy. Keep soil consistently moist but not waterlogged.";
            case "Potato__Early_Blight":
                return "Maintain plant vigor with proper fertilizer. Apply fungicides containing chlorothalonil or copper.";
            case "Potato__Late_Blight":
                return "Highly contagious! Remove infected plants immediately. Apply copper fungicides and improve drainage.";
            case "Potato__Healthy":
                return "Potatoes are healthy. Hill up soil around the base to protect growing tubers.";
            case "Raspberry__Healthy":
                return "Raspberry is healthy. Prune out old canes after harvest to encourage new growth.";
            case "Soybean__Healthy":
                return "Soybean is healthy. Monitor for aphids and ensure proper soil drainage.";
            case "Squash__Powdery_Mildew":
                return "Apply neem oil or potassium bicarbonate. Increase spacing for better air circulation.";
            case "Strawberry__Healthy":
                return "Strawberry is healthy. Mulch with straw to keep fruit off the ground.";
            case "Strawberry__Leaf_Scorch":
                return "Remove old leaves. Apply balanced fertilizer and avoid over-watering during the fruiting phase.";
            case "Tomato__Bacterial_Spot":
                return "Avoid handling plants when wet. Use copper-based sprays and remove lower infected leaves.";
            case "Tomato__Early_Blight":
                return "Mulch the base. Use fungicidal sprays and prune lower leaves to prevent soil splash-back.";
            case "Tomato__Late_Blight":
                return "Serious threat. Destroy infected plants. Use copper fungicides and avoid humid conditions.";
            case "Tomato__Leaf_Mold":
                return "Increase air circulation and reduce humidity in the greenhouse/garden area.";
            case "Tomato__Septoria_Leaf_Spot":
                return "Remove infected leaves. Apply fungicides and avoid overhead watering.";
            case "Tomato__Spider_Mites":
                return "Increase humidity or spray with water to knock off mites. Use insecticidal soap if needed.";
            case "Tomato__Target_Spot":
                return "Ensure proper spacing and use preventative fungicides. Avoid wetting the foliage.";
            case "Tomato__Tomato_Leaf_Curl_Virus":
                return "Transmitted by whiteflies. Use insecticidal soaps and remove infected plants to protect others.";
            case "Tomato__Tomato_Mosaic_Virus":
                return "No cure. Remove and burn infected plants. Disinfect tools and avoid using tobacco near plants.";
            case "Tomato__Healthy":
                return "Tomato is healthy! Keep staking and suckering for better yields.";
            default:
                return "Diagnosis: " + label.replace("__", " ") + ". Monitor the plant's health, ensure proper watering, and consult a local expert if symptoms worsen.";
        }
    }
}
