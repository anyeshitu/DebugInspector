package com.allynav.debug.inspector.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class UiRowAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<UiRow> rows = new ArrayList<>();

    UiRowAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    void replace(List<UiRow> values) {
        rows.clear();
        if (values != null) rows.addAll(values);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return rows.size(); }
    @Override public UiRow getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.inspector_list_item, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        UiRow row = getItem(position);
        holder.title.setText(row.title);
        holder.subtitle.setText(row.subtitle);
        holder.subtitle.setVisibility(empty(row.subtitle) ? View.GONE : View.VISIBLE);
        holder.meta.setText(row.meta);
        holder.meta.setVisibility(empty(row.meta) ? View.GONE : View.VISIBLE);
        holder.badge.setText(row.badge);
        holder.badge.setTextColor(colorFor(row.tone));
        return convertView;
    }

    private int colorFor(int tone) {
        if (tone == UiRow.TONE_SUCCESS) return inflater.getContext().getResources().getColor(R.color.inspector_status_2xx);
        if (tone == UiRow.TONE_INFO) return inflater.getContext().getResources().getColor(R.color.inspector_status_3xx);
        if (tone == UiRow.TONE_WARNING) return inflater.getContext().getResources().getColor(R.color.inspector_status_4xx);
        if (tone == UiRow.TONE_ERROR) return inflater.getContext().getResources().getColor(R.color.inspector_status_5xx);
        return inflater.getContext().getResources().getColor(R.color.inspector_status_idle);
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }

    private static final class Holder {
        final TextView title;
        final TextView subtitle;
        final TextView meta;
        final TextView badge;
        Holder(View view) {
            badge = view.findViewById(R.id.row_badge);
            title = view.findViewById(R.id.row_title);
            subtitle = view.findViewById(R.id.row_subtitle);
            meta = view.findViewById(R.id.row_meta);
        }
    }
}
