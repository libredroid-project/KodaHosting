package eu.kodanetwork.mchost.ui.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.kodanetwork.mchost.R;

public class ModrinthSearchAdapter extends RecyclerView.Adapter<ModrinthSearchAdapter.ViewHolder> {

    private final Context context;
    private JSONArray results;
    private final OnItemClickListener listener;
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnItemClickListener {
        void onDownloadClicked(JSONObject project, ProgressBar pb, ImageButton btn);
    }

    public ModrinthSearchAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
        this.results = new JSONArray();
    }

    public void setResults(JSONArray results) {
        this.results = results;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_modrinth_project, parent, false);
        eu.kodanetwork.mchost.util.TerminalThemeHelper.applyThemeToView(parent.getContext(), view);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject project = results.getJSONObject(position);
            holder.tvTitle.setText(project.optString("title"));
            holder.tvAuthor.setText(project.optString("author"));
            holder.tvDesc.setText(project.optString("description"));
            
            holder.pbDownload.setVisibility(View.GONE);
            holder.btnDownload.setVisibility(View.VISIBLE);

            String iconUrl = project.optString("icon_url", "");
            holder.ivIcon.setImageBitmap(null);
            if (!iconUrl.isEmpty()) {
                io.execute(() -> {
                    try {
                        InputStream in = new URL(iconUrl).openStream();
                        Bitmap bmp = BitmapFactory.decodeStream(in);
                        mainHandler.post(() -> holder.ivIcon.setImageBitmap(bmp));
                    } catch (Exception ignored) {}
                });
            }

            holder.btnDownload.setOnClickListener(v -> {
                listener.onDownloadClicked(project, holder.pbDownload, holder.btnDownload);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return results.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvAuthor, tvDesc;
        ImageButton btnDownload;
        ProgressBar pbDownload;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_project_icon);
            tvTitle = itemView.findViewById(R.id.tv_project_title);
            tvAuthor = itemView.findViewById(R.id.tv_project_author);
            tvDesc = itemView.findViewById(R.id.tv_project_desc);
            btnDownload = itemView.findViewById(R.id.btn_project_download);
            pbDownload = itemView.findViewById(R.id.pb_project_download);
        }
    }
}
