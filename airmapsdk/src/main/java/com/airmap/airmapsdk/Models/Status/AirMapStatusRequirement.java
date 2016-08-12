package com.airmap.airmapsdk.Models.Status;

import com.airmap.airmapsdk.Models.AirMapBaseModel;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * Created by Vansh Gandhi on 6/15/16.
 * Copyright © 2016 AirMap, Inc. All rights reserved.
 */
@SuppressWarnings("unused")
public class AirMapStatusRequirement implements Serializable, AirMapBaseModel {
    private AirMapStatusRequirementNotice notice;
    private AirMapStatusPermits permits;

    public AirMapStatusRequirement(JSONObject requirementJson) {
        constructFromJson(requirementJson);
    }

    public AirMapStatusRequirement() {
    }

    @Override
    public AirMapStatusRequirement constructFromJson(JSONObject json) {
        if (json != null) {
            setNotice(new AirMapStatusRequirementNotice(json.optJSONObject("notice")));
            setPermit(new AirMapStatusPermits(json.optJSONObject("permits")));
        }
        return this;
    }

    public AirMapStatusRequirementNotice getNotice() {
        return notice;
    }

    public AirMapStatusRequirement setNotice(AirMapStatusRequirementNotice notice) {
        this.notice = notice;
        return this;
    }

    public AirMapStatusPermits getPermit() {
        return permits;
    }

    public AirMapStatusRequirement setPermit(AirMapStatusPermits permits) {
        this.permits = permits;
        return this;
    }
}
