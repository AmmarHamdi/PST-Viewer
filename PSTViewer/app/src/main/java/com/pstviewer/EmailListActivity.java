package com.pstviewer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
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

    /** Available sort orders for the email list. */
    private enum SortOrder { DATE_DESC, DATE_ASC, SENDER, SUBJECT }

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private EmailAdapter adapter;
    private List<PSTMessage> allMessages = new ArrayList<>();
    private SortOrder currentSort = SortOrder.DATE_DESC;
    private String currentQuery = "";

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
            @Override public boolean onQueryTextSubmit(String q)  { currentQuery = q; applyFilterAndSort(); return true; }
            @Override public boolean onQueryTextChange(String q)  { currentQuery = q; applyFilterAndSort(); return true; }
        });

        adapter = new EmailAdapter(this, new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadMessages();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_email_list, menu);
        MenuItem sortItem = menu.findItem(R.id.sort_date_desc);
        if (sortItem != null) {
            sortItem.setChecked(true);
        }
        return true;
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
                applyFilterAndSort();
            });
        });
    }

    private void applyFilterAndSort() {
        List<PSTMessage> result;

        if (currentQuery == null || currentQuery.isEmpty()) {
            result = new ArrayList<>(allMessages);
        } else {
            String q = currentQuery.toLowerCase(Locale.getDefault());
            result = new ArrayList<>();
            for (PSTMessage m : allMessages) {
                try {
                    if ((m.getSubject() != null && m.getSubject().toLowerCase(Locale.getDefault()).contains(q)) ||
                        (m.getSenderName() != null && m.getSenderName().toLowerCase(Locale.getDefault()).contains(q))) {
                        result.add(m);
                    }
                } catch (Exception ignored) {}
            }
        }

        switch (currentSort) {
            case DATE_ASC:
                result.sort((a, b) -> {
                    java.util.Date da, db;
                    try { da = a.getMessageDeliveryTime(); } catch (Exception e) { da = null; }
                    try { db = b.getMessageDeliveryTime(); } catch (Exception e) { db = null; }
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return da.compareTo(db);
                });
                break;
            case SENDER:
                result.sort((a, b) -> {
                    String sa, sb;
                    try { sa = a.getSenderName(); if (sa == null || sa.isEmpty()) sa = a.getSenderEmailAddress(); } catch (Exception e) { sa = null; }
                    try { sb = b.getSenderName(); if (sb == null || sb.isEmpty()) sb = b.getSenderEmailAddress(); } catch (Exception e) { sb = null; }
                    if (sa == null && sb == null) return 0;
                    if (sa == null) return 1;
                    if (sb == null) return -1;
                    return sa.compareToIgnoreCase(sb);
                });
                break;
            case SUBJECT:
                result.sort((a, b) -> {
                    String sa, sb;
                    try { sa = a.getSubject(); } catch (Exception e) { sa = null; }
                    try { sb = b.getSubject(); } catch (Exception e) { sb = null; }
                    if (sa == null && sb == null) return 0;
                    if (sa == null) return 1;
                    if (sb == null) return -1;
                    return sa.compareToIgnoreCase(sb);
                });
                break;
            case DATE_DESC:
            default:
                result.sort((a, b) -> {
                    java.util.Date da, db;
                    try { da = a.getMessageDeliveryTime(); } catch (Exception e) { da = null; }
                    try { db = b.getMessageDeliveryTime(); } catch (Exception e) { db = null; }
                    if (da == null && db == null) return 0;
                    if (da == null) return 1;
                    if (db == null) return -1;
                    return db.compareTo(da);
                });
                break;
        }

        if (result.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
        adapter.setItems(result);
    }

    @Override
    public void onEmailClick(PSTMessage message) {
        setCurrentMessage(message);
        startActivity(new Intent(this, EmailDetailActivity.class));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.sort_date_desc) {
            item.setChecked(true);
            currentSort = SortOrder.DATE_DESC;
            applyFilterAndSort();
            return true;
        } else if (id == R.id.sort_date_asc) {
            item.setChecked(true);
            currentSort = SortOrder.DATE_ASC;
            applyFilterAndSort();
            return true;
        } else if (id == R.id.sort_sender) {
            item.setChecked(true);
            currentSort = SortOrder.SENDER;
            applyFilterAndSort();
            return true;
        } else if (id == R.id.sort_subject) {
            item.setChecked(true);
            currentSort = SortOrder.SUBJECT;
            applyFilterAndSort();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
