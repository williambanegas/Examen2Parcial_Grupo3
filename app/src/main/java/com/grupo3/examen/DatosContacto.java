package com.grupo3.examen;

import com.google.gson.annotations.SerializedName;

public final class DatosContacto {
    @SerializedName("nombre") private final String nombre;
    @SerializedName("telefono") private final String telefono;
    @SerializedName("latitud") private final double latitud;
    @SerializedName("longitud") private final double longitud;

    public DatosContacto(String nombre, String telefono, double latitud, double longitud) {
        this.nombre = nombre;
        this.telefono = telefono;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
}
