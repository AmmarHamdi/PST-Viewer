package com.pstviewer;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Displays the Pro upgrade offer and handles the Google Play purchase flow.
 */
public class UpgradeActivity extends AppCompatActivity implements BillingManager.BillingCallback {

    private BillingManager billingManager;
    private Button btnBuy;
    private Button btnRestore;
    private ProgressBar progressBar;
    private TextView tvAlreadyPro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upgrade);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.upgrade_title);
        }

        btnBuy       = findViewById(R.id.btnBuyPro);
        btnRestore   = findViewById(R.id.btnRestorePurchase);
        progressBar  = findViewById(R.id.progressBar);
        tvAlreadyPro = findViewById(R.id.tvAlreadyPro);

        if (ProManager.getInstance(this).isPro()) {
            showAlreadyPro();
        }

        billingManager = new BillingManager(this, this);

        btnBuy.setOnClickListener(v -> {
            setLoading(true);
            billingManager.launchPurchaseFlow(this);
        });

        btnRestore.setOnClickListener(v -> {
            setLoading(true);
            billingManager.queryExistingPurchases();
        });
    }

    private void showAlreadyPro() {
        tvAlreadyPro.setVisibility(View.VISIBLE);
        btnBuy.setEnabled(false);
        btnRestore.setEnabled(false);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnBuy.setEnabled(!loading);
        btnRestore.setEnabled(!loading);
    }

    // ── BillingCallback ──────────────────────────────────────────────────────

    @Override
    public void onPurchaseSuccess() {
        runOnUiThread(() -> {
            setLoading(false);
            showAlreadyPro();
            Toast.makeText(this, R.string.upgrade_success, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onPurchaseFailed(String message) {
        runOnUiThread(() -> {
            setLoading(false);
            Toast.makeText(this,
                    getString(R.string.upgrade_failed, message),
                    Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBillingServiceUnavailable() {
        runOnUiThread(() -> {
            setLoading(false);
            Toast.makeText(this, R.string.billing_unavailable, Toast.LENGTH_LONG).show();
        });
    }

    // ── Menu ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (billingManager != null) billingManager.disconnect();
        super.onDestroy();
    }
}
