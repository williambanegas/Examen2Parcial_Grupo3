package com.grupo3.examen;

import com.google.gson.annotations.SerializedName;

public final class Contacto {
    @SerializedName("id") private int id;
    @SerializedName("nombre") private String nombre;
    @SerializedName("telefono") private String telefono;
    @SerializedName("latitud") private double latitud;
    @SerializedName("longitud") private double longitud;
    @SerializedName("archivo") private String archivo;

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public String getArchivo() { return archivo; }
}
