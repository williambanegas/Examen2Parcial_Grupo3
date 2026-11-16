package com.grupo3.examen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

import android.text.InputType;
import android.text.method.TextKeyListener;

public class MainActivity extends AppCompatActivity {
    private static final MediaType TEXTO = MediaType.get("text/plain; charset=utf-8");
    private static final MediaType MP4 = MediaType.get("video/mp4");
    private static final long MAX_VIDEO_BYTES = 20_000_000L;
    private static final long MAX_DURACION_VIDEO_MS = 20_500L; // 20s con tolerancia por codec

    private final ExecutorService archivos = Executors.newSingleThreadExecutor();

    private Uri videoUri;
    private Uri uriTemporalVideo;
    private Double valorLatitud;
    private Double valorLongitud;
    private Call<Contacto> peticionActiva;
    private FusedLocationProviderClient fusedLocationClient;

    // Contrato con límite de 20 segundos para la cámara del sistema
    private static class CaptureVideo20s extends ActivityResultContracts.CaptureVideo {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull Uri input) {
            Intent intent = super.createIntent(context, input);
            intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20);
            return intent;
        }
    }

    private final ActivityResultLauncher<String[]> solicitarPermisos = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), resultados -> {
                boolean camara = Boolean.TRUE.equals(resultados.get(Manifest.permission.CAMERA));
                boolean audio = Boolean.TRUE.equals(resultados.get(Manifest.permission.RECORD_AUDIO));
                if (camara && audio) {
                    iniciarGrabacionVideo();
                } else {
                    mostrarDialogoPermisos();
                }
            });

    private final ActivityResultLauncher<Uri> capturarVideo = registerForActivityResult(
            new CaptureVideo20s(), exitoso -> {
                if (Boolean.TRUE.equals(exitoso) && uriTemporalVideo != null) {
                    validarYUsarVideo(uriTemporalVideo);
                }
            });


    private final ActivityResultLauncher<String[]> solicitarPermisosUbicacion = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), resultados -> {
                boolean fine = Boolean.TRUE.equals(resultados.get(Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarse = Boolean.TRUE.equals(resultados.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fine || coarse) {
                    obtenerUbicacionActual();
                } else {
                    Toast.makeText(this, "Se requiere permiso de ubicación para obtener coordenadas.", Toast.LENGTH_SHORT).show();
                }
            });

    private void obtenerUbicacionActual() {
        boolean tieneFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean tieneCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (tieneFine || tieneCoarse) {
            try {
                fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                    if (location != null) {
                        usarUbicacion(location.getLatitude(), location.getLongitude());
                    }
                });
                LocationRequest locationRequest = new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, 2000)
                        .setMinUpdateIntervalMillis(1000)
                        .build();

                fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult locationResult) {
                        if (locationResult.getLastLocation() != null) {
                            Location loc = locationResult.getLastLocation();
                            usarUbicacion(loc.getLatitude(), loc.getLongitude());
                            fusedLocationClient.removeLocationUpdates(this);
                        }
                    }
                }, getMainLooper());
            } catch (SecurityException ignored) {
            }
        } else {
            solicitarPermisosUbicacion.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private EditText nombre;
    private EditText telefono;
    private TextView latitud;
    private TextView longitud;
    private Button obtenerUbicacion;
    private TextView video;
    private TextView estado;
    private Button tomarVideo;
    private Button reproducirVideoLocal;
    private ImageView iconoPlayVideo;
    private Button guardar;
    private Button contactosSalvados;
    private Button reintentar;
    private ProgressBar progreso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.contacto_encabezado), true);
        View raiz = findViewById(R.id.formularioRaiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets margenes = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            vista.setPadding(margenes.left, margenes.top, margenes.right, margenes.bottom);
            return insets;
        });

        nombre = findViewById(R.id.nombreContacto);
        telefono = findViewById(R.id.telefonoContacto);
        telefono.setKeyListener(TextKeyListener.getInstance());
        telefono.setRawInputType(InputType.TYPE_CLASS_PHONE);
        latitud = findViewById(R.id.latitudContacto);
        longitud = findViewById(R.id.longitudContacto);
        obtenerUbicacion = findViewById(R.id.obtenerUbicacionContacto);
        video = findViewById(R.id.videoContacto);
        estado = findViewById(R.id.estadoGuardado);
        tomarVideo = findViewById(R.id.tomarVideo);
        reproducirVideoLocal = findViewById(R.id.reproducirVideoLocal);
        iconoPlayVideo = findViewById(R.id.iconoPlayVideo);
        View contenedorIconoVideo = findViewById(R.id.contenedorIconoVideo);
        guardar = findViewById(R.id.salvarContacto);
        contactosSalvados = findViewById(R.id.contactosSalvados);
        reintentar = findViewById(R.id.reintentarGuardado);
        progreso = findViewById(R.id.progresoGuardado);

        tomarVideo.setOnClickListener(vista -> verificarPermisosYGrabar());
        contenedorIconoVideo.setOnClickListener(vista -> reproducirOIniciarVideo());
        reproducirVideoLocal.setOnClickListener(vista -> reproducirVideo());

        guardar.setOnClickListener(vista -> guardarContacto());
        obtenerUbicacion.setOnClickListener(vista -> obtenerUbicacionActual());
        contactosSalvados.setOnClickListener(vista -> abrirContactosSalvados());
        reintentar.setOnClickListener(vista -> guardarContacto());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString("videoUri");
            if (uri != null) {
                usarVideo(Uri.parse(uri));
            }
            String uriTemp = savedInstanceState.getString("uriTemporalVideo");
            if (uriTemp != null) {
                uriTemporalVideo = Uri.parse(uriTemp);
            }
            if (savedInstanceState.containsKey("latitud") && savedInstanceState.containsKey("longitud")) {
                usarUbicacion(savedInstanceState.getDouble("latitud"),
                        savedInstanceState.getDouble("longitud"));
            }
        }
        if (savedInstanceState == null) {
            obtenerUbicacionActual();
        }
    }

    /**
     * Navegación a la Pantalla 2 (Lista de Contactos Salvados).
     *
     * GUÍA PARA EL INTEGRANTE 5:
     * Cuando crees la Activity de la Pantalla 2 (por ejemplo, ListaContactosActivity),
     * reemplaza este Toast por el Intent correspondiente:
     *     Intent intent = new Intent(this, ListaContactosActivity.class);
     *     startActivity(intent);
     */
    private void abrirContactosSalvados() {
        Intent intent = new Intent(this, ContactosActivity.class);
        startActivity(intent);
    }

    private void verificarPermisosYGrabar() {
        boolean tieneCamara = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean tieneAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        if (tieneCamara && tieneAudio) {
            iniciarGrabacionVideo();
        } else {
            solicitarPermisos.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            });
        }
    }

    private void iniciarGrabacionVideo() {
        try {
            File carpetaVideos = new File(getCacheDir(), "videos");
            if (!carpetaVideos.exists() && !carpetaVideos.mkdirs()) {
                throw new IOException("No se pudo crear directorio temporal.");
            }
            File archivoTemporal = File.createTempFile("clip_", ".mp4", carpetaVideos);
            uriTemporalVideo = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", archivoTemporal);
            capturarVideo.launch(uriTemporalVideo);
        } catch (IOException e) {
            estado.setText(R.string.contacto_error_lectura);
        }
    }

    private void validarYUsarVideo(Uri uri) {
        long duracionMs = 0;
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(this, uri);
            String duracionStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duracionStr != null) {
                duracionMs = Long.parseLong(duracionStr);
            }
        } catch (Exception ignored) {
            // Continuar con la validación de tamaño en guardar
        }

        if (duracionMs > MAX_DURACION_VIDEO_MS) {
            try {
                getContentResolver().delete(uri, null, null);
            } catch (Exception ignored) {
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.contacto_permisos_video_titulo)
                    .setMessage(R.string.contacto_video_duracion_excedida)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            usarVideo(null);
            return;
        }

        usarVideo(uri);
    }

    private void reproducirOIniciarVideo() {
        if (videoUri != null) {
            reproducirVideo();
        } else {
            verificarPermisosYGrabar();
        }
    }

    private void reproducirVideo() {
        if (videoUri == null) {
            return;
        }
        String titulo = nombre.getText().toString().trim();
        if (titulo.isEmpty()) {
            titulo = getString(R.string.ver_video_titulo);
        }
        VerVideoActivity.reproducirUri(this, videoUri, titulo);
    }

    private void mostrarDialogoPermisos() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.contacto_permisos_video_titulo)
                .setMessage(R.string.contacto_permisos_video_mensaje)
                .setPositiveButton(R.string.contacto_permisos_ajustes, (dialogo, que) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.contacto_permisos_cancelar, null)
                .show();
    }

    // Llamar desde el resultado exitoso de la grabación del integrante 4.
    public void usarVideo(Uri uri) {
        videoUri = uri;
        video.setText(uri == null ? R.string.contacto_sin_video : R.string.contacto_video_listo);
        boolean hayVideo = (uri != null);
        if (iconoPlayVideo != null) {
            iconoPlayVideo.setVisibility(hayVideo ? View.VISIBLE : View.GONE);
        }
        if (reproducirVideoLocal != null) {
            reproducirVideoLocal.setVisibility(hayVideo ? View.VISIBLE : View.GONE);
        }
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
            obtenerUbicacion.setVisibility(View.VISIBLE);
            return;
        }
        valorLatitud = nuevaLatitud;
        valorLongitud = nuevaLongitud;
        latitud.setText(getString(R.string.contacto_latitud_valor, Double.toString(nuevaLatitud)));
        longitud.setText(getString(R.string.contacto_longitud_valor, Double.toString(nuevaLongitud)));
        obtenerUbicacion.setVisibility(View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle estadoGuardado) {
        super.onSaveInstanceState(estadoGuardado);
        if (videoUri != null) {
            estadoGuardado.putString("videoUri", videoUri.toString());
        }
        if (uriTemporalVideo != null) {
            estadoGuardado.putString("uriTemporalVideo", uriTemporalVideo.toString());
        }
        if (valorLatitud != null && valorLongitud != null) {
            estadoGuardado.putDouble("latitud", valorLatitud);
            estadoGuardado.putDouble("longitud", valorLongitud);
        }
    }

    private void guardarContacto() {
        if (!guardar.isEnabled()) {
            return;
        }
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
        if (!telefonoActual.matches("(?:\\+504[ -]?)?[0-9]{4}[ -]?[0-9]{4}")) {
            telefono.setError(getString(R.string.contacto_telefono_invalido));
            telefono.requestFocus();
            return;
        }

        String telefonoNormalizado = telefonoActual
                .replaceFirst("^\\+504", "")
                .replace(" ", "")
                .replace("-", "");

        if (valorLatitud == null || valorLongitud == null) {
            estado.setText(R.string.contacto_falta_ubicacion);
            return;
        }
        if (videoUri == null) {
            estado.setText(R.string.contacto_video_requerido);
            return;
        }

        Uri uri = videoUri;
        DatosContacto datos = new DatosContacto(
                nombreActual, telefonoNormalizado, valorLatitud, valorLongitud);
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
                if (respuesta.isSuccessful() && creado != null && creado.getId() > 0) {
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
        obtenerUbicacion.setEnabled(!cargando);
        reintentar.setEnabled(!cargando);
        contactosSalvados.setEnabled(!cargando);
        tomarVideo.setEnabled(!cargando);
        if (reproducirVideoLocal != null) {
            reproducirVideoLocal.setEnabled(!cargando);
        }
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
