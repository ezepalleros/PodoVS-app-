package com.example.podovs;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;
import java.util.Random;

public class CreatorFragment extends DialogFragment {

    private static final String ARG_UID = "uid";

    public static CreatorFragment newInstance(@NonNull String uid) {
        CreatorFragment f = new CreatorFragment();
        Bundle b = new Bundle();
        b.putString(ARG_UID, uid);
        f.setArguments(b);
        return f;
    }

    private String uid;
    private FirestoreRepo repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_creator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        uid = getArguments() != null ? getArguments().getString(ARG_UID) : null;
        repo = new FirestoreRepo();

        View rootOverlay = view.findViewById(R.id.rootCreateRoomOverlay);
        MaterialCardView card = view.findViewById(R.id.cardCreateRoom);
        SwitchCompat swPublic = view.findViewById(R.id.swPublic);
        RadioGroup rgMode = view.findViewById(R.id.rgMode);
        TextView tvCodeLabel = view.findViewById(R.id.tvCodeLabel);
        TextView tvCodeValue = view.findViewById(R.id.tvCodeValue);
        MaterialButton btnCreate = view.findViewById(R.id.btnCreateRoomConfirm);
        View btnClose = view.findViewById(R.id.btnCloseDialog);

        if (rootOverlay != null) {
            rootOverlay.setOnClickListener(v -> dismiss());
        }
        if (card != null) {
            card.setOnClickListener(v -> {
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

        // código aleatorio
        String code = generateCode();
        if (tvCodeValue != null) {
            tvCodeValue.setText(code);
        }

        if (swPublic != null) {
            swPublic.setChecked(true);
        }
        updateCodeVisibility(swPublic == null || swPublic.isChecked(), tvCodeLabel, tvCodeValue);

        if (swPublic != null) {
            swPublic.setOnCheckedChangeListener((buttonView, isChecked) ->
                    updateCodeVisibility(isChecked, tvCodeLabel, tvCodeValue));
        }

        if (rgMode != null && rgMode.getCheckedRadioButtonId() == View.NO_ID) {
            rgMode.check(R.id.rbRace);
        }

        if (btnCreate != null) {
            btnCreate.setOnClickListener(v -> {
                if (uid == null) {
                    Toast.makeText(getContext(), "Error de sesión", Toast.LENGTH_SHORT).show();
                    dismiss();
                    return;
                }
                boolean isPublic = swPublic == null || swPublic.isChecked();
                boolean isRace = true;
                if (rgMode != null) {
                    isRace = rgMode.getCheckedRadioButtonId() != R.id.rbMarathon;
                }

                String finalCode = null;
                if (!isPublic && tvCodeValue != null) {
                    finalCode = tvCodeValue.getText().toString().trim();
                }

                if (!isPublic && TextUtils.isEmpty(finalCode)) {
                    Toast.makeText(getContext(), "Código inválido", Toast.LENGTH_SHORT).show();
                    return;
                }

                repo.createRoomFromOptions(
                        uid,
                        isPublic,
                        isRace,
                        finalCode,
                        aVoid -> {
                            Toast.makeText(getContext(), "Sala creada", Toast.LENGTH_SHORT).show();
                            dismiss();
                        },
                        e -> Toast.makeText(getContext(),
                                e.getMessage() == null ? "Error al crear sala" : e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            });
        }
    }

    private void updateCodeVisibility(boolean isPublic,
                                      @Nullable TextView label,
                                      @Nullable TextView value) {
        float alpha = isPublic ? 0.3f : 1f;
        int visibility = isPublic ? View.GONE : View.VISIBLE;

        if (label != null) {
            label.setAlpha(alpha);
            label.setVisibility(visibility);
        }
        if (value != null) {
            value.setAlpha(alpha);
            value.setVisibility(visibility);
        }
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
