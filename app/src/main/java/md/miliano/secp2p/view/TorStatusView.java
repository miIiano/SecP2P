package md.miliano.secp2p.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import md.miliano.secp2p.R;
import md.miliano.secp2p.tor.Tor;

public class TorStatusView extends LinearLayout {

    public TorStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void update() {
        TextView view = findViewById(R.id.status);
        Tor tor = Tor.getInstance(getContext());
        setVisibility(!tor.isReady() ? View.VISIBLE : View.GONE);
        String status = tor.getStatus();
        int i = status.indexOf(']');
        if (i >= 0) status = status.substring(i + 1);
        status = status.trim();
        String prefix = "Bootstrapped";
        if (status.contains("%") && status.length() > prefix.length() && status.startsWith(prefix)) {
            status = status.substring(prefix.length());
            status = status.trim();
            view.setText(status);
        } else if (view.length() == 0) {
            view.setText(R.string.starting_tor_);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            Tor tor = Tor.getInstance(getContext());
            tor.addLogListener(mLogListener);
            update();
        }
    }

    private final Tor.LogListener mLogListener = () -> post(() -> update());

    @Override
    protected void onDetachedFromWindow() {
        Tor tor = Tor.getInstance(getContext());
        tor.removeLogListener(mLogListener);
        if (!isInEditMode()) {
            super.onDetachedFromWindow();
        }
    }
}
