package md.miliano.secp2p.utils;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Base64;
import android.util.Log;
import android.view.View;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Objects;

import md.miliano.secp2p.R;

public class Util {

    public enum FGSAction {
        STOP("stop"),
        START("start");

        FGSAction(String action) {
        }
    }

    public static final int THUMBNAIL_SIZE = 64;

    public static final File EXTERNAL_FOLDER = new File(Environment.getExternalStorageDirectory(), "SecP2P");

    private static final Charset utf8 = StandardCharsets.UTF_8;

    public static String base64encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] base64decode(String str) {
        return Base64.decode(str, Base64.NO_WRAP);
    }

    public static byte[] bin(InputStream is) throws IOException {
        try {
            byte[] data = new byte[0];
            while (true) {
                byte[] buf = new byte[1024];
                int n = is.read(buf);
                if (n < 0) return data;
                byte[] newdata = new byte[data.length + n];
                System.arraycopy(data, 0, newdata, 0, data.length);
                System.arraycopy(buf, 0, newdata, data.length, n);
                data = newdata;
            }
        } finally {
            is.close();
        }
    }

    public static String getFolder(String mimeType) {
        File path = new File(EXTERNAL_FOLDER, mimeType.substring(0, mimeType.indexOf("/")));
        if (!path.exists()) {
            path.mkdirs();
        }
        return path.getAbsolutePath();
    }

    public static void setClipboard(Context context, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", text);
        clipboard.setPrimaryClip(clip);
    }

    public static byte[] filebin(File f) {
        try {
            return bin(new FileInputStream(f));
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    public static String filestr(File f) {
        return new String(filebin(f), utf8);
    }

    public static CharSequence linkify(final Context context, String content) {
        SpannableStringBuilder b = new SpannableStringBuilder(content);
        SpannableStringBuilder r = new SpannableStringBuilder(content);
        Linkify.addLinks(b, Linkify.WEB_URLS);
        URLSpan[] urls = b.getSpans(0, b.length(), URLSpan.class);
        for (int i = 0; i < urls.length && i < 8; i++) {
            URLSpan span = urls[i];
            int start = b.getSpanStart(span);
            int end = b.getSpanEnd(span);
            int flags = b.getSpanFlags(span);
            final String url = span.getURL();
            ClickableSpan s2 = new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    new AlertDialog.Builder(context)
                            .setTitle(url)
                            .setMessage("Open link in external app?")
                            .setNegativeButton("No", null)
                            .setPositiveButton("Yes", (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                            .show();
                }
            };
            r.setSpan(s2, start, end, flags);
        }
        return r;
    }

    @SuppressLint("NewApi")
    public static String getFilePath(Context context, Uri uri) throws URISyntaxException {
        String selection = null;
        String[] selectionArgs = null;
        if (DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }

            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver()
                        .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                Objects.requireNonNull(cursor).close();
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return uri.getPath();
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    public static void copyAsset(Context context, String assetName, String outputPath) throws IOException {
        InputStream is = context.getAssets().open(assetName);
        FileOutputStream fos = new FileOutputStream(outputPath);
        copyFile(is, fos);
        is.close();
        fos.close();
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }

    public static Bitmap lessResolution(String filePath, int width, int height) {
        int reqHeight = height;
        int reqWidth = width;
        BitmapFactory.Options options = new BitmapFactory.Options();
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;

        return rotateBitmap(BitmapFactory.decodeFile(filePath, options), orientation);
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {

        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %cB", value / 1024.0, ci.current());
    }

    public static int getResourceForFileType(String filePath) {
        if (filePath.endsWith("pdf")) {
            return R.drawable.ic_pdf;
        }
        if (filePath.endsWith("zip")) {
            return R.drawable.ic_zip;
        }
        if (filePath.endsWith("xls")) {
            return R.drawable.ic_xls;
        }
        if (filePath.endsWith("xlsx")) {
            return R.drawable.ic_xlsx;
        }
        if (filePath.endsWith("doc")) {
            return R.drawable.ic_doc;
        }
        if (filePath.endsWith("docx")) {
            return R.drawable.ic_docx;
        }
        if (filePath.endsWith("ppt") || filePath.endsWith("pptx")) {
            return R.drawable.ic_ppt;
        }
        if (filePath.endsWith("ai")) {
            return R.drawable.ic_ai;
        }
        if (filePath.endsWith("aac")) {
            return R.drawable.ic_aac;
        }
        if (filePath.endsWith("rar")) {
            return R.drawable.ic_rar;
        }
        if (filePath.endsWith("rtf")) {
            return R.drawable.ic_rtf;
        }
        if (filePath.endsWith("tga")) {
            return R.drawable.ic_tga;
        }
        if (filePath.endsWith("tgz")) {
            return R.drawable.ic_tgz;
        }
        if (filePath.endsWith("tiff")) {
            return R.drawable.ic_tiff;
        }
        if (filePath.endsWith("m4a")) {
            return R.drawable.ic_m4v;
        }
        return R.drawable.ic_file_svg;
    }

    public static String writeBitmapCache(Context context, Bitmap bitmap, String name) {

        File outputDir = context.getCacheDir();
        File imageFile = new File(outputDir, name + ".jpg");

        OutputStream os;
        try {
            os = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();
        } catch (Exception e) {
            Log.e(context.getClass().getSimpleName(), "Error writing file", e);
        }

        return imageFile.getAbsolutePath();
    }

    public static byte[] readSmallFile(Context context, String filePath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read = -1;
        byte[] buffer = new byte[512];
        FileInputStream fis = new FileInputStream(filePath);
        while ((read = fis.read(buffer)) > 0) {
            baos.write(buffer, 0, read);
            baos.flush();
        }
        fis.close();
        return baos.toByteArray();
    }
}
