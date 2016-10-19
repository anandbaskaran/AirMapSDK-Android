package com.airmap.airmapsdk.networking.services;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.airmap.airmapsdk.models.Coordinate;
import com.airmap.airmapsdk.models.status.AirMapStatus;
import com.airmap.airmapsdk.networking.callbacks.AirMapCallback;
import com.airmap.airmapsdk.networking.callbacks.GenericOkHttpCallback;

import java.util.Date;
import java.util.List;
import java.util.Map;

import okhttp3.Call;

/**
 * Created by Vansh Gandhi on 6/23/16.
 * Copyright © 2016 AirMap, Inc. All rights reserved.
 */
@SuppressWarnings("unused")
class StatusService extends BaseService {
    /**
     * Get a flight status based on a Point and Radius based flight
     *
     * @param coordinate   The center coordinate of the flight
     * @param buffer       Number of meters to buffer a flight (the radius of the flight)
     * @param types        Airspace types to include in the calculation and response
     * @param ignoredTypes Airspace types to ignore in the calculation and response
     * @param weather      Show the current weather conditions
     * @param date         Date and time for planned flight
     * @param listener     The callback that is invoked on success or error
     */
    public static Call checkCoordinate(Coordinate coordinate, @Nullable Double buffer,
                                       List<MappingService.AirMapAirspaceType> types,
                                       List<MappingService.AirMapAirspaceType> ignoredTypes,
                                       boolean weather, Date date,
                                       AirMapCallback<AirMapStatus> listener) {
        String url = statusPointUrl;
        Map<String, String> params = AirMapStatus.getAsParams(coordinate, types, ignoredTypes, weather, date);
        if (buffer != null) {
            params.put("buffer", String.valueOf(buffer));
        }
        return AirMap.getClient().get(url, params, new GenericOkHttpCallback(listener, AirMapStatus.class));
    }

    /**
     * Get a flight status based on a Multi-point based flight
     *
     * @param path         The points on the flight path
     * @param buffer       The line width for the flight
     * @param takeOffPoint The take off point along the flight path
     * @param types        Airspace types to include in the calculation and response
     * @param ignoredTypes Airspace types to ignore in the calculation and response
     * @param weather      Show the current weather conditions
     * @param date         Date and time for planned flight
     * @param listener     The callback that is invoked on success or error
     */
    public static void checkFlightPath(List<Coordinate> path, int buffer, Coordinate takeOffPoint,
                                       List<MappingService.AirMapAirspaceType> types,
                                       List<MappingService.AirMapAirspaceType> ignoredTypes,
                                       boolean weather, Date date,
                                       AirMapCallback<AirMapStatus> listener) {
        String url = statusPathUrl;
        Map<String, String> params = AirMapStatus.getAsParams(takeOffPoint, types, ignoredTypes, weather, date);
        params.put("geometry", "LINESTRING(" + makeGeoString(path) + ")");
        params.put("buffer", String.valueOf(buffer));
        AirMap.getClient().get(url, params, new GenericOkHttpCallback(listener, AirMapStatus.class));
    }

    /**
     * Get a flight status based on a flight within a polygon
     *
     * @param geometry     The polygon the flight will be in
     * @param takeOffPoint The take off point of the flight
     * @param types        Airspace types to include in the calculation and response
     * @param ignoredTypes Airspace types to ignore in the calculation and response
     * @param weather      Show the current weather conditions
     * @param date         Date and time for planned flight
     * @param listener     The callback that is invoked on success or error
     */
    public static void checkPolygon(List<Coordinate> geometry, Coordinate takeOffPoint,
                                    List<MappingService.AirMapAirspaceType> types,
                                    List<MappingService.AirMapAirspaceType> ignoredTypes,
                                    boolean weather, Date date,
                                    AirMapCallback<AirMapStatus> listener) {
        String url = statusPolygonUrl;
        Map<String, String> params = AirMapStatus.getAsParams(takeOffPoint, types, ignoredTypes, weather, date);
        params.put("geometry", "POLYGON(" + makeGeoString(geometry) + ")");
        AirMap.getClient().get(url, params, new GenericOkHttpCallback(listener, AirMapStatus.class));
    }

    /**
     * Formats a list of Coordinates into WKT format
     *
     * @param coordinates The list of coordinates to include in the result
     * @return A WKT formatted string of coordinates
     */
    private static String makeGeoString(List<Coordinate> coordinates) {
        return TextUtils.join(",", coordinates);
    }
}
