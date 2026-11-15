package com.grupo3.examen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    public static final String BASE_URL =
            "https://api-pm1-examen2parcial-grupo3-337965348211.europe-west1.run.app/";

    private static final ApiService SERVICIO = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(90, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService.class);

    private ApiClient() {}

    public static ApiService servicio() {
        return SERVICIO;
    }

    public static String mensajeError(Response<?> respuesta) {
        try (ResponseBody cuerpo = respuesta.errorBody()) {
            if (cuerpo != null) {
                JsonElement datos = JsonParser.parseString(cuerpo.string());
                if (datos.isJsonObject()) {
                    String mensaje = mensajeJson(datos.getAsJsonObject());
                    if (!mensaje.isEmpty()) {
                        return mensaje;
                    }
                }
            }
        } catch (IOException | JsonParseException ignored) {
            // La API o su infraestructura pueden devolver una página en vez de JSON.
        }
        return "El servidor devolvió un error (HTTP " + respuesta.code() + ").";
    }

    private static String mensajeJson(JsonObject datos) {
        String mensaje = texto(datos.get("mensaje"));
        if (!mensaje.isEmpty()) {
            return mensaje;
        }
        JsonElement errores = datos.get("errors");
        if (errores != null && errores.isJsonObject()) {
            for (Map.Entry<String, JsonElement> campo : errores.getAsJsonObject().entrySet()) {
                if (campo.getValue().isJsonArray()) {
                    JsonArray mensajes = campo.getValue().getAsJsonArray();
                    for (JsonElement valor : mensajes) {
                        mensaje = texto(valor);
                        if (!mensaje.isEmpty()) {
                            return mensaje;
                        }
                    }
                }
            }
        }
        return texto(datos.get("title"));
    }

    private static String texto(JsonElement valor) {
        return valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isString()
                ? valor.getAsString().trim() : "";
    }
}
