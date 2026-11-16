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

public class ContactosAdapter extends ArrayAdapter<Contacto> {
    public interface Acciones {
        void editar(Contacto contacto);
        void eliminar(Contacto contacto);
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
        Button editar = vista.findViewById(R.id.cg_editar);
        Button eliminar = vista.findViewById(R.id.cg_eliminar);
        nombre.setText(contacto == null || contacto.getNombre() == null
                ? getContext().getString(R.string.cg_sin_nombre) : contacto.getNombre());
        telefono.setText(contacto == null || contacto.getTelefono() == null
                ? getContext().getString(R.string.cg_sin_telefono) : contacto.getTelefono());
        editar.setEnabled(habilitado && contacto != null && contacto.getId() > 0);
        eliminar.setEnabled(habilitado && contacto != null && contacto.getId() > 0);
        editar.setOnClickListener(v -> acciones.editar(contacto));
        eliminar.setOnClickListener(v -> acciones.eliminar(contacto));
        return vista;
    }
}
