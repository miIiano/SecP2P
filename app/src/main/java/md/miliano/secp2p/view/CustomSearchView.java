package md.miliano.secp2p.view;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import md.miliano.secp2p.R;

public class CustomSearchView extends FrameLayout {

    public TextView txtSearch;
    public ImageView imvwUp;
    public ImageView imvwDown;

    public CustomSearchView(Context context) {
        super(context);
        inflate(context, R.layout.chat_search_view, this);

        txtSearch = findViewById(R.id.txtSearch);
        imvwDown = findViewById(R.id.imvwDown);
        imvwUp = findViewById(R.id.imvwUp);
    }
}
