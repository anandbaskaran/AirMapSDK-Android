package com.airmap.airmapsdk.UI.Fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;

import com.airmap.airmapsdk.Models.Flight.AirMapFlight;
import com.airmap.airmapsdk.Models.Status.AirMapStatus;
import com.airmap.airmapsdk.Models.Status.AirMapStatusAdvisory;
import com.airmap.airmapsdk.Models.Status.AirMapStatusRequirementNotice;
import com.airmap.airmapsdk.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Vansh Gandhi on 7/25/16.
 * Copyright © 2016 AirMap, Inc. All rights reserved.
 */
public class ReviewNoticeFragment extends Fragment {

    private static final String ARG_STATUS = "status";
    private static final String ARG_NOTICE = "notice";

    private OnFragmentInteractionListener mListener;

    private Switch submitNoticeSwitch;
    private ListView digitalNoticeList;
    private TextView notDigitalLabel;
    private ListView notDigitalNoticeList;

    private AirMapStatus status;
    private List<AirMapStatusRequirementNotice> digitalNotices;
    private List<String> digitalNoticeNames;
    private List<AirMapStatusRequirementNotice> notDigitalNotices;
    private List<String> notDigitalNoticeNames;

    public ReviewNoticeFragment() {
        // Required empty public constructor
    }

    public static ReviewNoticeFragment newInstance(AirMapStatus status, boolean submitNotice) {
        ReviewNoticeFragment fragment = new ReviewNoticeFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_STATUS, status);
        args.putBoolean(ARG_NOTICE, submitNotice);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_review_notices, container, false);
        initializeViews(view);
        status = (AirMapStatus) getArguments().getSerializable(ARG_STATUS);
        getNotices();
        setupDigitalNoticeList();
        setupNotDigitalNoticeList();
        submitNoticeSwitch.setChecked(getArguments().getBoolean(ARG_NOTICE, true));
        submitNoticeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mListener.getFlight().setNotify(isChecked);
            }
        });
        return view;
    }

    private void initializeViews(View view) {
        submitNoticeSwitch = (Switch) view.findViewById(R.id.submit_notice_switch);
        digitalNoticeList = (ListView) view.findViewById(R.id.digital_notice_list);
        notDigitalLabel = (TextView) view.findViewById(R.id.not_digital_label);
        notDigitalNoticeList = (ListView) view.findViewById(R.id.not_digital_list);
    }

    private void getNotices() {
        digitalNotices = new ArrayList<>();
        digitalNoticeNames = new ArrayList<>();
        notDigitalNotices = new ArrayList<>();
        notDigitalNoticeNames = new ArrayList<>();
        for (AirMapStatusAdvisory advisory : status.getAdvisories()) {
            if (advisory.getRequirements() != null && advisory.getRequirements().getNotice() != null) {
                AirMapStatusRequirementNotice notice = advisory.getRequirements().getNotice();
                if (notice.isDigital()) {
                    digitalNotices.add(notice);
                    digitalNoticeNames.add(advisory.getName());
                } else if (notice.isNoticeRequired()) {
                    notDigitalNotices.add(notice);
                    notDigitalNoticeNames.add(advisory.getName());
                }
            }
        }
    }

    private void setupDigitalNoticeList() {
        if (digitalNoticeNames.isEmpty()) {
            submitNoticeSwitch.setVisibility(View.GONE);
        } else {
            digitalNoticeList.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, digitalNoticeNames));
        }
    }

    private void setupNotDigitalNoticeList() {
        if (notDigitalNoticeNames.isEmpty()) {
            notDigitalLabel.setVisibility(View.GONE);
        } else {
            List<Map<String, String>> list = new ArrayList<>();
            for (int i = 0; i < notDigitalNotices.size(); i++) {
                Map<String, String> map = new HashMap<>();
                map.put("name", notDigitalNoticeNames.get(i));
                String number = notDigitalNotices.get(i).getPhoneNumber();
                if (number == null || number.length() < 10) {
                    number = "";
                }
                map.put("phone", number);
                list.add(map);
            }
            notDigitalNoticeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    String number = notDigitalNotices.get(position).getPhoneNumber();
                    if (number != null && number.length() >= 10) {
                        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
                        startActivity(intent);
                    }
                }
            });
            notDigitalNoticeList.setAdapter(new SimpleAdapter(getContext(), list, android.R.layout.simple_list_item_2, new String[]{"name", "phone"}, new int[]{android.R.id.text1, android.R.id.text2}));
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener {
        AirMapFlight getFlight();
    }
}
