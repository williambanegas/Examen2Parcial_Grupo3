package com.grupo3.examen;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Pantalla de Reproducción de Video (Parte 4 del Examen).
 * Cumple con el requisito: "En la pantalla 2, reproducir el video descargado con VideoView o Media3 ExoPlayer."
 *
 * =========================================================================================
 * GUÍA DE INTEGRACIÓN PARA EL INTEGRANTE 5 (Pantalla 2 - Contactos Guardados):
 * =========================================================================================
 * Cuando el usuario toque un contacto de la lista o elija la opción "Reproducir Video",
 * simplemente invoca esta línea:
 *
 *     VerVideoActivity.reproducirContacto(context, contacto.getId(), contacto.getNombre());
 *
 * Esto se encargará automáticamente de:
 *  1. Abrir esta actividad con MaterialToolbar y botón de regreso.
 *  2. Conectarse al endpoint oficial: GET /api/contactos/{id}/archivo
 *  3. Mostrar ProgressBar mientras se realiza el búfer de red.
 *  4. Adjuntar controles MediaController (Play, Pausa, barra de tiempo).
 *  5. Manejar errores de conectividad con botón de reintento.
 * =========================================================================================
 */
public class VerVideoActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_URI = "extra_video_uri";
    public static final String EXTRA_TITULO = "extra_titulo";
    private static final String KEY_POSICION = "key_posicion";

    private VideoView videoView;
    private View layoutCargando;
    private View layoutError;
    private TextView textoError;
    private Uri uriVideo;
    private int posicionActual = 0;

    /**
     * Inicia la reproducción del video de un contacto almacenado en la API REST.
     * Utilizado para la Pantalla 2 (Lista de Contactos del Integrante 5).
     */
    public static void reproducirContacto(Context context, int contactoId, String nombreContacto) {
        String url = ApiClient.BASE_URL + "api/contactos/" + contactoId + "/archivo";
        Intent intent = new Intent(context, VerVideoActivity.class);
        intent.putExtra(EXTRA_VIDEO_URI, url);
        if (nombreContacto != null && !nombreContacto.trim().isEmpty()) {
            intent.putExtra(EXTRA_TITULO, nombreContacto.trim());
        }
        context.startActivity(intent);
    }

    /**
     * Inicia la reproducción de un video local a través de su Uri.
     * Utilizado para la vista previa en la Pantalla 1 tras grabar o seleccionar un video.
     */
    public static void reproducirUri(Context context, Uri uri, String titulo) {
        Intent intent = new Intent(context, VerVideoActivity.class);
        intent.putExtra(EXTRA_VIDEO_URI, uri.toString());
        if (titulo != null && !titulo.trim().isEmpty()) {
            intent.putExtra(EXTRA_TITULO, titulo.trim());
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_ver_video);

        View raiz = findViewById(R.id.verVideoRaiz);
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets margenes = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            vista.setPadding(margenes.left, margenes.top, margenes.right, margenes.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbarVerVideo);
        videoView = findViewById(R.id.videoView);
        layoutCargando = findViewById(R.id.cargandoVideoLayout);
        layoutError = findViewById(R.id.errorVideoLayout);
        textoError = findViewById(R.id.textoErrorVideo);
        Button botonReintentar = findViewById(R.id.botonReintentarVideo);

        String titulo = getIntent().getStringExtra(EXTRA_TITULO);
        if (titulo != null && !titulo.isEmpty()) {
            toolbar.setTitle(titulo);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        String uriString = getIntent().getStringExtra(EXTRA_VIDEO_URI);
        if (uriString != null && !uriString.trim().isEmpty()) {
            uriVideo = Uri.parse(uriString);
        }

        if (savedInstanceState != null) {
            posicionActual = savedInstanceState.getInt(KEY_POSICION, 0);
        }

        configurarReproductor();

        botonReintentar.setOnClickListener(v -> cargarVideo());
        cargarVideo();
    }

    private void configurarReproductor() {
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        videoView.setOnPreparedListener(mp -> {
            layoutCargando.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);
            if (posicionActual > 0) {
                videoView.seekTo(posicionActual);
            }
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            layoutCargando.setVisibility(View.GONE);
            layoutError.setVisibility(View.VISIBLE);
            textoError.setText(R.string.ver_video_error);
            return true;
        });

        videoView.setOnCompletionListener(mp -> posicionActual = 0);
    }

    private void cargarVideo() {
        if (uriVideo == null) {
            layoutCargando.setVisibility(View.GONE);
            layoutError.setVisibility(View.VISIBLE);
            textoError.setText(R.string.ver_video_error);
            return;
        }

        layoutCargando.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        videoView.setVideoURI(uriVideo);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (videoView != null) {
            outState.putInt(KEY_POSICION, videoView.getCurrentPosition());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            posicionActual = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (videoView != null) {
            videoView.stopPlayback();
        }
        super.onDestroy();
    }
}
