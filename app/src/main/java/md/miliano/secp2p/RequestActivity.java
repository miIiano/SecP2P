package md.miliano.secp2p;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.realm.Realm;
import io.realm.RealmResults;
import md.miliano.secp2p.adapters.RequestsAdapter;
import md.miliano.secp2p.db.Contact;

public class RequestActivity extends AppCompatActivity {

    private RecyclerView mRVRequests;
    private RequestsAdapter mRequestsAdapter;

    private RealmResults<Contact> mContacts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_request);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mRVRequests = findViewById(R.id.rcvwRequests);
        mContacts = Realm.getDefaultInstance().where(Contact.class).notEqualTo("incoming", 0).findAll();
        mContacts.addChangeListener((contacts, changeSet) -> updateNoDataView());
        mRVRequests.setLayoutManager(new LinearLayoutManager(this));
        mRequestsAdapter = new RequestsAdapter(mContacts, this);
        mRVRequests.setAdapter(mRequestsAdapter);
        mRVRequests.setHasFixedSize(true);
        mRVRequests.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        updateNoDataView();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mContacts.removeAllChangeListeners();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void updateNoDataView() {
        findViewById(R.id.txtNoRequests).setVisibility(mRequestsAdapter.getItemCount() > 0 ? View.INVISIBLE : View.VISIBLE);
    }

}
