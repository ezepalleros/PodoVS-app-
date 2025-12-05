package com.example.podovs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationFragment extends Fragment {

    private RecyclerView rv;
    private NotifAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_notification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        ((TextView) v.findViewById(R.id.tvTitle)).setText("Notificaciones");
        ImageButton btnClose = v.findViewById(R.id.btnClose);
        btnClose.setOnClickListener(b ->
                requireActivity().getSupportFragmentManager().popBackStack()
        );

        rv = v.findViewById(R.id.rvNotifications);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        List<NotificationHelper.Item> items = NotificationHelper.getLast(requireContext(), 10);
        if (adapter == null) {
            adapter = new NotifAdapter(items);
            rv.setAdapter(adapter);
        } else {
            adapter.setItems(items);
        }
    }

    // ===== Adapter simple (sin archivo extra) =====
    private static class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {
        private final SimpleDateFormat fmt =
                new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        private List<NotificationHelper.Item> data;

        NotifAdapter(List<NotificationHelper.Item> data) {
            this.data = data;
        }

        void setItems(List<NotificationHelper.Item> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            NotificationHelper.Item it = data.get(position);
            String title = safeTitle(it);
            String message = safeMessage(it);
            long when = safeWhen(it);

            h.title.setText(title != null ? title : "Notificación");
            String whenStr = fmt.format(when);
            h.subtitle.setText(whenStr + " • " + (message != null ? message : ""));
        }

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }

        // ---- Helpers tolerantes a nombres de campo distintos ----
        private static String safeTitle(NotificationHelper.Item it) {
            // intenta getTitle(), campo "title", o "titulo"
            String s = tryGetterString(it, "getTitle");
            if (s != null) return s;
            s = tryFieldString(it, "title");
            if (s != null) return s;
            return tryFieldString(it, "titulo");
        }

        private static String safeMessage(NotificationHelper.Item it) {
            // intenta getMessage(), getBody(), getText(), luego campos "message"/"body"/"text"
            String s = tryGetterString(it, "getMessage");
            if (s != null) return s;
            s = tryGetterString(it, "getBody");
            if (s != null) return s;
            s = tryGetterString(it, "getText");
            if (s != null) return s;

            s = tryFieldString(it, "message");
            if (s != null) return s;
            s = tryFieldString(it, "body");
            if (s != null) return s;
            return tryFieldString(it, "text");
        }

        private static long safeWhen(NotificationHelper.Item it) {
            // intenta getWhenMillis(), getWhen(), getTimestamp(); luego campos "whenMillis"/"when"/"timestamp"
            Long l = tryGetterLong(it, "getWhenMillis");
            if (l != null) return l;
            l = tryGetterLong(it, "getWhen");
            if (l != null) return l;
            l = tryGetterLong(it, "getTimestamp");
            if (l != null) return l;

            l = tryFieldLong(it, "whenMillis");
            if (l != null) return l;
            l = tryFieldLong(it, "when");
            if (l != null) return l;
            l = tryFieldLong(it, "timestamp");
            if (l != null) return l;

            return System.currentTimeMillis();
        }

        private static String tryGetterString(Object obj, String method) {
            try {
                Method m = obj.getClass().getMethod(method);
                Object v = m.invoke(obj);
                return v != null ? v.toString() : null;
            } catch (Exception ignored) { return null; }
        }
        private static Long tryGetterLong(Object obj, String method) {
            try {
                Method m = obj.getClass().getMethod(method);
                Object v = m.invoke(obj);
                if (v instanceof Number) return ((Number) v).longValue();
                return v != null ? Long.parseLong(v.toString()) : null;
            } catch (Exception ignored) { return null; }
        }
        private static String tryFieldString(Object obj, String field) {
            try {
                Field f = obj.getClass().getField(field);
                Object v = f.get(obj);
                return v != null ? v.toString() : null;
            } catch (Exception ignored) { return null; }
        }
        private static Long tryFieldLong(Object obj, String field) {
            try {
                Field f = obj.getClass().getField(field);
                Object v = f.get(obj);
                if (v instanceof Number) return ((Number) v).longValue();
                return v != null ? Long.parseLong(v.toString()) : null;
            } catch (Exception ignored) { return null; }
        }
    }
}
