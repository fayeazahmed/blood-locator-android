package com.example.bloodlocator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import org.jetbrains.annotations.NotNull;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements Style.OnStyleLoaded, PermissionsListener, View.OnLayoutChangeListener, MapboxMap.OnMapClickListener, MapboxMap.OnMapLongClickListener {

    private FloatingActionButton floatingBtn;
    private Spinner spinner;

    private MapView mapView;
    private MapboxMap map;
    private PermissionsManager permissionsManager;
    private Location originLocation;
    private ArrayAdapter<String> bloodGroupAdapter;
    private GestureDetector detector;
    private static Dialog aboutAppDialog;

    private static final String SOURCE_ID = "SOURCE_ID";
    private static final String ICON_ID = "ICON_ID";
    private static final String LAYER_ID = "LAYER_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);

        spinner = findViewById(R.id.spinnerMain);
        floatingBtn = findViewById(R.id.floatingActionButton);
        RelativeLayout spinnerLayout = findViewById(R.id.spinnerLayout);
        spinnerLayout.addOnLayoutChangeListener(this);

        setupSpinner();

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(mapboxMap -> {
            map = mapboxMap;
            map.addOnMapClickListener(MainActivity.this);
            map.addOnMapLongClickListener(MainActivity.this);
        });

        detector = new GestureDetector(this, new MyGestureListener());
    }

    private void updateMap(List<DocumentSnapshot> docs) {
        List<Feature> symbolLayerIconFeatureList = new ArrayList<>();

        for (DocumentSnapshot donor : docs)
        {
            Feature feature = Feature.fromGeometry(
                    Point.fromLngLat(Objects.requireNonNull(donor.getGeoPoint("coordinates")).getLongitude(), Objects.requireNonNull(donor.getGeoPoint("coordinates")).getLatitude()));
            feature.addStringProperty("markerId", donor.getId());
            feature.addStringProperty("donorName", donor.getString("name"));
            feature.addStringProperty("donorBg", donor.getString("bloodGroup"));
            feature.addStringProperty("donorLocation", donor.getString("address"));
            feature.addStringProperty("donorContact", donor.getString("contact"));
            feature.addStringProperty("donorPw", donor.getString("password"));
            symbolLayerIconFeatureList.add(feature);
        }

        map.setStyle(new Style.Builder().fromUri(Style.LIGHT)
                .withImage(ICON_ID, BitmapFactory.decodeResource(
                        MainActivity.this.getResources(), R.drawable.marker_blood))
                .withSource(new GeoJsonSource(SOURCE_ID,
                        FeatureCollection.fromFeatures(symbolLayerIconFeatureList)))
                .withLayer(new SymbolLayer(LAYER_ID, SOURCE_ID)
                        .withProperties(
                                iconImage(ICON_ID),
                                iconAllowOverlap(true),
                                iconIgnorePlacement(true)
                        )
                ), this);
    }

    private void setupSpinner() {
        ArrayList<String> bloodGroupList = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.blood_groups)));
        bloodGroupAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.spinner_text, bloodGroupList);
        bloodGroupAdapter.setDropDownViewResource(R.layout.dropdown_item);
        spinner.setAdapter(bloodGroupAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Database.getDonors(parent.getItemAtPosition(position).toString(), docs -> updateMap(docs));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void showDialog(Feature feature) {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.bottom_sheet);

        TextView bg = dialog.findViewById(R.id.diaogBloodGroup);
        TextView donorName = dialog.findViewById(R.id.diaogName);
        TextView location = dialog.findViewById(R.id.diaogLocation);
        TextView contact = dialog.findViewById(R.id.diaogContact);
        TextView deleteBtn = dialog.findViewById(R.id.delBtn);

        deleteBtn.setOnClickListener(v -> {
            EditText password = dialog.findViewById(R.id.diaogDelete);
            password.setVisibility(View.VISIBLE);
            password.setOnEditorActionListener((v1, actionId, event) -> {
                if(actionId == EditorInfo.IME_ACTION_GO && !password.getText().toString().isEmpty()) {
                    if(feature.getStringProperty("donorPw").equals(password.getText().toString())) {
                        Database.deleteDonor(feature.getStringProperty("markerId"), success -> {
                            if(success) {
                                Database.getDonors(feature.getStringProperty("donorBg"), this::updateMap);
                                spinner.setSelection(bloodGroupAdapter.getPosition(feature.getStringProperty("donorBg")));
                                Toast.makeText(MainActivity.this, "Your location is removed", Toast.LENGTH_LONG).show();
                            } else
                                Toast.makeText(MainActivity.this, "Sorry! Something went wrong", Toast.LENGTH_SHORT).show();
                        });
                    } else
                        Toast.makeText(MainActivity.this, "Password does not match :/", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
                return false;
            });
        });

        bg.setText(feature.getStringProperty("donorBg"));
        donorName.setText(feature.getStringProperty("donorName"));
        location.setText(feature.getStringProperty("donorLocation"));
        contact.setText(feature.getStringProperty("donorContact"));

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.show();
    }

    private void showForm() {
        Intent intent = new Intent(getApplicationContext(), FormActivity.class);
        intent.putExtra("coordinates", originLocation);
        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK)
        {
            if(data.getBooleanExtra("result", false)) {
                Database.getDonors(data.getStringExtra("bloodGroup"), this::updateMap);
                spinner.setSelection(bloodGroupAdapter.getPosition(data.getStringExtra("bloodGroup")));
                Toast.makeText(this, "Data added successfully.", Toast.LENGTH_LONG).show();
            }
            else
                Toast.makeText(this, "Sorry! Something went wrong.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStyleLoaded(@NonNull Style style) {
        enableLocationComponent(style);
        floatingBtn.setOnClickListener(v -> {
            enableLocationComponent(style);
            Snackbar snackbar = Snackbar.make(mapView, R.string.snackbar_message, Snackbar.LENGTH_SHORT);
            snackbar.setAction(R.string.snackbar_action, view -> showForm());
            snackbar.show();
        });
    }

    @Override
    public boolean onMapClick(@NotNull LatLng point) {
        PointF screenPoint = map.getProjection().toScreenLocation(point);
        List<Feature> features = map.queryRenderedFeatures(screenPoint, LAYER_ID);
        if (!features.isEmpty()) {
            showDialog(features.get(0));
        }
        return false;
    }

    @Override
    public boolean onMapLongClick(@NonNull @NotNull LatLng point) {
        Location coordinates = new Location("");
        coordinates.setLatitude(point.getLatitude());
        coordinates.setLongitude(point.getLongitude());
        Intent intent = new Intent(getApplicationContext(), FormActivity.class);
        intent.putExtra("coordinates", coordinates);
        startActivityForResult(intent, 2);
        return false;
    }

    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
        // Get an instance of the component
            LocationComponent locationComponent = map.getLocationComponent();
        // Activate with options
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());
        // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);
        // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);
        // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);
            originLocation = locationComponent.getLastKnownLocation();
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void showInfo(View v) {
        aboutAppDialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen);
        aboutAppDialog.setContentView(R.layout.info_app);
        aboutAppDialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        aboutAppDialog.show();
        RelativeLayout layout = aboutAppDialog.findViewById(R.id.infoLayout);
        layout.setOnTouchListener((v12, event) -> detector.onTouchEvent(event));
        ImageButton btn = aboutAppDialog.findViewById(R.id.infoBackBtn);
        btn.setOnClickListener(v1 -> aboutAppDialog.dismiss());
    }

    static class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float x1 = e1.getX();
            float y1 = e1.getY();
            float x2 = e2.getX();
            float y2 = e2.getY();
            if(isSwipeDown(x1,y1,x2,y2)) {
                aboutAppDialog.dismiss();
            }
            return true;
        }

        public boolean isSwipeDown(float x1, float y1, float x2, float y2){
            return inRange(getAngle(x1, y1, x2, y2));
        }

        private boolean inRange(double angle){
            return (angle >= (float) 225) && (angle < (float) 315);
        }

        public double getAngle(float x1, float y1, float x2, float y2) {
            double rad = Math.atan2(y1-y2,x2-x1) + Math.PI;
            return (rad*180/Math.PI + 180)%360;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "YO", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            map.getStyle(this::enableLocationComponent);
        } else {
            Toast.makeText(this, "user_location_permission_not_granted", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
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
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        spinner.setDropDownWidth(right - left);
    }
}