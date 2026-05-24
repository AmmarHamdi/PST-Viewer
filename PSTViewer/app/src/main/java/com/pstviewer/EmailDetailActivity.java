package com.pstviewer;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.pff.PSTAttachment;
import com.pff.PSTMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EmailDetailActivity extends AppCompatActivity {

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm", Locale.getDefault());

    private WebView webView;
    private WebView printWebView;
    private PSTMessage currentMessage;

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

        currentMessage = message;
        webView = findViewById(R.id.webViewBody);
        populateViews(message);
    }

    private void populateViews(PSTMessage msg) {
        TextView tvSubject = findViewById(R.id.tvSubject);
        TextView tvFrom    = findViewById(R.id.tvFrom);
        TextView tvTo      = findViewById(R.id.tvTo);
        TextView tvCc      = findViewById(R.id.tvCc);
        TextView tvDate    = findViewById(R.id.tvDate);
        LinearLayout llAttachments = findViewById(R.id.llAttachments);

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
            chip.setOnClickListener(v -> {
                if (requirePro()) saveAndOpenAttachment(att, finalFilename);
            });
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_email_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { onBackPressed(); return true; }
        if (id == R.id.action_print) {
            if (requirePro()) printEmail();
            return true;
        }
        if (id == R.id.export_share_text) {
            if (requirePro()) shareEmailAsText();
            return true;
        }
        if (id == R.id.export_html) {
            if (requirePro()) exportEmailAsHtml();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Use Android PrintManager + WebView.createPrintDocumentAdapter to print / save as PDF. */
    private void printEmail() {
        if (currentMessage == null) return;
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        if (printManager == null) return;
        String subject = getPrintableSubject();

        if (printWebView != null) {
            printWebView.destroy();
        }
        printWebView = new WebView(this);
        configureWebView(printWebView);
        printWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(subject);
                printManager.print(subject, adapter, new PrintAttributes.Builder().build());
            }
        });
        printWebView.loadDataWithBaseURL(null, buildEmailDocumentHtml(currentMessage), "text/html", "UTF-8", null);
    }

    /** Share the email as plain text via system share intent. */
    private void shareEmailAsText() {
        if (currentMessage == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            String subject = currentMessage.getSubject();
            sb.append("Subject: ").append(subject != null ? subject : "(no subject)").append("\n");
            sb.append("From: ").append(formatAddress(
                    currentMessage.getSenderName(),
                    currentMessage.getSenderEmailAddress())).append("\n");
            String to = currentMessage.getDisplayTo();
            if (to != null && !to.isEmpty()) sb.append("To: ").append(to).append("\n");
            String cc = currentMessage.getDisplayCC();
            if (cc != null && !cc.isEmpty()) sb.append("Cc: ").append(cc).append("\n");
            Date date = currentMessage.getMessageDeliveryTime();
            if (date != null) sb.append("Date: ").append(DATE_FMT.format(date)).append("\n");
            sb.append("\n");
            String body = currentMessage.getBody();
            if (body == null || body.isEmpty()) body = stripHtml(currentMessage.getBodyHTML());
            if (body != null) sb.append(body);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_export)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /** Export the email body and message metadata as an HTML file and share it via the system share sheet. */
    private void exportEmailAsHtml() {
        if (currentMessage == null) return;
        try {
            String subject = getPrintableSubject();
            // Sanitise for use as filename
            String filename = subject.replaceAll("[^a-zA-Z0-9._\\-]", "_") + ".html";

            // Write to cache/exports/ and share via FileProvider (no extra permission needed)
            File exportsDir = new File(getCacheDir(), "exports");
            //noinspection ResultOfMethodCallIgnored
            exportsDir.mkdirs();
            File outFile = new File(exportsDir, filename);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                writer.write(buildEmailDocumentHtml(currentMessage));
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", outFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/html");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_html)));

        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "").replaceAll("&nbsp;", " ").trim();
    }

    private String getPrintableSubject() {
        try {
            String subject = currentMessage != null ? currentMessage.getSubject() : null;
            return subject == null || subject.isEmpty() ? "email" : subject;
        } catch (Exception ignored) {
            return "email";
        }
    }

    private String buildEmailDocumentHtml(PSTMessage message) {
        String subject = getPrintableSubject();
        String from = "Unknown";
        String to = null;
        String cc = null;
        Date date = null;
        int attachCount = 0;
        String htmlBody = null;
        String plainBody = null;

        try {
            from = formatAddress(message.getSenderName(), message.getSenderEmailAddress());
        } catch (Exception ignored) {}
        try {
            to = message.getDisplayTo();
        } catch (Exception ignored) {}
        try {
            cc = message.getDisplayCC();
        } catch (Exception ignored) {}
        try {
            date = message.getMessageDeliveryTime();
        } catch (Exception ignored) {}
        try {
            attachCount = message.getNumberOfAttachments();
        } catch (Exception ignored) {}
        try {
            htmlBody = message.getBodyHTML();
        } catch (Exception ignored) {}
        try {
            plainBody = message.getBody();
        } catch (Exception ignored) {}

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<style>"
                + "body{font-family:sans-serif;font-size:15px;padding:16px;}"
                + "table{border-collapse:collapse;width:100%;margin-bottom:16px;}"
                + "td{padding:4px 8px;vertical-align:top;}"
                + "td.label{font-weight:bold;color:#555;width:92px;}"
                + "hr{border:none;border-top:1px solid #ddd;margin:12px 0;}"
                + "img{max-width:100%;height:auto;}"
                + "pre{white-space:pre-wrap;}"
                + "</style></head><body>");

        sb.append("<table>");
        sb.append("<tr><td class='label'>Subject:</td><td>")
                .append(escapeHtml(subject))
                .append("</td></tr>");
        sb.append("<tr><td class='label'>From:</td><td>")
                .append(escapeHtml(from))
                .append("</td></tr>");
        if (to != null && !to.isEmpty()) {
            sb.append("<tr><td class='label'>To:</td><td>")
                    .append(escapeHtml(to)).append("</td></tr>");
        }
        if (cc != null && !cc.isEmpty()) {
            sb.append("<tr><td class='label'>Cc:</td><td>")
                    .append(escapeHtml(cc)).append("</td></tr>");
        }
        if (date != null) {
            sb.append("<tr><td class='label'>Date:</td><td>")
                    .append(escapeHtml(DATE_FMT.format(date))).append("</td></tr>");
        }
        if (attachCount > 0) {
            sb.append("<tr><td class='label'>Attachments:</td><td>");
            for (int i = 0; i < attachCount; i++) {
                try {
                    PSTAttachment attachment = message.getAttachment(i);
                    String filename = attachment.getLongFilename();
                    if (filename == null || filename.isEmpty()) filename = attachment.getFilename();
                    if (filename == null || filename.isEmpty()) filename = "attachment_" + i;
                    sb.append(escapeHtml(filename));
                    if (i < attachCount - 1) sb.append(", ");
                } catch (Exception ignored) {}
            }
            sb.append("</td></tr>");
        }
        sb.append("</table><hr/>");

        if (htmlBody != null && !htmlBody.isEmpty()) {
            sb.append(htmlBody);
        } else {
            if (plainBody == null || plainBody.isEmpty()) plainBody = "(empty message)";
            sb.append("<pre>").append(escapeHtml(plainBody)).append("</pre>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Returns true if the user has Pro, otherwise shows the upgrade dialog and returns false.
     */
    private boolean requirePro() {
        if (ProManager.getInstance(this).isPro()) return true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.pro_gate_title)
                .setMessage(R.string.pro_gate_message)
                .setPositiveButton(R.string.pro_gate_upgrade, (d, w) ->
                        startActivity(new Intent(this, UpgradeActivity.class)))
                .setNegativeButton(R.string.pro_gate_cancel, null)
                .show();
        return false;
    }

    @Override
    protected void onDestroy() {
        if (printWebView != null) {
            printWebView.destroy();
            printWebView = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
