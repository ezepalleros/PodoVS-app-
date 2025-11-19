package com.example.podovs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

public class CodeFragment extends AppCompatDialogFragment {

    private static final String ARG_ROOM_ID = "room_id";

    public static CodeFragment newInstance(@NonNull String roomId, String uid) {
        CodeFragment f = new CodeFragment();
        Bundle b = new Bundle();
        b.putString(ARG_ROOM_ID, roomId);
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View view = inflater.inflate(R.layout.fragment_code, null, false);
        EditText etCode = view.findViewById(R.id.etRoomCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Ingresar código")
                .setView(view)
                .setPositiveButton("Unirse", (dialog, which) -> {
                    String roomId = getArguments() != null ? getArguments().getString(ARG_ROOM_ID) : null;
                    String code = etCode.getText().toString().trim();

                    if (roomId == null) return;

                    if (TextUtils.isEmpty(code)) {
                        Toast.makeText(ctx, "Ingresá el código de 4 letras.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (code.length() != 4) {
                        Toast.makeText(ctx, "El código debe tener 4 letras.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (getActivity() instanceof VersusActivity) {
                        ((VersusActivity) getActivity()).joinRoomWithCode(roomId, code);
                    }
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    // nada
                });

        return builder.create();
    }
}
