<p align="center">
  <img width="1920" height="480" alt="android" src="https://github.com/user-attachments/assets/034ce1b3-8d7f-42b1-b4a3-6386543a0716" />
</p>


<h1 align="center">BCW: Classeviva, in Material Design</h1>

<p align="center">
  <strong>Un client per iOS, Android e Web per il registro elettronico <a href="https://web.spaggiari.eu">Classeviva</a> di Spaggiari</strong>
</p>

<p align="center">
  BCW non è affiliata a Gruppo Spaggiari Parma S.p.A.
</p>

<p align="center">
  <a href="https://github.com/kubos5/BCW">BCW per iOS</a> ·
  <a href="https://github.com/kubos5/BCW-web">BCW per Web</a> ·
  <strong><a href="https://github.com/kubos5/BCW-android">BCW per Android</a></strong> ·
  <a href="https://github.com/kubos5/BCW-api">Backend</a>
</p>

<br><br>

## Installazione

1. scarica l'ultima versione dalle [release](https://github.com/kubos5/BCW/releases/latest)
2. installa l'APK su un dispositivo Android
3. fatto!

Alternativamente, puoi compilare manualmente:

### Installazione manuale con Android Studio

1. installa [Android Studio](https://developer.android.com/studio)
2. clona
    ```
    https://github.com/kubos5/BCW-android
    ```
3. esegui su un telefono connesso via USB o su un simulatore

Da terminale:

```bash
./gradlew :app:assembleDebug
```

**Requisiti:** Android 8.0 (API 26) o più; Live Updates da Android 16; per compilare JDK 17 e SDK 36.

### Prove

```bash
./gradlew :app:testDebugUnitTest
```

Confrontano demo, orario e colori del tema con la versione web, e avviano l'app vera con Robolectric salvando gli screenshot in `app/build/screenshots`.

---

## Mantieni il passo con il progetto principale

BCW-android dipende da [**kubos5/BCW**](https://github.com/kubos5/BCW), il progetto principale per iPhone e iPad: le funzioni nascono lì e qui vengono portate su Android. Per aggiornare questa versione con agenti AI (Claude Code, Codex, o l'agente che preferisci), usa questo prompt:

```text
Aggiorna BCW-android con le ultime novità della repo principale kubos5/BCW.

1. Guarda cosa c'è di nuovo in kubos5/BCW (gh api repos/kubos5/BCW/commits):
   - leggi gli ultimi commit e i file che toccano (gh api repos/kubos5/BCW/commits/<sha>)
   - concentrati su Features/ e Store/: sono le funzioni visibili e i dati che le alimentano
   - il README di kubos5/BCW descrive ogni funzione in italiano: è la specifica a cui tendere

2. Per ogni novità che ha senso su Android (salta Liquid Glass, Dynamic Island e
   le altre cose proprie di iOS):
   - leggi come funziona nella repo principale e come l'app Android risolve oggi la
     stessa cosa (cerca in app/src/main/java/com/bcw/classeviva/ la parte corrispondente)
   - portala su Android con gli strumenti di questa repo: Compose e Material 3
     Expressive in ui/ (Design e Components sono l'equivalente di Design/ e
     Components/ dell'app iOS), i dati in data/, le parti di sistema in platform/
   - rispetto i criteri dell'app: transizioni di Material 3 (asse condiviso,
     dissolvenza), colori del tema, animazioni ridotte per chi le disattiva,
     testi in italiano

3. Fai funzionare tutto:
   - compila: ./gradlew :app:compileDebugKotlin
   - prova: ./gradlew :app:testDebugUnitTest
   - installa e guarda: ./gradlew :app:installDebug poi avvia l'app su emulatore
     o telefono, e verifica le schermate toccate dalla novità
   - aggiorna il README se la novità cambia una funzione elencata

4. Apri una pull request verso main con un titolo e una descrizione in italiano,
   una funzione per PR, dicendo cosa hai portato dalla repo principale e come l'hai
   adattata ad Android.
```
inoltre

```
Per facilitare il processo, ho deciso di aggiungere il file AGENTS.md, contenente indicazioni chiare perchè l'agente di codice possa avere una maggiore comprensione del contesto.
```
## Licenza

Distribuita con licenza MIT. Vedi [LICENSE](LICENSE).

---

<a href="https://star-history.com/#kubos5/BCW-android&Timeline">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=kubos5/BCW-android&type=Timeline&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=kubos5/BCW-android&type=Timeline" />
   <img alt="Grafico degli stargazers nel tempo" src="https://api.star-history.com/svg?repos=kubos5/BCW-android&type=Timeline" width="600" />
 </picture>
</a>
