package br.com.demak.orcamentos;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        // Se não estiver na tela inicial, o botão Voltar do celular leva para o início.
        web.evaluateJavascript(
            "(function(){var b=document.getElementById('home-btn');if(b&&b.style.display!=='none'){b.click();return 1;}return 0;})()",
            value -> { if (!"1".equals(value)) finish(); });
    }

    public class Bridge {
        @JavascriptInterface
        public void saveFile(String name, String mime, String b64, boolean open) {
            try {
                Uri uri = write(name, mime, Base64.decode(b64, Base64.DEFAULT));
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Salvo em Downloads: " + name, Toast.LENGTH_LONG).show();
                    if (open) view(uri, mime);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível salvar o arquivo.", Toast.LENGTH_LONG).show());
            }
        }


        @JavascriptInterface
        public void shareFiles(String json) {
            try {
                JSONArray arr = new JSONArray(json);
                ArrayList<Uri> uris = new ArrayList<>();
                String mime = null;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String m = o.getString("mime");
                    uris.add(write(o.getString("name"), m, Base64.decode(o.getString("b64"), Base64.DEFAULT)));
                    mime = (mime == null || mime.equals(m)) ? m : "*/*";
                }
                final String type = mime == null ? "*/*" : mime;
                runOnUiThread(() -> {
                    Intent send;
                    if (uris.size() == 1) {
                        send = new Intent(Intent.ACTION_SEND);
                        send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                    } else {
                        send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                    }
                    send.setType(type);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Enviar orçamento"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível compartilhar os arquivos.", Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void shareFile(String name, String mime, String b64) {
            try {
                Uri uri = write(name, mime, Base64.decode(b64, Base64.DEFAULT));
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(mime);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Enviar orçamento"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Não foi possível compartilhar o arquivo.", Toast.LENGTH_LONG).show());
            }
        }
    }

    private void view(Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Abrir com"));
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum aplicativo para abrir este arquivo.", Toast.LENGTH_LONG).show();
        }
    }

    /** Grava o arquivo em Downloads (Android 10+) ou na pasta do app (Android 7-9). */
    private Uri write(String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(data);
            }
            return uri;
        } else {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null && !dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            try (FileOutputStream os = new FileOutputStream(f)) {
                os.write(data);
            }
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        }
    }
}
