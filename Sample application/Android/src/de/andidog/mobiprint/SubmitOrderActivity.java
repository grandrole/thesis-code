package de.andidog.mobiprint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class SubmitOrderActivity extends Activity
{
    private OrderCollectionAdapter adapter;

    private String currentLocation = null;

    private EditText locationEditText;

    private LocationListener locationListener;

    private static final String TAG = "SubmitOrderActivity";

    private void addLocationRadioButton(final Store location)
    {
        final int id = location.getId();
        final RadioGroup locations = (RadioGroup)findViewById(R.id.locations);

        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View locationLayout = inflater.inflate(R.layout.location, null);

        RadioButton radioButton = (RadioButton)locationLayout.findViewById(R.id.select_location);
        radioButton.setId(id);
        TextView nameTextView = (TextView)locationLayout.findViewById(R.id.location_name);
        nameTextView.setText(location.getName());
        nameTextView.setClickable(false);
        TextView addressTextView = (TextView)locationLayout.findViewById(R.id.location_address);
        addressTextView.setText(location.getAddress());
        addressTextView.setClickable(false);

        locationLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v)
            {
                // Note: View ID is set to location ID
                locations.check(id);
            }
        });

        locations.addView(locationLayout);
    }

    /**
     * @return May return NULL.
     */
    private String getCachedLocation()
    {
        File cacheDir = getCacheDir();
        File cachedFile = new File(cacheDir.getAbsolutePath(), "location.txt");
        FileInputStream stream = null;

        try
        {
            boolean bam=cachedFile.exists();
            long lbam=cachedFile.length();

            if(cachedFile.exists() && cachedFile.length() > 5)
            {
                stream = new FileInputStream(cachedFile);
                InputStreamReader in = new InputStreamReader(stream);

                // Fair enough :D
                char[] buffer = new char[(int)cachedFile.length()*4];
                int len = in.read(buffer, 0, buffer.length);
                return String.valueOf(buffer, 0, len).trim();
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
            Toast.makeText(this, "Failed to read cached location: " + e, Toast.LENGTH_LONG).show();
        }

        return null;
    }

    private String locationToString(Location location)
    {
        if(location == null)
            return null;

        return String.format("%.5f;%.5f", location.getLatitude(), location.getLongitude());
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.submit_order);

        locationEditText = (EditText)findViewById(R.id.location_edit);

        adapter = OrderCollectionAdapter.getInstance(this);

        Order currentOrder = null;

        for(Order order : adapter.getAllOrders())
            if(order.getSubmissionDate() == null)
            {
                currentOrder = order;
                break;
            }

        if(currentOrder == null)
            throw new AssertionError();

        TextView heading = (TextView)findViewById(R.id.submit_heading);
        heading.setText(String.format(getResources().getString(R.string.submit_heading_fmt),
                                      currentOrder.getPictureIds().length));

        addLocationRadioButton(new Store(1, "LITTLE", "somewhere"));
        addLocationRadioButton(new Store(2, "F-Markt", "somewhere else"));

        startLocating();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params)
            {
                try
                {
                    Thread.sleep(500);
                }
                catch(InterruptedException e)
                {
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void result)
            {
                locationEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s)
                    {
                        setCurrentLocation(s.toString());
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after)
                    {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count)
                    {
                    }
                });
            }
        }.execute();
    }

    private synchronized void setCurrentLocation(String currentLocation)
    {
        boolean initial = this.currentLocation == null;
        this.currentLocation = currentLocation.trim();

        if(initial || !locationEditText.getText().toString().equals(this.currentLocation))
        {
            locationEditText.setText(this.currentLocation);
            updateStores();

            if(this.currentLocation.length() > 0)
            {
                File cacheDir = getCacheDir();
                File cachedFile = new File(cacheDir.getAbsolutePath(), "location.txt");
                FileOutputStream stream = null;

                try
                {
                    stream = new FileOutputStream(cachedFile);
                    stream.write(this.currentLocation.getBytes());
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to store cached location: " + e, Toast.LENGTH_LONG).show();

                    try
                    {
                        if(stream != null)
                            stream.close();
                    }
                    catch(IOException e2)
                    {
                        e2.printStackTrace();
                    }
                }
            }
        }
    }

    private void startLocating()
    {
        final LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location)
            {
                Log.i(TAG, "Found coarse location");
                setCurrentLocation(locationToString(location));
                locationManager.removeUpdates(locationListener);
            }

            @Override
            public void onProviderDisabled(String provider)
            {
            }

            @Override
            public void onProviderEnabled(String provider)
            {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras)
            {
            }
        };

        new Thread(new Runnable() {
            public void run()
            {
                // Wait 30 seconds at the most for first location update
                try
                {
                    Thread.sleep(30000);
                }
                catch(InterruptedException e)
                {
                }

                locationManager.removeUpdates(locationListener);
            }
        }).start();

        Location cachedLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if(cachedLocation == null)
            cachedLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        String cachedLocationStr;

        if(cachedLocation != null && System.currentTimeMillis() - cachedLocation.getTime() > 1000 * 120)
            cachedLocationStr = getCachedLocation();
        else
            cachedLocationStr = locationToString(cachedLocation);

        setCurrentLocation(cachedLocationStr == null ? "" : cachedLocationStr);

        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null);
        locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null);
    }

    private void updateStores()
    {
        Log.i("STORES", "Updating stores from location " + currentLocation);
    }
}