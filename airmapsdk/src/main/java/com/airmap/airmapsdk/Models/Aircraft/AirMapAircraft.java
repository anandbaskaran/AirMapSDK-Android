package com.airmap.airmapsdk.Models.Aircraft;

import com.airmap.airmapsdk.Models.AirMapBaseModel;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Vansh Gandhi on 7/26/16.
 * Copyright © 2016 AirMap, Inc. All rights reserved.
 */
@SuppressWarnings("unused")
public class AirMapAircraft implements Serializable, AirMapBaseModel {

    private String aircraftId;
    private String nickname;
    private AirMapAircraftModel model;

    public AirMapAircraft(JSONObject aircraftJson) {
        constructFromJson(aircraftJson);
    }

    public AirMapAircraft() {
    }

    @Override
    public AirMapAircraft constructFromJson(JSONObject json) {
        if (json != null) {
            setAircraftId(json.optString("id"));
            setNickname(json.optString("nickname"));
            setModel(new AirMapAircraftModel(json.optJSONObject("model")));
        }
        return this;
    }

    /**
     * @return The appropriate parameters when we POST
     */
    public Map<String, String> getAsParamsPost() {
        Map<String, String> params = new HashMap<>();
        params.put("model_id", getModel().getModelId());
        params.put("nickname", getNickname());
        return params;
    }

    /**
     * @return The appropriate parameters when we PATCH
     */
    public Map<String, String> getAsParamsPatch() {
        Map<String, String> params = new HashMap<>();
        params.put("nickname", getNickname());
        return params;
    }

    public String getAircraftId() {
        return aircraftId;
    }

    public AirMapAircraft setAircraftId(String aircraftId) {
        this.aircraftId = aircraftId;
        return this;
    }

    public String getNickname() {
        return nickname;
    }

    public AirMapAircraft setNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public AirMapAircraftModel getModel() {
        return model;
    }

    public AirMapAircraft setModel(AirMapAircraftModel model) {
        this.model = model;
        return this;
    }

    /**
     * Comparison based on ID
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof AirMapAircraft && getAircraftId().equals(((AirMapAircraft) o).getAircraftId());
    }

    @Override
    public String toString() {
        return getNickname();
    }
}
