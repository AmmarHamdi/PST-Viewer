package com.pstviewer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(PSTRepository.FolderItem item);
    }

    private final Context context;
    private List<PSTRepository.FolderItem> items;
    private final OnFolderClickListener listener;

    public FolderAdapter(Context context, List<PSTRepository.FolderItem> items,
                         OnFolderClickListener listener) {
        this.context  = context;
        this.items    = items;
        this.listener = listener;
    }

    public void setItems(List<PSTRepository.FolderItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PSTRepository.FolderItem item = items.get(position);

        // Indent based on tree depth
        int paddingDp = 16 + (item.depth * 20);
        int paddingPx = (int) (paddingDp * context.getResources().getDisplayMetrics().density);
        holder.tvName.setPadding(paddingPx, holder.tvName.getPaddingTop(),
                holder.tvName.getPaddingRight(), holder.tvName.getPaddingBottom());

        holder.tvName.setText(item.getDisplayName());

        int count = item.getCount();
        if (count > 0) {
            holder.tvCount.setText(String.valueOf(count));
            holder.tvCount.setVisibility(View.VISIBLE);
        } else {
            holder.tvCount.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onFolderClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvCount;

        ViewHolder(View itemView) {
            super(itemView);
            tvName  = itemView.findViewById(R.id.tvFolderName);
            tvCount = itemView.findViewById(R.id.tvFolderCount);
        }
    }
}
