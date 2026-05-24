package com.pstviewer;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pff.PSTMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmailAdapter extends RecyclerView.Adapter<EmailAdapter.ViewHolder> {

    public interface OnEmailClickListener {
        void onEmailClick(PSTMessage message);
    }

    /** Avatar background colors, cycled by the sender initial's char code. */
    private static final int[] AVATAR_COLOR_RES = {
            R.color.avatar_0, R.color.avatar_1, R.color.avatar_2,
            R.color.avatar_3, R.color.avatar_4, R.color.avatar_5,
            R.color.avatar_6, R.color.avatar_7, R.color.avatar_8,
            R.color.avatar_9
    };

    private final Context context;
    private List<PSTMessage> items;
    private final OnEmailClickListener listener;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public EmailAdapter(Context context, List<PSTMessage> items, OnEmailClickListener listener) {
        this.context  = context;
        this.items    = items;
        this.listener = listener;
    }

    public void setItems(List<PSTMessage> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_email, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PSTMessage msg = items.get(position);

        try {
            // Sender
            String sender = msg.getSenderName();
            if (sender == null || sender.isEmpty()) sender = msg.getSenderEmailAddress();
            if (sender == null) sender = "?";
            holder.tvSender.setText(sender);

            // Avatar – first letter + color
            String initial = sender.trim().isEmpty()
                    ? "?"
                    : sender.trim().substring(0, 1).toUpperCase(Locale.getDefault());
            holder.tvAvatar.setText(initial);
            setAvatarColor(holder.tvAvatar, initial);

            // Subject
            String subject = msg.getSubject();
            holder.tvSubject.setText(subject != null && !subject.isEmpty() ? subject : "(no subject)");

            // Date
            Date date = msg.getMessageDeliveryTime();
            holder.tvDate.setText(date != null ? DATE_FMT.format(date) : "");

            // Preview of body
            String body = msg.getBodyPrefix();
            if (body == null || body.isEmpty()) {
                body = stripHtml(msg.getBodyHTML());
            }
            if (body != null && !body.isEmpty()) {
                body = body.trim().replace('\n', ' ').replace('\r', ' ');
                if (body.length() > 120) body = body.substring(0, 120) + "…";
                holder.tvPreview.setText(body);
                holder.tvPreview.setVisibility(View.VISIBLE);
            } else {
                holder.tvPreview.setVisibility(View.GONE);
            }

            // Attachments indicator
            if (msg.getNumberOfAttachments() > 0) {
                holder.tvAttachment.setText("📎 " + msg.getNumberOfAttachments());
                holder.tvAttachment.setVisibility(View.VISIBLE);
            } else {
                holder.tvAttachment.setVisibility(View.GONE);
            }

            // Unread styling: bold subject + orange stripe + bold sender
            boolean read = msg.isRead();
            if (!read) {
                holder.tvSubject.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.tvSender.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.viewUnreadIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.tvSubject.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.tvSender.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.viewUnreadIndicator.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            holder.tvSubject.setText("(error loading message)");
        }

        holder.itemView.setOnClickListener(v -> listener.onEmailClick(msg));
    }

    @Override
    public int getItemCount() { return items.size(); }

    /** Set a deterministic color on the avatar circle based on the sender initial. */
    private void setAvatarColor(TextView avatarView, String initial) {
        int index = initial.isEmpty() ? 0 : Math.abs(initial.charAt(0)) % AVATAR_COLOR_RES.length;
        int color = ContextCompat.getColor(context, AVATAR_COLOR_RES[index]);
        // Mutate a copy of the background drawable to avoid tinting all items
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        avatarView.setBackground(bg);
    }

    private static String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View viewUnreadIndicator;
        final TextView tvAvatar;
        final TextView tvSender, tvSubject, tvDate, tvPreview, tvAttachment;

        ViewHolder(View v) {
            super(v);
            viewUnreadIndicator = v.findViewById(R.id.viewUnreadIndicator);
            tvAvatar     = v.findViewById(R.id.tvAvatar);
            tvSender     = v.findViewById(R.id.tvSender);
            tvSubject    = v.findViewById(R.id.tvSubject);
            tvDate       = v.findViewById(R.id.tvDate);
            tvPreview    = v.findViewById(R.id.tvPreview);
            tvAttachment = v.findViewById(R.id.tvAttachment);
        }
    }
}

