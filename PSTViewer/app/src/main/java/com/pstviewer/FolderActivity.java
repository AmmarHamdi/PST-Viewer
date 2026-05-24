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
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pff.PSTFile;
import com.pff.PSTFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FolderActivity extends AppCompatActivity implements FolderAdapter.OnFolderClickListener {

    public static final String EXTRA_FOLDER_PATH = "folder_path";
    public static final String EXTRA_FOLDER_NAME = "folder_name";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private FolderAdapter adapter;
    private List<PSTRepository.FolderItem> allItems = new ArrayList<>();

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

        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q)  { filter(q); return true; }
            @Override public boolean onQueryTextChange(String q)  { filter(q); return true; }
        });

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
                allItems = finalItems;
                if (finalItems.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    adapter.setItems(finalItems);
                }
            });
        });
    }

    private void filter(String query) {
        if (query == null || query.isEmpty()) {
            adapter.setItems(allItems);
            tvEmpty.setVisibility(allItems.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        String q = query.toLowerCase(Locale.getDefault());
        List<PSTRepository.FolderItem> filtered = new ArrayList<>();
        for (PSTRepository.FolderItem item : allItems) {
            if (item.getDisplayName().toLowerCase(Locale.getDefault()).contains(q)) {
                filtered.add(item);
            }
        }
        adapter.setItems(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
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
