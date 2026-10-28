package com.anniezhang.textfromimage;

public class HistoryItem {
    private String plantName;
    private String result;
    private String date;
    private String imageUri;

    public HistoryItem(String plantName, String result, String date, String imageUri) {
        this.plantName = plantName;
        this.result = result;
        this.date = date;
        this.imageUri = imageUri;
    }

    public String getPlantName() { return plantName; }
    public String getResult() { return result; }
    public String getDate() { return date; }
    public String getImageUri() { return imageUri; }
}
