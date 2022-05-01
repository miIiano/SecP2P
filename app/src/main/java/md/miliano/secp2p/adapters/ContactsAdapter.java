package md.miliano.secp2p.adapters;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.model.FileDownloadStatus;

import java.io.File;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmRecyclerViewAdapter;
import io.realm.RealmResults;
import io.realm.Sort;
import md.miliano.secp2p.ChatActivity;
import md.miliano.secp2p.R;
import md.miliano.secp2p.db.Contact;
import md.miliano.secp2p.db.Message;
import md.miliano.secp2p.tor.Tor;
import md.miliano.secp2p.ui.DeleteContact;
import md.miliano.secp2p.utils.TimeAgo;

public class ContactsAdapter extends RealmRecyclerViewAdapter<Contact, ContactViewHolder> {


    private final Context mContext;

    private RealmResults<Contact> mCopyData;

    public Set<Integer> mSelectedPositions = new HashSet<>();

    private boolean mIsShareAdapter;

    public ContactsAdapter(RealmResults<Contact> data, Context context, boolean isShareAdapter) {
        super(data, true);
        mContext = context;
        mCopyData = data;
        mIsShareAdapter = isShareAdapter;
        setHasStableIds(true);
    }


    public void filter(String text) {
        if (text != null && !text.isEmpty()) {
            RealmResults<Contact> contacts = mCopyData.where().contains("address", text, Case.INSENSITIVE)
                    .or().contains("name", text, Case.INSENSITIVE).findAll();
            updateData(contacts);
        } else {
            updateData(mCopyData);
        }
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final ContactViewHolder viewHolder = new ContactViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_contact, parent, false));
        if (mIsShareAdapter) {
            viewHolder.llMainView.setOnClickListener(v -> {
                if (mSelectedPositions.contains(viewHolder.position)) {
                    mSelectedPositions.remove(viewHolder.position);
                } else {
                    mSelectedPositions.add(viewHolder.position);
                }
                notifyItemChanged(viewHolder.position);
            });
        } else {
            viewHolder.itemView.setOnClickListener(v -> {
                if (viewHolder.contact.getPubKey() == null) {
                    Toast.makeText(mContext, "Your request is pending...", Toast.LENGTH_SHORT).show();
                    return;
                }
                mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("chat:" + viewHolder.contact.getAddress()), mContext, ChatActivity.class));
            });
            viewHolder.itemView.setOnLongClickListener(v -> {
                contactLongPress(viewHolder.contact);
                return true;
            });
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        final Contact contact = getItem(position);
        holder.contact = contact;
        holder.position = position;
        File file = new File(mContext.getFilesDir(), contact.getAddress() + ".jpg");
        if (mIsShareAdapter) {
            if (mSelectedPositions.contains(position)) {
                holder.imageView.setImageResource(R.drawable.ic_done_svg);
            } else {
                if (file.exists()) {
                    Glide.with(mContext)
                            .load(Uri.fromFile(file))
                            .placeholder(new ColorDrawable(Color.RED))
                            .fallback(new ColorDrawable(Color.BLUE))
                            .apply(RequestOptions.circleCropTransform())
                            .into(holder.imageView);
                }
            }
        } else {
            if (file.exists()) {
                Glide.with(mContext)
                        .load(Uri.fromFile(file))
                        .placeholder(new ColorDrawable(Color.RED))
                        .fallback(new ColorDrawable(Color.BLUE))
                        .apply(RequestOptions.circleCropTransform())
                        .into(holder.imageView);
            } else {
                if (Tor.getInstance(mContext).isReady()) {
                    String url = "https://" + contact.getAddress() + ".onion:" + Tor.getFileServerPort() + "/dp";
                    FileDownloader.getImpl()
                            .create(url)
                            .setPath(file.getAbsolutePath(), false)
                            .setTag(contact.getAddress())
                            .addFinishListener(task -> {
                                if (task.getStatus() == FileDownloadStatus.completed) {
                                    new Handler(Looper.getMainLooper()).post(() ->
                                            Glide.with(mContext)
                                                    .load(Uri.fromFile(file))
                                                    .placeholder(new ColorDrawable(Color.RED))
                                                    .fallback(new ColorDrawable(Color.BLUE))
                                                    .apply(RequestOptions.circleCropTransform())
                                                    .into(holder.imvwStatus));
                                }
                            })
                            .start();
                }
            }
        }

        Message lastMessage = getLastMessage(contact);
        if (lastMessage == null) {
            holder.address.setText(contact.getAddress());
        } else {
            int resourceId = -1;
            String fileContent = null;
            if (lastMessage.getType() == Message.TYPE_IMAGE) {
                resourceId = R.drawable.ic_image;
                fileContent = "Photo";
            } else if (lastMessage.getType() == Message.TYPE_VIDEO) {
                resourceId = R.drawable.ic_video;
                fileContent = "Video";
            } else if (lastMessage.getType() == Message.TYPE_AUDIO) {
                resourceId = R.drawable.ic_mic;
                fileContent = "Audio";
            } else if (lastMessage.getType() == Message.TYPE_FILE) {
                resourceId = R.drawable.ic_file;
                fileContent = "File";
            }
            if (lastMessage.getContent() != null && !lastMessage.getContent().isEmpty()) {
                holder.address.setText(lastMessage.getContent());
            } else {
                holder.address.setText(fileContent);
            }
            holder.address.setCompoundDrawablesWithIntrinsicBounds(Math.max(resourceId, 0), 0, 0, 0);

            boolean pending = lastMessage.getPending() == 1;
            if (!isTx(lastMessage)) {
                holder.imvwStatus.setVisibility(View.GONE);
            } else {
                holder.imvwStatus.setVisibility(View.VISIBLE);
                if (pending) {
                    holder.imvwStatus.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_pending));
                } else {
                    holder.imvwStatus.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_sent));
                }
            }
        }
        String name = contact.getName();
        if (name == null || name.equals("")) name = "Anonymous";
        holder.name.setText(name);
        holder.time.setText(TimeAgo.getTimeAgo(contact.getLastMessageTime(), mContext));
        long n = contact.getPending();
        if (n > 0) {
            holder.count.setVisibility(View.VISIBLE);
            holder.count.setText(n > 99 ? "99" : "" + n);
        } else {
            holder.count.setVisibility(View.GONE);
            holder.count.setText(null);
        }
    }

    public boolean isTx(Message message) {
        String sender = message.getSender();
        return sender.equals(Tor.getInstance(mContext).getID());
    }

    private Message getLastMessage(Contact contact) {
        Tor tor = Tor.getInstance(mContext);
        Realm realm = Realm.getDefaultInstance();
        Message first = Realm.getDefaultInstance().where(Message.class)
                .beginGroup().equalTo("sender", tor.getID()).and().equalTo("receiver", contact.getAddress()).endGroup()
                .or()
                .beginGroup().equalTo("sender", contact.getAddress()).and().equalTo("receiver", tor.getID()).endGroup()
                .sort("stableId", Sort.DESCENDING)
                .findFirst();
        realm.close();
        return first;
    }

    @Override
    public long getItemId(int index) {
        return Objects.requireNonNull(getItem(index)).get_id();
    }

    private void contactLongPress(final Contact contact) {
        View v = LayoutInflater.from(mContext).inflate(R.layout.dialog_contact, null);
        ((TextView) v.findViewById(R.id.name)).setText(contact.getName());
        ((TextView) v.findViewById(R.id.address)).setText(contact.getAddress());
        final Dialog dlg = new AlertDialog.Builder(mContext)
                .setView(v)
                .create();
        v.findViewById(R.id.changeName).setOnClickListener(v12 -> {
            dlg.hide();
            changeContactName(contact);
        });
        v.findViewById(R.id.copyId).setOnClickListener(v13 -> {
            dlg.hide();
            ((android.content.ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE)).setText(contact.getAddress());
            Snackbar.make(v13, mContext.getString(R.string.copied_to_clipboard) + " " + contact.getAddress(), Snackbar.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.delete).setOnClickListener(v14 -> {
            dlg.hide();
            new AlertDialog.Builder(mContext)
                    .setTitle(R.string.delete_contact_q)
                    .setMessage(String.format(mContext.getString(R.string.really_delete_contact), contact.getAddress()))
                    .setPositiveButton(R.string.yes, (dialog, which) -> new DeleteContact(mContext).execute(contact.get_id()))
                    .setNegativeButton(R.string.No, (dialog, which) -> {
                    })
                    .show();
        });

        dlg.show();
    }

    private void changeContactName(final Contact contact) {
        final FrameLayout view = new FrameLayout(mContext);
        final EditText editText = new EditText(mContext);
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        view.addView(editText);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, mContext.getResources().getDisplayMetrics());
        view.setPadding(padding, padding, padding, padding);
        editText.setText(contact.getName());
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.title_change_alias)
                .setView(view)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    Realm realm = Realm.getDefaultInstance();
                    realm.beginTransaction();
                    contact.setName(editText.getText().toString());
                    realm.commitTransaction();
                    realm.close();

                    Snackbar.make(view, mContext.getString(R.string.alias_changed) + " " + contact.getAddress(), Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                }).show();
    }
}

