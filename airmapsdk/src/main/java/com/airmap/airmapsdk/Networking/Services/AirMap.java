package com.airmap.airmapsdk.networking.services;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.airmap.airmapsdk.AirMapException;
import com.airmap.airmapsdk.AirMapLog;
import com.airmap.airmapsdk.Auth;
import com.airmap.airmapsdk.models.aircraft.AirMapAircraft;
import com.airmap.airmapsdk.models.aircraft.AirMapAircraftManufacturer;
import com.airmap.airmapsdk.models.aircraft.AirMapAircraftModel;
import com.airmap.airmapsdk.models.comm.AirMapComm;
import com.airmap.airmapsdk.models.Coordinate;
import com.airmap.airmapsdk.models.flight.AirMapFlight;
import com.airmap.airmapsdk.models.permits.AirMapAvailablePermit;
import com.airmap.airmapsdk.models.permits.AirMapPilotPermit;
import com.airmap.airmapsdk.models.pilot.AirMapPilot;
import com.airmap.airmapsdk.models.status.AirMapStatus;
import com.airmap.airmapsdk.networking.callbacks.AirMapCallback;
import com.airmap.airmapsdk.networking.callbacks.AirMapTrafficListener;
import com.airmap.airmapsdk.networking.callbacks.LoginListener;
import com.airmap.airmapsdk.networking.callbacks.RefreshTokenListener;
import com.airmap.airmapsdk.ui.activities.CreateEditAircraftActivity;
import com.airmap.airmapsdk.ui.activities.CreateFlightActivity;
import com.airmap.airmapsdk.ui.activities.LoginActivity;
import com.airmap.airmapsdk.ui.activities.ProfileActivity;
import com.airmap.airmapsdk.Utils;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;

/**
 * Created by Vansh Gandhi on 6/16/16.
 * Copyright © 2016 AirMap, Inc. All rights reserved.
 */
@SuppressWarnings("unused")
public class AirMap {
    private Context context;
    private static String authToken;
    private static String apiKey;
    private static JSONObject config;
    private static String userId; //User ID extracted from JWT token
    private static boolean certificatePinning; //Whether to enable certificate pinning

    private static AirMap ourInstance;
    private static AirMapClient client;

    private static TrafficService airMapTrafficService;
    private static MappingService airMapMapMappingService;

    private static LoginListener loginListener;

    /**
     * Initializes the SDK. This must be called before any requests can be made
     * The API key to be used with the requests must be provided in the airmap.config.json file in
     * your
     * /assets directory.
     *
     * @param context         An Android Context
     * @param authToken       The auth token to be used with the requests
     * @param pinCertificates A Boolean value, if true, will use pinned certificates to validate
     *                        the AirMap server trust
     * @return An AirMap instance
     * @see <a href="http://www.airmap.com/makers">http://www.airmap.com/makers</a>
     */
    public static AirMap init(Context context, String authToken, boolean pinCertificates) {
        ourInstance = new AirMap(context, authToken, pinCertificates);
        airMapTrafficService = new TrafficService(context); //Initialized here because TrafficService requires AirMap to be initialized already, so it is called after the constructor
        airMapMapMappingService = new MappingService(); //Initialized here because MappingService requires AirMap to be initialized already, so it is called after the constructor
        return ourInstance;
    }

    /**
     * Initializes the SDK. This must be called before any requests can be made
     *
     * @param context   An Android Context
     * @param authToken The auth token to be used with the requests
     * @return An AirMap instance
     * @see <a href="http://www.airmap.com/makers">http://www.airmap.com/makers</a>
     */
    public static AirMap init(Context context, String authToken) {
        return init(context, authToken, false);
    }

    /**
     * Initializes the SDK. This must be called before any requests can be made
     *
     * @param context An Android Context
     * @return An AirMap instance
     * @see <a href="http://www.airmap.com/makers">http://www.airmap.com/makers</a>
     */
    public static AirMap init(Context context) {
        return init(context, "");
    }

    /**
     * Get an AirMap instance
     *
     * @return an AirMap instance
     */
    public static AirMap getInstance() {
        if (ourInstance == null) {
            throw new IllegalStateException("Need to call init() first");
        }
        return ourInstance;
    }

    /**
     * Constructs an AirMap instance
     *
     * @param auth            The auth token
     * @param pinCertificates Whether to enable certificate pinning (recommended)
     */
    private AirMap(Context context, String auth, boolean pinCertificates) {
        this.context = context;
        authToken = auth == null ? "" : auth;
        certificatePinning = pinCertificates;
        decodeToken(auth);
        try {
            InputStream inputStream = getContext().getResources().getAssets().open("airmap.config.json");
            String json = Utils.readInputStreamAsString(inputStream);
            config = new JSONObject(json);
            apiKey = getConfig().getJSONObject("airmap").getString("api_key");
        } catch (IOException | JSONException | NullPointerException e) {
            e.printStackTrace();
            throw new RuntimeException("Please ensure you have your airmap.config.json file in your /assets directory");
        }
        client = new AirMapClient(apiKey, auth);
    }

    /**
     * Check if both API key and Auth Token are valid
     *
     * @return Validity of credentials
     */
    public static boolean hasValidCredentials() {
        return apiKey != null && !apiKey.isEmpty() && authToken != null && !authToken.isEmpty() && hasValidAuthenticatedUser();
    }

    /**
     * Get the expiration date of the provided Auth Token. If there was an error parsing the token,
     * then the current time is returned
     *
     * @return The expiration date
     */
    public NumericDate getAuthTokenExpirationDate() {
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setSkipAllValidators()
                .setDisableRequireSignature()
                .setSkipSignatureVerification()
                .build();
        try {
            JwtClaims claims = consumer.processToClaims(authToken);
            return claims.getExpirationTime();
        } catch (InvalidJwtException | MalformedClaimException e) {
            AirMapLog.v("AirMap", "Invalid auth token");
            return NumericDate.now();
        }
    }

    /**
     * This function should be called when the application enters the background (onDestroy,
     * onStop, onPause)
     */
    public static void suspend() {
        TrafficService service = getAirMapTrafficService();
        if (service != null && service.isConnected()) {
            service.disconnect();
        }
    }

    /**
     * This function needs to be called when the application is no longer in the background
     * (onStart, onResume)
     */
    public static void resume() {
        TrafficService service = getAirMapTrafficService();
        if (service != null) {
            service.connect();
        }
    }

    /**
     * Decodes the JWT Auth Token and parses it to get the user ID
     *
     * @param jwt The JWT Auth Token
     */
    private void decodeToken(String jwt) {
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setSkipAllValidators()
                .setDisableRequireSignature()
                .setSkipSignatureVerification()
                .build();
        try {
            JwtClaims claims = consumer.processToClaims(jwt);
            userId = claims.getSubject();
        } catch (InvalidJwtException | MalformedClaimException e) {
            Log.e("AirMap", "Invalid auth token");
        }
    }

    /**
     * Needs to be called whenever the auth token changes
     * If the auth token has changed, this allows it to be reflected in the web services
     *
     * @param auth The new auth token
     */
    public void setAuthToken(String auth) {
        authToken = auth;
        client.setAuthToken(authToken);
        getAirMapTrafficService().setAuthToken(auth);
        decodeToken(authToken);
    }

    /**
     * Needs to be called whenever the API key changes
     * If the API key has changed, this allows it to be reflected in the web services
     *
     * @param apiKey The new API key
     */
    protected void setApiKey(String apiKey) {
        client.setApiKey(apiKey);
    }

    /**
     * @return a context
     */
    public Context getContext() {
        return context;
    }

    /**
     * @return the authToken
     */
    protected String getAuthToken() {
        return authToken;
    }

    /**
     * @return the API key
     */
    protected String getApiKey() {
        return apiKey;
    }

    /**
     * @return the User ID of the authenticated user (if one exists)
     */
    public static String getUserId() {
        return userId;
    }

    public static JSONObject getConfig() {
        return config;
    }

    public static void setLoginListener(LoginListener listener) {
        loginListener = listener;
    }

    /**
     * Logs the user out
     */
    public void logout() {
        setAuthToken(null);
        userId = null;
        getClient().clearAndResetHeaders();
    }

    /**
     * @return The AirMapClient
     */
    protected static AirMapClient getClient() {
        return client;
    }

    /**
     * @return whether AirMap has been initialized
     */
    public static boolean hasBeenInitialized() {
        return ourInstance != null;
    }

    /**
     * @return whether there is an authenticated pilot associated with the SDK instance
     */
    public static boolean hasValidAuthenticatedUser() {
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setSkipAllValidators()
                .setDisableRequireSignature()
                .setSkipSignatureVerification()
                .build();
        try {
            JwtClaims claims = consumer.processToClaims(authToken);
            return claims.getExpirationTime().isAfter(NumericDate.now());
        } catch (InvalidJwtException | MalformedClaimException e) {
            return false;
        }
    }

    /**
     * Checks to see if certificate pinning is enable
     *
     * @return whether certificate pinning is enabled
     */
    public static boolean isCertificatePinningEnabled() {
        return certificatePinning;
    }

    public static void enableCertificatePinning(boolean enable) {
        AirMap.certificatePinning = enable;
        getClient().clearAndResetHeaders();
    }

    /**
     * Enables logging from the network requests for debugging purposes
     *
     * @param enable whether to enable logging or not
     */
    public static void enableLogging(boolean enable) {
        AirMapLog.ENABLED = enable;
    }

    /**
     * @return the traffic service
     */
    public static TrafficService getAirMapTrafficService() {
        return airMapTrafficService;
    }

    //Login

    /**
     * Calls the appropriate method in the login listener
     */
    protected static void showLogin() {
        if (loginListener != null) {
            loginListener.shouldAuthenticate();
        }
    }

    /**
     * Show the login screen
     *
     * @param activity    Activity to create the UI with and to deliver results to
     * @param requestCode The request code to start the activity with
     */
    public static void showLogin(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, LoginActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Show the login screen
     *
     * @param fragment    Fragment to create the UI with and to deliver results to
     * @param requestCode The request code to start the activity with
     */
    public static void showLogin(Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getActivity(), LoginActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    /**
     * Show the login screen
     *
     * @param fragment    Fragment to create the UI with and to deliver results to
     * @param requestCode The request code to start the activity with
     */
    public static void showLogin(android.support.v4.app.Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getContext(), LoginActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    /**
     * Refreshes the pilot's authentication token
     *
     * @param listener The callback that is invoked on success or error
     */
    public static void refreshAccessToken(RefreshTokenListener listener) {
        Auth.refreshAccessToken(getInstance().getContext(), listener);
    }

    //Aircraft

    /**
     * Get a list of all Aircraft Manufacturers
     *
     * @param callback The callback that is called on success or error
     */
    public static void getManufacturers(@Nullable AirMapCallback<List<AirMapAircraftManufacturer>> callback) {
        AircraftService.getManufacturers(callback);
    }

    /**
     * Get a list of all Aircraft Models
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getModels(@Nullable AirMapCallback<List<AirMapAircraftModel>> callback) {
        AircraftService.getModels(callback);
    }

    /**
     * Get a list of all Aircraft Models from a given Manufacturer
     *
     * @param manufacturerId The id of the manufacturer to get models for
     * @param callback       The callback that is invoked on success or error
     */
    public static void getModels(String manufacturerId, @Nullable AirMapCallback<List<AirMapAircraftModel>> callback) {
        AircraftService.getModels(manufacturerId, callback);
    }

    /**
     * Get a model by ID
     *
     * @param modelId  The ID of the model to get
     * @param callback The callback that is invoked on success or error
     */
    public static void getModel(String modelId, @Nullable AirMapCallback<AirMapAircraftModel> callback) {
        AircraftService.getModel(modelId, callback);
    }

    //Flight

    /**
     * List Flights. All parameters are optional and nullable
     *
     * @param limit           Max number of flights to return
     * @param pilotId         Search for flights from a particular pilot
     * @param startAfter      Search for flights that start after this time
     * @param startBefore     Search for flights that start before this time
     * @param endAfter        Search for flights that end after this time
     * @param endBefore       Search for flights that end before this time
     * @param country         Search for flights within this country (Length: 3, case insensitive)
     * @param startsAfterNow  Search for flights starting after now (the startsAfter Date will be ignored)
     * @param startsBeforeNow Search for flights starting before now (the startsBefore Date will be ignored)
     * @param endsAfterNow    Search for flights ending after now (the endsAfter Date will be ignored)
     * @param endsBeforeNow   Search for flights ending before now (the endsBefore Date will be ignored)
     * @param city            Search for flights within this city
     * @param state           Search for flights within this state
     * @param enhanced        Returns enhanced flight, pilot, and aircraft information
     * @param callback        The callback that is invoked on success or error
     */
    public static void getFlights(@Nullable Integer limit, @Nullable String pilotId,
                                  @Nullable Date startAfter, @Nullable Date startBefore,
                                  @Nullable Date endAfter, @Nullable Date endBefore,
                                  @Nullable Boolean startsAfterNow, @Nullable Boolean startsBeforeNow,
                                  @Nullable Boolean endsAfterNow, @Nullable Boolean endsBeforeNow,
                                  @Nullable String country, @Nullable String city,
                                  @Nullable String state, @Nullable Boolean enhanced,
                                  @Nullable AirMapCallback<List<AirMapFlight>> callback) {
        FlightService.getFlights(limit, pilotId, startAfter, startBefore, endAfter, endBefore,
                startsAfterNow, startsBeforeNow, endsAfterNow, endsBeforeNow, country, city, state,
                enhanced, callback);
    }

    /**
     * Get a list of all public flights combined with the authenticated pilot's private flights
     *
     * @param limit    Max number of flights to return
     * @param from     Search for flights from this date
     * @param to       Search for flights to this date
     * @param callback The callback that is invoked on success or error
     */
    public static void getPublicFlights(@Nullable Integer limit, Date from, Date to,
                                        @Nullable AirMapCallback<List<AirMapFlight>> callback) {
        FlightService.getPublicFlights(limit, from, to, callback);
    }

    /**
     * Get the authenticated pilot's current active flight
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getCurrentFlight(final @Nullable AirMapCallback<AirMapFlight> callback) {
        AirMapCallback<List<AirMapFlight>> proxy = new AirMapCallback<List<AirMapFlight>>() {
            @Override
            public void onSuccess(List<AirMapFlight> response) {
                if (callback != null) {
                    if (response != null && !response.isEmpty()) {
                        callback.onSuccess(response.get(0));
                    } else {
                        callback.onSuccess(null);
                    }
                }
            }

            @Override
            public void onError(AirMapException e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        };
        if (AirMap.getUserId() != null) {
            FlightService.getFlights(null, AirMap.getUserId(), null, null, null, null, null, true, true, null, null, null, null, true, proxy);
        } else {
            if (callback != null) {
                callback.onSuccess(null);
            }
        }
    }

    /**
     * Get a flight by its ID
     *
     * @param flightId The ID of the flight to get
     * @param enhance  If true, the response will include explicit profile and aircraft information
     *                 instead of just IDs
     * @param callback The callback that is invoked on success or error
     */
    public static void getFlight(String flightId, boolean enhance, @Nullable AirMapCallback<AirMapFlight> callback) {
        FlightService.getFlight(flightId, enhance, callback);
    }

    /**
     * Get a flight by its ID
     *
     * @param flightId The ID of the flight to get
     * @param callback The callback that is invoked on success or error
     */
    public static void getFlight(String flightId, @Nullable AirMapCallback<AirMapFlight> callback) {
        getFlight(flightId, false, callback);
    }

    /**
     * Get all the flights belonging to a pilot
     *
     * @param pilot    The pilot to get flights for
     * @param callback The callback that is invoked on success or error
     */
    public static void getFlights(AirMapPilot pilot, @Nullable AirMapCallback<List<AirMapFlight>> callback) {
        FlightService.getFlights(null, pilot.getPilotId(), null, null, null, null, null, null, null, null, null, null, null, true, callback);
    }

    /**
     * Get all the flights belonging to the authenticated pilot
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getFlights(@Nullable AirMapCallback<List<AirMapFlight>> callback) {
        FlightService.getFlights(null, AirMap.getUserId(), null, null, null, null, null, null, null, null, null, null, null, true, callback);
    }

    /**
     * Create a flight for the pilot
     *
     * @param flight   The flight to create
     * @param callback The callback that is invoked on success or error
     */
    public static void createFlight(AirMapFlight flight, @Nullable AirMapCallback<AirMapFlight> callback) {
        FlightService.createFlight(flight, callback);
    }

    /**
     * Start the create flight process in a custom UI with a built-in permit decision flow. This
     * will use the passed in context to start the UI and to send any activity results
     *
     * @param activity    The activity to start the UI with and to send Activity Result to
     * @param requestCode The request code you would like to start the activity with
     * @param coordinate  The location to create the flight
     * @param extras      Extra information to collect from the pilot in the profile page (key:
     *                    json key, value: EditText Hint)
     */
    public static void createFlight(Activity activity, int requestCode, Coordinate coordinate, @Nullable HashMap<String, String> extras) {
        Intent intent = new Intent(activity, CreateFlightActivity.class);
        intent.putExtra(CreateFlightActivity.COORDINATE, coordinate);
        if (extras != null) {
            intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, extras);
        }
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Start the create flight process in a custom UI with a built-in permit decision flow. This
     * will use the passed in context to start the UI and to send any activity results
     *
     * @param fragment    The fragment to start the UI with and to send Activity Result to
     * @param requestCode The request code you would like to start the activity with
     * @param coordinate  The location to create the flight
     * @param extras      Extra information to collect from the pilot in the profile page (key:
     *                    json key, value: EditText Hint)
     */
    public static void createFlight(Fragment fragment, int requestCode, Coordinate coordinate, @Nullable HashMap<String, String> extras) {
        Intent intent = new Intent(fragment.getActivity(), CreateFlightActivity.class);
        intent.putExtra(CreateFlightActivity.COORDINATE, coordinate);
        if (extras != null) {
            intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, extras);
        }
        fragment.startActivityForResult(intent, requestCode);
    }

    /**
     * Start the create flight process in a custom UI with a built-in permit decision flow. This
     * will use the passed in context to start the UI and to send any activity results
     *
     * @param fragment    The fragment to start the UI with and to send Activity Result to
     * @param requestCode The request code you would like to start the activity with
     * @param coordinate  The location to create the flight
     * @param extras      Extra information to collect from the pilot in the profile page (key:
     *                    json key, value: EditText Hint)
     */
    public static void createFlight(android.support.v4.app.Fragment fragment, int requestCode, Coordinate coordinate, @Nullable HashMap<String, String> extras) {
        Intent intent = new Intent(fragment.getContext(), CreateFlightActivity.class);
        intent.putExtra(CreateFlightActivity.COORDINATE, coordinate);
        if (extras != null) {
            intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, extras);
        }
        fragment.startActivityForResult(intent, requestCode);
    }


    /**
     * End a flight belonging to the logged in pilot
     *
     * @param flight   The flight to close
     * @param callback The callback that is invoked on success or error
     */
    public static void endFlight(AirMapFlight flight, @Nullable AirMapCallback<AirMapFlight> callback) {
        FlightService.endFlight(flight, callback);
    }

    /**
     * Delete a flight belonging to the logged in pilot
     *
     * @param flight   The flight to delete
     * @param callback The callback that is invoked on success or error
     */
    public static void deleteFlight(AirMapFlight flight, @Nullable AirMapCallback<Void> callback) {
        FlightService.deleteFlight(flight, callback);
    }

    /**
     * Get a comm key for a given flight to enable traffic alerts
     *
     * @param flight   The flight to get the comm key for
     * @param callback The callback that is invoked on success or error
     */
    public static void startComm(AirMapFlight flight, @Nullable AirMapCallback<AirMapComm> callback) {
        FlightService.getCommKey(flight, callback);
    }

    /**
     * Stop receiving notifications for traffic alerts
     *
     * @param flight   The flight to stop receiving notifications for
     * @param callback The callback that is invoked on success or error
     */
    public static void clearComm(AirMapFlight flight, @Nullable AirMapCallback<Void> callback) {
        FlightService.clearCommKey(flight, callback);
    }

    //Permits

    /**
     * Get permits by permit IDs and/or organization ID. Either permitIds or organizationId must be
     * non-null
     *
     * @param permitIds      The list of permits to get
     * @param organizationId The organization get permits for
     * @param callback       The callback that is invoked on success or error
     */
    public static void getPermits(@Nullable List<String> permitIds, @Nullable String organizationId, @Nullable AirMapCallback<List<AirMapAvailablePermit>> callback) {
        PermitService.getPermits(permitIds, organizationId, callback);
    }

    /**
     * Gets permits by a singular permit ID and/or organization ID. either permitId or
     * organizationId must be non-null
     *
     * @param permitId       The ID of the permit to get
     * @param organizationId The organization to get permits for
     * @param callback       The callback that is invoked on success or error
     */
    public static void getPermits(@Nullable String permitId, @Nullable String organizationId, @Nullable AirMapCallback<List<AirMapAvailablePermit>> callback) {
        List<String> permitIds = new ArrayList<>();
        if (permitId != null && !permitId.isEmpty()) {
            permitIds.add(permitId);
        }
        getPermits(permitIds, organizationId, callback);
    }

    /**
     * Get a permit by a permit ID
     *
     * @param permitId The ID of the permit to get
     * @param callback The callback that is invoked on success or error
     */
    public static void getPermit(@NonNull String permitId, @Nullable AirMapCallback<List<AirMapAvailablePermit>> callback) {
        getPermits(permitId, null, callback);
    }

    /**
     * Apply for a permit
     *
     * @param permit   The permit to apply for
     * @param callback The callback that is invoked on success or error
     */
    public static void applyForPermit(AirMapAvailablePermit permit, @Nullable AirMapCallback<AirMapPilotPermit> callback) {
        PermitService.applyForPermit(permit, callback);
    }

    //Pilot

    /**
     * Get a pilot by ID
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getPilot(String pilotId, @Nullable AirMapCallback<AirMapPilot> callback) {
        PilotService.getPilot(pilotId, callback);
    }

    /**
     * Get the authenticated pilot
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getPilot(@Nullable AirMapCallback<AirMapPilot> callback) {
        getPilot(AirMap.getUserId(), callback);
    }

    /**
     * Get permits that the authenticated pilot has obtained
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getAuthenticatedPilotPermits(@Nullable AirMapCallback<List<AirMapPilotPermit>> callback) {
        PilotService.getPermits(callback);
    }

    /**
     * Delete a permit from the pilot's profile
     *
     * @param permitId The ID of the permit to delete
     * @param callback The callback that is invoked on success or error
     */
    public static void deletePermit(String permitId, @Nullable AirMapCallback<Void> callback) {
        PilotService.deletePermit(permitId, callback);
    }

    /**
     * Update a pilot
     *
     * @param pilot    The updated version of the pilot
     * @param callback The callback that is invoked on success or error
     */
    public static void updatePilot(AirMapPilot pilot, @Nullable AirMapCallback<AirMapPilot> callback) {
        PilotService.updatePilot(pilot, callback);
    }

    /**
     * Update the authenticated pilot's phone number
     *
     * @param phoneNumber The updated phone number
     * @param callback    The callback that is invoked on success or error
     */
    public static void updatePhoneNumber(String phoneNumber, @Nullable AirMapCallback<Void> callback) {
        PilotService.updatePhoneNumber(phoneNumber, callback);
    }

    /**
     * Verify the user's phone number
     *
     * @param listener The callback that is invoked on success or error
     */
    public static void sendVerificationToken(AirMapCallback<Void> listener) {
        PilotService.sendVerificationToken(listener);
    }

    /**
     * Verify that the text message the pilot received was the correct one
     *
     * @param token    The token that the pilot received in the text
     * @param callback The callback that is invoked on success or error
     */
    public static void verifyPhoneToken(String token, @Nullable AirMapCallback<Void> callback) {
        PilotService.verifyToken(token, callback);
    }

    /**
     * Display the authenticated pilot's profile
     *
     * @param activity the activity to start the UI with
     * @param extras   Extra information to collect from the pilot in the profile page (key: json
     *                 key, value: EditText Hint)
     */
    public static void showProfile(Activity activity, @Nullable HashMap<String, String> extras) {
        showProfile(activity, AirMap.getUserId(), extras);
    }

    /**
     * Display the authenticated pilot's profile
     *
     * @param fragment the fragment to start the UI with
     * @param extras   Extra information to collect from the pilot in the profile page (key: json
     *                 key, value: EditText Hint)
     */
    public static void showProfile(android.support.v4.app.Fragment fragment, @Nullable HashMap<String, String> extras) {
        showProfile(fragment, AirMap.getUserId(), extras);
    }

    /**
     * Display the authenticated pilot's profile
     *
     * @param fragment the fragment to start the UI with
     * @param extras   Extra information to collect from the pilot in the profile page (key: json
     *                 key, value: EditText Hint)
     */
    public static void showProfile(Fragment fragment, @Nullable HashMap<String, String> extras) {
        showProfile(fragment, AirMap.getUserId(), extras);
    }

    /**
     * Display the authenticated pilot's profile
     *
     * @param activity the activity to start the UI with
     * @param pilotId  The ID of the pilot to show the profile for
     * @param extras   Extra information to collect from the pilot in the profile page (key: json
     *                 key, value: EditText Hint)
     */
    public static void showProfile(Activity activity, String pilotId, @Nullable HashMap<String, String> extras) {
        if ((pilotId == null || pilotId.isEmpty()) && AirMap.hasValidAuthenticatedUser()) {
            pilotId = AirMap.getUserId();
        }
        Intent intent = new Intent(activity, ProfileActivity.class);
        intent.putExtra(ProfileActivity.ARG_PILOT_ID, pilotId);
        if (extras != null) {
            intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, extras);
        }
        activity.startActivity(intent);
    }

    /**
     * Display the authenticated pilot's profile
     *
     * @param fragment the fragment to start the UI with
     * @param pilotId  The ID of the pilot to show the profile for
     * @param extras   Extra information to collect from the pilot in the profile page (key: json
     *                 key, value: EditText Hint)
     */
    public static void showProfile(android.support.v4.app.Fragment fragment, String pilotId, @Nullable HashMap<String, String> extras) {
        if ((pilotId == null || pilotId.isEmpty()) && AirMap.hasValidAuthenticatedUser()) {
            pilotId = AirMap.getUserId();
        }
        Intent intent = new Intent(fragment.getContext(), ProfileActivity.class);
        intent.putExtra(ProfileActivity.ARG_PILOT_ID, pilotId);
        if (extras != null) {
            intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, extras);
        }
        fragment.startActivity(intent);
    }

    /**
     * Display the authenticated pilot's profile
     *
     * @param fragment the fragment to start the UI with
     * @param pilotId  The ID of the pilot to show the profile for
     * @param extras   Extra information to collect from the pilot in the profile page (key: json
     *                 key, value: EditText Hint)
     */
    public static void showProfile(Fragment fragment, String pilotId, @Nullable HashMap<String, String> extras) {
        if ((pilotId == null || pilotId.isEmpty()) && AirMap.hasValidAuthenticatedUser()) {
            pilotId = AirMap.getUserId();
        }
        Intent intent = new Intent(fragment.getActivity(), ProfileActivity.class);
        intent.putExtra(ProfileActivity.ARG_PILOT_ID, pilotId);
        if (extras != null) {
            intent.putExtra(CreateFlightActivity.KEY_VALUE_EXTRAS, extras);
        }
        fragment.startActivity(intent);
    }


    /**
     * Get all the authenticated pilot's aircraft
     *
     * @param callback The callback that is invoked on success or error
     */
    public static void getAircraft(@Nullable AirMapCallback<List<AirMapAircraft>> callback) {
        PilotService.getAircraft(callback);
    }

    /**
     * Gets an aircraft by its ID
     *
     * @param aircraftId The ID of the aircraft to get
     * @param callback   The callback that is invoked on success or error
     */
    public static void getAircraft(String aircraftId, @Nullable AirMapCallback<AirMapAircraft> callback) {
        PilotService.getAircraft(aircraftId, callback);
    }

    /**
     * Create an aircraft for the authenticated pilot
     *
     * @param aircraft The aircraft to add to the pilot's profile
     * @param callback The callback that is invoked on success or error
     */
    public static void createAircraft(AirMapAircraft aircraft, @Nullable AirMapCallback<AirMapAircraft> callback) {
        PilotService.createAircraft(aircraft, callback);
    }

    /**
     * Update the nickname of the authenticated pilot's aircraft
     *
     * @param aircraft The aircraft with the updated nickname
     * @param callback The callback that is invoked on success or error
     */
    public static void updateAircraft(AirMapAircraft aircraft, @Nullable AirMapCallback<AirMapAircraft> callback) {
        PilotService.updateAircraft(aircraft, callback);
    }

    /**
     * Delete an aircraft from the authenticated pilots' profile
     *
     * @param aircraft The aircraft to delete
     * @param callback The callback that is invoked on success or error
     */
    public static void deleteAircraft(AirMapAircraft aircraft, @Nullable AirMapCallback<Void> callback) {
        PilotService.deleteAircraft(aircraft, callback);
    }

    /**
     * Display UI to create an aircraft
     */
    public static void showCreateAircraft() {
        Context context = AirMap.getInstance().getContext();
        Intent intent = new Intent(context, CreateEditAircraftActivity.class);
        context.startActivity(intent);
    }

    /**
     * Display UI to create an aircraft. Use this if you want the the result of the activity
     *
     * @param activity    An activity to create the CreateEditAircraftActivity from (needed to
     *                    start an activity for result and to deliver the result to the activity)
     * @param requestCode the requestCode to start the Activity with
     */
    public static void showCreateAircraft(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, CreateEditAircraftActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Display UI to create an aircraft. Use this if you want the the result of the activity
     *
     * @param fragment    An activity to create the CreateEditAircraftActivity from (needed to
     *                    start an activity for result and to deliver the result to the activity)
     * @param requestCode the requestCode to start the Activity with
     */
    public static void showCreateAircraft(Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getActivity(), CreateEditAircraftActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    /**
     * Display UI to create an aircraft. Use this if you want the the result of the activity
     *
     * @param fragment    An activity to create the CreateEditAircraftActivity from (needed to
     *                    start an activity for result and to deliver the result to the activity)
     * @param requestCode the requestCode to start the Activity with
     */
    public static void showCreateAircraft(android.support.v4.app.Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getContext(), CreateEditAircraftActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    /**
     * Get a flight status based on a Point and Radius based flight
     *
     * @param coordinate   The coordinate of the flight
     * @param buffer       Number of meters to buffer a flight (the radius of the flight)
     * @param types        Airspace types to include in the calculation and response
     * @param ignoredTypes Airspace types to ignore in the calculation and response
     * @param showWeather  Whether to show the current weather conditions
     * @param date         Date and time for planned flight
     * @param callback     The callback that is invoked on success or error
     */
    public static Call checkCoordinate(Coordinate coordinate, @Nullable Double buffer,
                                       @Nullable List<MappingService.AirMapAirspaceType> types,
                                       @Nullable List<MappingService.AirMapAirspaceType> ignoredTypes,
                                       boolean showWeather, @Nullable Date date,
                                       @Nullable AirMapCallback<AirMapStatus> callback) {
        return StatusService.checkCoordinate(coordinate, buffer, types, ignoredTypes, showWeather, date, callback);
    }

    /**
     * Get a flight status based on a Multi-point based flight
     *
     * @param path         The points on the flight path
     * @param buffer        The line width for the flight
     * @param takeOffPoint The coordinate of the flight
     * @param types        Airspace types to include in the calculation and response
     * @param ignoredTypes Airspace types to ignore in the calculation and response
     * @param showWeather  Whether to show the current weather conditions
     * @param date         Date and time for planned flight
     * @param callback     The callback that is invoked on success or error
     */
    public static void checkFlightPath(List<Coordinate> path, int buffer, Coordinate takeOffPoint,
                                       List<MappingService.AirMapAirspaceType> types,
                                       List<MappingService.AirMapAirspaceType> ignoredTypes, boolean showWeather,
                                       @Nullable Date date, @Nullable AirMapCallback<AirMapStatus> callback) {
        StatusService.checkFlightPath(path, buffer, takeOffPoint, types, ignoredTypes, showWeather, date, callback);
    }

    /**
     * Get a flight status based on a flight within a polygon
     *
     * @param geometry     The polygon the flight will be in
     * @param takeOffPoint The take off point of the flight
     * @param types        Airspace types to include in the calculation and response
     * @param ignoredTypes Airspace types to ignore in the calculation and response
     * @param showWeather  Whether to show the current weather conditions
     * @param date         Date and time for planned flight
     * @param callback     The callback that is invoked on success or error
     */
    public static void checkPolygon(List<Coordinate> geometry, Coordinate takeOffPoint,
                                    List<MappingService.AirMapAirspaceType> types,
                                    List<MappingService.AirMapAirspaceType> ignoredTypes,
                                    boolean showWeather, @Nullable Date date,
                                    @Nullable AirMapCallback<AirMapStatus> callback) {
        StatusService.checkPolygon(geometry, takeOffPoint, types, ignoredTypes, showWeather, date, callback);
    }

    /**
     * Generates and returns map tile source url based upon map layers and theme
     *
     * @param layers The layers that the map should include
     * @param theme  The theme of the map
     * @return the map tile url
     */

    public static String getTileSourceUrl(List<MappingService.AirMapLayerType> layers, MappingService.AirMapMapTheme theme) {
        return airMapMapMappingService.getTileSourceUrl(layers, theme);
    }

    /**
     * Starts the Traffic Alerts service to receive traffic alerts for the current active flight
     *
     * @param listener The callback that is invoked when traffic is added, updated, and removed
     */
    public static void enableTrafficAlerts(AirMapTrafficListener listener) {
        getAirMapTrafficService().addListener(listener);
        getAirMapTrafficService().connect();
    }

    /**
     * Disconnects from Traffic Alerts
     */
    public static void disableTrafficAlerts() {
        getAirMapTrafficService().disconnect();
        getAirMapTrafficService().removeAllListeners();
    }

    /**
     * Adds a callback to the traffic service
     *
     * @param callback The callback that is invoked when traffic is added, updated, and removed
     */
    public static void addTrafficListener(AirMapTrafficListener callback) {
        getAirMapTrafficService().addListener(callback);
    }

    /**
     * Removes all traffic callbacks
     */
    public static void removeAllTrafficListeners() {
        getAirMapTrafficService().removeAllListeners();
    }
}