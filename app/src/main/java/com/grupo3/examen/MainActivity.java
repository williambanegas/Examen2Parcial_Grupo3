package com.grupo3.examen;

import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final MediaType TEXTO = MediaType.get("text/plain; charset=utf-8");
    private static final MediaType MP4 = MediaType.get("video/mp4");
    private static final long MAX_VIDEO_BYTES = 20_000_000L;

    private final ExecutorService archivos = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> elegirVideoPrueba = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    usarVideo(uri);
                    usarUbicacion(14.48, -88.78);
                }
            });

    private EditText nombre;
    private EditText telefono;
    private TextView latitud;
    private TextView longitud;
    private TextView video;
    private TextView estado;
    private Button guardar;
    private Button prueba;
    private Button reintentar;
    private ProgressBar progreso;

    private Uri videoUri;
    private Double valorLatitud;
    private Double valorLongitud;
    private Call<Contacto> peticionActiva;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        View raiz = findViewById(R.id.formularioRaiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets margenes = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            vista.setPadding(margenes.left, margenes.top, margenes.right, margenes.bottom);
            return insets;
        });

        nombre = findViewById(R.id.nombreContacto);
        telefono = findViewById(R.id.telefonoContacto);
        latitud = findViewById(R.id.latitudContacto);
        longitud = findViewById(R.id.longitudContacto);
        video = findViewById(R.id.videoContacto);
        estado = findViewById(R.id.estadoGuardado);
        guardar = findViewById(R.id.salvarContacto);
        prueba = findViewById(R.id.cargarPrueba);
        reintentar = findViewById(R.id.reintentarGuardado);
        progreso = findViewById(R.id.progresoGuardado);

        guardar.setOnClickListener(vista -> guardarContacto());
        reintentar.setOnClickListener(vista -> guardarContacto());
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            prueba.setVisibility(View.VISIBLE);
            prueba.setOnClickListener(vista -> elegirVideoPrueba.launch("video/mp4"));
        }

        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString("videoUri");
            if (uri != null) {
                usarVideo(Uri.parse(uri));
            }
            if (savedInstanceState.containsKey("latitud")) {
                usarUbicacion(savedInstanceState.getDouble("latitud"),
                        savedInstanceState.getDouble("longitud"));
            }
        }
    }

    // Llamar desde el resultado exitoso de la grabación del integrante 4.
    public void usarVideo(Uri uri) {
        videoUri = uri;
        video.setText(uri == null ? R.string.contacto_sin_video : R.string.contacto_video_listo);
    }

    // Llamar desde el resultado de ubicación del integrante 6.
    public void usarUbicacion(double nuevaLatitud, double nuevaLongitud) {
        if (Double.isNaN(nuevaLatitud) || Double.isNaN(nuevaLongitud)
                || nuevaLatitud < -90 || nuevaLatitud > 90
                || nuevaLongitud < -180 || nuevaLongitud > 180) {
            valorLatitud = null;
            valorLongitud = null;
            latitud.setText(R.string.contacto_latitud_pendiente);
            longitud.setText(R.string.contacto_longitud_pendiente);
            estado.setText(R.string.contacto_falta_ubicacion);
            return;
        }
        valorLatitud = nuevaLatitud;
        valorLongitud = nuevaLongitud;
        latitud.setText(getString(R.string.contacto_latitud_valor, Double.toString(nuevaLatitud)));
        longitud.setText(getString(R.string.contacto_longitud_valor, Double.toString(nuevaLongitud)));
    }

    @Override
    protected void onSaveInstanceState(Bundle estadoGuardado) {
        super.onSaveInstanceState(estadoGuardado);
        if (videoUri != null) {
            estadoGuardado.putString("videoUri", videoUri.toString());
        }
        if (valorLatitud != null && valorLongitud != null) {
            estadoGuardado.putDouble("latitud", valorLatitud);
            estadoGuardado.putDouble("longitud", valorLongitud);
        }
    }

    private void guardarContacto() {
        estado.setText("");
        reintentar.setVisibility(View.GONE);
        String nombreActual = nombre.getText().toString().trim();
        String telefonoActual = telefono.getText().toString().trim();

        if (nombreActual.isEmpty()) {
            nombre.setError(getString(R.string.contacto_falta_nombre));
            nombre.requestFocus();
            return;
        }
        if (telefonoActual.isEmpty()) {
            telefono.setError(getString(R.string.contacto_falta_telefono));
            telefono.requestFocus();
            return;
        }
        if (valorLatitud == null || valorLongitud == null) {
            estado.setText(R.string.contacto_falta_ubicacion);
            return;
        }
        if (videoUri == null) {
            estado.setText(R.string.contacto_video_requerido);
            return;
        }

        Uri uri = videoUri;
        DatosContacto datos = new DatosContacto(nombreActual, telefonoActual, valorLatitud, valorLongitud);
        mostrarCarga(true);
        estado.setText(R.string.contacto_guardando);

        archivos.execute(() -> {
            try {
                File archivo = copiarVideo(uri);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) {
                        archivo.delete();
                    } else {
                        enviar(datos, archivo);
                    }
                });
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && !isFinishing()) {
                        mostrarCarga(false);
                        estado.setText(error instanceof VideoInvalido
                                ? error.getMessage() : getString(R.string.contacto_error_lectura));
                    }
                });
            }
        });
    }

    private File copiarVideo(Uri uri) throws IOException {
        File archivo = File.createTempFile("contacto-", ".mp4", getCacheDir());
        boolean completo = false;
        try {
            try (InputStream entrada = getContentResolver().openInputStream(uri);
                 FileOutputStream salida = new FileOutputStream(archivo)) {
                if (entrada == null) {
                    throw new IOException("No se pudo abrir el video.");
                }
                byte[] bloque = new byte[8192];
                long total = 0;
                int leidos;
                while ((leidos = entrada.read(bloque)) != -1) {
                    total += leidos;
                    if (total > MAX_VIDEO_BYTES) {
                        throw new VideoInvalido(getString(R.string.contacto_video_grande));
                    }
                    salida.write(bloque, 0, leidos);
                }
                if (total == 0) {
                    throw new VideoInvalido(getString(R.string.contacto_video_vacio));
                }
            }
            completo = true;
            return archivo;
        } finally {
            if (!completo) {
                archivo.delete();
            }
        }
    }

    private void enviar(DatosContacto datos, File archivo) {
        RequestBody contenido = RequestBody.create(archivo, MP4);
        MultipartBody.Part videoParte = MultipartBody.Part.createFormData(
                "archivo", "contacto.mp4", contenido);

        peticionActiva = ApiClient.servicio().guardar(
                RequestBody.create(datos.getNombre(), TEXTO),
                RequestBody.create(datos.getTelefono(), TEXTO),
                RequestBody.create(Double.toString(datos.getLatitud()), TEXTO),
                RequestBody.create(Double.toString(datos.getLongitud()), TEXTO),
                videoParte);

        peticionActiva.enqueue(new Callback<Contacto>() {
            @Override
            public void onResponse(Call<Contacto> call, Response<Contacto> respuesta) {
                archivo.delete();
                if (isDestroyed() || isFinishing()) {
                    if (respuesta.errorBody() != null) {
                        respuesta.errorBody().close();
                    }
                    return;
                }
                peticionActiva = null;
                mostrarCarga(false);
                Contacto creado = respuesta.body();
                if (respuesta.isSuccessful() && creado != null) {
                    nombre.setText("");
                    telefono.setText("");
                    usarVideo(null);
                    estado.setText(getString(R.string.contacto_guardado, creado.getId()));
                } else if (respuesta.isSuccessful()) {
                    estado.setText(R.string.contacto_respuesta_vacia);
                } else {
                    estado.setText(ApiClient.mensajeError(respuesta));
                }
            }

            @Override
            public void onFailure(Call<Contacto> call, Throwable error) {
                archivo.delete();
                if (call.isCanceled() || isDestroyed() || isFinishing()) {
                    return;
                }
                peticionActiva = null;
                mostrarCarga(false);
                if (error instanceof IOException) {
                    estado.setText(R.string.contacto_error_conexion);
                    reintentar.setVisibility(View.VISIBLE);
                } else {
                    estado.setText(R.string.contacto_respuesta_vacia);
                }
            }
        });
    }

    private void mostrarCarga(boolean cargando) {
        progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
        guardar.setEnabled(!cargando);
        prueba.setEnabled(!cargando);
        nombre.setEnabled(!cargando);
        telefono.setEnabled(!cargando);
    }

    @Override
    protected void onDestroy() {
        if (peticionActiva != null) {
            peticionActiva.cancel();
        }
        archivos.shutdown();
        super.onDestroy();
    }

    private static final class VideoInvalido extends IOException {
        VideoInvalido(String mensaje) {
            super(mensaje);
        }
    }
}
