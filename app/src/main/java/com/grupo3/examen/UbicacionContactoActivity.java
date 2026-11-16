package com.grupo3.examen;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class UbicacionContactoActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private double latitud;
    private double longitud;
    private String nombre;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ubicacion_contacto);

        latitud = getIntent().getDoubleExtra("latitud", 0.0);
        longitud = getIntent().getDoubleExtra("longitud", 0.0);
        nombre = getIntent().getStringExtra("nombre");

        if (nombre == null || nombre.isEmpty()) {
            nombre = "Contacto";
        }

        // Si las coordenadas son 0.0 o inválidas, asignar valores por defecto o notificar
        if (latitud == 0.0 && longitud == 0.0) {
            latitud = 15.50;
            longitud = -88.00;
        }

        Log.d("UbicacionMap", "Mostrando ubicación para: " + nombre + " [Lat: " + latitud + ", Lng: " + longitud + "]");

        TextView titulo = findViewById(R.id.map_titulo);
        if (titulo != null) {
            titulo.setText(getString(R.string.map_titulo_contacto, nombre));
        }

        Button volver = findViewById(R.id.map_volver);
        if (volver != null) {
            volver.setOnClickListener(v -> finish());
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker taking latitude and longitude of the saved contact
        LatLng sydney = new LatLng(latitud, longitud);
        mMap.addMarker(new MarkerOptions().position(sydney).title(nombre));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(15f));
    }
}
