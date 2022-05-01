package md.miliano.secp2p.tor;

public class Native {

    static {
        System.loadLibrary("app");
    }

    native public static void killTor();

}
