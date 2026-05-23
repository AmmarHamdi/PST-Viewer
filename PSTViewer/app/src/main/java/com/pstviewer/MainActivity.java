package com.pstviewer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.pff.PSTFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnOpen;
    private Button btnBrowse;
    private MaterialCardView cardInfo;
    private TextView tvFileName;
    private TextView tvFileSize;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // SAF file picker launcher
    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) openPstFromUri(uri);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus    = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);
        btnOpen     = findViewById(R.id.btnOpen);
        btnBrowse   = findViewById(R.id.btnBrowse);
        cardInfo    = findViewById(R.id.cardInfo);
        tvFileName  = findViewById(R.id.tvFileName);
        tvFileSize  = findViewById(R.id.tvFileSize);

        btnOpen.setOnClickListener(v ->
                filePickerLauncher.launch(new String[]{"*/*"}));

        btnBrowse.setOnClickListener(v -> {
            if (PSTRepository.getInstance().isOpen()) {
                startActivity(new Intent(this, FolderActivity.class));
            } else {
                Toast.makeText(this, "Please open a PST file first", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle VIEW intent (opened from a file manager)
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            openPstFromUri(intent.getData());
        }

        // Restore state if we already have a file open
        if (PSTRepository.getInstance().isOpen()) {
            showFileInfo(PSTRepository.getInstance().getPstPath(),
                         new File(PSTRepository.getInstance().getPstPath()).length());
            btnBrowse.setEnabled(true);
        }
    }

    private void openPstFromUri(Uri uri) {
        setLoading(true, "Copying file…");

        executor.execute(() -> {
            try {
                // Copy the URI content to a temp file (java-libpst needs a real File path)
                File tempFile = new File(getCacheDir(), "archive_" + System.currentTimeMillis() + ".pst");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    if (in == null) throw new Exception("Cannot open input stream");
                    byte[] buf = new byte[8192];
                    int read;
                    while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                }

                mainHandler.post(() -> setLoading(true, "Parsing PST…"));

                PSTFile pstFile = new PSTFile(tempFile.getAbsolutePath());
                PSTRepository.getInstance().setPstFile(pstFile, tempFile.getAbsolutePath());

                mainHandler.post(() -> {
                    setLoading(false, null);
                    showFileInfo(getOriginalFileName(uri), tempFile.length());
                    btnBrowse.setEnabled(true);
                    // Auto-navigate into folder list
                    startActivity(new Intent(MainActivity.this, FolderActivity.class));
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false, null);
                    tvStatus.setText("Error: " + e.getMessage());
                    Toast.makeText(this, "Failed to open PST: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showFileInfo(String name, long bytes) {
        tvFileName.setText(name);
        tvFileSize.setText(formatSize(bytes));
        cardInfo.setVisibility(View.VISIBLE);
        tvStatus.setText("PST archive loaded successfully");
    }

    private void setLoading(boolean loading, String message) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnOpen.setEnabled(!loading);
        if (message != null) tvStatus.setText(message);
    }

    private String getOriginalFileName(Uri uri) {
        String path = uri.getLastPathSegment();
        return path != null ? path : "archive.pst";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
