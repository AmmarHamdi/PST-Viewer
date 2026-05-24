package com.pstviewer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Singleton that persists the user's Pro purchase status.
 * The billing library re-validates purchases at runtime; this class caches
 * the result so the UI can be gated without hitting the network on every screen.
 */
public class ProManager {

    /** Google Play product ID — must match the entry in the Play Console. */
    public static final String PRODUCT_ID = "pro_upgrade";

    private static final String PREF_NAME = "pst_pro_prefs";
    private static final String KEY_IS_PRO = "is_pro";

    private static ProManager instance;
    private final SharedPreferences prefs;

    private ProManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ProManager getInstance(Context context) {
        if (instance == null) {
            instance = new ProManager(context);
        }
        return instance;
    }

    /** Returns true if the user has purchased Pro. */
    public boolean isPro() {
        return prefs.getBoolean(KEY_IS_PRO, false);
    }

    /**
     * Persist the Pro status.  Called by {@link BillingManager} after a
     * successful purchase or purchase query.
     */
    public void setPro(boolean pro) {
        prefs.edit().putBoolean(KEY_IS_PRO, pro).apply();
    }
}
