package md.miliano.secp2p.adapters;

import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import md.miliano.secp2p.R;
import md.miliano.secp2p.db.Message;

public class ChatHolderText extends RecyclerView.ViewHolder {

    public RelativeLayout llMainView;
    public TextView content, time, description;
    public View left, right;
    public CardView card;
    public View abort;
    public Message message;
    public Message quotedMessage;
    public ImageView status;
    public int position;
    public RelativeLayout rlReply;
    public ImageView imvwReply;
    public ImageView imvwBar;
    public TextView txtReplyText;

    public ChatHolderText(View v) {
        super(v);
        llMainView = v.findViewById(R.id.llMainView);
        content = v.findViewById(R.id.message);
        time = v.findViewById(R.id.dateTime);
        status = v.findViewById(R.id.status);
        description = v.findViewById(R.id.description);
        left = v.findViewById(R.id.left);
        right = v.findViewById(R.id.right);
        card = v.findViewById(R.id.card);
        abort = v.findViewById(R.id.abort);

        rlReply = v.findViewById(R.id.rlReply);
        imvwReply = v.findViewById(R.id.imvwReply);
        imvwBar = v.findViewById(R.id.bar);
        txtReplyText = v.findViewById(R.id.txtReplyText);
    }
}
