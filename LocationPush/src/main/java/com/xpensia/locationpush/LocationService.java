package com.xpensia.locationpush;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/**
 * Created by jeansebtr on 13-06-03.
 */
public class LocationService extends Service implements LocationListener {
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
    public void onDestroy() {
        mLocationManager.removeUpdates(this);

        super.onDestroy();
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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Boolean secure = prefs.getBoolean("secure", true);
        String key = prefs.getString("key", null);
        String pass = prefs.getString("pass", null);
        String domain = prefs.getString("domain", "xpensia.cloudant.com");
        String db = prefs.getString("database", "gps");
        String username = prefs.getString("username", null);

        Log.w("SendCoord", "preparing query...");
        // the server
        HttpHost httpHost = new HttpHost(domain, secure ? 443 : 80, secure ? "https" : "http");
        //HttpHost httpHost = new HttpHost("192.168.1.145", 5002, "http");
        // the query
        HttpPost httppost = new HttpPost("/" + db + "/");
        httppost.setHeader("Host", domain);
        if (key != null && !key.equals("") && pass != null && !pass.equals("")) {
            httppost.setHeader("Authorization", "Basic " + Base64.encodeToString((key + ":" + pass).getBytes(), Base64.NO_WRAP));
        }
        httppost.setHeader("Accept", "application/json");

        Log.w("SendCoord", "preparing json...");
        JSONObject obj = new JSONObject();
        StringEntity se;
        try {
            if (username != null && !username.equals("")) {
                obj.put("username", username);
            }
            obj.put("doc_type", "coord");
            obj.put("lat", location.getLatitude());
            obj.put("lng", location.getLongitude());
            obj.put("alt", location.getAltitude());
            obj.put("acc", location.getAccuracy());
            obj.put("date", location.getTime());

            Log.w("SendCoord json", obj.toString());
            se = new StringEntity(obj.toString());
            //se.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
            httppost.setEntity(se);
            httppost.setHeader(HTTP.CONTENT_TYPE, "application/json");

            new AsyncTask<Pair<HttpHost, HttpPost>, Void, HttpResponse>() {
                private IOException mException = null;

                @Override
                protected HttpResponse doInBackground(Pair<HttpHost, HttpPost>... requests) {
                    HttpPost req = requests[0].right;
                    Log.w("SendCoord req", req.getRequestLine().toString());
                    for (Header header : req.getAllHeaders()) {
                        Log.w("SendCoord req", header.getName() + ": " + header.getValue());
                    }

                    HttpClient httpclient = new DefaultHttpClient();
                    //ResponseHandler responseHandler = new BasicResponseHandler();
                    try {
                        HttpResponse response = httpclient.execute(requests[0].left, requests[0].right);

                        Log.w("SendCoord Code", String.valueOf(response.getStatusLine().getStatusCode()));
                        for (Header header : response.getAllHeaders()) {
                            Log.w("SendCoord Header", header.getName() + " = " + header.getValue());
                        }
                        BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent(), "UTF-8"));
                        StringBuilder builder = new StringBuilder();
                        for (String line = null; (line = reader.readLine()) != null; ) {
                            builder.append(line).append("\n");
                        }
                        Log.w("SendCoord Result", builder.toString());

                        return response;
                    } catch (IOException e) {
                        mException = e;
                        Log.e("SendCoord", "Error", e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(HttpResponse httpResponse) {
                    if (mException != null) {
                        Log.e("SendCoord", "Error", mException);
                        return;
                    }
                    super.onPostExecute(httpResponse);
                }
            }.execute(new Pair<HttpHost, HttpPost>(httpHost, httppost));
        } catch (JSONException e) {
            Log.e("SendCoord", "Error", e);
        } catch (UnsupportedEncodingException e) {
            Log.e("SendCoord", "Error", e);
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {
        if (s.equals(LocationManager.GPS_PROVIDER)) {
            startForeground(1, mBuilder.build());
            Toast.makeText(this, "Le service GPS a \u00e9t\u00e9 activ\u00e9.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onProviderDisabled(String s) {
        if (s.equals(LocationManager.GPS_PROVIDER)) {
            stopForeground(true);
            Toast.makeText(this, "Le service GPS a \u00e9t\u00e9 d\u00e9sactiv\u00e9.", Toast.LENGTH_LONG).show();
        }
    }

    private class Pair<X, Y> {
        public final X left;
        public final Y right;

        public Pair(X left, Y right) {
            this.left = left;
            this.right = right;
        }
    }
}
