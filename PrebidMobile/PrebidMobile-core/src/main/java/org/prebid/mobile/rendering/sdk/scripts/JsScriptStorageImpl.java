package org.prebid.mobile.rendering.sdk.scripts;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;

import org.prebid.mobile.LogUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class JsScriptStorageImpl implements JsScriptStorage {

    private final static String TAG = "JsScriptsStorage";

    private final SharedPreferences preferences;
    private final AssetManager assetManager;
    private final File innerFolder;

    public JsScriptStorageImpl(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        assetManager = context.getAssets();
        innerFolder = context.getFilesDir();
    }

    public File getInnerFile(String path) {
        return new File(innerFolder, path);
    }

    public boolean isFileAlreadyDownloaded(File file, String preferencesKey) {
        return file.exists() && preferences.contains(preferencesKey);
    }

    public void createParentFolders(File file) {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            boolean foldersCreated = parentFile.mkdirs();
            if (foldersCreated) {
                LogUtil.info(TAG, "Subfolders created");
            }
        }
    }

    public void markFileAsDownloadedCompletely(String path) {
        preferences.edit().putBoolean(path, true).apply();
    }

    public void fileDownloadingFailed(String path) {
        preferences.edit().remove(path).apply();
        removeFile(new File(innerFolder, path));
    }

    public boolean isAssetAvailable(String path) {
        try {
            InputStream inputStream = openAsset(path);
            inputStream.close();
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    public InputStream openAsset(String path) throws IOException {
        return assetManager.open(path);
    }

    private void removeFile(File file) {
        try {
            boolean isFileRemoved = file.delete();
            if (isFileRemoved) {
                LogUtil.info(TAG, "Not fully downloaded file removed.");
            }
        } catch (Throwable ignore) {
        }
    }

}
