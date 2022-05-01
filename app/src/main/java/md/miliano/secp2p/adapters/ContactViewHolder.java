package md.miliano.secp2p.adapters;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import md.miliano.secp2p.R;
import md.miliano.secp2p.db.Contact;

public class ContactViewHolder extends RecyclerView.ViewHolder {
    public TextView address, name;
    public View accept, decline;
    public TextView count, time;
    public ImageView imageView;
    public ImageView imvwStatus;
    public Contact contact;
    public int position;
    public LinearLayout llMainView;

    public ContactViewHolder(View view) {
        super(view);
        llMainView = view.findViewById(R.id.llMainView);
        address = view.findViewById(R.id.address);
        name = view.findViewById(R.id.name);
        accept = view.findViewById(R.id.accept);
        decline = view.findViewById(R.id.decline);
        imageView = view.findViewById(R.id.imageView);
        count = view.findViewById(R.id.count);
        time = view.findViewById(R.id.time);
        imvwStatus = view.findViewById(R.id.status);
    }
}
