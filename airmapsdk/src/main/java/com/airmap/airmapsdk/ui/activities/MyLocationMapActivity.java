package com.airmap.airmapsdk.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import com.airmap.airmapsdk.AirMapLog;
import com.airmap.airmapsdk.Analytics;
import com.airmap.airmapsdk.R;
import com.airmap.airmapsdk.ui.views.AirMapMapView;
import com.airmap.airmapsdk.util.AirMapConstants;
import com.airmap.airmapsdk.util.Utils;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.style.layers.CannotAddLayerException;
import com.mapbox.mapboxsdk.style.sources.CannotAddSourceException;
import com.mapbox.services.android.telemetry.location.AndroidLocationEngine;
import com.mapbox.services.android.telemetry.location.GoogleLocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;

public abstract class MyLocationMapActivity extends AppCompatActivity implements LocationEngineListener, AirMapMapView.OnMapLoadListener {

    private static final String TAG = "MyLocationMapActivity";

    private static final int REQUEST_LOCATION_PERMISSION = 7737;
    private static final int REQUEST_TURN_ON_LOCATION = 8849;

    private LocationLayerPlugin locationLayerPlugin;
    private LocationEngine locationEngine;

    private boolean hasLoadedMyLocation;
    private boolean isLocationDialogShowing;
    private boolean isMapFailureDialogShowing;

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (getMapView() != null) {
            getMapView().addOnMapLoadListener(this);
        }

        requestLocationPermissionIfNeeded();
    }

    @Override
    public void onRestart() {
        super.onRestart();

        if (getMapView() != null) {
            getMapView().addOnMapLoadListener(this);
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onStart() {
        super.onStart();

        if (locationLayerPlugin != null && requestLocationPermissionIfNeeded()) {
            locationLayerPlugin.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (locationLayerPlugin != null) {
            locationLayerPlugin.onStop();
        }

        if (getMapView() != null) {
            getMapView().removeOnMapLoadListener(this);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_TURN_ON_LOCATION: {
                if (resultCode == Activity.RESULT_OK) {
                    AirMapLog.d(TAG, "Location setting turned on by user");
                    goToLastLocation(false,3);
                } else {
                    AirMapLog.d(TAG, "Location setting not turned on by user");
                    hasLoadedMyLocation = true;
                }

                isLocationDialogShowing = false;
                break;
            }
        }
    }

    @Override
    public void onConnected() {
        AirMapLog.d(TAG, "LocationEngine onConnected");

        if (locationEngine != null) {
            if (requestLocationPermissionIfNeeded()) {
                AirMapLog.d(TAG, "requestLocationUpdates");
                locationEngine.requestLocationUpdates();
                turnOnLocation();
            }
        }

        goToLastLocation(false);
    }


    @Override
    public void onLocationChanged(Location location) {
        AirMapLog.d(TAG, "LocationEngine onLocationChanged: " + location);
        zoomTo(location, false);
    }

    @SuppressLint("MissingPermission")
    public void goToLastLocation(boolean force) {
        if (requestLocationPermissionIfNeeded()) {
            if (locationEngine != null && locationEngine.getLastLocation() != null) {
                zoomTo(locationEngine.getLastLocation(), force);
            } else {
                turnOnLocation();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void goToLastLocation(final boolean force, final int retries) {
        if (locationEngine != null && locationEngine.getLastLocation() != null) {
            zoomTo(locationEngine.getLastLocation(), false);
        } else if (retries > 0) {
            int delay = 2500 * Math.max((3 - retries), 1);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    AirMapLog.e(TAG, "goToLastLocation w/ " + retries + " retries");
                    if (locationEngine != null) {
                        locationEngine.requestLocationUpdates();
                    }
                    goToLastLocation(force, retries - 1);
                }
            }, delay);
        }
    }

    private void zoomTo(Location location, boolean force) {
        // only zoom to user's location once
        if (!hasLoadedMyLocation || force) {
            AirMapLog.e(TAG, "zoomTo: " + location);

            getMapView().getMap().easeCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));
            locationEngine.removeLocationUpdates();
            hasLoadedMyLocation = true;

            // save location to prefs
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putFloat(AirMapConstants.LAST_LOCATION_LATITUDE, (float) location.getLatitude())
                    .putFloat(AirMapConstants.LAST_LOCATION_LONGITUDE, (float) location.getLongitude())
                    .apply();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapLoaded() {
        if (hasLoadedMyLocation) {
            return;
        }
        AirMapLog.e(TAG, "onMapLoaded");

        // use saved location is there is one
        float savedLatitude = PreferenceManager.getDefaultSharedPreferences(this)
                .getFloat(AirMapConstants.LAST_LOCATION_LATITUDE, 0);
        float savedLongitude = PreferenceManager.getDefaultSharedPreferences(this)
                .getFloat(AirMapConstants.LAST_LOCATION_LONGITUDE, 0);
        if (savedLatitude != 0 && savedLongitude != 0) {
            getMapView().getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(savedLatitude, savedLongitude), 13));
        }

        locationEngine = new AndroidLocationEngine(MyLocationMapActivity.this);
        locationEngine.addLocationEngineListener(MyLocationMapActivity.this);
        locationEngine.setPriority(LocationEnginePriority.BALANCED_POWER_ACCURACY);
        locationEngine.setFastestInterval(250);
        locationEngine.setInterval(250);
        locationEngine.activate();

        try {
            locationLayerPlugin = new LocationLayerPlugin(getMapView(), getMapView().getMap(), locationEngine, R.style.CustomLocationLayer);

            if (requestLocationPermissionIfNeeded()) {
                locationLayerPlugin.setLocationLayerEnabled(LocationLayerMode.TRACKING);
            }
        } catch (CannotAddLayerException | CannotAddSourceException e) {
            AirMapLog.e(TAG, "Unable to add location layer", e);
            Analytics.report(e);
        }
    }

    @Override
    public void onMapFailed(AirMapMapView.MapFailure failure) {
        switch (failure) {
            case INACCURATE_DATE_TIME_FAILURE:
                // record issue in firebase & logs
                Analytics.report(new Exception("Mapbox map failed to load due to invalid date/time"));
                AirMapLog.e(TAG, "Mapbox map failed to load due to invalid date/time");

                // ask user to enable "Automatic Date/Time"
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error_loading_map_title)
                        .setMessage(R.string.error_loading_map_message)
                        .setPositiveButton(R.string.error_loading_map_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // open settings and kill this activity
                                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS));
                                finish();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            case NETWORK_CONNECTION_FAILURE:
                // record issue in firebase & logs
                String log = Utils.getMapboxLogs();
                Analytics.report(new Exception("Mapbox map failed to load due to no network connection: " + log));
                AirMapLog.e(TAG, "Mapbox map failed to load due to no network connection");

                // check if dialog is already showing
                if (isMapFailureDialogShowing) {
                    return;
                }

                // ask user to turn on wifi/LTE
                new AlertDialog.Builder(this)
                        .setTitle(R.string.error_loading_map_title)
                        .setMessage(R.string.error_loading_map_network_message)
                        .setPositiveButton(R.string.error_loading_map_network_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                isMapFailureDialogShowing = false;
                                // open settings and kill this activity
                                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                                finish();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                isMapFailureDialogShowing = false;
                            }
                        })
                        .show();

                isMapFailureDialogShowing = true;

                break;
            case UNKNOWN_FAILURE:
            default:
                // record issue in firebase & logs
                String logs = Utils.getMapboxLogs();
                Analytics.report(new Exception("Mapbox map failed to load due to unknown reason: " + logs));
                AirMapLog.e(TAG, "Mapbox map failed to load due to unknown reason: " + logs);

                //TODO: show generic error to user?
                break;
        }
    }

    private boolean requestLocationPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            for (int i = 0; i < permissions.length; i++) {
                if ((permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) || permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION))
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    goToLastLocation(false);
                }
            }
        }
    }

    /**
     * This turns on Wifi/cell location tracking using Google Play services
     * It shows a dismissable dialog for users that don't have location already enabled
     */
    public void turnOnLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setNumUpdates(1);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        Task<LocationSettingsResponse> task = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @SuppressLint("MissingPermission")
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied. The client can initialize
                // location requests here.
                if (locationEngine != null) {
                    locationEngine.requestLocationUpdates();
                }

                goToLastLocation(false, 3);
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    if (isLocationDialogShowing) {
                        return;
                    }

                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MyLocationMapActivity.this, REQUEST_TURN_ON_LOCATION);

                        isLocationDialogShowing = true;
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });
    }

    protected abstract AirMapMapView getMapView();

    public void setMapView(AirMapMapView mapView) {
        mapView.addOnMapLoadListener(this);
    }
}
