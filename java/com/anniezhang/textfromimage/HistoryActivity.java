package com.anniezhang.textfromimage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList;
    private TextView emptyTv;
    private Button btnBack, btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerViewHistory);
        emptyTv = findViewById(R.id.emptyTv);
        btnBack = findViewById(R.id.btnBackHistory);
        btnClear = findViewById(R.id.btnClearHistory);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyList = new ArrayList<>();

        loadHistory();

        adapter = new HistoryAdapter(historyList, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(HistoryItem item) {
                Intent intent = new Intent(HistoryActivity.this, ResultActivity.class);
                intent.putExtra("fullResult", item.getResult());
                intent.putExtra("imageUri", item.getImageUri());
                intent.putExtra("fromHistory", true);
                startActivity(intent);
            }

            @Override
            public void onShareClick(HistoryItem item) {
                shareResult(item);
            }
        });
        recyclerView.setAdapter(adapter);

        updateEmptyView();

        btnBack.setOnClickListener(v -> finish());

        btnClear.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("PlantHistory", MODE_PRIVATE);
            prefs.edit().putString("data", "[]").apply();
            historyList.clear();
            adapter.notifyDataSetChanged();
            updateEmptyView();
        });
    }

    private void shareResult(HistoryItem item) {
        String shareBody = "AI_PlantGuardian Diagnosis Report\n\n" +
                "Plant: " + item.getPlantName() + "\n" +
                "Date: " + item.getDate() + "\n\n" +
                item.getResult() + "\n\n" +
                "Shared via AI_PlantGuardian App";

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Plant Disease Diagnosis: " + item.getPlantName());
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    private void updateEmptyView() {
        if (historyList.isEmpty()) {
            emptyTv.setVisibility(View.VISIBLE);
        } else {
            emptyTv.setVisibility(View.GONE);
        }
    }

    private void loadHistory() {
        SharedPreferences prefs = getSharedPreferences("PlantHistory", MODE_PRIVATE);
        String historyJson = prefs.getString("data", "[]");
        try {
            JSONArray array = new JSONArray(historyJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                historyList.add(new HistoryItem(
                        obj.getString("plantName"),
                        obj.getString("result"),
                        obj.getString("date"),
                        obj.optString("imageUri", "")
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
