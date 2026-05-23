package com.pstviewer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pff.PSTFile;
import com.pff.PSTFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderActivity extends AppCompatActivity implements FolderAdapter.OnFolderClickListener {

    public static final String EXTRA_FOLDER_PATH = "folder_path";
    public static final String EXTRA_FOLDER_NAME = "folder_name";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FolderAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Folders");
        }

        recyclerView = findViewById(R.id.recyclerFolders);
        progressBar  = findViewById(R.id.progressBar);
        tvEmpty      = findViewById(R.id.tvEmpty);

        adapter = new FolderAdapter(this, new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);

        loadFolders();
    }

    private void loadFolders() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            List<PSTRepository.FolderItem> items = new ArrayList<>();
            try {
                PSTFile pstFile = PSTRepository.getInstance().getPstFile();
                if (pstFile != null) {
                    PSTFolder root = pstFile.getRootFolder();
                    // Skip the root node itself, collect its children
                    if (root.hasSubfolders()) {
                        for (PSTFolder topLevel : root.getSubFolders()) {
                            items.addAll(PSTRepository.flattenFolders(topLevel, 0));
                        }
                    }
                }
            } catch (Exception e) {
                // Show what we have
            }

            final List<PSTRepository.FolderItem> finalItems = items;
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                if (finalItems.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    adapter.setItems(finalItems);
                }
            });
        });
    }

    @Override
    public void onFolderClick(PSTRepository.FolderItem item) {
        Intent intent = new Intent(this, EmailListActivity.class);
        // Store the selected folder in repository so EmailListActivity can retrieve it
        EmailListActivity.setCurrentFolder(item.folder);
        intent.putExtra(EXTRA_FOLDER_NAME, item.getDisplayName());
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
