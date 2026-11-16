package com.grupo3.examen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class ContactosContratoTest {
    private ApiService servicio(int codigo, String cuerpo, AtomicReference<Request> solicitud) {
        OkHttpClient cliente = new OkHttpClient.Builder().addInterceptor(cadena -> {
            solicitud.set(cadena.request());
            return new okhttp3.Response.Builder().request(cadena.request())
                    .protocol(Protocol.HTTP_1_1).code(codigo).message("Prueba")
                    .body(ResponseBody.create(cuerpo, MediaType.get("application/json"))).build();
        }).build();
        return new Retrofit.Builder().baseUrl("https://prueba.invalid/")
                .client(cliente).addConverterFactory(GsonConverterFactory.create())
                .build().create(ApiService.class);
    }

    @Test
    public void busquedaEnviaTextoYConservaCerosDelTelefono() throws Exception {
        AtomicReference<Request> solicitud = new AtomicReference<>();
        ApiService api = servicio(200,
                "[{\"id\":7,\"nombre\":\"Ana\",\"telefono\":\"00123\",\"latitud\":14.48,\"longitud\":-88.78}]",
                solicitud);
        Response<java.util.List<Contacto>> respuesta = api.listar("Ana + 001").execute();
        assertEquals("Ana + 001", solicitud.get().url().queryParameter("buscar"));
        assertEquals("/api/contactos", solicitud.get().url().encodedPath());
        assertEquals("00123", respuesta.body().get(0).getTelefono());
    }

    @Test
    public void actualizarEnviaSoloCuatroCamposYCoordenadasOriginales() throws Exception {
        Contacto original = new Gson().fromJson(
                "{\"id\":7,\"latitud\":14.1234567,\"longitud\":-88.7654321,\"archivo\":\"https://video.invalid/a.mp4\"}",
                Contacto.class);
        AtomicReference<Request> solicitud = new AtomicReference<>();
        ApiService api = servicio(200, "{\"id\":7,\"nombre\":\"Ana\",\"telefono\":\"001\"}", solicitud);
        api.actualizar(original.getId(), new DatosContacto("Ana", "001",
                original.getLatitud(), original.getLongitud())).execute();
        Request peticion = solicitud.get();
        Buffer buffer = new Buffer();
        peticion.body().writeTo(buffer);
        JsonObject json = new Gson().fromJson(buffer.readUtf8(), JsonObject.class);
        assertEquals("PUT", peticion.method());
        assertEquals("/api/contactos/7", peticion.url().encodedPath());
        assertTrue(peticion.body().contentType().toString().startsWith("application/json"));
        assertEquals(4, json.size());
        assertEquals("Ana", json.get("nombre").getAsString());
        assertEquals("001", json.get("telefono").getAsString());
        assertEquals(original.getLatitud(), json.get("latitud").getAsDouble(), 0);
        assertEquals(original.getLongitud(), json.get("longitud").getAsDouble(), 0);
        assertFalse(json.has("archivo"));
    }

    @Test
    public void eliminarAcepta204SinCuerpo() throws Exception {
        AtomicReference<Request> solicitud = new AtomicReference<>();
        Response<Void> respuesta = servicio(204, "", solicitud).eliminar(7).execute();
        assertTrue(respuesta.isSuccessful());
        assertNull(respuesta.body());
        assertEquals("DELETE", solicitud.get().method());
        assertEquals("/api/contactos/7", solicitud.get().url().encodedPath());
    }

    @Test
    public void listaVaciaYErroresNoSeConfunden() throws Exception {
        AtomicReference<Request> solicitud = new AtomicReference<>();
        assertTrue(servicio(200, "[]", solicitud).listar(null).execute().body().isEmpty());
        assertNull(solicitud.get().url().queryParameter("buscar"));
        Response<Void> respuesta = servicio(404, "{\"mensaje\":\"No existe\"}", solicitud).eliminar(7).execute();
        assertFalse(respuesta.isSuccessful());
        assertEquals("No existe", ApiClient.mensajeError(respuesta));
    }
}
