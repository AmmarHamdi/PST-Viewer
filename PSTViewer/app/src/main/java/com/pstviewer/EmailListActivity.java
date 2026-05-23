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

import com.pff.PSTFolder;
import com.pff.PSTMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailListActivity extends AppCompatActivity implements EmailAdapter.OnEmailClickListener {

    // Static reference so we don't need to serialise PSTFolder through an Intent
    private static PSTFolder currentFolder;

    public static void setCurrentFolder(PSTFolder folder) {
        currentFolder = folder;
    }

    // Static reference for the selected message
    private static PSTMessage currentMessage;

    public static void setCurrentMessage(PSTMessage message) {
        currentMessage = message;
    }

    public static PSTMessage getCurrentMessage() {
        return currentMessage;
    }

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private EmailAdapter adapter;
    private List<PSTMessage> allMessages = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_list);

        String folderName = getIntent().getStringExtra(FolderActivity.EXTRA_FOLDER_NAME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(folderName != null ? folderName : "Emails");
        }

        recyclerView = findViewById(R.id.recyclerEmails);
        progressBar  = findViewById(R.id.progressBar);
        tvEmpty      = findViewById(R.id.tvEmpty);

        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q)  { filter(q); return true; }
            @Override public boolean onQueryTextChange(String q)  { filter(q); return true; }
        });

        adapter = new EmailAdapter(this, new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);

        loadMessages();
    }

    private void loadMessages() {
        if (currentFolder == null) { finish(); return; }
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            List<PSTMessage> messages = PSTRepository.loadMessages(currentFolder);
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                allMessages = messages;
                if (messages.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    adapter.setItems(messages);
                }
            });
        });
    }

    private void filter(String query) {
        if (query == null || query.isEmpty()) {
            adapter.setItems(allMessages);
            return;
        }
        String q = query.toLowerCase(Locale.getDefault());
        List<PSTMessage> filtered = new ArrayList<>();
        for (PSTMessage m : allMessages) {
            try {
                if ((m.getSubject() != null && m.getSubject().toLowerCase(Locale.getDefault()).contains(q)) ||
                    (m.getSenderName() != null && m.getSenderName().toLowerCase(Locale.getDefault()).contains(q))) {
                    filtered.add(m);
                }
            } catch (Exception ignored) {}
        }
        adapter.setItems(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onEmailClick(PSTMessage message) {
        setCurrentMessage(message);
        startActivity(new Intent(this, EmailDetailActivity.class));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
