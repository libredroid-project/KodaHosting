package eu.kodanetwork.mchost.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;

import eu.kodanetwork.mchost.R;

public class SupportSelectionDialog extends Dialog {

    private SupportDialogListener listener;

    public interface SupportDialogListener {
        void onReportBugClicked();
        void onReportServerClicked();
        void onMyTicketsClicked();
    }

    public SupportSelectionDialog(Context context, SupportDialogListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_support_selection);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getWindow().setLayout(
                (int)(getContext().getResources().getDisplayMetrics().widthPixels * 0.90),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        LinearLayout llLoginWarning = findViewById(R.id.ll_login_warning);
        LinearLayout llButtons = findViewById(R.id.ll_buttons);

        boolean isLoggedIn = eu.kodanetwork.mchost.network.supabase.SupabaseAuth.isLoggedIn(getContext());

        if (!isLoggedIn) {
            llLoginWarning.setVisibility(View.VISIBLE);
            llButtons.setVisibility(View.GONE);
        } else {
            llLoginWarning.setVisibility(View.GONE);
            llButtons.setVisibility(View.VISIBLE);
        }

        findViewById(R.id.btn_report_bug).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onReportBugClicked();
        });

        findViewById(R.id.btn_report_server).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onReportServerClicked();
        });

        findViewById(R.id.btn_my_tickets).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onMyTicketsClicked();
        });

        findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
    }
}
