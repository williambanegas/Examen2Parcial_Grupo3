package com.grupo3.examen;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;
import androidx.appcompat.widget.TooltipCompat;

public class ContactosAdapter extends ArrayAdapter<Contacto> {
    public interface Acciones {
        void editar(Contacto contacto);
        void eliminar(Contacto contacto);
        void verUbicacion(Contacto contacto);
        void verVideo(Contacto contacto);
    }

    private final Acciones acciones;
    private boolean habilitado;

    public ContactosAdapter(Context contexto, Acciones acciones) {
        super(contexto, 0, new ArrayList<>());
        this.acciones = acciones;
    }

    public void habilitar(boolean valor) {
        habilitado = valor;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int posicion, @Nullable View vista, @NonNull ViewGroup padre) {
        if (vista == null) {
            vista = LayoutInflater.from(getContext()).inflate(R.layout.item_contacto, padre, false);
        }
        Contacto contacto = getItem(posicion);
        TextView nombre = vista.findViewById(R.id.cg_nombre);
        TextView telefono = vista.findViewById(R.id.cg_telefono);
        Button verVideo = vista.findViewById(R.id.cg_ver_video);
        Button ubicacion = vista.findViewById(R.id.cg_ubicacion);
        Button editar = vista.findViewById(R.id.cg_editar);
        Button eliminar = vista.findViewById(R.id.cg_eliminar);

        nombre.setText(contacto == null || contacto.getNombre() == null
                ? getContext().getString(R.string.cg_sin_nombre) : contacto.getNombre());
        telefono.setText(contacto == null || contacto.getTelefono() == null
                ? getContext().getString(R.string.cg_sin_telefono) : contacto.getTelefono());

        String textoNombre = nombre.getText().toString().trim();
        String[] partes = textoNombre.split("\\s+");
        String iniciales = textoNombre.isEmpty() ? "?" : partes[0].substring(0, 1);
        if (partes.length > 1) iniciales += partes[partes.length - 1].substring(0, 1);
        ((TextView) vista.findViewById(R.id.cg_avatar)).setText(iniciales.toUpperCase(Locale.getDefault()));
        for (Button boton : new Button[]{verVideo, ubicacion, editar, eliminar}) {
            if (boton != null) {
                String etiqueta = boton.getContentDescription().toString().split(": ")[0];
                boton.setContentDescription(etiqueta + ": " + nombre.getText());
                TooltipCompat.setTooltipText(boton, etiqueta);
            }
        }
        boolean activo = habilitado && contacto != null && contacto.getId() > 0;
        if (verVideo != null) {
            verVideo.setEnabled(activo);
            verVideo.setOnClickListener(v -> acciones.verVideo(contacto));
        }
        ubicacion.setEnabled(activo);
        editar.setEnabled(activo);
        eliminar.setEnabled(activo);

        ubicacion.setOnClickListener(v -> acciones.verUbicacion(contacto));
        editar.setOnClickListener(v -> acciones.editar(contacto));
        eliminar.setOnClickListener(v -> acciones.eliminar(contacto));
        return vista;
    }
}
