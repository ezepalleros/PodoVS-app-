package com.example.podovs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import java.util.Locale;
import java.util.Random;

public class CreatorFragment extends DialogFragment {

    private static final String ARG_UID = "uid";

    private String uid;
    private FirestoreRepo repo;

    private SwitchCompat swPublic;
    private RadioGroup rgMode;
    private TextView tvCodeLabel;
    private TextView tvCodeValue;
    private Button btnCreate;

    public static CreatorFragment newInstance(@NonNull String uid) {
        CreatorFragment f = new CreatorFragment();
        Bundle b = new Bundle();
        b.putString(ARG_UID, uid);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new FirestoreRepo();
        if (getArguments() != null) {
            uid = getArguments().getString(ARG_UID);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_creator, container, false);

        swPublic = v.findViewById(R.id.swPublic);
        rgMode = v.findViewById(R.id.rgMode);
        tvCodeLabel = v.findViewById(R.id.tvCodeLabel);
        tvCodeValue = v.findViewById(R.id.tvCodeValue);
        btnCreate = v.findViewById(R.id.btnCreateRoomConfirm);

        // por defecto pública / carrera
        swPublic.setChecked(true);
        rgMode.check(R.id.rbRace);

        updateCodeVisibility();

        swPublic.setOnCheckedChangeListener((buttonView, isChecked) -> updateCodeVisibility());

        btnCreate.setOnClickListener(view -> onCreateClicked());

        return v;
    }

    private void updateCodeVisibility() {
        boolean isPublic = swPublic.isChecked();
        if (isPublic) {
            tvCodeLabel.setVisibility(View.GONE);
            tvCodeValue.setVisibility(View.GONE);
        } else {
            tvCodeLabel.setVisibility(View.VISIBLE);
            tvCodeValue.setVisibility(View.VISIBLE);
            tvCodeValue.setText(generateCode());
        }
    }

    private String generateCode() {
        char[] letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(letters[r.nextInt(letters.length)]);
        }
        return sb.toString();
    }

    private void onCreateClicked() {
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(getContext(), "Error de sesión.", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        boolean isPublic = swPublic.isChecked();
        int checkedId = rgMode.getCheckedRadioButtonId();
        boolean isRace = checkedId == R.id.rbRace;

        String code = isPublic ? "" : tvCodeValue.getText().toString().trim();
        if (!isPublic && code.length() != 4) {
            Toast.makeText(getContext(), "Código inválido.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreate.setEnabled(false);

        repo.createRoomFromOptions(uid, isPublic, isRace, code,
                v -> {
                    Toast.makeText(getContext(),
                            "Sala creada correctamente.",
                            Toast.LENGTH_SHORT).show();
                    dismiss();
                },
                e -> {
                    btnCreate.setEnabled(true);
                    String msg = (e != null && e.getMessage() != null)
                            ? e.getMessage()
                            : "Error al crear la sala.";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                });
    }
}
