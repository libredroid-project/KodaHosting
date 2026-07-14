package org.librecommunications.app.ui.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.librecommunications.app.R;
import org.librecommunications.app.data.LibreDatabase;
import org.librecommunications.app.data.entity.Contact;
import org.librecommunications.app.data.entity.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";

    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private ImageButton btnAttach;
    private TextView tvStatusText;
    private View statusDot;
    private TextView tvContactName;
    
    private MessageAdapter adapter;
    private List<Message> messages = new ArrayList<>();
    
    private String contactId;
    private ExecutorService dbExecutor;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        sendFile(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Prevent screenshots
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        
        setContentView(R.layout.activity_chat);
        
        contactId = getIntent().getStringExtra("contactId");
        if (contactId == null) {
            Toast.makeText(this, "Error: No contact ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        dbExecutor = Executors.newSingleThreadExecutor();
        
        initViews();
        setupRecyclerView();
        loadContactStatus();
    }

    private void initViews() {
        recyclerMessages = findViewById(R.id.recycler_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnAttach = findViewById(R.id.btn_attach);
        
        tvStatusText = findViewById(R.id.tv_status_text);
        statusDot = findViewById(R.id.status_dot);
        tvContactName = findViewById(R.id.tv_contact_name);
        
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnCall = findViewById(R.id.btn_call);
        ImageButton btnVerify = findViewById(R.id.btn_verify);
        
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnCall != null) btnCall.setOnClickListener(v -> Toast.makeText(this, "Secure Voice Call coming soon", Toast.LENGTH_SHORT).show());
        if (btnVerify != null) btnVerify.setOnClickListener(v -> Toast.makeText(this, "Verify safety number", Toast.LENGTH_SHORT).show());
        
        if (btnSend != null) btnSend.setOnClickListener(v -> sendMessage());
        if (btnAttach != null) btnAttach.setOnClickListener(v -> openFilePicker());
    }
    
    private void setupRecyclerView() {
        adapter = new MessageAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(layoutManager);
        recyclerMessages.setAdapter(adapter);
    }

    private void loadContactStatus() {
        try {
            LibreDatabase.getDatabase(this).contactDao().getContactByIdLiveData(contactId).observe(this, contact -> {
                if (contact != null) {
                    if (tvContactName != null) tvContactName.setText(contact.name);
                    if (contact.isOnline) {
                        if (tvStatusText != null) tvStatusText.setText("Online (Secure P2P)");
                        if (statusDot != null) statusDot.setBackgroundResource(R.drawable.bg_status_dot_online);
                        if (etMessage != null) { etMessage.setEnabled(true); etMessage.setHint("Type a message..."); }
                        if (btnSend != null) btnSend.setEnabled(true);
                        if (btnAttach != null) btnAttach.setEnabled(true);
                    } else {
                        if (tvStatusText != null) tvStatusText.setText("Offline / Waiting...");
                        if (statusDot != null) statusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
                        if (etMessage != null) { etMessage.setEnabled(false); etMessage.setHint("Waiting for connection..."); }
                        if (btnSend != null) btnSend.setEnabled(false);
                        if (btnAttach != null) btnAttach.setEnabled(false);
                    }
                } else {
                    // Contact doesn't exist in DB yet — show placeholder state
                    if (tvContactName != null) tvContactName.setText("Unknown Node");
                    if (tvStatusText != null) tvStatusText.setText("Not connected");
                    if (statusDot != null) statusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
                    if (etMessage != null) { etMessage.setEnabled(false); etMessage.setHint("No connection..."); }
                    if (btnSend != null) btnSend.setEnabled(false);
                    if (btnAttach != null) btnAttach.setEnabled(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to load contact status", e);
            if (tvContactName != null) tvContactName.setText("Chat");
            if (tvStatusText != null) tvStatusText.setText("Error loading contact");
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"image/*", "application/pdf"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }
    
    private void sendMessage() {
        if (etMessage == null) return;
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        
        Message msg = Message.createOutgoingText(contactId, text);
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerMessages.scrollToPosition(messages.size() - 1);
        
        etMessage.setText("");
    }

    private void sendFile(Uri uri) {
        Toast.makeText(this, "Preparing to send file securely...", Toast.LENGTH_LONG).show();
        
        Message msg = Message.createOutgoingMedia(contactId, 1, uri.toString());
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerMessages.scrollToPosition(messages.size() - 1);
    }
    
    @Override
    protected void onDestroy() {
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        super.onDestroy();
    }

    // --- Inner Adapter Class ---
    public static class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_SENT = 1;
        private static final int VIEW_TYPE_RECEIVED = 2;
        
        private final List<Message> messages;

        public MessageAdapter(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isOutgoing ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_SENT) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
                return new SentMessageHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
                return new ReceivedMessageHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Message message = messages.get(position);
            
            if (holder.getItemViewType() == VIEW_TYPE_SENT) {
                ((SentMessageHolder) holder).bind(message);
            } else {
                ((ReceivedMessageHolder) holder).bind(message);
            }
        }

        @Override
        public int getItemCount() { return messages.size(); }

        static class SentMessageHolder extends RecyclerView.ViewHolder {
            TextView tvText;
            public SentMessageHolder(@NonNull View itemView) {
                super(itemView);
                tvText = itemView.findViewById(R.id.tvMessageContent);
                if (tvText == null) tvText = itemView.findViewById(android.R.id.text1);
            }
            void bind(Message msg) {
                if (tvText == null) return;
                if (msg.messageType == 0) {
                    tvText.setText(msg.content);
                } else {
                    tvText.setText("[Secure Attachment]");
                }
            }
        }
        
        static class ReceivedMessageHolder extends RecyclerView.ViewHolder {
            TextView tvText;
            public ReceivedMessageHolder(@NonNull View itemView) {
                super(itemView);
                tvText = itemView.findViewById(R.id.tvMessageContent);
                if (tvText == null) tvText = itemView.findViewById(android.R.id.text1);
            }
            void bind(Message msg) {
                if (tvText == null) return;
                if (msg.messageType == 0) {
                    tvText.setText(msg.content);
                } else {
                    tvText.setText("[Secure Attachment]");
                }
            }
        }
    }
}
