package com.grupo3.examen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TelefonoContacto {
    private static final Pattern FORMATO_NACIONAL =
            Pattern.compile("(?:\\+504[ -]?)?([0-9]{4})[ -]?([0-9]{4})");

    private TelefonoContacto() {}

    static String normalizar(String telefono) {
        Matcher coincidencia = FORMATO_NACIONAL.matcher(telefono.trim());
        if (!coincidencia.matches()) {
            return null;
        }
        return coincidencia.group(1) + coincidencia.group(2);
    }
}
