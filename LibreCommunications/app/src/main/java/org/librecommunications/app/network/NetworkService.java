package org.librecommunications.app.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.librecommunications.app.R;
import org.librecommunications.app.data.LibreDatabase;
import org.librecommunications.app.data.entity.Message;
import org.librecommunications.app.crypto.SessionManager;
import org.librecommunications.app.crypto.E2EEManager;
import org.librecommunications.app.network.p2p.P2pServerThread;
import org.librecommunications.app.network.p2p.P2pClientThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkService extends Service {

    private static final String TAG = "NetworkService";
    private static final String CHANNEL_ID = "libre_network_channel";
    private static final int NOTIFICATION_ID = 1001;

    private P2pServerThread serverThread;
    private ExecutorService dbExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        dbExecutor = Executors.newSingleThreadExecutor();
        startServer();
    }

    public static final String ACTION_ACCEPT_HANDSHAKE = "org.librecommunications.app.ACTION_ACCEPT_HANDSHAKE";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_ACCEPT_HANDSHAKE.equals(intent.getAction())) {
            String targetAddress = intent.getStringExtra(org.librecommunications.app.ui.ConnectionRequestActivity.EXTRA_ADDRESS);
            String peerPubKey = intent.getStringExtra(org.librecommunications.app.ui.ConnectionRequestActivity.EXTRA_PUBKEY);
            if (targetAddress != null && peerPubKey != null) {
                acceptHandshake(targetAddress, peerPubKey);
            }
            return START_STICKY;
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Libre Communications")
                .setContentText("Mesh network active and listening.")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        
        // Broadcast this node to the mesh via DNS-SD
        org.librecommunications.app.network.p2p.WifiDirectManager.getInstance(this).startLocalService();
        
        return START_STICKY;
    }

    private void acceptHandshake(String senderAddress, String peerPubKey) {
        SessionManager sessionManager = SessionManager.getInstance();
        sessionManager.establishSession(senderAddress, peerPubKey);
        
        try {
            JSONObject ack = new JSONObject();
            ack.put("type", "handshake_ack");
            ack.put("pubKey", sessionManager.getSession(senderAddress).getMyPublicKeyBase64());
            sendMessageRaw(senderAddress, ack.toString());
            Log.d(TAG, "Handshake accepted, sent ack to " + senderAddress);

            // Update contact online status in DB
            dbExecutor.execute(() -> {
                org.librecommunications.app.data.dao.ContactDao dao = LibreDatabase.getDatabase(getApplicationContext()).contactDao();
                org.librecommunications.app.data.entity.Contact c = dao.getContactById(senderAddress);
                if (c != null) {
                    c.isOnline = true;
                    dao.update(c);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void startServer() {
        serverThread = new P2pServerThread(this::handleIncomingMessage);
        serverThread.start();
    }

    private void handleIncomingMessage(String payload, String senderAddress) {
        Log.d(TAG, "Received raw payload from " + senderAddress);
        try {
            JSONObject json = new JSONObject(payload);
            String type = json.optString("type", "plaintext");

            SessionManager sessionManager = SessionManager.getInstance();

            if ("handshake".equals(type)) {
                String peerPubKey = json.getString("pubKey");
                
                // Launch ConnectionRequestActivity
                Intent requestIntent = new Intent(this, org.librecommunications.app.ui.ConnectionRequestActivity.class);
                requestIntent.putExtra(org.librecommunications.app.ui.ConnectionRequestActivity.EXTRA_ADDRESS, senderAddress);
                requestIntent.putExtra(org.librecommunications.app.ui.ConnectionRequestActivity.EXTRA_PUBKEY, peerPubKey);
                requestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(requestIntent);
                
                Log.d(TAG, "Handshake received, prompting user for " + senderAddress);
                return;
            } 
            else if ("handshake_ack".equals(type)) {
                String peerPubKey = json.getString("pubKey");
                sessionManager.establishSession(senderAddress, peerPubKey);
                Log.d(TAG, "Handshake ack received, secure session established with " + senderAddress);

                // Update contact online status in DB
                dbExecutor.execute(() -> {
                    org.librecommunications.app.data.dao.ContactDao dao = LibreDatabase.getDatabase(getApplicationContext()).contactDao();
                    org.librecommunications.app.data.entity.Contact c = dao.getContactById(senderAddress);
                    if (c != null) {
                        c.isOnline = true;
                        dao.update(c);
                    }
                });
                return;
            } 
            else if ("encrypted".equals(type)) {
                if (!sessionManager.hasSecureSession(senderAddress)) {
                    Log.e(TAG, "Received encrypted message but no secure session exists!");
                    return; // Drop message, or initiate handshake
                }
                
                String cipherText = json.getString("cipherText");
                String decryptedPayload = sessionManager.getSession(senderAddress).decryptMessage(cipherText);
                
                if (decryptedPayload == null) {
                    Log.e(TAG, "Failed to decrypt message!");
                    return;
                }
                
                // Parse decrypted message
                json = new JSONObject(decryptedPayload);
            }

            // Normal plaintext or successfully decrypted message processing
            String messageId = json.getString("messageId");
            String contactId = json.getString("contactId");
            String content = json.getString("content");
            long timestamp = json.getLong("timestamp");

            Message message = new Message(messageId, contactId, content, timestamp, false, 2, 0, null);

            dbExecutor.execute(() -> {
                LibreDatabase.getDatabase(getApplicationContext()).messageDao().insert(message);
            });
            Log.d(TAG, "Message inserted into db securely");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse incoming message", e);
        }
    }

    /**
     * Send a secure message over P2P. Initiates handshake if needed.
     */
    public void sendSecureMessage(String targetAddress, String plaintextPayload) {
        SessionManager sessionManager = SessionManager.getInstance();
        
        if (!sessionManager.hasSecureSession(targetAddress)) {
            Log.d(TAG, "No secure session, initiating handshake...");
            try {
                JSONObject handshake = new JSONObject();
                handshake.put("type", "handshake");
                handshake.put("pubKey", sessionManager.getSession(targetAddress).getMyPublicKeyBase64());
                sendMessageRaw(targetAddress, handshake.toString());
            } catch (Exception e) { e.printStackTrace(); }
            
            // Queue message for later or wait... (for simplicity in this prototype we'll just log)
            Log.w(TAG, "Message dropped pending handshake. Please retry sending.");
            return;
        }
        
        Log.d(TAG, "Encrypting and sending message...");
        String cipherText = sessionManager.getSession(targetAddress).encryptMessage(plaintextPayload);
        try {
            JSONObject encrypted = new JSONObject();
            encrypted.put("type", "encrypted");
            encrypted.put("cipherText", cipherText);
            sendMessageRaw(targetAddress, encrypted.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void sendMessageRaw(String address, String payload) {
        new P2pClientThread(address, payload, new P2pClientThread.SendCallback() {
            @Override
            public void onSuccess() { Log.d(TAG, "Raw payload sent successfully"); }
            @Override
            public void onFailure(Exception e) { Log.e(TAG, "Raw payload failed to send", e); }
        }).start();
    }

    @Override
    public void onDestroy() {
        if (serverThread != null) {
            serverThread.stopServer();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mesh Network Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the mesh network running in the background");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
