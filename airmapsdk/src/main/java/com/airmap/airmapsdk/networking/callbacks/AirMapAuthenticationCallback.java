package com.airmap.airmapsdk.networking.callbacks;

import android.app.Activity;
import android.util.Log;

import com.airmap.airmapsdk.AirMapException;
import com.airmap.airmapsdk.models.pilot.AirMapPilot;
import com.airmap.airmapsdk.networking.services.AirMap;
import com.airmap.airmapsdk.util.PreferenceUtils;
import com.airmap.airmapsdk.util.SecuredPreferenceException;
import com.airmap.airmapsdk.util.Utils;
import com.auth0.android.lock.AuthenticationCallback;
import com.auth0.android.lock.Lock;
import com.auth0.android.lock.utils.LockException;
import com.auth0.android.result.Credentials;

public class AirMapAuthenticationCallback extends AuthenticationCallback {

    private static final String TAG = "AirMapAuthCallback";

    private Activity activity;
    private LoginCallback callback;
    private Lock lock;

    public AirMapAuthenticationCallback(Activity activity, LoginCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    @Override
    public void onAuthentication(Credentials credentials) {
        try {
            PreferenceUtils.getPreferences(activity).edit()
                    .putString(Utils.REFRESH_TOKEN_KEY, credentials.getRefreshToken())
                    .apply();
        } catch (SecuredPreferenceException e) {
            Log.e(TAG, "Secured Preferences Failed", e);
        }

        AirMap.setAuthToken(credentials.getIdToken());
        AirMap.getPilot(new AirMapCallback<AirMapPilot>() {
            @Override
            public void onSuccess(final AirMapPilot response) {
                if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
                    Log.e(TAG, "Activity was killed before login returned. However auth token was saved.");
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(response);
                    }
                });
            }

            @Override
            public void onError(final AirMapException e) {
                Log.e(TAG, "get pilot failed", e);
                if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(e);
                    }
                });
            }
        });

        if (lock != null) {
            lock.onDestroy(activity);
            lock = null;
        }
    }

    public void setLock(Lock lock) {
        this.lock = lock;
    }

    @Override
    public void onCanceled() {
        if (lock != null) {
            lock.onDestroy(activity);
            lock = null;
        }
    }

    @Override
    public void onError(LockException error) {
        Log.e(TAG, "Error authenticating with auth0", error);
        callback.onError(new AirMapException(error.getMessage()));

        if (lock != null) {
            lock.onDestroy(activity);
            lock = null;
        }
    }
}
