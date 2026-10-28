package com.anniezhang.textfromimage;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<HistoryItem> historyList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(HistoryItem item);
        void onShareClick(HistoryItem item);
    }

    public HistoryAdapter(List<HistoryItem> historyList, OnItemClickListener listener) {
        this.historyList = historyList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryItem item = historyList.get(position);
        holder.plantName.setText(item.getPlantName());
        holder.date.setText("Date: " + item.getDate());
        holder.result.setText(item.getResult());

        if (item.getImageUri() != null && !item.getImageUri().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(Uri.parse(item.getImageUri()))
                    .placeholder(R.drawable.ic_launcher_background)
                    .into(holder.historyImage);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });

        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShareClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView plantName, date, result;
        ImageView historyImage;
        View btnShare;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            plantName = itemView.findViewById(R.id.historyPlantName);
            date = itemView.findViewById(R.id.historyDate);
            result = itemView.findViewById(R.id.historyResult);
            historyImage = itemView.findViewById(R.id.historyImage);
            btnShare = itemView.findViewById(R.id.btnShareHistory);
        }
    }
}
