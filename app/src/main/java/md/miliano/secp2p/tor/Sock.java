package md.miliano.secp2p.tor;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import io.realm.Realm;
import md.miliano.secp2p.BuildConfig;
import md.miliano.secp2p.db.Contact;

public class Sock {

    static final int TIMEOUT = 60000;
    Socket mSock;
    BufferedReader mReader;
    BufferedWriter mWriter;

    public Sock(String host, int port) {
        mSock = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", Tor.getSocksPort())));
        try {
            mSock.connect(new InetSocketAddress(host, port), TIMEOUT);
            mReader = new BufferedReader(new InputStreamReader(mSock.getInputStream()));
            mWriter = new BufferedWriter(new OutputStreamWriter(mSock.getOutputStream()));
        } catch (IOException e) {
            log("IO Exception");
            if (e instanceof SocketException) {
                Contact contact = Realm.getDefaultInstance().where(Contact.class).equalTo("address", host.split("\\.")[0]).findFirst();
                if (contact != null) {
                    log(contact.getName() + " not available ");
                }
            }
            try {
                mSock.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void log(String s) {
        if (!BuildConfig.DEBUG) return;
        Log.i("Sock", s);
    }

    public void writeLine(String... ss) {
        StringBuilder s = new StringBuilder();

        if (ss.length > 0) s = new StringBuilder(ss[0]);
        for (int i = 1; i < ss.length; i++)
            s.append(" ").append(ss[i]);

        log(" >> " + s);
        if (mWriter != null) {
            try {
                mWriter.write(s + "\r\n");
            } catch (SocketTimeoutException ex) {
                log("timeout");
                try {
                    mSock.close();
                } catch (IOException ex2) {
                    //ok
                }
            } catch (Exception ex) {
                //ok
            }
        }
    }

    public String readLine() {
        String s = null;
        if (mReader != null) {
            try {
                s = mReader.readLine();
            } catch (SocketTimeoutException ex) {
                log("timeout");
                try {
                    mSock.close();
                } catch (IOException ex2) {
                    //ok
                }
            } catch (Exception ex) {
                //ok
            }
        }
        if (s == null)
            s = "";
        else
            s = s.trim();
        log(" << " + s);
        return s;
    }

    public boolean readBool() {
        return readLine().equals("1");
    }

    public String readString() {
        return readLine();
    }

    public boolean queryBool(String... request) {
        writeLine(request);
        flush();
        return readBool();
    }

    public String queryString(String... request) {
        writeLine(request);
        flush();
        return readString();
    }

    public boolean queryAndClose(String... request) {
        boolean x = queryBool(request);
        close();
        return x;
    }

    public String queryAndCloseString(String... request) {
        String x = queryString(request);
        close();
        return x;
    }

    public void flush() {
        if (mWriter != null) {
            try {
                mWriter.flush();
            } catch (SocketTimeoutException ex) {
                log("timeout");
                try {
                    mSock.close();
                } catch (IOException ex2) {
                    //ok
                }
            } catch (Exception ex) {
                //ok
            }
        }
    }

    public void close() {

        flush();

        if (mSock != null) {
            try {
                mSock.close();
            } catch (Exception ex) {
                log(ex.getLocalizedMessage());
            }
        }

        mReader = null;
        mWriter = null;
        mSock = null;

    }

    public boolean isClosed() {
        //try {
        return mSock.isClosed();
        /*} catch(IOException ex) {
            return true;
        }*/
    }


}
