package md.miliano.secp2p.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import io.realm.Realm;
import io.realm.RealmRecyclerViewAdapter;
import io.realm.RealmResults;
import md.miliano.secp2p.R;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.tor.Client;
import md.miliano.secp2p.ui.DeleteContact;

public class RequestsAdapter extends RealmRecyclerViewAdapter<Contact, RequestViewHolder> {

    private final Context mContext;


    public RequestsAdapter(RealmResults<Contact> data, Context context) {
        super(data, true);
        mContext = context;

        setHasStableIds(true);
    }



    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final RequestViewHolder viewHolder = new RequestViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_request, parent, false));
        viewHolder.accept.setOnClickListener(v -> {
            Contact contact = viewHolder.contact;
            Realm realm = Realm.getDefaultInstance();
            realm.beginTransaction();
            contact.setFriendReqAccepted(0);
            contact.setIncoming(0);
            contact.setOutgoing(0);
            realm.commitTransaction();
            realm.close();
            Client.getInstance(mContext).sendPublicKey(contact.getAddress());
            Client.getInstance(mContext).startAskForNewMessages(contact.getAddress());
        });
        viewHolder.decline.setOnClickListener(v -> {
            final String address = viewHolder.contact.getAddress();
            new DeleteContact(mContext).execute(viewHolder.contact.get_id());
            Snackbar.make(viewHolder.itemView, mContext.getString(R.string.contact_request_declined), Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo, v1 -> Contact.rmContact(address))
                    .show();
        });
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        final Contact contact = getItem(position);
        holder.contact = contact;
        holder.address.setText(contact.getAddress());
        holder.description.setText(contact.getDescription());
        String name = contact.getName();
        if (name == null || name.equals("")) name = "Anonymous";
        holder.name.setText(name);
    }

    @Override
    public long getItemId(int index) {
        return getItem(index).get_id();
    }

}

class RequestViewHolder extends RecyclerView.ViewHolder {
    TextView address, name, description;
    View accept, decline;
    View badge;
    TextView count;
    Contact contact;

    public RequestViewHolder(View view) {
        super(view);
        address = view.findViewById(R.id.address);
        name = view.findViewById(R.id.name);
        description = view.findViewById(R.id.description);
        accept = view.findViewById(R.id.accept);
        decline = view.findViewById(R.id.decline);
        badge = view.findViewById(R.id.badge);
        if (badge != null) count = view.findViewById(R.id.count);
    }
}
