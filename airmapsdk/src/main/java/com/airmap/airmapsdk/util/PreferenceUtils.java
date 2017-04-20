package com.airmap.airmapsdk.util;

import android.content.Context;
import android.content.SharedPreferences;

import devliving.online.securedpreferencestore.RecoveryHandler;
import devliving.online.securedpreferencestore.SecuredPreferenceStore;

/**
 * Created by collin@airmap.com on 12/16/16.
 */

public class PreferenceUtils {

    private static final String TAG = "PreferenceUtils";

    public static SharedPreferences getPreferences(Context context) throws SecuredPreferenceException {
        try {
            return SecuredPreferenceStore.getSharedInstance(context);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SecuredPreferenceException(e.getMessage());
        }
    }

    public static void setRecoveryHandler(RecoveryHandler recoveryHandler) {
        SecuredPreferenceStore.setRecoveryHandler(recoveryHandler);
    }
}
