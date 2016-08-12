package com.airmap.airmapsdktest;

import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.airmap.airmapsdk.AirMapException;
import com.airmap.airmapsdk.Models.Coordinate;
import com.airmap.airmapsdk.Models.Flight.AirMapFlight;
import com.airmap.airmapsdk.Models.Pilot.AirMapPilot;
import com.airmap.airmapsdk.Models.Traffic.AirMapTraffic;
import com.airmap.airmapsdk.Networking.Callbacks.AirMapCallback;
import com.airmap.airmapsdk.Networking.Callbacks.AirMapTrafficListener;
import com.airmap.airmapsdk.Networking.Services.AirMap;
import com.airmap.airmapsdk.UI.Activities.CreateFlightActivity;
import com.airmap.airmapsdk.UI.Activities.LoginActivity;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, View.OnClickListener, AirMapTrafficListener, MapboxMap.OnMapLongClickListener {
    private final int REQUEST_LOGIN = 1; //The request code for the LoginActivity
    private final int REQUEST_FLIGHT = 2; //The request code for creating a flight
    private final String[] compassDirections = new String[]{"n", "nne", "ne", "ene", "e", "ese", "se", "sse", "s", "ssw", "sw", "wsw", "w", "wnw", "nw", "nnw"}; //Compass directions

    private MapView mapView; //A MapBox MapView
    private MapboxMap map; //A MapboxMap is used to interact with the MapBox SDK

    private FloatingActionButton trafficFab;

    private List<MarkerOptions> markers; //List to keep track of annotations on the map

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        trafficFab = (FloatingActionButton) findViewById(R.id.traffic_fab);
        trafficFab.hide(); //Wait until user is logged in to show button
        trafficFab.setOnClickListener(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //AirMap.init is called in the onCreate of MyApplication
        AirMap.showLogin(this, REQUEST_LOGIN); //Show the login screen and have the user log in
        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        markers = new ArrayList<>();
    }

    /**
     * When the user finishes logging in, this method is called with the requestCode that was
     * originally passed in, whether the login was successful or not, and an AirMapPilot if the
     * Login was successful
     *
     * @param requestCode The requestCode originally passed in. In this case, it is REQUEST_LOGIN
     * @param resultCode  Either Activity.RESULT_OK or Activity.RESULT_CANCELLED
     * @param data        If resultCode == Activity.RESULT_OK, then data will contain an
     *                    AirMapPilot
     *                    with the key LoginActivity.PILOT
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_LOGIN && resultCode == RESULT_OK) { //Check if login was successful
            AirMapPilot pilot = (AirMapPilot) data.getSerializableExtra(LoginActivity.PILOT); //Get the pilot from the data
            Toast.makeText(MainActivity.this, "Hi, " + pilot.getFirstName() + "!", Toast.LENGTH_SHORT).show();
            trafficFab.show(); //Show the button now that the user is logged in
            AirMap.getAllPublicAndAuthenticatedPilotFlights(null, new Date(), new AirMapCallback<List<AirMapFlight>>() { //Get all public flights and display them on the map
                @Override
                public void onSuccess(List<AirMapFlight> response) {
                    if (map != null) { //Make sure the map has been initialized already
                        for (AirMapFlight publicFlight : response) {
                            //Add a map annotation with the location of the flight
                            map.addMarker(new MarkerOptions()
                                    .icon(IconFactory.getInstance(MainActivity.this).fromResource(R.drawable.airmap_flight_marker))
                                    .position(getLatLngFromCoordinate(publicFlight.getCoordinate())));
                        }
                    }
                }

                @Override
                public void onError(AirMapException e) {
                    e.printStackTrace();
                    Log.e("MainActivity", "Error getting flights");
                }
            });
        } else if (requestCode == REQUEST_FLIGHT && resultCode == RESULT_OK) { //Check if flight creation was successful
            AirMapFlight flight = (AirMapFlight) data.getSerializableExtra(CreateFlightActivity.FLIGHT); //Get the flight from the data
            map.addMarker(new MarkerOptions() //Add the flight to the map
                    .position(getLatLngFromCoordinate(flight.getCoordinate()))
                    .icon(IconFactory.getInstance(this).fromResource(R.drawable.airmap_flight_marker)));
        }
    }

    /**
     * MapBox method that is called when the map is ready
     */
    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        map = mapboxMap;
        map.setOnMapLongClickListener(this);
    }

    /**
     * Called when the Traffic FAB is clicked. The purpose is to enable traffic alerts if the pilot
     * has an active flight
     */
    @Override
    public void onClick(final View view) {
        if (!AirMap.getAirMapTrafficService().isConnected()) {
            AirMap.enableTrafficAlerts(this);
        } else {
            AirMap.disableTrafficAlerts();
        }
        AirMap.getCurrentFlight(new AirMapCallback<AirMapFlight>() { //Check if user has an active flight
            @Override
            public void onSuccess(AirMapFlight response) {
                if (response == null) {
                    view.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "You do not have an active flight", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onError(AirMapException e) {
            }
        });
    }

    /**
     * Called when traffic needs to be added
     *
     * @param added A list of traffic that needs to be added to the map
     */
    @Override
    public void onAddTraffic(List<AirMapTraffic> added) {
        for (final AirMapTraffic traffic : added) {
            final MarkerOptions marker = new MarkerOptions().
                    position(getLatLngFromCoordinate(traffic.getCoordinate())).
                    title(traffic.getId()).
                    icon(getIcon(traffic));
            markers.add(marker);
            runOnUiThread(new Runnable() {
                public void run() {
                    map.addMarker(marker);
                }
            });
        }
    }

    /**
     * Called when traffic needs to be updated
     *
     * @param updated A list of traffic that is already on the map whose location/speed/etc need to
     *                be updated
     */
    @Override
    public void onUpdateTraffic(List<AirMapTraffic> updated) {
        for (AirMapTraffic traffic : updated) {
            final MarkerOptions options = searchForId(traffic.getId());
            if (options == null) {
                return;
            }
            final LatLng old = options.getPosition();
            markers.remove(options);
            options.position(getLatLngFromCoordinate(traffic.getCoordinate()));
            options.icon(getIcon(traffic));
            markers.add(options);
            runOnUiThread(new Runnable() {
                public void run() {
                    Marker marker = options.getMarker();
                    ValueAnimator markerAnimator = ObjectAnimator.ofObject(marker, "position", new LatLngEvaluator(), old, marker.getPosition());
                    markerAnimator.setDuration(1100);
                    markerAnimator.start();
                }
            });
        }
    }

    /**
     * Called when traffic needs to be removed
     *
     * @param removed A list of traffic that should no longer be on the map
     */
    @Override
    public void onRemoveTraffic(List<AirMapTraffic> removed) {
        for (AirMapTraffic traffic : removed) {
            final MarkerOptions options = searchForId(traffic.getId());
            if (options == null) {
                return;
            }
            markers.remove(options);
            runOnUiThread(new Runnable() {
                public void run() {
                    map.removeMarker(options.getMarker());
                }
            });
        }
    }

    /**
     * Called when the map is long clicked
     * @param point The location of the click
     */
    @Override
    public void onMapLongClick(@NonNull LatLng point) {
        AirMap.createFlight(this, REQUEST_FLIGHT, getCoordinateFromLatLng(point), null); //The created flight will be returned in onActivityResult
    }


    /**
     * Dynamically provides an icon based on which direction the traffic is traveling
     *
     * @param traffic The traffic to get an icon for
     * @return An icon
     */
    private Icon getIcon(AirMapTraffic traffic) {
        IconFactory factory = IconFactory.getInstance(this);
        int id = 0;
        if (traffic == null) {
            id = R.drawable.airmap_flight_marker;
        } else if (traffic.getTrafficType() == AirMapTraffic.TrafficType.Alert) {
            id = getResources().getIdentifier("traffic_marker_icon_" + directionFromBearing(traffic.getTrueHeading()), "drawable", "com.airmap.airmapsdktest");
        } else if (traffic.getTrafficType() == AirMapTraffic.TrafficType.SituationalAwareness) {
            id = getResources().getIdentifier("sa_traffic_marker_icon_" + directionFromBearing(traffic.getTrueHeading()), "drawable", "com.airmap.airmapsdktest");
        }
        return factory.fromDrawable(ContextCompat.getDrawable(this, id));
    }

    /**
     * Converts a bearing into a compass direction
     *
     * @param bearing The bearing to convert
     * @return A compass direction
     */
    public String directionFromBearing(double bearing) {
        int index = (int) ((bearing / 22.5) + 0.5) % 16;
        return compassDirections[index];

    }

    /**
     * Utility method to convert from an AirMap Coordinate to a MapBox LatLng
     *
     * @param coordinate An AirMap Coordinate
     * @return A MapBox LatLng
     */
    public LatLng getLatLngFromCoordinate(Coordinate coordinate) {
        if (coordinate != null) {
            return new LatLng(coordinate.getLatitude(), coordinate.getLongitude());
        }
        return null;
    }

    /**
     * Utility method to convert from a MapBox LatLng to an AirMap Coordinate
     * @param point A MapBox LatLng
     * @return An AirMap Coordinate
     */
    public Coordinate getCoordinateFromLatLng(LatLng point) {
        if (point != null) {
            return new Coordinate(point.getLatitude(), point.getLongitude());
        }
        return null;
    }

    /**
     * Search for an annotation
     *
     * @param id The id to search for
     * @return The found annotation
     */
    private MarkerOptions searchForId(String id) {
        for (MarkerOptions options : markers) {
            if (options.getTitle().equals(id)) {
                return options;
            }
        }
        return null;
    }

    /**
     * Used to animate traffic markers
     */
    private static class LatLngEvaluator implements TypeEvaluator<LatLng> {
        // Method is used to interpolate the marker animation.
        private LatLng latLng = new LatLng();

        @Override
        public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
            latLng.setLatitude(startValue.getLatitude() + ((endValue.getLatitude() - startValue.getLatitude()) * fraction));
            latLng.setLongitude(startValue.getLongitude() + ((endValue.getLongitude() - startValue.getLongitude()) * fraction));
            return latLng;
        }
    }

    //MapBox required Override methods
    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}