package com.xpensia.locationpush;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

/**
 * Created by jeansebtr on 13-06-03.
 */
public class LocationService extends Service implements LocationListener{
    private Handler mHandler = new Handler();

    private NotificationManager mNotificationManager = null;
    private LocationManager mLocationManager = null;
    private NotificationCompat.Builder mBuilder = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // open settings on click on notification
        Intent intent = new Intent(this, SettingsActivity.class);
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // prepare ongoing notification
        mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Service de localisation")
                .setContentText("Initialisation du service de positionnement")
                .setContentIntent(pendingIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // put in foreground and show notification
        startForeground(1, mBuilder.build());

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 50, this);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        String text = "Lat: "
                .concat(String.valueOf(location.getLatitude()))
                .concat(" Lng: ")
                .concat(String.valueOf(location.getLongitude()));
        mBuilder.setContentText(text);
        mNotificationManager.notify(1, mBuilder.build());
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
