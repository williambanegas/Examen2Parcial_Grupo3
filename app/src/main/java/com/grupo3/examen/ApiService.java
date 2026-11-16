package com.grupo3.examen;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;

public interface ApiService {
    @Multipart
    @POST("api/contactos")
    Call<Contacto> guardar(
            @Part("nombre") RequestBody nombre,
            @Part("telefono") RequestBody telefono,
            @Part("latitud") RequestBody latitud,
            @Part("longitud") RequestBody longitud,
            @Part MultipartBody.Part archivo
    );

    @GET("api/contactos")
    Call<List<Contacto>> listar(@Query("buscar") String buscar);

    @Streaming
    @GET("api/contactos/{id}/archivo")
    Call<ResponseBody> descargarArchivo(@Path("id") int id);

    @PUT("api/contactos/{id}")
    Call<Contacto> actualizar(@Path("id") int id, @Body DatosContacto datos);

    @DELETE("api/contactos/{id}")
    Call<Void> eliminar(@Path("id") int id);
}
