package org.librecommunications.app.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.librecommunications.app.R;
import org.librecommunications.app.crypto.SessionManager;
import org.librecommunications.app.network.NetworkService;

public class SettingsFragment extends Fragment {

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        LinearLayout layoutEditProfile = view.findViewById(R.id.layoutEditProfile);
        SwitchMaterial switchMesh = view.findViewById(R.id.switchMesh);
        LinearLayout layoutEncryption = view.findViewById(R.id.layoutEncryption);
        Button btnSignOut = view.findViewById(R.id.btnSignOut);

        // --- Profile ---
        if (layoutEditProfile != null) {
            layoutEditProfile.setOnClickListener(v -> showEditProfileDialog());
        }

        // --- Mesh Network ---
        if (switchMesh != null) {
            switchMesh.setChecked(prefs.getBoolean("mesh_enabled", true));
            switchMesh.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("mesh_enabled", isChecked).apply();
                if (isChecked) {
                    requireContext().startService(new Intent(requireContext(), NetworkService.class));
                    Toast.makeText(requireContext(), "Mesh Network Enabled", Toast.LENGTH_SHORT).show();
                } else {
                    requireContext().stopService(new Intent(requireContext(), NetworkService.class));
                    Toast.makeText(requireContext(), "Mesh Network Disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // --- Encryption Keys ---
        if (layoutEncryption != null) {
            layoutEncryption.setOnClickListener(v -> showEncryptionKeysDialog());
        }

        // --- Disconnect Node ---
        if (btnSignOut != null) {
            btnSignOut.setOnClickListener(v -> {
                requireContext().stopService(new Intent(requireContext(), NetworkService.class));
                Toast.makeText(requireContext(), "Node disconnected from mesh.", Toast.LENGTH_SHORT).show();
                requireActivity().finishAffinity();
            });
        }
        
        return view;
    }

    private void showEditProfileDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("Enter Display Name");
        input.setTextColor(getResources().getColor(android.R.color.white, null));
        input.setText(prefs.getString("display_name", "Anonymous Node"));

        new AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Edit Display Name")
            .setView(input)
            .setPositiveButton("Save", (dialog, which) -> {
                prefs.edit().putString("display_name", input.getText().toString()).apply();
                Toast.makeText(requireContext(), "Name saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEncryptionKeysDialog() {
        String pubKey = SessionManager.getInstance().getMyPublicKey();
        String fingerprint = pubKey.substring(0, Math.min(pubKey.length(), 32)) + "...";

        new AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Encryption Identity")
            .setMessage("Your ECDH Public Key Fingerprint:\n\n" + fingerprint + "\n\nThis key is used to establish Perfect Forward Secrecy over the mesh network.")
            .setPositiveButton("Copy", (dialog, which) -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Public Key", pubKey);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), "Key copied to clipboard", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Regenerate", (dialog, which) -> {
                Toast.makeText(requireContext(), "Keys are ephemeral by default in this prototype.", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Close", null)
            .show();
    }
}
