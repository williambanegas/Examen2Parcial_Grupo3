package com.grupo3.examen;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class NavegacionContactosTest {
    @Test
    public void formularioYContactosRespondenALosToques() {
        android.app.UiAutomation automation = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getUiAutomation();
        automation.grantRuntimePermission("com.grupo3.examen", android.Manifest.permission.ACCESS_COARSE_LOCATION);
        automation.grantRuntimePermission("com.grupo3.examen", android.Manifest.permission.ACCESS_FINE_LOCATION);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.salvarContacto)).perform(click());
            onView(withId(R.id.nombreContacto)).check(matches(hasErrorText(
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                            .getTargetContext().getString(R.string.contacto_falta_nombre))));
            androidx.test.espresso.Espresso.closeSoftKeyboard();
            onView(withId(R.id.contactosSalvados)).perform(click());
            onView(withId(R.id.cg_busqueda)).check(matches(isEnabled()));
            onView(withId(R.id.cg_buscar)).check(matches(isEnabled()));
            onView(withId(R.id.cg_limpiar)).check(matches(isEnabled()));
            onView(withId(R.id.cg_reintentar)).check(matches(isEnabled()));
            onView(withId(R.id.cg_volver)).perform(click());
            onView(withId(R.id.contactosSalvados)).check(matches(isDisplayed()));
        }
    }
}
