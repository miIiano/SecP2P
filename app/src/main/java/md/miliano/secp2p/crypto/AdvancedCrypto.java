package md.miliano.secp2p.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AdvancedCrypto {

    private static String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static int IV_LENGTH = 16;
    private static String SECRET_KEY_ALGORITHM = "AES";
    private static String RANDOM_ALGORITHM = "SHA1PRNG";
    private static int KEY_SIZE = 256;


    private static String SALT = "E79A7E78A03C";


    private static int PBE_ITERATION_COUNT = 100;

    private static String PBE_ALGORITHM = "PBEWithSHA256And256BitAES-CBC-BC";

    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) throws Exception {
        this.key = key;
        this.secret = getSecretKey(key);
    }

    public SecretKey getSecret() {
        return secret;
    }

    public void setSecret(SecretKey secret) {
        this.secret = secret;
    }

    private SecretKey secret;

    public AdvancedCrypto(String key) throws Exception {
        this.key = key;
        this.secret = getSecretKey(key);
    }

    public String encrypt(String cleartext)
            throws Exception {
        byte[] iv = generateIv();
        String ivHex = toHex(iv);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        Cipher encryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM);
        encryptionCipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
        byte[] encryptedText = encryptionCipher.doFinal(cleartext
                .getBytes(StandardCharsets.UTF_8));
        String encryptedHex = toHex(encryptedText);

        return ivHex + encryptedHex;
    }

    public String decrypt(String encrypted)
            throws Exception {

        Cipher decryptionCipher = Cipher.getInstance(CIPHER_ALGORITHM);
        String ivHex = encrypted.substring(0, IV_LENGTH * 2);
        String encryptedHex = encrypted.substring(IV_LENGTH * 2);
        IvParameterSpec ivspec = new IvParameterSpec(toByte(ivHex));
        decryptionCipher.init(Cipher.DECRYPT_MODE, secret, ivspec);
        byte[] decryptedText = decryptionCipher
                .doFinal(toByte(encryptedHex));
        return new String(decryptedText, StandardCharsets.UTF_8);
    }

    public void encryptFile(String inputFile, String outputFile)
            throws Exception {

        byte[] iv = generateIv();
        String ivHex = toHex(iv);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secret, ivspec);
        File file = new File(outputFile);

        FileInputStream fis = new FileInputStream(inputFile);

        FileOutputStream fos = new FileOutputStream(file);
        CipherOutputStream cos = new CipherOutputStream(fos, cipher);

        fos.write(ivHex.getBytes());
        fos.flush();

        int read = -1;
        byte[] buffer = new byte[2048];
        while ((read = fis.read(buffer)) > 0) {
            cos.write(buffer, 0, read);
            cos.flush();
        }

        cos.close();
        fos.close();
        fis.close();
    }

    public void decryptFile(String inputFile, String outputFile)
            throws Exception {

        FileInputStream fis = new FileInputStream(inputFile);

        byte[] ivHexBytes = new byte[IV_LENGTH * 2];
        fis.read(ivHexBytes, 0, ivHexBytes.length);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        String ivHex = new String(ivHexBytes);
        IvParameterSpec ivspec = new IvParameterSpec(toByte(ivHex));
        cipher.init(Cipher.DECRYPT_MODE, secret, ivspec);

        File file = new File(outputFile);

        FileOutputStream fos = new FileOutputStream(file);

        CipherInputStream cis = new CipherInputStream(fis, cipher);

        int read = -1;
        byte[] buffer = new byte[2048];
        while ((read = cis.read(buffer)) > 0) {
            fos.write(buffer, 0, read);
            fos.flush();
        }

        cis.close();
        fis.close();
        fos.close();
    }

    public CipherInputStream getCipherInputStream(byte[] iv, InputStream is) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        String ivHex = new String(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(toByte(ivHex));
        cipher.init(Cipher.DECRYPT_MODE, secret, ivSpec);
        return new CipherInputStream(is, cipher);
    }

    public SecretKey getSecretKey(String password) throws Exception {
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password.toCharArray(),
                toByte(SALT), PBE_ITERATION_COUNT, KEY_SIZE);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(
                PBE_ALGORITHM);
        SecretKey tmp = factory.generateSecret(pbeKeySpec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(),
                SECRET_KEY_ALGORITHM);
        return secret;
    }

    public byte[] generateIv() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance(RANDOM_ALGORITHM);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        return iv;
    }

    public static byte[] toByte(String hexString) {
        int len = hexString.length() / 2;
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++)
            result[i] = Integer.valueOf(hexString.substring(2 * i, 2 * i + 2),
                    16).byteValue();
        return result;
    }

    public static String toHex(byte[] buf) {
        if (buf == null)
            return "";
        StringBuffer result = new StringBuffer(2 * buf.length);
        for (byte b : buf) {
            appendHex(result, b);
        }
        return result.toString();
    }

    private final static String HEX = "0123456789ABCDEF";

    private static void appendHex(StringBuffer sb, byte b) {
        sb.append(HEX.charAt((b >> 4) & 0x0f)).append(HEX.charAt(b & 0x0f));
    }
}