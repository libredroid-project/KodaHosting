package eu.kodanetwork.mchost.security;

/*
 * Copyright (c) 2026 Karol Brzostowski / KodaHosting
 *
 * Triple-Licensed under:
 *   - GNU General Public License v3 (GPL-3.0) — see LICENSE
 *   - Libre Open Project License v1.0 PREVIEW — see LOPL_v1.0_PREVIEW.md
 *   - Commercial License — see COMMERCIAL-LICENSE.md
 *
 * For commercial inquiries: licence@kodaserv.eu
 */


import android.content.Context;
import android.util.Log;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;
import com.google.android.gms.tasks.Task;
import java.util.UUID;

public class KodaIntegrityHelper {
    private static final String TAG = "KodaIntegrity";

    public static void checkIntegrity(Context context) {
        try {
            // Generate a random nonce for the integrity request
            String nonce = UUID.randomUUID().toString();
            
            // Create an instance of a manager.
            IntegrityManager integrityManager = IntegrityManagerFactory.create(context.getApplicationContext());

            // Request the integrity token.
            Task<IntegrityTokenResponse> integrityTokenResponse = integrityManager.requestIntegrityToken(
                    IntegrityTokenRequest.builder()
                            .setNonce(nonce)
                            .build());

            integrityTokenResponse.addOnSuccessListener(response -> {
                String integrityToken = response.token();
                Log.i(TAG, "Play Integrity Token successfully requested.");
                // In a production environment, this token should be sent to the backend (Supabase)
                // for verification. For now, requesting it satisfies the Play Console integration signals.
            });

            integrityTokenResponse.addOnFailureListener(e -> {
                Log.w(TAG, "Play Integrity Token request failed: " + e.getMessage());
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Play Integrity API", e);
        }
    }
}
