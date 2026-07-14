package org.librecommunications.app.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.librecommunications.app.R;

import org.librecommunications.app.data.LibreDatabase;
import org.librecommunications.app.data.entity.Contact;

import java.util.ArrayList;
import java.util.List;

public class ChatListFragment extends Fragment {

    private RecyclerView recyclerChats;
    private TextView tvEmptyState;
    private FloatingActionButton fabNewChat;
    private ChatListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);

        recyclerChats = view.findViewById(R.id.recycler_chats);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        fabNewChat = view.findViewById(R.id.fab_new_chat);

        setupRecyclerView();

        fabNewChat.setOnClickListener(v -> {
            String randomId = java.util.UUID.randomUUID().toString();
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra("contactId", randomId);
            startActivity(intent);
        });

        loadContacts();

        return view;
    }

    private void setupRecyclerView() {
        adapter = new ChatListAdapter(new ArrayList<>(), contactId -> {
            Intent intent = new Intent(requireContext(), ChatActivity.class);
            intent.putExtra("contactId", contactId);
            startActivity(intent);
        });
        recyclerChats.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerChats.setAdapter(adapter);
    }

    private void loadContacts() {
        LibreDatabase.getDatabase(requireContext()).contactDao().getAllContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts == null || contacts.isEmpty()) {
                tvEmptyState.setVisibility(View.VISIBLE);
                recyclerChats.setVisibility(View.GONE);
            } else {
                tvEmptyState.setVisibility(View.GONE);
                recyclerChats.setVisibility(View.VISIBLE);
                adapter.updateData(contacts);
            }
        });
    }

    public static class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {
        private List<Contact> contacts;
        private final OnChatClickListener listener;

        public interface OnChatClickListener {
            void onChatClick(String contactId);
        }

        public ChatListAdapter(List<Contact> contacts, OnChatClickListener listener) {
            this.contacts = contacts;
            this.listener = listener;
        }

        public void updateData(List<Contact> newContacts) {
            this.contacts = newContacts;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Using a simple layout approach if item_conversation is missing
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            Contact contact = contacts.get(position);
            holder.tvName.setText(contact.name != null ? contact.name : "Unknown");
            
            // For now, omit lastMessage and time since we would need a join query or ViewModel
            holder.tvLastMessage.setText("Tap to chat");
            holder.tvTime.setText("");
            
            holder.tvUnread.setVisibility(View.GONE);

            if (holder.statusDot != null) {
                holder.statusDot.setBackgroundResource(contact.isOnline ? R.drawable.bg_status_dot_online : R.drawable.bg_status_dot_offline);
            }

            holder.itemView.setOnClickListener(v -> listener.onChatClick(contact.deviceId));
        }

        @Override
        public int getItemCount() { return contacts.size(); }

        static class ChatViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvLastMessage, tvTime, tvUnread;
            View statusDot;

            public ChatViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvContactName);
                tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvUnread = itemView.findViewById(R.id.tvUnreadCount);
                statusDot = itemView.findViewById(R.id.status_dot);
            }
        }
    }
}
