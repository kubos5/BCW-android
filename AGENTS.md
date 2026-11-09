# AGENTS.md: BCW per Android

Istruzioni per gli agenti AI (Claude Code, Codex e simili) e per chiunque lavori su questa repo. Rispondi e scrivi in italiano: testi dell'interfaccia, commenti, messaggi di commit e pull request.

## 1. Cos'è BCW

BCW (Better ClasseViVa) è un client alternativo per il registro elettronico Classeviva di Gruppo Spaggiari Parma. Non è affiliato a Spaggiari. Usa le stesse API REST dell'app ufficiale, con le stesse funzioni e qualcosa in più: orario settimanale personalizzabile, media e voto necessario per un obiettivo, voti ipotetici ("E se prendessi…"), compiti da segnare come fatti, notifiche e promemoria, widget, account BCW per sincronizzare tutto tra dispositivi.

Questa repo è la versione Android (Kotlin, Jetpack Compose, Material 3 Expressive). Le altre due versioni sono:

| Repo | Piattaforma | Stack | Ruolo |
| --- | --- | --- | --- |
| kubos5/BCW | iPhone e iPad | SwiftUI, iOS 26+ | Progetto principale: le funzioni nascono qui |
| kubos5/BCW-web | Web e PWA | HTML e JavaScript senza dipendenze né build | Stesso design di iOS in Material 3 Expressive, riferimento visivo e di dati |
| kubos5/BCW-android | Android (questa repo) | Kotlin, Compose | Porta su Android quello che c'è su iOS e web |
| kubos5/BCW-api | Backend | Cloudflare Workers + D1 | Account BCW e sincronizzazione |

Licenza MIT. Nome del pacchetto: `com.bcw.classeviva` (la build debug aggiunge il suffisso `.debug`).

## 2. Regole da ricordare sempre

1. L'ordine di lavoro del progetto è iOS, poi web, poi Android. Android non inventa funzioni: le porta. Una funzione solo Android va concordata con chi mantiene il progetto.
2. La specifica di ogni funzione sta nei file `CLAUDE.md` di kubos5/BCW (iOS) e kubos5/BCW-web, e nel README di kubos5/BCW. Prima di scrivere codice leggi come funziona lì, compresi i dettagli e le scelte dell'utente annotate.
3. I formati condivisi (JSON dell'orario, backup, chiavi delle impostazioni sincronizzate, dati della demo, colori del tema) devono restare identici nelle tre versioni. Vedi la sezione 3.4.
4. Tutto il testo visibile è in italiano. Le stringhe stanno quasi tutte direttamente nel codice Kotlin (`strings.xml` ne ha solo una decina).
5. Mai nero o bianco puri come sfondo: usa sempre i ruoli di colore del tema (`ui/theme/`).
6. Animazioni: solo le molle e le transizioni di Material 3 già in `ui/shell/Motion.kt` (asse condiviso per elementi allo stesso livello, dissolvenza per contenuti senza relazione, espansione per le sezioni). Niente curve fatte a mano. Rispetta "animazioni ridotte".
7. Una funzione per pull request, sempre verso `main`.
8. Non usare mai credenziali Classeviva vere in test, screenshot o commit. Per provare l'app usa la modalità demo.
9. Non committare `local.properties`, keystore (`*.jks`, `*.keystore`), APK o AAB: sono già in `.gitignore`.
10. Se un comportamento nella specifica non è chiaro o iOS e web si contraddicono, non scegliere in silenzio: scrivilo nella descrizione della PR.

## 3. Come funziona

### 3.1 Struttura del codice

```
app/src/main/java/com/bcw/classeviva/
├── BCWApplication.kt     Crea AppModel, canali delle notifiche, aggiornamento in background
├── MainActivity.kt       Unica attività, gestisce anche i collegamenti bcw://
├── data/                 Dati e logica, senza interfaccia
│   ├── Api.kt            Client Classeviva (OkHttp), Transport sostituibile, cache su disco, rinnovo token
│   ├── AppModel.kt       Stato globale dell'app (stato di Compose), più account, sessione, sezioni, ArchiveModel
│   ├── Models.kt         Modelli con decodifica tollerante (l'API non è documentata)
│   ├── Cloud.kt          Account BCW: BCWServer, CloudAPI, CloudSync, SYNCED_PREFERENCES, SyncCategory
│   ├── Preferences.kt    Impostazioni: un documento JSON con le stesse chiavi di web e iOS
│   ├── GradeBook.kt      Medie, andamento, voto necessario, voti ipotetici
│   ├── News.kt           Rilevamento delle novità per le notifiche
│   ├── CalendarExport.kt Esportazione di compiti, verifiche ed eventi nel calendario
│   ├── SecureStore.kt    Credenziali e sessioni cifrate (AES-GCM, chiave nell'Android Keystore)
│   ├── Demo.kt           Server finto e deterministico per "Prova la demo"
│   └── timetable/        Orario: modello (Timetable.kt), layout, generatore, giorno predefinito (TimetableLogic.kt), salvataggio e backup (TimetableStore.kt)
├── platform/             Parti di sistema: Notifications.kt (canali e promemoria), BackgroundRefresh.kt (WorkManager), LiveUpdates.kt (notifica "Orario di oggi"), Platform.kt (blocco biometrico)
├── widget/Widgets.kt     Widget Glance: Orario, Lezione in corso, Compiti e verifiche, Prossima verifica, Media
├── ui/
│   ├── Root.kt           Radice: accesso, introduzione, shell, blocco, cambio di sessione
│   ├── shell/            Shell.kt (barre e navigazione), Page.kt (descrittore di pagina), Motion.kt, UiController.kt
│   ├── nav/Navigator.kt  Schede principali (Dashboard, Orario, Voti, Tu) e pila delle pagine aperte
│   ├── components/       Componenti riutilizzabili (liste segmentate, chip, badge voto, anelli, barra del calendario, grafici, forme, logo)
│   ├── screens/          Una cartella o un file per sezione: Dashboard, Grades, You, Search, Noticeboard, settings/, timetable/, login/, onboarding/
│   ├── theme/            ColorEngine.kt (schema colori Material da un accento) e Theme.kt (ruoli, toni dei voti, colori delle materie)
│   ├── icons/            Icone Material Symbols come tracciati (le stesse del web)
│   └── viewer/           Anteprima dei file con zoom
└── util/                 Dates.kt (orologio sostituibile nei test), Json.kt, Text.kt
```

### 3.2 Flusso dei dati

1. `Api.kt` parla con Classeviva attraverso un `Transport`. In modalità demo il trasporto è `DemoTransport` (`Demo.kt`) e nessuna richiesta esce dal telefono.
2. `AppModel` carica le sezioni, le tiene in stato di Compose e le salva nella cache su disco, separata per account, per l'uso offline.
3. Le schermate in `ui/screens/` leggono lo stato di `AppModel` e si ridisegnano da sole. Le azioni passano da metodi di `AppModel`.
4. Dopo ogni aggiornamento `AppModel` alimenta anche widget, promemoria, notifiche delle novità e notifica "Orario di oggi".
5. Con un account BCW, `CloudSync` unisce a tre vie le impostazioni elencate in `SYNCED_PREFERENCES` e l'orario di ogni account (vince il lato cambiato; se cambiano entrambi vince questo dispositivo; la prima volta il server).
6. Il cambio di sessione (altro account, demo) sfuma l'interfaccia, cambia i dati mentre non si vede e la fa riapparire quando i dati ci sono (al massimo 3 secondi).

### 3.3 API Classeviva

Il dettaglio degli endpoint, delle intestazioni e delle stranezze verificate (per esempio le POST senza dati vanno inviate con corpo vuoto, e `noticeboard/attach` richiede di aver prima letto la comunicazione) è nella sezione "API Classeviva" del `CLAUDE.md` di kubos5/BCW. Non duplicarlo qui. Se lo cambi in `Api.kt`, cambia anche lì.

Punti da non dimenticare:
- le credenziali non lasciano mai il dispositivo se non verso i server di Classeviva (e, con l'account BCW, verso BCW-api, che le cifra);
- gli anni precedenti hanno solo le pagelle dall'archivio `webYY.spaggiari.eu`; per il resto si apre il sito di Classeviva in una scheda del browser;
- le verifiche si riconoscono con un'euristica sul testo, perché Classeviva non ha un codice dedicato.

### 3.4 Formati condivisi con iOS e web

Questi dati viaggiano tra le tre app (tramite backup o account BCW). Cambiarli da una parte sola rompe le altre.

- Orario: stesso JSON ovunque (minuti dalla mezzanotte, giorni con 1 = lunedì, UUID maiuscoli, date senza millisecondi). Il backup ha formato `com.bcw.timetable-backup`. Codice: `data/timetable/Timetable.kt`; riferimento nel web: `js/timetable.js`, in iOS: `Models/Timetable.swift`.
- Impostazioni: stesse chiavi e stessi valori di `js/prefs.js` (web) e `Store/Preferences.swift` (iOS). Quelle che si sincronizzano sono in `SYNCED_PREFERENCES` (`Cloud.kt`). Una chiave nuova va in iOS, web e Android con lo stesso nome; se non si sincronizza (blocco, notifiche, promemoria, cose proprie di una piattaforma) resta fuori dall'elenco. Le chiavi nuove vanno nella categoria Impostazioni di `SyncCategory`. I campi che Android non usa (per esempio `appIconColor`) vanno conservati, non scartati.
- Demo: i dati finti sono generati con lo stesso algoritmo (`SeededRandom`, `StableID`) e producono gli stessi dati di `js/demo.js`.
- Colori: `ColorEngine` deve generare gli stessi ruoli del web (`js/theme.js` con material-color-utilities) per ogni accento, stile e contrasto.
- Collegamenti: `bcw://orario?giorno=AAAA-MM-GG`, `bcw://dashboard?giorno=AAAA-MM-GG`, `bcw://voti`.

Questi punti sono controllati dai test di parità (sezione 4).

### 3.5 Equivalenze tra le tre versioni

Serve per trovare su Android il codice che corrisponde a un file cambiato su iOS o web.

| iOS (kubos5/BCW, cartella `BCW/`) | Web (kubos5/BCW-web) | Android (`app/src/main/java/com/bcw/classeviva/`) |
| --- | --- | --- |
| Networking/ClassevivaClient.swift | js/api.js | data/Api.kt |
| Networking/Keychain.swift | localStorage | data/SecureStore.kt |
| Networking/BCWClient.swift, Store/CloudSync.swift | js/cloud.js | data/Cloud.kt |
| Models/ (esclusa Timetable) | js/models.js | data/Models.kt |
| Models/Timetable.swift | js/timetable.js | data/timetable/Timetable.kt |
| Store/TimetableStore.swift | js/timetable.js | data/timetable/TimetableStore.kt |
| Store/TimetableGenerator.swift, layout dell'orario | js/timetable.js | data/timetable/TimetableLogic.kt |
| Store/AppModel.swift | js/store.js | data/AppModel.kt |
| Store/Preferences.swift | js/prefs.js | data/Preferences.kt |
| Store/GradeBook.swift | js/gradebook.js | data/GradeBook.kt |
| Store/DemoServer.swift | js/demo.js | data/Demo.kt |
| Store/NewsNotifications.swift | js/news.js | data/News.kt e platform/Notifications.kt |
| Store/Reminders.swift | js/reminders.js | platform/Notifications.kt (ReminderScheduler) |
| Store/ArchiveModel.swift | js/store.js | ArchiveModel in data/AppModel.kt |
| Calendario (EventKit) | js/calendar-export.js | data/CalendarExport.kt |
| Store/WidgetExporter, BCWWidgets, Shared/Widgets | non c'è | widget/Widgets.kt |
| Store/LiveActivityManager, Shared/LiveActivity | non c'è | platform/LiveUpdates.kt |
| Theme/ | css/bcw.css, js/theme.js | ui/theme/ |
| Components/, Design/ | js/components.js, css/bcw.css | ui/components/ e ui/shell/ |
| Features/Dashboard | js/views/dashboard.js | ui/screens/DashboardScreen.kt |
| Features/Timetable | js/views/timetable.js, timetable-sheets.js | ui/screens/timetable/ |
| Features/Grades | js/views/grades.js | ui/screens/GradesScreen.kt |
| Features/You (sottosezioni) | js/views/you.js, sections.js, noticeboard.js | ui/screens/YouScreen.kt, SectionScreens.kt, NoticeboardScreens.kt |
| Features/Search | js/views/search.js | ui/screens/SearchScreen.kt |
| Features/Settings | js/views/settings.js | ui/screens/settings/ |
| Features/Login | js/views/login.js | ui/screens/login/LoginScreen.kt |
| Features/Onboarding | js/onboarding.js | ui/screens/onboarding/Onboarding.kt |
| Resources/AppIcon*.icon | js/app-icon.js, icons/ | res/drawable/ic_launcher_*.xml |

I widget corrispondono per nome e funzione: Orario (`TimetableWidget`), Lezione in corso (`NowWidget`), Compiti e verifiche (`AgendaWidget`), Prossima verifica (`NextTestWidget`), Media (`AverageWidget`). L'attività in diretta "Orario di oggi" di iOS su Android è una notifica che da Android 16 diventa Live Update (`POST_PROMOTED_NOTIFICATIONS`).

Se un file non compare in tabella, cerca l'equivalente per nome del tipo o della funzione (`grep -rn` sul nome in `app/src/main/java`).

### 3.6 Design

Material 3 Expressive come sul web: stessi colori generati da un accento (predefinito rosso mattone `#a23e2f`), font Google Sans Flex incluso in `res/font/` con asse ROND per titoli e numeri (licenza OFL in `FONT-LICENSE-GoogleSansFlex.txt`), icone Material Symbols Rounded, liste segmentate, forme espressive, barra di navigazione flottante con quattro schede (Dashboard, Orario, Voti, Tu; tre se l'Orario è nascosto). Su Android non esiste Liquid Glass: vale sempre lo stile Material.

## 4. Compilare e provare

Requisiti: JDK 17, Android SDK 36, Android Studio o solo la riga di comando. Minimo Android 8.0 (API 26); i Live Update richiedono Android 16.

```bash
./gradlew :app:compileDebugKotlin     # compila
./gradlew :app:testDebugUnitTest      # prove
./gradlew :app:assembleDebug          # APK di debug
./gradlew :app:installDebug           # installa su telefono o emulatore
./gradlew launchDebug                 # installa e avvia (serve sdk.dir in local.properties)
```

La build debug ha ID `com.bcw.classeviva.debug`; la release `com.bcw.classeviva`. Per provare a mano usa "Prova la demo" (nessun account necessario).

### Prove automatiche

- `ThemeParityTest`: i colori generati da `ColorEngine` coincidono con quelli del web (riferimento `theme-ref.json`).
- `DemoParityTest`: dati della demo identici a quelli del web (riferimento `demo-ref.json`, prodotto da `js/demo.js` con `TZ=Europe/Rome` e orologio fissato all'8/11/2026 alle 10:00).
- `TimetableParityTest`: lettura e scrittura dell'orario, layout, generatore, palette, backup e giorno predefinito coincidono con `js/timetable.js` (riferimento `tt-ref.json`).
- `AppUiTest`: avvia l'app vera con Robolectric (SDK 35, schermo 411 x 891 dp), percorre introduzione, demo, schede e Orario, e salva gli screenshot in `app/build/screenshots/`. Guardali: sono il modo di vedere cosa ha fatto la modifica.

I file `*-ref.json` in `app/src/test/resources/` sono il contratto con il web. Nella repo non c'è uno script che li rigeneri. Se il web cambia i dati di riferimento di proposito, rigenerali eseguendo i moduli JS del web nelle stesse condizioni (fuso Europe/Rome, data e ora fissate come sopra, stessi campi che il test legge) e poi adatta il codice Kotlin finché i test passano. Non modificare mai un riferimento a mano per far passare un test. Eccezione nota e documentata nel test: il separatore (`outline-variant`) dello stile Vivace con contrasto alto differisce leggermente tra le due librerie.

I test girano con `-Duser.timezone=Europe/Rome`. Per i test che dipendono dal tempo usa `Dates.clock`.

## 5. Allinearsi alle novità di iOS e web

Quando iOS e web escono con una nuova versione, Android deve raggiungerle. Segui questi passi in ordine.

### Passo 1: da dove si parte

1. Leggi la versione di Android: `versionName` in `app/build.gradle.kts` e l'ultima riga della tabella in "Registro degli allineamenti" (sezione 7). Quella è la versione di iOS e web già portata.
2. Guarda le versioni attuali delle altre repo. iOS ha un tag per ogni versione (`v3.3`, `v3.3.1`, …) e `MARKETING_VERSION` in `project.yml`. Il web non ha tag: ogni versione è un commit il cui messaggio comincia con "Versione X.Y.Z".
3. Se le versioni sono già uguali non c'è niente da fare, tranne eventuali correzioni elencate nel registro come "da recuperare".

### Passo 2: cosa è cambiato

Clona le due repo in una cartella temporanea e confronta dalla versione già portata a quella nuova (qui, come esempio, da 3.3 a 3.3.1).

```bash
git clone https://github.com/kubos5/BCW /tmp/bcw-ios
git clone https://github.com/kubos5/BCW-web /tmp/bcw-web

# iOS
git -C /tmp/bcw-ios log --oneline v3.3..v3.3.1
git -C /tmp/bcw-ios diff --stat v3.3 v3.3.1
git -C /tmp/bcw-ios diff v3.3 v3.3.1 -- CLAUDE.md README.md
git -C /tmp/bcw-ios diff v3.3 v3.3.1 -- BCW/Features BCW/Store BCW/Models BCW/Networking BCW/Components BCW/Design BCW/Theme

# Web: trova i commit di versione, poi confronta
git -C /tmp/bcw-web log --oneline --grep='^Versione'
git -C /tmp/bcw-web diff --stat <commit-versione-portata> <commit-versione-nuova>
git -C /tmp/bcw-web diff <commit-versione-portata> <commit-versione-nuova> -- CLAUDE.md README.md js css
```

Senza clonare: `gh api repos/kubos5/BCW/compare/v3.3...v3.3.1` oppure la pagina `https://github.com/kubos5/BCW/compare/v3.3...v3.3.1`.

Leggi nell'ordine:
1. il diff di `CLAUDE.md` (iOS e web): è il posto dove l'autore annota le richieste dell'utente, le scelte e le correzioni. Spiega il perché delle modifiche al codice, quindi leggilo per primo;
2. il diff di `README.md` di iOS: l'elenco delle funzioni è la specifica a cui tendere;
3. i messaggi dei commit;
4. il diff del codice, per i dettagli.

### Passo 3: classificare ogni modifica

Per ogni modifica scrivi a quale categoria appartiene.

- Funzione o comportamento visibile portabile: da portare.
- Formato o dato condiviso (JSON dell'orario, chiavi delle impostazioni, backup, demo, colori, endpoint): da portare per primo, perché un'incompatibilità rompe la sincronizzazione tra le app. Controlla anche `SYNCED_PREFERENCES` e i riferimenti dei test.
- Correzione di un bug nella logica condivisa (medie, orario, generatore, notifiche): controlla se Android ha lo stesso bug nel codice equivalente.
- Design: nuovi componenti, colori, forme, animazioni. Il web è il riferimento migliore (stessi componenti di Material 3). Porta lo stesso aspetto con gli strumenti di Compose e di `ui/components/`.
- Solo piattaforma: da ignorare (elenco sotto).

Una modifica può stare in più categorie. Una novità può anche essere già presente su Android: cerca prima di scrivere (vedi passo 4).

### Passo 4: cosa deve cambiare su Android

Per ogni modifica da portare:

1. Trova il codice equivalente con la tabella 3.5 e con `grep -rn` sul testo italiano della funzione (le stringhe sono in codice).
2. Leggi come Android risolve oggi quella cosa. Se la novità c'è già, non fare niente e annotalo nel registro.
3. Confronta la specifica nuova con il comportamento attuale. Se la funzione esisteva in versione diversa, la modifica è un'evoluzione: cambia il minimo necessario.
4. Individua tutti i punti toccati, perché una funzione di solito attraversa più strati: dati (`data/`), interfaccia (`ui/screens/`, `ui/components/`), impostazioni (`Preferences.kt`, `SettingsScreen.kt`, `SYNCED_PREFERENCES`), demo (`Demo.kt`), widget e notifica "Orario di oggi" (`widget/`, `platform/`), introduzione (`Onboarding.kt`), ricerca (`SearchScreen.kt`) e test.

### Passo 5: portare

- Mantieni nomi di chiavi, valori predefiniti e testi uguali a iOS e web (i testi sono in italiano e di solito identici).
- Se serve dato di prova nuovo, aggiungilo a `Demo.kt` con lo stesso algoritmo di `js/demo.js`, altrimenti `DemoParityTest` fallisce.
- Se aggiungi una preferenza che si sincronizza, aggiungila a `Preferences.defaults` e a `SYNCED_PREFERENCES`, con la stessa chiave del web.
- Se la modifica tocca l'orario, mantieni la compatibilità del JSON in entrambe le direzioni (un backup di iOS o web si legge su Android e viceversa).
- Se la modifica tocca ciò che mostrano widget o notifica "Orario di oggi", aggiorna anche quelli.
- Rispetta Android: gesto indietro predittivo, barre di sistema (la barra di navigazione sta sopra quella di sistema), schermi larghi, rotazione, tema chiaro, scuro e dinamico, accessibilità (`contentDescription`).
- Una funzione per commit e per pull request.

### Passo 6: verificare

1. `./gradlew :app:compileDebugKotlin`
2. `./gradlew :app:testDebugUnitTest`. Se un test di parità fallisce, o hai sbagliato tu, o il web ha cambiato i dati: in questo secondo caso rigenera il riferimento come spiegato sopra.
3. Aggiungi o aggiorna una prova in `AppUiTest` per la schermata toccata e guarda gli screenshot in `app/build/screenshots/`. Controlla tema chiaro e scuro.
4. `./gradlew :app:installDebug` su emulatore o telefono, apri la demo e verifica le schermate toccate, anche con animazioni ridotte.
5. Confronta a occhio con la demo del web (`node server.js` nella repo web, poi "Prova la demo") e con la descrizione del README di iOS.

### Passo 7: chiudere

1. Aggiorna `versionName` e `versionCode` in `app/build.gradle.kts` portandoli alla versione raggiunta di iOS e web. Per non rompere l'ordine delle versioni (oggi la 3.3 ha `versionCode` 33, quindi 3.3.1 come 331 supererebbe una futura 3.4 come 34), da ora conviene `MAGGIORE*10000 + MINORE*100 + PATCH` (3.3.1 diventa 30301).
2. Aggiungi una riga al registro (sezione 7): versione Android, tag iOS, commit del web, data e cosa è stato lasciato fuori e perché.
3. Se l'elenco delle funzioni nel README cambia, aggiornalo.
4. Se hai cambiato qualcosa di questo file (mappa dei percorsi, procedura), aggiornalo nella stessa PR.
5. Apri la pull request verso `main` (sezione 8).
6. Dopo l'unione, crea il tag `vX.Y.Z` come nelle altre repo.

### Cose da ignorare quando si legge il diff di iOS

Liquid Glass e tutto ciò che riguarda `LiquidGlass`; Dynamic Island e `ActivityKit` (su Android c'è la notifica Live Update); WidgetKit, App Group, accessori della schermata di blocco; `AppIcon.icon`, icone alternative e `AppIconController`; Portachiavi (`Keychain`) e Face ID (su Android sono SecureStore e BiometricPrompt, già fatti); `Info.plist`, `.xcodeproj`, `project.yml`, XcodeGen; layout iPad (`iPad`, `BCWWindowClass`); argomenti di avvio per le prove (`-BCWTab` e simili); autotest DEBUG di iOS; consigli su simulatori e `xcodebuild`; il Mac (non più supportato nemmeno su iOS). Cambiano invece sempre qualcosa per Android le sezioni di CLAUDE.md su orario, voti, Dashboard, account BCW, impostazioni, onboarding, notifiche delle novità e API.

### Cose da ignorare quando si legge il diff del web

Proxy, `server.js`, Cloudflare Worker, CORS, `config.json`, service worker, manifest, installazione come PWA, passkey (su Android c'è il blocco biometrico), `localStorage` e IndexedDB (su Android sono SharedPreferences e file), scorciatoie da tastiera di Alt, layout oltre 840 px adattato al desktop (su Android vale solo per tablet e schermi larghi, dove serve), `dom.js` e il morph del DOM. Il web resta però il riferimento per colori, forme, transizioni, testi, `calendarBar` e struttura delle pagine.

### Se qualcosa non torna

- iOS e web non coincidono: vale iOS per le funzioni, il web per i dati condivisi e i colori. Annotalo nella PR.
- Non sei sicuro se una funzione ha senso su Android: portala se è una funzione dell'app (voti, orario, bacheca, account), saltala se dipende da una capacità che Android non ha, e dillo.
- Il diff è enorme: dividi per funzione, una PR ciascuna, partendo dai formati condivisi.

### Prompt breve per l'agente

```text
Allinea BCW-android alle ultime novità di kubos5/BCW (iOS) e kubos5/BCW-web.
Segui AGENTS.md, sezione 5, passo per passo. Parti dalla versione indicata nel registro,
leggi i CLAUDE.md e i README delle altre repo, classifica le modifiche, portale una per volta
e apri una pull request in italiano per ciascuna funzione.
```

## 6. Manutenzione ordinaria

- Dipendenze: tutte le versioni sono in `gradle/libs.versions.toml` (Compose BOM, Material 3 `1.5.0-alpha`, Glance, WorkManager, OkHttp, kotlinx.serialization, material-color-utilities). Material 3 Expressive è ancora in alpha: leggi le note di rilascio prima di aggiornarlo e controlla gli screenshot dopo. Non cambiare `materialkolor-mcu` senza rifare passare `ThemeParityTest`: i colori devono coincidere con quelli del web.
- Aggiornare SDK: se alzi `compileSdk` o `targetSdk` (oggi 36) controlla permessi, notifiche, widget e Live Update, e che Robolectric supporti l'SDK dei test (oggi 35).
- Release: la build `release` è ridotta con R8 (`proguard-rules.pro`) ed è firmata con la chiave di debug, così si installa senza una chiave propria. Per una distribuzione vera serve una firma propria, tenuta fuori dalla repo.
- Cambiamenti dell'API Classeviva: sono il motivo più probabile di una rottura improvvisa. Controlla `Api.kt` e `Models.kt`: la decodifica è volutamente tollerante (campi mancanti o di tipo diverso non devono far crashare l'app). Se trovi un cambio, sistema anche iOS e web, o segnalalo.
- Icona: le sorgenti del marchio sono nel web (`icons/icon.svg`, `js/app-icon.js`) e in iOS (`AppIcon.icon`). Se cambiano, ridisegna `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` e `ic_notification.xml`.
- Icone dell'interfaccia: i tracciati in `ui/icons/IconPaths.kt` vengono da Material Symbols (come `js/icons.js` nel web). Per una nuova icona usa lo stesso tracciato del web.
- Widget e notifiche: dopo una modifica prova su un dispositivo vero; Robolectric non li copre. I widget si aggiornano da `Widgets` quando `AppModel` cambia.

## 7. Registro degli allineamenti

Una riga per ogni versione raggiunta. Riempila al passo 7.

| Android | iOS (tag) | Web (commit "Versione …") | Data | Note |
| --- | --- | --- | --- | --- |
| 3.3 | v3.3 | "Versione 3.3" | 08/11/2026 | Versione iniziale di BCW per Android. Punto di partenza dichiarato dal `versionName`; verificare al primo allineamento quale commit del web corrisponde |

Da portare dopo la 3.3: tutto ciò che iOS e web hanno aggiunto con la 3.3.1. Controllalo al passo 2, perché alcune voci (per esempio "Rivedi l'introduzione" e la sezione GitHub nelle impostazioni) sono già presenti nel codice Android.

## 8. Come contribuire

1. Parti da `main` aggiornato e crea un ramo con un prefisso: `feat/…` per una funzione, `fix/…` per una correzione, `docs/…` per documentazione e README (come nelle PR esistenti: `feat/widget-grades-brand`, `fix/accent-swatch`, `docs/readme`).
2. Fai modifiche piccole e mirate: una funzione per PR. Non mescolare il porting di una novità con un rifacimento.
3. Messaggi di commit in italiano, brevi, che dicono cosa cambia per l'utente, con un prefisso di ambito quando aiuta (per esempio "Widget: ultimi voti nella Media, marchio configurabile e stile M3 Expressive", "Gesto indietro predittivo: transizione nativa nella scheda Tu"). Per le versioni: "Versione 3.3.1: …".
4. Prima di aprire la PR: compila, esegui i test, guarda gli screenshot e prova la demo su emulatore o telefono.
5. La PR va verso `main`, con titolo e descrizione in italiano. Nella descrizione scrivi: cosa hai portato (con il riferimento a iOS e web, per esempio il commit o la sezione del CLAUDE.md), come l'hai adattato ad Android, cosa hai lasciato fuori e perché, come l'hai provato (comandi, dispositivo e screenshot).
6. Il README segue lo stile delle altre repo: niente trattini lunghi, niente grassetto o corsivo nelle parti aggiunte, tabelle solo se indispensabili, italiano.
7. Se trovi un problema in iOS o nel web mentre porti una funzione, segnalalo nella repo giusta invece di correggerlo solo qui.

Chi contribuisce con un agente AI deve rileggere il lavoro prima della PR: l'agente deve aver compilato e fatto passare i test, e nella descrizione va detto cosa è stato davvero provato su un dispositivo e cosa no.

## 9. Cose da sapere

- Android non ha un `CLAUDE.md` proprio: questo file è la guida unica. Gli strumenti che leggono solo `CLAUDE.md` possono usare un file che contenga `@AGENTS.md`.
- L'app è fatta per studenti e famiglie: la privacy conta. Niente analytics, niente invio di dati a terzi, `allowBackup` spento, credenziali solo nel `SecureStore`.
- Lo stile di lavoro con l'autore: quando una scelta di design o di comportamento è una richiesta esplicita dell'utente, in iOS e nel web è annotata nel `CLAUDE.md` con la dicitura "richiesta dell'utente" o "scelta dell'utente". Su Android vanno rispettate come le altre.
- Le funzioni "non verificate con un account reale" (elencate in `CLAUDE.md` di iOS) sono nello stesso stato su Android: se le provi con un account vero, dillo nella PR.

## 10. Link utili

- iOS: https://github.com/kubos5/BCW
- Web: https://github.com/kubos5/BCW-web (demo: https://kubos5.github.io/BCW-web)
- Android: https://github.com/kubos5/BCW-android
- Backend: https://github.com/kubos5/BCW-api
- Classeviva: https://web.spaggiari.eu
- Material 3: https://m3.material.io
- Licenza: MIT, vedi `LICENSE`
