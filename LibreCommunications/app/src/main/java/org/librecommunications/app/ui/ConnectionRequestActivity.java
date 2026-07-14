package org.librecommunications.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.librecommunications.app.R;
import org.librecommunications.app.network.NetworkService;
import org.librecommunications.app.ui.chat.ChatActivity;

public class ConnectionRequestActivity extends AppCompatActivity {

    public static final String ACTION_HANDSHAKE_RECEIVED = "org.librecommunications.app.ACTION_HANDSHAKE_RECEIVED";
    public static final String EXTRA_ADDRESS = "extra_address";
    public static final String EXTRA_PUBKEY = "extra_pubkey";

    private String targetAddress;
    private String peerPubKey;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_request);

        targetAddress = getIntent().getStringExtra(EXTRA_ADDRESS);
        peerPubKey = getIntent().getStringExtra(EXTRA_PUBKEY);

        if (targetAddress == null) {
            finish();
            return;
        }

        TextView tvRequestMessage = findViewById(R.id.tv_request_message);
        tvRequestMessage.setText(targetAddress + " wants to connect securely.");

        Button btnAccept = findViewById(R.id.btn_accept);
        Button btnDecline = findViewById(R.id.btn_decline);

        btnAccept.setOnClickListener(v -> {
            // Tell NetworkService to send handshake_ack
            Intent acceptIntent = new Intent(this, NetworkService.class);
            acceptIntent.setAction(NetworkService.ACTION_ACCEPT_HANDSHAKE);
            acceptIntent.putExtra(EXTRA_ADDRESS, targetAddress);
            acceptIntent.putExtra(EXTRA_PUBKEY, peerPubKey);
            startService(acceptIntent);

            // Open chat immediately
            Intent chatIntent = new Intent(this, ChatActivity.class);
            chatIntent.putExtra("contact_id", targetAddress); // using address as ID for prototype
            startActivity(chatIntent);
            finish();
        });

        btnDecline.setOnClickListener(v -> {
            Toast.makeText(this, "Connection declined", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
