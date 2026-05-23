package com.pstviewer;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.pff.PSTAttachment;
import com.pff.PSTMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EmailDetailActivity extends AppCompatActivity {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_detail);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        PSTMessage message = EmailListActivity.getCurrentMessage();
        if (message == null) { finish(); return; }

        populateViews(message);
    }

    private void populateViews(PSTMessage msg) {
        TextView tvSubject = findViewById(R.id.tvSubject);
        TextView tvFrom    = findViewById(R.id.tvFrom);
        TextView tvTo      = findViewById(R.id.tvTo);
        TextView tvCc      = findViewById(R.id.tvCc);
        TextView tvDate    = findViewById(R.id.tvDate);
        LinearLayout llAttachments = findViewById(R.id.llAttachments);
        WebView webView    = findViewById(R.id.webViewBody);

        try {
            // Subject
            String subject = msg.getSubject();
            tvSubject.setText(subject != null && !subject.isEmpty() ? subject : "(no subject)");
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(tvSubject.getText());

            // From
            String senderName  = msg.getSenderName();
            String senderEmail = msg.getSenderEmailAddress();
            tvFrom.setText(formatAddress(senderName, senderEmail));

            // To
            String to = msg.getDisplayTo();
            if (to != null && !to.isEmpty()) {
                tvTo.setText(to);
                tvTo.setVisibility(View.VISIBLE);
                findViewById(R.id.tvToLabel).setVisibility(View.VISIBLE);
            } else {
                tvTo.setVisibility(View.GONE);
                findViewById(R.id.tvToLabel).setVisibility(View.GONE);
            }

            // CC
            String cc = msg.getDisplayCC();
            if (cc != null && !cc.isEmpty()) {
                tvCc.setText(cc);
                tvCc.setVisibility(View.VISIBLE);
                findViewById(R.id.tvCcLabel).setVisibility(View.VISIBLE);
            } else {
                tvCc.setVisibility(View.GONE);
                findViewById(R.id.tvCcLabel).setVisibility(View.GONE);
            }

            // Date
            Date date = msg.getMessageDeliveryTime();
            tvDate.setText(date != null ? DATE_FMT.format(date) : "");

            // Body
            configureWebView(webView);
            String htmlBody = msg.getBodyHTML();
            if (htmlBody != null && !htmlBody.isEmpty()) {
                webView.loadDataWithBaseURL(null, wrapHtml(htmlBody), "text/html", "UTF-8", null);
            } else {
                // Fallback to plain text
                String plainBody = msg.getBody();
                if (plainBody == null || plainBody.isEmpty()) plainBody = "(empty message)";
                webView.loadDataWithBaseURL(null,
                        wrapHtml("<pre style='white-space:pre-wrap;font-family:sans-serif;'>"
                                + escapeHtml(plainBody) + "</pre>"),
                        "text/html", "UTF-8", null);
            }

            // Attachments
            int attachCount = msg.getNumberOfAttachments();
            if (attachCount > 0) {
                llAttachments.setVisibility(View.VISIBLE);
                for (int i = 0; i < attachCount; i++) {
                    try {
                        PSTAttachment att = msg.getAttachment(i);
                        addAttachmentChip(llAttachments, att, msg, i);
                    } catch (Exception ignored) {}
                }
            } else {
                llAttachments.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            Toast.makeText(this, "Error loading message: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void configureWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false); // safety: no JS in emails
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(100);
        webView.setBackgroundColor(Color.WHITE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Open links externally
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
                return true;
            }
        });
    }

    private void addAttachmentChip(LinearLayout container, PSTAttachment att, PSTMessage msg, int index) {
        try {
            String filename = att.getLongFilename();
            if (filename == null || filename.isEmpty()) filename = att.getFilename();
            if (filename == null || filename.isEmpty()) filename = "attachment_" + index;

            TextView chip = new TextView(this);
            chip.setText("📎 " + filename + " (" + formatSize(att.getAttachSize()) + ")");
            chip.setBackgroundResource(R.drawable.bg_chip);
            int dp8 = dp(8);
            chip.setPadding(dp8 * 2, dp8, dp8 * 2, dp8);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp8, dp8);
            chip.setLayoutParams(lp);

            final String finalFilename = filename;
            chip.setOnClickListener(v -> saveAndOpenAttachment(att, finalFilename));
            container.addView(chip);
        } catch (Exception ignored) {}
    }

    private void saveAndOpenAttachment(PSTAttachment att, String filename) {
        try {
            File dir = new File(getCacheDir(), "attachments");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, filename);
            try (InputStream in = att.getFileInputStream();
                 FileOutputStream out = new FileOutputStream(file)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getContentResolver().getType(uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String wrapHtml(String body) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<style>body{font-family:sans-serif;font-size:15px;padding:8px;}"
                + "img{max-width:100%;height:auto;}</style></head><body>"
                + body + "</body></html>";
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                   .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String formatAddress(String name, String email) {
        if (name != null && !name.isEmpty()) {
            if (email != null && !email.isEmpty() && !email.equals(name)) {
                return name + " <" + email + ">";
            }
            return name;
        }
        return email != null ? email : "Unknown";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
