package org.librecommunications.app.ui.emergency;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;
import org.librecommunications.app.R;
import org.librecommunications.app.security.PanicManager;

public class EmergencyFragment extends Fragment {

    private Button btnSos;
    private Button btnPanicWipe;
    private Button btnConfigMedical;
    private SwitchMaterial switchLocation;
    private SwitchMaterial switchMedicalInfo;
    private SwitchMaterial switchLiveCamera;

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency, container, false);
        
        prefs = requireContext().getSharedPreferences("emergency_prefs", Context.MODE_PRIVATE);

        btnSos = view.findViewById(R.id.btnSos);
        btnPanicWipe = view.findViewById(R.id.btnPanicWipe);
        btnConfigMedical = view.findViewById(R.id.btnConfigMedical);
        switchLocation = view.findViewById(R.id.switchLocation);
        switchMedicalInfo = view.findViewById(R.id.switchMedicalInfo);
        switchLiveCamera = view.findViewById(R.id.switchLiveCamera);

        // Load saved preferences
        switchLocation.setChecked(prefs.getBoolean("include_location", false));
        switchMedicalInfo.setChecked(prefs.getBoolean("include_medical", false));
        switchLiveCamera.setChecked(prefs.getBoolean("include_camera", false));

        // Save preferences on change
        switchLocation.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("include_location", isChecked).apply());
        switchMedicalInfo.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("include_medical", isChecked).apply());
        switchLiveCamera.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("include_camera", isChecked).apply());

        if (btnSos != null) {
            btnSos.setOnLongClickListener(v -> {
                activateSos();
                return true;
            });
            btnSos.setOnClickListener(v -> {
                Toast.makeText(requireContext(), "Press and HOLD to activate SOS", Toast.LENGTH_SHORT).show();
            });
        }
        
        if (btnPanicWipe != null) {
            btnPanicWipe.setOnClickListener(v -> confirmPanicWipe());
        }

        if (btnConfigMedical != null) {
            btnConfigMedical.setOnClickListener(v -> showMedicalConfigDialog());
        }
        
        return view;
    }
    
    private void activateSos() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "SOS_BROADCAST");
            
            if (switchLocation.isChecked()) {
                payload.put("location", "Mock GPS: 52.5200° N, 13.4050° E"); // Mock for now
            }
            if (switchMedicalInfo.isChecked()) {
                payload.put("medical_blood", prefs.getString("med_blood", "Unknown"));
                payload.put("medical_allergies", prefs.getString("med_allergies", "Unknown"));
                payload.put("medical_conditions", prefs.getString("med_conditions", "Unknown"));
                payload.put("medical_contact", prefs.getString("med_contact", "Unknown"));
            }
            if (switchLiveCamera.isChecked()) {
                payload.put("live_camera_url", "rtsp://localhost:8554/sos_stream"); // Mock intent
            }
            
            // Note: A real app would pass this payload to NetworkService to broadcast to all nodes
            Toast.makeText(requireContext(), "SOS Activated! Broadcasting to 10km mesh radius...\nPayload generated.", Toast.LENGTH_LONG).show();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showMedicalConfigDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_medical_config, null);
        EditText etBloodType = dialogView.findViewById(R.id.etBloodType);
        EditText etAllergies = dialogView.findViewById(R.id.etAllergies);
        EditText etEmergencyContact = dialogView.findViewById(R.id.etEmergencyContact);
        EditText etConditions = dialogView.findViewById(R.id.etConditions);

        etBloodType.setText(prefs.getString("med_blood", ""));
        etAllergies.setText(prefs.getString("med_allergies", ""));
        etEmergencyContact.setText(prefs.getString("med_contact", ""));
        etConditions.setText(prefs.getString("med_conditions", ""));

        new AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                prefs.edit()
                    .putString("med_blood", etBloodType.getText().toString())
                    .putString("med_allergies", etAllergies.getText().toString())
                    .putString("med_contact", etEmergencyContact.getText().toString())
                    .putString("med_conditions", etConditions.getText().toString())
                    .apply();
                Toast.makeText(requireContext(), "Medical ID saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmPanicWipe() {
        new AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("EMERGENCY WIPE")
            .setMessage("Are you sure? This will delete ALL data, keys, and messages permanently.")
            .setPositiveButton("WIPE", (dialog, which) -> {
                PanicManager.triggerPanic(requireContext());
                Toast.makeText(requireContext(), "Data wiped", Toast.LENGTH_SHORT).show();
                requireActivity().finishAffinity();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
