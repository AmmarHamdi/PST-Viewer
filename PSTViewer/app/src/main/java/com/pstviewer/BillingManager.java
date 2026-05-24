package com.pstviewer;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Wraps the Google Play Billing client.
 * One instance per Activity that needs billing interaction.
 * Always call {@link #disconnect()} in the Activity's onDestroy.
 */
public class BillingManager implements PurchasesUpdatedListener {

    /** Callback delivered on the main thread. */
    public interface BillingCallback {
        void onPurchaseSuccess();
        void onPurchaseFailed(String message);
        void onBillingServiceUnavailable();
    }

    private final Context context;
    private final BillingCallback callback;
    private BillingClient billingClient;
    private ProductDetails proProductDetails;

    public BillingManager(Context context, BillingCallback callback) {
        this.context  = context.getApplicationContext();
        this.callback = callback;
        connect();
    }

    // ── Connection ──────────────────────────────────────────────────────────

    private void connect() {
        try {
            billingClient = BillingClient.newBuilder(context)
                    .setListener(this)
                    .enablePendingPurchases()
                    .build();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(@NonNull BillingResult result) {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        queryExistingPurchases();
                        queryProductDetails();
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    // Google Play will reconnect automatically; nothing to do here.
                }
            });
        } catch (Exception e) {
            // Billing library unavailable (e.g. device has no Google Play)
            callback.onBillingServiceUnavailable();
        }
    }

    public void disconnect() {
        if (billingClient != null && billingClient.isReady()) {
            billingClient.endConnection();
        }
    }

    // ── Query existing purchases (restore) ──────────────────────────────────

    public void queryExistingPurchases() {
        if (billingClient == null || !billingClient.isReady()) return;
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(@NonNull BillingResult result,
                                                         @NonNull List<Purchase> purchases) {
                        handlePurchaseList(purchases);
                    }
                });
    }

    // ── Query product details (for price display) ────────────────────────────

    private void queryProductDetails() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(ProManager.PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && productDetailsList != null && !productDetailsList.isEmpty()) {
                proProductDetails = productDetailsList.get(0);
            }
        });
    }

    // ── Launch purchase flow ─────────────────────────────────────────────────

    /**
     * Launches the Google Play purchase flow for the Pro upgrade.
     *
     * @param activity The foreground Activity used to host the Play dialog.
     */
    public void launchPurchaseFlow(Activity activity) {
        if (billingClient == null || !billingClient.isReady()) {
            callback.onBillingServiceUnavailable();
            return;
        }
        if (proProductDetails == null) {
            // Product details not yet fetched; re-query then retry
            queryProductDetails();
            callback.onPurchaseFailed("Product details not yet available. Please try again.");
            return;
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(proProductDetails)
                                .build()))
                .build();

        billingClient.launchBillingFlow(activity, flowParams);
    }

    // ── PurchasesUpdatedListener ─────────────────────────────────────────────

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result,
                                   List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            handlePurchaseList(purchases);
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            // No-op: user closed the dialog
        } else {
            callback.onPurchaseFailed(result.getDebugMessage());
        }
    }

    // ── Purchase processing ──────────────────────────────────────────────────

    private void handlePurchaseList(List<Purchase> purchases) {
        for (Purchase purchase : purchases) {
            if (purchase.getProducts().contains(ProManager.PRODUCT_ID)) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    // Persist Pro status locally
                    ProManager.getInstance(context).setPro(true);
                    // Acknowledge if needed (required within 3 days to avoid refund)
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    }
                    callback.onPurchaseSuccess();
                }
            }
        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, billingResult -> {
            // Nothing extra needed after acknowledgement
        });
    }
}
