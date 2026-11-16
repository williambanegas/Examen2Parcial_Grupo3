package com.grupo3.examen;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashSet;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ContactosActivity extends AppCompatActivity implements ContactosAdapter.Acciones {
    private EditText busqueda;
    private Button buscar;
    private Button limpiar;
    private Button reintentar;
    private TextView estado;
    private ProgressBar progreso;
    private ContactosAdapter adapter;
    private Call<List<Contacto>> consulta;
    private Call<?> cambio;
    private AlertDialog dialogo;
    private String filtro = "";
    private boolean cargando;
    private boolean listaValida;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_contactos);
        View raiz = findViewById(R.id.cg_raiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets margenes = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            vista.setPadding(margenes.left, margenes.top, margenes.right, margenes.bottom);
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), raiz).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), raiz).setAppearanceLightNavigationBars(true);
        ViewCompat.requestApplyInsets(raiz);
        busqueda = findViewById(R.id.cg_busqueda);
        buscar = findViewById(R.id.cg_buscar);
        limpiar = findViewById(R.id.cg_limpiar);
        reintentar = findViewById(R.id.cg_reintentar);
        estado = findViewById(R.id.cg_estado);
        progreso = findViewById(R.id.cg_progreso);
        ListView lista = findViewById(R.id.cg_lista);
        adapter = new ContactosAdapter(this, this);
        lista.setAdapter(adapter);
        findViewById(R.id.cg_volver).setOnClickListener(v -> finish());
        buscar.setOnClickListener(v -> buscarContactos());
        limpiar.setOnClickListener(v -> {
            busqueda.setText("");
            buscarContactos();
        });
        reintentar.setOnClickListener(v -> consultar());
        busqueda.setOnEditorActionListener((v, accion, evento) -> {
            if (accion == EditorInfo.IME_ACTION_SEARCH) {
                buscarContactos();
                return true;
            }
            return false;
        });
        if (guardado != null) {
            filtro = guardado.getString("cg_filtro", "");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cambio == null && consulta == null && dialogo == null) {
            consultar();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle salida) {
        salida.putString("cg_filtro", filtro);
        super.onSaveInstanceState(salida);
    }

    private void buscarContactos() {
        if (cargando || cambio != null) return;
        filtro = busqueda.getText().toString().trim();
        InputMethodManager teclado = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (teclado != null) teclado.hideSoftInputFromWindow(busqueda.getWindowToken(), 0);
        consultar();
    }

    private boolean cerrada() {
        return isFinishing() || isDestroyed();
    }

    private void mostrarCarga(boolean valor) {
        cargando = valor;
        progreso.setVisibility(valor ? View.VISIBLE : View.GONE);
        buscar.setEnabled(!valor);
        limpiar.setEnabled(!valor);
        busqueda.setEnabled(!valor);
        reintentar.setEnabled(!valor);
        adapter.habilitar(!valor && listaValida);
    }

    private void consultar() {
        if (cerrada() || cambio != null) return;
        if (consulta != null) consulta.cancel();
        listaValida = false;
        reintentar.setVisibility(View.GONE);
        mostrarCarga(true);
        estado.setText(R.string.cg_cargando);
        try {
            consulta = ApiClient.servicio().listar(filtro.isEmpty() ? null : filtro);
            consulta.enqueue(new Callback<List<Contacto>>() {
                @Override
                public void onResponse(Call<List<Contacto>> call, Response<List<Contacto>> respuesta) {
                    if (cerrada() || call != consulta) {
                        cerrarError(respuesta);
                        return;
                    }
                    consulta = null;
                    List<Contacto> contactos = respuesta.body();
                    if (respuesta.isSuccessful() && contactos != null && listaCorrecta(contactos)) {
                        adapter.clear();
                        adapter.addAll(contactos);
                        listaValida = true;
                        mostrarCarga(false);
                        estado.setText(contactos.isEmpty()
                                ? (filtro.isEmpty() ? R.string.cg_vacio : R.string.cg_sin_resultados)
                                : R.string.cg_actualizar);
                        reintentar.setText(R.string.cg_actualizar);
                        reintentar.setVisibility(View.VISIBLE);
                    } else {
                        falloConsulta(respuesta.isSuccessful()
                                ? getString(R.string.cg_inesperada) : errorHttp(respuesta));
                    }
                }

                @Override
                public void onFailure(Call<List<Contacto>> call, Throwable error) {
                    if (cerrada() || call != consulta || call.isCanceled()) return;
                    consulta = null;
                    falloConsulta(getString(R.string.cg_conexion));
                }
            });
        } catch (RuntimeException error) {
            consulta = null;
            falloConsulta(getString(R.string.cg_inesperada));
        }
    }

    private boolean listaCorrecta(List<Contacto> contactos) {
        HashSet<Integer> ids = new HashSet<>();
        for (Contacto contacto : contactos) {
            if (contacto == null || contacto.getId() <= 0 || !ids.add(contacto.getId())) return false;
        }
        return true;
    }

    private void falloConsulta(String mensaje) {
        listaValida = false;
        adapter.clear();
        mostrarCarga(false);
        estado.setText(mensaje);
        reintentar.setText(R.string.cg_reintentar);
        reintentar.setVisibility(View.VISIBLE);
    }

    private String errorHttp(Response<?> respuesta) {
        if (respuesta.code() == 404) {
            cerrarError(respuesta);
            return getString(R.string.cg_no_existe);
        }
        try {
            return ApiClient.mensajeError(respuesta);
        } catch (RuntimeException error) {
            return getString(R.string.cg_inesperada);
        }
    }

    private void cerrarError(Response<?> respuesta) {
        if (respuesta.errorBody() != null) respuesta.errorBody().close();
    }

    @Override
    public void editar(Contacto contacto) {
        if (cargando || !listaValida || dialogo != null) return;
        View formulario = getLayoutInflater().inflate(R.layout.dialog_editar_contacto, null);
        EditText nombre = formulario.findViewById(R.id.cg_editar_nombre);
        EditText telefono = formulario.findViewById(R.id.cg_editar_telefono);
        ProgressBar carga = formulario.findViewById(R.id.cg_editar_progreso);
        TextView mensaje = formulario.findViewById(R.id.cg_editar_estado);
        nombre.setText(contacto.getNombre());
        telefono.setText(contacto.getTelefono());
        AlertDialog editor = new AlertDialog.Builder(this)
                .setTitle(R.string.cg_editar).setView(formulario)
                .setNegativeButton(R.string.cg_cancelar, null)
                .setPositiveButton(R.string.cg_guardar, null).create();
        dialogo = editor;
        editor.setOnDismissListener(d -> dialogo = null);
        editor.setOnShowListener(d -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (cambio != null) return;
            boolean nombreValido = validar(nombre, 150);
            boolean telefonoValido = validar(telefono, 30);
            if (!nombreValido || !telefonoValido) return;
            if (!Double.isFinite(contacto.getLatitud()) || !Double.isFinite(contacto.getLongitud())
                    || Math.abs(contacto.getLatitud()) > 90 || Math.abs(contacto.getLongitud()) > 180) {
                mensaje.setText(R.string.cg_coordenadas);
                return;
            }
            DatosContacto datos = new DatosContacto(nombre.getText().toString().trim(),
                    telefono.getText().toString().trim(), contacto.getLatitud(), contacto.getLongitud());
            editor.setCancelable(false);
            editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            nombre.setEnabled(false);
            telefono.setEnabled(false);
            carga.setVisibility(View.VISIBLE);
            mensaje.setText(R.string.cg_procesando);
            mostrarCarga(true);
            try {
                Call<Contacto> peticion = ApiClient.servicio().actualizar(contacto.getId(), datos);
                cambio = peticion;
                peticion.enqueue(new Callback<Contacto>() {
                    @Override
                    public void onResponse(Call<Contacto> call, Response<Contacto> respuesta) {
                        if (cerrada() || call != cambio) {
                            cerrarError(respuesta);
                            return;
                        }
                        cambio = null;
                        Contacto actualizado = respuesta.body();
                        if (respuesta.isSuccessful() && actualizado != null
                                && actualizado.getId() == contacto.getId()
                                && actualizado.getNombre() != null && actualizado.getTelefono() != null) {
                            editor.dismiss();
                            aviso(R.string.cg_guardado);
                            consultar();
                        } else if (respuesta.code() == 400) {
                            mostrarCarga(false);
                            mensaje.setText(errorHttp(respuesta));
                            carga.setVisibility(View.GONE);
                            nombre.setEnabled(true);
                            telefono.setEnabled(true);
                            editor.setCancelable(true);
                            editor.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            editor.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        } else {
                            String texto = respuesta.isSuccessful() ? getString(R.string.cg_incierto) : errorHttp(respuesta);
                            editor.dismiss();
                            revisarResultado(texto);
                        }
                    }

                    @Override
                    public void onFailure(Call<Contacto> call, Throwable error) {
                        if (cerrada() || call != cambio || call.isCanceled()) return;
                        cambio = null;
                        editor.dismiss();
                        revisarResultado(getString(R.string.cg_incierto));
                    }
                });
            } catch (RuntimeException error) {
                cambio = null;
                editor.dismiss();
                revisarResultado(getString(R.string.cg_incierto));
            }
        }));
        editor.show();
        if (editor.getWindow() != null) {
            editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private boolean validar(EditText campo, int maximo) {
        String texto = campo.getText().toString().trim();
        campo.setError(null);
        if (texto.isEmpty()) {
            campo.setError(getString(R.string.cg_requerido));
            return false;
        }
        if (texto.length() > maximo) {
            campo.setError(getString(R.string.cg_limite, maximo));
            return false;
        }
        return true;
    }

    @Override
    public void eliminar(Contacto contacto) {
        if (cargando || !listaValida || dialogo != null) return;
        dialogo = new AlertDialog.Builder(this).setTitle(R.string.cg_eliminar)
                .setMessage(getString(R.string.cg_confirmar,
                        contacto.getNombre() == null ? getString(R.string.cg_sin_nombre) : contacto.getNombre()))
                .setNegativeButton(R.string.cg_cancelar, null)
                .setPositiveButton(R.string.cg_eliminar, (d, cual) -> eliminarConfirmado(contacto))
                .create();
        dialogo.setOnDismissListener(d -> dialogo = null);
        dialogo.show();
    }

    private void eliminarConfirmado(Contacto contacto) {
        if (cargando || cambio != null) return;
        mostrarCarga(true);
        estado.setText(R.string.cg_procesando);
        reintentar.setVisibility(View.GONE);
        try {
            Call<Void> peticion = ApiClient.servicio().eliminar(contacto.getId());
            cambio = peticion;
            peticion.enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> respuesta) {
                    if (cerrada() || call != cambio) {
                        cerrarError(respuesta);
                        return;
                    }
                    cambio = null;
                    if (respuesta.isSuccessful()) {
                        aviso(R.string.cg_eliminado);
                        consultar();
                    } else {
                        revisarResultado(errorHttp(respuesta));
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable error) {
                    if (cerrada() || call != cambio || call.isCanceled()) return;
                    cambio = null;
                    revisarResultado(getString(R.string.cg_incierto));
                }
            });
        } catch (RuntimeException error) {
            cambio = null;
            revisarResultado(getString(R.string.cg_incierto));
        }
    }

    private void revisarResultado(String mensaje) {
        listaValida = false;
        mostrarCarga(false);
        estado.setText(mensaje);
        reintentar.setText(R.string.cg_actualizar);
        reintentar.setVisibility(View.VISIBLE);
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }

    @Override
    public void verUbicacion(Contacto contacto) {
        if (contacto == null) return;
        Intent intent = new Intent(this, UbicacionContactoActivity.class);
        intent.putExtra("latitud", contacto.getLatitud());
        intent.putExtra("longitud", contacto.getLongitud());
        intent.putExtra("nombre", contacto.getNombre());
        startActivity(intent);
    }

    private void aviso(int mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (consulta != null) consulta.cancel();
        if (cambio != null) cambio.cancel();
        if (dialogo != null) dialogo.dismiss();
        super.onDestroy();
    }
}
