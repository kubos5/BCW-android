# BCW: Better ClasseViVa per Android
Un client nativo per Android del registro elettronico [Classeviva](https://web.spaggiari.eu) di Gruppo Spaggiari Parma, in stile Material 3 Expressive.
È la versione Android di [BCW per iPhone e iPad](https://github.com/kubos5/BCW), con le stesse funzioni della [versione web](https://github.com/kubos5/BCW-web) e lo stesso [account BCW](https://github.com/kubos5/BCW-api).

> BCW non è affiliato a Gruppo Spaggiari Parma S.p.A.

---

## Installazione

Finché l'app non sarà su Google Play puoi compilarla da te:

1. installa [Android Studio](https://developer.android.com/studio) (o solo l'Android SDK con la piattaforma 36)
2. clona `https://github.com/kubos5/BCW-android` e aprilo con Android Studio
3. esegui su un telefono collegato (con il debug USB attivo) o su un emulatore

Da riga di comando:

```bash
./gradlew :app:assembleDebug
```

L'APK di debug è in `app/build/outputs/apk/debug/` e si installa accanto a una versione di release (ha l'identificativo `com.bcw.classeviva.debug`).

### Prove

```bash
./gradlew :app:testDebugUnitTest
```

Le prove confrontano la demo, l'orario (formato, disposizione, generazione) e i colori del tema con quelli della versione web, e avviano l'app vera con Robolectric (introduzione, demo, schede e Orario), salvando gli screenshot in `app/build/screenshots`.

---

## Funzioni

Le stesse dell'app per iPhone, con gli equivalenti Android delle parti di sistema:

- voti con media (di tutti i voti o delle medie, con o senza pesi), effetto di ogni voto, grafici dell'andamento, obiettivo di media e "E se prendessi…"
- Dashboard con la settimana o il mese, compiti da segnare come fatti (e nascondere), verifiche, eventi, presenze e lezioni; ricerca in tutto il registro
- Tu con "Da vedere", bacheca (adesione, firma, risposta, allegati), note, scrutini, assenze, materiale, lezioni, agenda, materie, libri, calendario e anni precedenti
- più account Classeviva (anche genitore con più figli) da cambiare tenendo premuta la scheda Tu o dall'avatar; modalità demo
- account BCW: abbini più account Classeviva e ritrovi impostazioni, aspetto e orario (con i file) su ogni dispositivo, anche nell'app per iPhone e sul web
- consultazione offline con i dati dell'ultimo aggiornamento
- Orario: vista Lista e vista Giorno (la settimana intera sui tablet), modifica con trascinamento, ridimensionamento e doppio tocco, spostamento degli orari, allineamento automatico o libero, intervalli, colori e palette, bozza generata dalle lezioni, verifiche di Classeviva sulle lezioni con le regole per nasconderle, note e file per un giorno o per ogni settimana, backup compatibile con iPhone e web
- introduzione alla prima apertura, in bianco e nero finché non scegli un colore
- aspetto: colore d'accento (o i colori del sistema, Material You), stile dei colori, contrasto, tema chiaro o scuro, angoli, animazioni e caratteri arrotondati (Google Sans Flex)

### Integrazioni Android

- **Notifiche**: promemoria la sera prima di compiti e verifiche; novità (voti, comunicazioni, note, assenze, agenda) controllate in background circa ogni ora
- **Lezione in corso**: una notifica che si aggiorna da sola (Live Updates su Android 16, nella barra di stato e nella schermata di blocco), al posto dell'attività in diretta di iOS; parte mezz'ora prima della prima lezione e si avvia o si ferma a mano dalla scheda in cima all'orario di oggi
- **Widget**: Orario, Lezione in corso, Compiti e verifiche, Prossima verifica e Media, con i colori del tema in chiaro e in scuro; toccandoli si apre l'app sul giorno o sulla scheda giusta
- **Blocco** con impronta, volto o blocco schermo del telefono
- **Calendario**: compiti, verifiche ed eventi si aggiungono al calendario del telefono
- **Icona a tema** di Android 13 e successivi

Non ci sono le funzioni proprie di iOS (Liquid Glass, icone alternative, Dynamic Island).

### Requisiti

- Android 8.0 (API 26) o successivo; Live Updates da Android 16
- per compilare: JDK 17 e Android SDK 36
