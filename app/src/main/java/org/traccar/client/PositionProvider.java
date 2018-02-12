/*
 * Copyright 2013 - 2017 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationCallback;


public class PositionProvider {

    private static final String TAG = PositionProvider.class.getSimpleName();

    private static final int MINIMUM_INTERVAL = 1000;

    public interface PositionListener {
        void onPositionUpdate(Position position);
    }

    private final PositionListener listener;

    private final Context context;
    private SharedPreferences preferences;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;


    private String deviceId;
    private long interval;
    private double distance;
    private double angle;

    private Location lastLocation;

    private boolean started;

    public PositionProvider(final Context context, final PositionListener listener) {
        this.context = context;
        this.listener = listener;

        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        deviceId = preferences.getString(MainFragment.KEY_DEVICE, "undefined");
        interval = Long.parseLong(preferences.getString(MainFragment.KEY_INTERVAL, "600")) * 1000;
        distance = Integer.parseInt(preferences.getString(MainFragment.KEY_DISTANCE, "0"));
        angle = Integer.parseInt(preferences.getString(MainFragment.KEY_ANGLE, "0"));
        locationClient = LocationServices.getFusedLocationProviderClient(context);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null && (lastLocation == null
                        || location.getTime() - lastLocation.getTime() >= interval
                        || distance > 0 && DistanceCalculator.distance(location.getLatitude(), location.getLongitude(), lastLocation.getLatitude(), lastLocation.getLongitude()) >= distance
                        || angle > 0 && Math.abs(location.getBearing() - lastLocation.getBearing()) >= angle)) {
                    Log.i(TAG, "location new");
                    lastLocation = location;
                    listener.onPositionUpdate(new Position(deviceId, location, getBatteryLevel(context)));
                } else {
                    Log.i(TAG, location != null ? "location ignored" : "location nil");
                }
            };
        };
    }

    @SuppressLint("MissingPermission")
    public void startUpdates() {
        if (!started) {
            LocationRequest request = LocationRequest.create()
                    .setPriority(getPriority(preferences.getString(MainFragment.KEY_ACCURACY, "medium")))
                    .setInterval(distance > 0 || angle > 0 ? MINIMUM_INTERVAL : interval);
            locationClient.requestLocationUpdates(request, locationCallback,null);
            started = true;
        }

    }

    private int getPriority(String accuracy) {
        switch (accuracy) {
            case "high":
                return LocationRequest.PRIORITY_HIGH_ACCURACY;
            case "low":
                return LocationRequest.PRIORITY_LOW_POWER;
            default:
                return LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
        }
    }


    public void stopUpdates() {
        if (started) {
            locationClient.removeLocationUpdates(locationCallback);
            started = false;
        }
    }

    public static double getBatteryLevel(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
            return (level * 100.0) / scale;
        }
        return 0;
    }

}
