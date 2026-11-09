package com.bcw.classeviva.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import com.bcw.classeviva.BCWApplication
import com.bcw.classeviva.MainActivity
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * L'app vera (MainActivity) sulla JVM con Robolectric: introduzione, demo, schede e Orario in tutte
 * le modalità. Ogni passaggio salva uno screenshot in `app/build/screenshots` per controllarlo.
 * Le animazioni infinite (forme che ruotano, indicatori) non lasciano mai la UI "ferma": il tempo
 * avanza a mano.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppUiTest {
    /** Nell'app WorkManager parte con androidx.startup, che Robolectric non avvia. */
    @get:Rule(order = 0)
    val workManager = object : ExternalResource() {
        override fun before() {
            // Resta inizializzato tra una prova e l'altra (stessa JVM).
            if (!WorkManager.isInitialized()) WorkManager.initialize(ApplicationProvider.getApplicationContext(), Configuration.Builder().build())
        }
    }

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val model get() = (rule.activity.application as BCWApplication).model

    private fun settle(ms: Long = 1500) {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    /**
     * Disegna tutte le finestre (l'attività, i fogli, i menu) in un'immagine, nella loro posizione.
     * La cattura di Compose aspetterebbe un nuovo fotogramma, che con il tempo fermo non arriva.
     */
    @Suppress("UNCHECKED_CAST")
    private fun shot(name: String, wait: Long = 800) {
        if (wait > 0) settle(wait)
        val decor = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rule.runOnUiThread {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)!!
            val names = global.javaClass.getMethod("getViewRootNames").invoke(global) as Array<String>
            for (n in names) {
                val view = global.javaClass.getMethod("getRootView", String::class.java).invoke(global, n) as? View ?: continue
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                canvas.save()
                canvas.translate(location[0].toFloat(), location[1].toFloat())
                view.draw(canvas)
                canvas.restore()
            }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(value: String): SemanticsNodeInteraction = rule.onAllNodesWithText(value).onFirst()

    /** Aspetta una condizione (dati caricati in background). */
    private fun waitFor(what: String, seconds: Int = 20, condition: () -> Boolean) {
        repeat(seconds * 10) {
            if (condition()) return
            Thread.sleep(50)
            settle(100)
        }
        error("Non è successo: $what")
    }

    /** Aspetta che compaia un testo (la generazione dell'orario, un foglio che si apre). */
    private fun waitForText(value: String, seconds: Int = 20) {
        repeat(seconds * 10) {
            if (rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
            settle(100)
        }
        error("«$value» non è comparso")
    }

    /** Apre il menu del pulsante +: le voci entrano nell'albero dell'accessibilità quando l'animazione le posiziona. */
    private fun openFabMenu() {
        clickDescription("Aggiungi o modifica")
        waitForText("Genera dalle lezioni")
    }

    /** Tocca un pulsante con questa descrizione, aspettando che ci sia. */
    private fun clickDescription(value: String) {
        waitFor("pulsante «$value»") { rule.onAllNodesWithContentDescription(value).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithContentDescription(value).onFirst().performClick()
        settle()
    }

    private fun click(value: String) {
        waitForText(value)
        text(value).performClick()
        settle()
    }

    @Test
    fun onboardingSteps() {
        settle(3000)
        shot("onboarding-01-welcome")
        click("Inizia")
        shot("onboarding-02-colors")
        waitForText("Verde")
        text("Verde").performClick()
        settle(350)
        shot("onboarding-02a-reveal", wait = 0)
        settle(1500)
        shot("onboarding-02b-colors-chosen")
        click("Continua")
        shot("onboarding-03-appearance")
        click("Continua")
        shot("onboarding-04-feel")
        click("Continua")
        shot("onboarding-05-dashboard")
        click("Calendario")
        click("Continua")
        shot("onboarding-06-features")
        click("Continua")
        shot("onboarding-07-classeviva")
        click("Prova la demo")
        settle(3000)
        shot("onboarding-08-bcw")
        click("No, grazie")
        shot("onboarding-09-timetable")
        click("Crea l'anteprima")
        waitForText("Applica l'orario")
        shot("onboarding-10-preview")
        click("Applica l'orario")
        shot("onboarding-11-done")
        click("Apri BCW")
        settle(2000)
        shot("onboarding-12-app")
        check(!model.timetable.isEmpty) { "L'orario dell'introduzione non è stato applicato" }
    }

    /** La schermata di accesso riapre l'introduzione (3.3.1, come la versione web). */
    @Test
    fun loginIntroLink() {
        settle(3000)
        click("Salta introduzione")
        settle(1200)
        shot("login-01")
        check(rule.onAllNodesWithText("Rivedi l'introduzione").fetchSemanticsNodes().isNotEmpty()) { "Manca «Rivedi l'introduzione» nell'accesso" }
        click("Rivedi l'introduzione")
        settle(2000)
        shot("login-02-intro")
        check(rule.onAllNodesWithText("Inizia").fetchSemanticsNodes().isNotEmpty()) { "L'introduzione non si è riaperta" }
        click("Salta introduzione")
        settle(1500)
        check(rule.onAllNodesWithText("Rivedi l'introduzione").fetchSemanticsNodes().isNotEmpty()) { "Dopo l'introduzione non si torna all'accesso" }
    }

    /** Impostazioni > Generale > Su GitHub: tre versioni, backend e autore in gruppi separati (3.3.1). */
    @Test
    fun settingsGithubGroups() {
        startDemo()
        rule.onNode(hasText("Tu") and hasClickAction()).performClick()
        settle()
        clickDescription("Impostazioni")
        settle()
        click("Generale")
        waitForText("Sicurezza")
        settle()
        rule.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(4)
        settle()
        shot("settings-github")
        for (title in listOf("BCW", "BCW-web", "BCW-android", "BCW-api", "kubos5")) {
            check(rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()) { "Manca «$title» in Su GitHub" }
        }
    }

    /** Salta l'introduzione e apre la demo. */
    private fun startDemo() {
        settle(3000)
        click("Salta introduzione")
        settle(1000)
        click("Prova la demo")
        waitFor("dati della demo") { model.grades.isNotEmpty() && model.agenda.isNotEmpty() && model.notices.isNotEmpty() }
        settle(1000)
    }

    @Test
    fun demoTabs() {
        startDemo()
        shot("demo-01-dashboard")
        // Il giorno segue il dito: a metà trascinamento è spostato, lasciando cambia.
        val list = rule.onAllNodes(hasScrollToIndexAction()).onFirst()
        list.performTouchInput {
            down(center)
            moveBy(Offset(-60f, 0f))
            moveBy(Offset(-240f, 0f))
        }
        settle(100)
        shot("demo-01b-swipe-held", wait = 0)
        list.performTouchInput {
            moveBy(Offset(-300f, 0f))
            up()
        }
        settle(150)
        shot("demo-01c-swipe-release", wait = 0)
        waitForText("Martedì")
        shot("demo-01d-swipe-done")
        list.performTouchInput {
            down(center)
            moveBy(Offset(60f, 0f))
            moveBy(Offset(60f, 0f))
            up()
        }
        settle()
        check(rule.onAllNodesWithText("Martedì").fetchSemanticsNodes().isNotEmpty()) { "Uno scorrimento corto non deve cambiare giorno" }
        rule.onNode(hasText("Voti") and hasClickAction()).performClick()
        settle()
        shot("demo-02-grades")
        click("E se prendessi…")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(2)
        settle()
        shot("demo-02c-whatif")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(0)
        settle()
        rule.onNode(hasText("Tu") and hasClickAction()).performClick()
        settle(120)
        shot("demo-03-nav-transition", wait = 0)
        settle()
        shot("demo-03-you")
        // Un nuovo tocco su Tu non deve far richiudere le pagine aperte dopo.
        rule.onNode(hasText("Tu") and hasClickAction()).performClick()
        settle()
        rule.onAllNodesWithText("comunicazioni da leggere", substring = true).onFirst().performClick()
        waitForText("Cerca nelle comunicazioni")
        settle(1500)
        check(rule.onAllNodesWithText("Cerca nelle comunicazioni").fetchSemanticsNodes().isNotEmpty()) { "La bacheca si è richiusa" }
        shot("demo-03b-noticeboard")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        settle()
        click("Ritardi")
        waitForText("Assenze e ritardi")
        settle(1500)
        check(rule.onAllNodesWithText("Assenze e ritardi").fetchSemanticsNodes().isNotEmpty()) { "Assenze e ritardi si è richiusa" }
        shot("demo-03e-absences")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        settle()
        clickDescription("Impostazioni")
        shot("demo-03c-settings")
        click("Aspetto")
        shot("demo-03d-appearance")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        settle()
        rule.activity.onBackPressedDispatcher.onBackPressed()
        settle()
        rule.onNode(hasText("Orario") and hasClickAction()).performClick()
        settle()
        shot("demo-04-timetable-empty")
    }

    @Test
    fun timetableFlows() {
        startDemo()
        rule.onNode(hasText("Orario") and hasClickAction()).performClick()
        settle()
        // Genera dalle lezioni con il menu del pulsante +.
        openFabMenu()
        shot("tt-01-fab-menu")
        click("Genera dalle lezioni")
        shot("tt-02-generator")
        click("Crea l'anteprima")
        waitForText("Anteprima dell'orario")
        shot("tt-03-draft")
        click("Applica")
        settle()
        shot("tt-04-list")
        check(!model.timetable.isEmpty) { "La bozza non è stata applicata" }
        // Vista Giorno e modifica.
        clickDescription("Vista Giorno")
        shot("tt-05-day")
        openFabMenu()
        shot("tt-05b-day-fab")
        click("Modifica orario")
        shot("tt-06-edit")
        clickDescription("Sposta gli orari")
        waitForText("TUTTI I GIORNI")
        shot("tt-07-shift")
        clickDescription("Chiudi")
        click("Fine")
        clickDescription("Vista Lista")
        // Dettaglio ed editor di una lezione.
        val lesson = model.timetable.timetable.entries.first { !it.isBreak }
        val day = (0L..6L).map { java.time.LocalDate.now().plusDays(it) }.first { it.dayOfWeek.value == lesson.weekday }
        rule.runOnUiThread { model.open(android.net.Uri.parse("bcw://orario?giorno=$day")) }
        settle()
        shot("tt-08-list-day")
        text(lesson.displayTitle).performClick()
        settle()
        shot("tt-09-detail")
        clickDescription("Modifica")
        shot("tt-10-editor")
        rule.onNodeWithText("Colore").performClick()
        settle()
        shot("tt-11-color")
    }

    @Test
    @Config(qualifiers = "+night")
    fun darkTheme() {
        startDemo()
        shot("dark-01-dashboard")
        rule.onNode(hasText("Voti") and hasClickAction()).performClick()
        settle()
        shot("dark-02-grades")
        rule.onNode(hasText("Orario") and hasClickAction()).performClick()
        settle()
        shot("dark-02b-timetable-empty")
        openFabMenu()
        shot("dark-02c-fab")
        click("Genera dalle lezioni")
        click("Crea l'anteprima")
        waitForText("Anteprima dell'orario")
        click("Applica")
        val day = (1L..7L).map { java.time.LocalDate.now().plusDays(it) }.first { it.dayOfWeek.value == 2 }
        rule.runOnUiThread { model.open(android.net.Uri.parse("bcw://orario?giorno=$day")) }
        settle()
        shot("dark-03-timetable")
        clickDescription("Vista Giorno")
        shot("dark-04-day")
    }

    /** Tablet in orizzontale: barra laterale, settimana intera nella vista Giorno, trascinabile tra i giorni. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tablet() {
        startDemo()
        shot("tablet-01-dashboard")
        rule.onNode(hasText("Orario") and hasClickAction()).performClick()
        settle()
        openFabMenu()
        click("Genera dalle lezioni")
        click("Crea l'anteprima")
        waitForText("Anteprima dell'orario")
        click("Applica")
        val day = (1L..7L).map { java.time.LocalDate.now().plusDays(it) }.first { it.dayOfWeek.value == 3 }
        rule.runOnUiThread { model.open(android.net.Uri.parse("bcw://orario?giorno=$day")) }
        settle()
        shot("tablet-02-timetable-list")
        clickDescription("Vista Settimana")
        shot("tablet-03-week")
    }
}
