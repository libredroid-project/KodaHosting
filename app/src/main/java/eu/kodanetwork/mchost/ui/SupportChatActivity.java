package eu.kodanetwork.mchost.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import eu.kodanetwork.mchost.R;
import eu.kodanetwork.mchost.security.PraetorSecurity;
import eu.kodanetwork.mchost.network.supabase.SupportApi;
import eu.kodanetwork.mchost.util.ThemeHelper;

public class SupportChatActivity extends AppCompatActivity {

    private String ticketId;
    private String myUuid;
    private String sessionToken;
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private List<JSONObject> messagesList = new ArrayList<>();
    
    private Uri selectedAttachmentUri = null;
    private String selectedAttachmentName = null;
    private LinearLayout llAttachmentPreview;
    private TextView tvAttachmentName;
    private EditText etMessage;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    private android.os.Handler pollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            loadMessages();
            pollHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        eu.kodanetwork.mchost.util.Material3ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        ThemeHelper.apply(this);
        setContentView(R.layout.activity_support_chat);

        ticketId = getIntent().getStringExtra("TICKET_ID");
        String title = getIntent().getStringExtra("TICKET_TITLE");
        myUuid = eu.kodanetwork.mchost.App.getPrefs(this).getString("app_uuid", null);
        sessionToken = eu.kodanetwork.mchost.App.getPrefs(this).getString("koda_session_token", null);

        TextView tvTitle = findViewById(R.id.tv_chat_title);
        tvTitle.setText(title != null ? title : "Support Ticket");

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            View root = findViewById(android.R.id.content);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int topInset = insets.getSystemWindowInsetTop();
                int bottomInset = insets.getSystemWindowInsetBottom();
                v.setPadding(0, topInset, 0, bottomInset);
                if (rvChat != null && adapter != null && adapter.getItemCount() > 0) {
                    rvChat.scrollToPosition(adapter.getItemCount() - 1);
                }
                return insets;
            });
        }
        
        // Export button placeholder
        findViewById(R.id.btn_export).setOnClickListener(v -> {
            exportChat();
        });

        rvChat = findViewById(R.id.rv_chat);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        adapter = new ChatAdapter();
        rvChat.setAdapter(adapter);

        etMessage = findViewById(R.id.et_message);
        llAttachmentPreview = findViewById(R.id.ll_attachment_preview);
        tvAttachmentName = findViewById(R.id.tv_attachment_name);

        findViewById(R.id.btn_remove_attachment).setOnClickListener(v -> {
            selectedAttachmentUri = null;
            selectedAttachmentName = null;
            llAttachmentPreview.setVisibility(View.GONE);
        });

        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedAttachmentUri = result.getData().getData();
                    if (selectedAttachmentUri != null) {
                        selectedAttachmentName = getFileName(selectedAttachmentUri);
                        tvAttachmentName.setText(selectedAttachmentName);
                        llAttachmentPreview.setVisibility(View.VISIBLE);
                    }
                }
            }
        );

        findViewById(R.id.btn_attach).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });

        findViewById(R.id.btn_send).setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (msg.isEmpty() && selectedAttachmentUri == null) return;
            
            sendMessage(msg);
        });

        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.postDelayed(pollRunnable, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void loadMessages() {
        new Thread(() -> {
            try {
                JSONObject rpcObj = new JSONObject();
                rpcObj.put("p_ticket_id", ticketId);
                rpcObj.put("p_reporter_uuid", myUuid);
                String response = SupportApi.makeSupabaseRequest(
                        "rest/v1/rpc/rpc_get_ticket_messages", "POST", rpcObj.toString(), sessionToken);
                JSONArray arr = new JSONArray(response);
                
                List<JSONObject> newMsgs = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    newMsgs.add(arr.getJSONObject(i));
                }

                runOnUiThread(() -> {
                    messagesList.clear();
                    messagesList.addAll(newMsgs);
                    adapter.notifyDataSetChanged();
                    if (!messagesList.isEmpty()) {
                        rvChat.scrollToPosition(messagesList.size() - 1);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(SupportChatActivity.this, "Error loading messages: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void exportChat() {
        if (messagesList == null || messagesList.isEmpty()) {
            Toast.makeText(this, "No messages to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Support Ticket: ").append(ticketId).append("\n");
        sb.append("Date: ").append(new java.util.Date().toString()).append("\n\n");

        for (JSONObject msg : messagesList) {
            try {
                String senderUuid = msg.getString("sender_uuid");
                String message = msg.optString("message", "");
                String attachmentUrl = msg.optString("attachment_url", null);
                String date = msg.optString("created_at", "").split("\\.")[0].replace("T", " ");
                boolean isAdmin = msg.optBoolean("is_admin", false) || "admin".equals(senderUuid);
                boolean isMe = senderUuid.equals(myUuid);

                String sender = isAdmin ? "Support Admin" : (isMe ? "You" : "System");

                sb.append("[").append(date).append("] ").append(sender).append(":\n");
                if (!message.isEmpty()) {
                    sb.append(message).append("\n");
                }
                if (attachmentUrl != null && !attachmentUrl.isEmpty() && !attachmentUrl.equals("null")) {
                    sb.append("(Attachment: ").append(attachmentUrl).append(")\n");
                }
                sb.append("\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Export Chat History");
        startActivity(shareIntent);
    }

    private void sendMessage(String text) {
        String attachmentUrlLocal = null;
        if (selectedAttachmentUri != null) {
            // Placeholder for real upload logic
            uploadAttachmentSync();
        } else {
            sendTextMessage(text, null);
        }
    }

    private void uploadAttachmentSync() {
        // Run in thread, upload file, get public URL, then send message
        new Thread(() -> {
            try {
                String fileName = System.currentTimeMillis() + "_" + selectedAttachmentName.replaceAll("[^a-zA-Z0-9.]", "_");
                URL url = new URL(PraetorSecurity.getSupabaseUrl() + "/storage/v1/object/support_attachments/" + fileName);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + PraetorSecurity.getSupabaseKey());
                conn.setRequestProperty("apikey", PraetorSecurity.getSupabaseKey());
                
                // Determine content type roughly
                String contentType = "application/octet-stream";
                if (selectedAttachmentName.toLowerCase().endsWith(".png")) contentType = "image/png";
                if (selectedAttachmentName.toLowerCase().endsWith(".jpg") || selectedAttachmentName.toLowerCase().endsWith(".jpeg")) contentType = "image/jpeg";
                
                conn.setRequestProperty("Content-Type", contentType);
                conn.setDoOutput(true);

                InputStream is = getContentResolver().openInputStream(selectedAttachmentUri);
                OutputStream os = conn.getOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
                os.close();
                is.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 201) {
                    String publicUrl = PraetorSecurity.getSupabaseUrl() + "/storage/v1/object/public/support_attachments/" + fileName;
                    String msgText = etMessage.getText().toString().trim();
                    sendTextMessage(msgText, publicUrl);
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to upload file. Code: " + responseCode, Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Upload error", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void sendTextMessage(String text, String attachmentUrl) {
        new Thread(() -> {
            try {
                JSONObject msgObj = new JSONObject();
                msgObj.put("p_ticket_id", ticketId);
                msgObj.put("p_sender_uuid", myUuid);
                if (!text.isEmpty()) {
                    msgObj.put("p_message", text);
                } else {
                    msgObj.put("p_message", "");
                }
                if (attachmentUrl != null) {
                    // Note: rpc_create_ticket_message doesn't currently support attachment_url directly
                    // It was built for (ticket_id, sender_uuid, message). 
                    // I will append the url to the message if needed.
                    msgObj.put("p_message", text.isEmpty() ? "Attachment: " + attachmentUrl : text + "\nAttachment: " + attachmentUrl);
                }

                SupportApi.makeSupabaseRequest(
                        "rest/v1/rpc/rpc_create_ticket_message", "POST", msgObj.toString(), sessionToken);

                runOnUiThread(() -> {
                    etMessage.setText("");
                    selectedAttachmentUri = null;
                    selectedAttachmentName = null;
                    llAttachmentPreview.setVisibility(View.GONE);
                    loadMessages(); // Refresh chat
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(SupportChatActivity.this, "Error sending message: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject msg = messagesList.get(position);
            try {
                String senderUuid = msg.getString("sender_uuid");
                String message = msg.optString("message", "");
                String attachmentUrl = msg.optString("attachment_url", null);
                String date = msg.optString("created_at", "").split("\\.")[0].replace("T", " ");

                boolean isMe = senderUuid.equals(myUuid);
                boolean isAdmin = msg.optBoolean("is_admin", false) || "admin".equals(senderUuid);

                boolean isLight = ThemeHelper.isLightMode(SupportChatActivity.this);
                LinearLayout.LayoutParams containerParams = (LinearLayout.LayoutParams) holder.llMessageContainer.getLayoutParams();
                if (isMe) {
                    containerParams.gravity = android.view.Gravity.END;
                    holder.tvSenderName.setText(getString(R.string.chat_you));
                    holder.tvSenderName.setTextColor(isLight ? 0xFF555555 : 0xFF888888);
                    holder.llBubble.setBackgroundResource(isLight ? R.drawable.bg_chat_bubble_me_light : R.drawable.bg_chat_bubble_me);
                    holder.tvMessage.setTextColor(isLight ? 0xFF111111 : 0xFFFFFFFF);
                } else {
                    containerParams.gravity = android.view.Gravity.START;
                    if (isAdmin) {
                        holder.tvSenderName.setText(getString(R.string.chat_admin));
                        holder.tvSenderName.setTextColor(0xFFFF8C00); // Orange for admin
                        holder.llBubble.setBackgroundResource(isLight ? R.drawable.bg_chat_bubble_admin_light : R.drawable.bg_chat_bubble_admin);
                        holder.tvMessage.setTextColor(isLight ? 0xFF111111 : 0xFFFFFFFF);
                    } else {
                        holder.tvSenderName.setText(getString(R.string.chat_system));
                        holder.tvSenderName.setTextColor(isLight ? 0xFF555555 : 0xFF888888);
                        holder.llBubble.setBackgroundResource(isLight ? R.drawable.bg_chat_bubble_system_light : R.drawable.bg_chat_bubble_system);
                        holder.tvMessage.setTextColor(isLight ? 0xFF111111 : 0xFFFFFFFF);
                    }
                }
                holder.llMessageContainer.setLayoutParams(containerParams);

                if (!message.isEmpty()) {
                    holder.tvMessage.setVisibility(View.VISIBLE);
                    holder.tvMessage.setText(message);
                } else {
                    holder.tvMessage.setVisibility(View.GONE);
                }

                if (attachmentUrl != null && !attachmentUrl.isEmpty() && !attachmentUrl.equals("null")) {
                    holder.tvAttachmentLink.setVisibility(View.VISIBLE);
                    holder.tvAttachmentLink.setText("📎 View Attachment");
                    holder.tvAttachmentLink.setOnClickListener(v -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(attachmentUrl));
                        startActivity(browserIntent);
                    });
                } else {
                    holder.tvAttachmentLink.setVisibility(View.GONE);
                }

                holder.tvTime.setText(date);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return messagesList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout llMessageContainer, llBubble;
            TextView tvSenderName, tvMessage, tvTime, tvAttachmentLink;
            ImageView ivAttachment;

            ViewHolder(View itemView) {
                super(itemView);
                llMessageContainer = itemView.findViewById(R.id.ll_message_container);
                llBubble = itemView.findViewById(R.id.ll_bubble);
                tvSenderName = itemView.findViewById(R.id.tv_sender_name);
                tvMessage = itemView.findViewById(R.id.tv_message);
                tvTime = itemView.findViewById(R.id.tv_time);
                tvAttachmentLink = itemView.findViewById(R.id.tv_attachment_link);
                ivAttachment = itemView.findViewById(R.id.iv_attachment);
            }
        }
    }
}
