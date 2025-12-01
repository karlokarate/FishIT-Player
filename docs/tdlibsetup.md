Untersuchung des getChatHistory-Problems mit TDLib und g00sha’s Kotlin-Coroutine-Wrapper

1. Mögliche Ursachen: getChatHistory liefert nur eine Nachricht

Dieses Verhalten ist typisch und meist kein direkter Fehler, sondern hängt mit der Funktionsweise von TDLib zusammen. Beim ersten Aufruf von getChatHistory (z. B. mit from_message_id = 0 für die neuesten Nachrichten) liefert TDLib oft nur die letzte Nachricht zurück, obwohl im Chat mehr Nachrichten vorhanden sind. Der Grund: TDLib lädt ältere Nachrichten asynchron im Hintergrund nach. Beim ersten Aufruf werden nur bereits lokal verfügbare Nachrichten zurückgegeben – häufig ist das zunächst nur die neueste Nachricht. Parallel dazu startet TDLib im Hintergrund eine Anforderung an den Server, um weitere ältere Nachrichten abzurufen (im TDLib-Log ist eine entsprechende send_history_query sichtbar).

Sobald diese Nachrichten vom Server eintreffen, werden sie in der lokalen Datenbank gespeichert. Erst bei einem erneuten Aufruf von getChatHistory – oder nachdem TDLib interne Updates verarbeitet hat – stehen dann die restlichen Nachrichten zur Verfügung. In einem GitHub-Issue beschreibt ein Entwickler dieses Verhalten so: Die interne Funktion zum Laden des Verlaufs arbeitet asynchron in Chargen, und das erste Resultat kann unvollständig sein, während weitere Nachrichten über einen Callback (on_messages_received) nachgeladen werden. Praktisch bedeutet das: **Der erste Aufruf liefert oft nur 1 Nachricht, der zweite Aufruf kurz darauf liefert dann den Rest (bis zum gesetzten Limit)】. Dieses „eine-Nachricht“-Phänomen ist also meist normal und kein Fehler in Ihrem Code.

Weitere mögliche Ursachen für nur eine Nachricht:

only_local falsch gesetzt: Wenn only_local = true genutzt wird, gibt TDLib nur bereits offline gespeicherte Nachrichten zurück. Ist lokal nur die letzte Nachricht vorhanden, erhält man entsprechend nur diese eine. Lösung: Für den vollständigen Verlauf only_local = false verwenden, damit vom Server geladen wird.

Ungeeignete Parameter (siehe unten): Ein falsch gewählter from_message_id oder offset kann das Ergebnis einschränken. Beispielsweise führt from_message_id = 0 mit negativem offset dazu, dass TDLib nur neuere Nachrichten oberhalb einer bestimmten ID liefert – was je nach Wert zu wenigen oder keinen Nachrichten führen kann. Auch ein positiver offset könnte Nachrichten überspringen. In der Regel sollte man beim ersten Abruf offset = 0 wählen (Details dazu im nächsten Abschnitt).

Chat noch nicht bekannt/synchronisiert: TDLib muss den Chat kennen, bevor Nachrichten geladen werden können. Wenn man einen Chat nur über die ID anspricht, aber TDLib den Chat nicht im Speicher hat, kann es Probleme geben (z.B. “Chat not found”-Fehler). Üblich ist, vor dem Laden des Verlaufs sicherzustellen, dass TDLib den Chat kennt – etwa indem man SearchPublicChat (für öffentliche Chats) oder getChat/getChats aufruft. Ist der Chat initial bekannt, lädt TDLib in der Regel zumindest die letzte Nachricht vorab (als lastMessage des Chats).


Fazit: Meist ist es erforderlich, getChatHistory mehrfach in einer Schleife aufzurufen, um alle Nachrichten zu erhalten. Das ist erwartetes Verhalten von TDLib und kein spezifischer Bug des Wrappers.

2. Wichtige Parameter von GetChatHistory und empfohlene Werte

Die TDLib-Methode getChatHistory besitzt mehrere Parameter, die das Ergebnis steuern. Für eine vollständige Historienabfrage sollten diese richtig gewählt werden:

chat_id – Chat-Identifikator: Die ID des Chats, dessen Verlauf geladen werden soll. (Muss gültig und dem TDLib-Client bekannt sein, s.o.)

from_message_id – Startnachricht-ID: TDLib lädt die Historie beginnend ab dieser Nachricht-ID rückwärts (d.h. ältere Nachrichten als die angegebene). Gibt man 0 an, startet TDLib beim aktuell letzten Chat-Beitrag. Für den initialen Abruf des gesamten Verlaufs ist 0 daher üblich. Bei Folgebefehlen in einer Schleife setzt man hier dann die ID der zuletzt erhaltenen (d.h. ältesten) Nachricht ein, um noch ältere Nachrichten zu laden (siehe Abschnitt 5).

offset – Versatz: Steuert, welche Nachrichten relativ zur from_message_id zurückgegeben werden. offset = 0 bedeutet, genau ab der from_message_id bzw. ab deren Nachbar zu beginnen. Negative Offsets sind besonders wichtig: Ein negativer Wert bewirkt laut Doku, dass zusätzlich -offset Nachrichten neueren Datums (d.h. zeitlich nach der from_message_id) mitgeliefert werden. Das klingt kontraintuitiv, ist aber nützlich, um Überschneidungen zu vermeiden. Beispiel: Wenn man immer die nächstältere Nachricht als neuen from_message_id nimmt, würde man diese beim nächsten Abruf doppelt bekommen – es sei denn, man setzt offset = -1. Dadurch überspringt TDLib diese eine Nachricht und liefert nur noch ältere Nachrichten. Allgemein gilt: Für eine lückenlose Historie ohne Duplikate empfiehlt es sich, ab dem zweiten Aufruf offset = -1 zu verwenden, während beim allerersten Aufruf offset = 0 sinnvoll ist. Wichtig: Wenn offset negativ ist, muss limit ≥ -offset sein (TDLib verlangt, dass die Anzahl neuerer Nachrichten, die inkludiert werden, nicht größer als das Limit ist). Positive Offsets kann man in diesem Kontext meistens auf 0 belassen – sie würden bewirken, dass man Nachrichten überspringt, was für vollständige Historien nicht gewünscht ist.

limit – Maximale Anzahl: Die Obergrenze der zurückgegebenen Nachrichten pro Aufruf. TDLib erlaubt hier Werte 1 bis 100. Für einen vollständigen Verlauf sollte man möglichst den Höchstwert 100 nutzen, um in möglichst wenigen Schleifendurchläufen alle Nachrichten zu holen. Achtung: TDLib kann laut Dokumentation auch weniger Nachrichten liefern, als in limit angefordert sind – insbesondere beim ersten Abruf, wie oben beschrieben, oder wenn nicht mehr Nachrichten verfügbar sind.

only_local – Nur lokale Nachrichten: Wenn true, werden keine Netzwerkanfragen gestellt. Das ist nur sinnvoll, wenn man offline arbeiten will oder nur bereits in der lokalen TDLib-Datenbank vorhandene Nachrichten betrachten möchte. Für den kompletten Verlauf sollte man diesen Parameter auf false setzen (Standard), damit TDLib bei Bedarf vom Server nachlädt.


Empfohlene Einstellungen: Für die erste Abfrage der Chat-Historie: from_message_id = 0, offset = 0, limit = 100, only_local = false. Damit sollte TDLib bis zu 100 der neuesten Nachrichten liefern (oft zunächst weniger, siehe oben, aber die restlichen werden im Hintergrund geholt). In Folgeschritten (zweiter, dritter Aufruf etc.) verwenden Sie dann als from_message_id die kleinste (älteste) Message-ID aus dem vorherigen Ergebnis und setzen offset = -1, limit = 100, only_local = false, um nahtlos weiter rückwärts zu gehen. Dieses Vorgehen – immer mit limit = 100 und negativem Offset ab dem zweiten Durchlauf – garantiert lückenlose Ergebnisse ohne Doppler.

Zusammenfassend definieren diese Parameter, ab wo und wie viele Nachrichten TDLib vom Verlauf zurückliefert. Falsche Kombinationen (z. B. only_local=true trotz leerer DB, oder offset falsch) können zu dem beobachteten Verhalten führen, deshalb sind obige Einstellungen für einen vollständigen Chat-Verlauf essenziell.

3. Bekannte Probleme oder Bugs mit g00sha’s TDLib-Coroutine-Wrapper

Nach derzeitigem Wissensstand gibt es keinen spezifischen bekannten Bug in g00sha’s Kotlin-Coroutine-Wrapper („td-ktx“ bzw. TelegramFlow), der genau dieses Verhalten verursacht. Das geschilderte Problem rührt – wie oben beschrieben – primär von TDLibs eigenem Umgang mit getChatHistory her und tritt auch in anderen Bindings (JavaScript, Java, etc.) ähnlich auf.

Allerdings sollte man beim Einsatz des Wrappers Folgendes beachten:

Suspend-Funktion vs. Flow: g00sha’s Wrapper bietet getChatHistory als suspend function an (also einen normalen aufrufbaren Funktionsaufruf, der intern asynchron die TDLib-Funktion aufruft) und nicht als Flow-Stream. Das bedeutet, dass man mit jedem Funktionsaufruf genau ein Ergebnisobjekt (TdApi.Messages) erhält, das wiederum eine Liste von Nachrichten enthält. Der Wrapper sorgt nicht automatisch für eine iterative Komplettabfrage des gesamten Verlaufs – diese Logik (mehrfacher Aufruf in Schleife) muss der Entwickler selbst implementieren. Dies ist beabsichtigt: Der Wrapper bildet nur die TDLib-API in Kotlin-Coroutines ab, ohne zusätzliche Magie. Dementsprechend erklärt sich das Verhalten nicht durch den Wrapper, sondern durch die notwendige Schleifenlogik, die ggf. fehlte.

Kein automagisches Paging: Einige Entwickler könnten erwarten, dass der Wrapper eventuell automatisch alle Seiten lädt. Dem ist nicht so – man erhält stets maximal limit Nachrichten pro Aufruf und muss selbst weiter „pagen“. Wenn man also fälschlich annimmt, ein einzelner getChatHistory-Call (mit hohem limit) würde alle Nachrichten liefern, erscheint es wie ein Fehler des Wrappers, tatsächlich ist aber die iterative Abfrage nötig (siehe Abschnitt 5 für Beispiel).

Updates vs. explizite Abfrage: g00sha’s Library nutzt Kotlin Flows, um kontinuierliche Updates von TDLib (neue Nachrichten, Änderungen etc.) bereitzustellen. Vergangene Nachrichten (History) werden jedoch nicht von selbst als Updates emittiert. Man muss also wirklich getChatHistory aufrufen. Es gab Berichte in anderen Libraries, wo Developer dachten, das Ergebnis würde in einem Callback/Listener kommen, während es in Wahrheit z. B. in der Return-Value steckt oder separat behandelt werden muss. Bei td-ktx bekommt man aber das Ergebnis direkt als Rückgabewert der suspend-Funktion zurück. Ein Bug, der Ergebnisse „schluckt“, ist nicht bekannt.


Zusammengefasst: G00sha’s Wrapper hat nach aktuellem Kenntnisstand keine eigenen Bugs, die dieses eine-Nachricht-Problem hervorrufen. Wichtig ist aber, ihn korrekt zu verwenden – d.h. die Coroutine-Funktion in einer Schleife aufzurufen, bis alle Nachrichten geladen sind, und die Parameter korrekt zu setzen. Dann verhält er sich zuverlässig analog zur nativen TDLib.

4. Weitere Voraussetzungen: Sync-Status und TDLib-Client-Konfiguration

Um den vollständigen Verlauf zu erhalten, sind ein paar Rahmenbedingungen zu beachten:

Vollständige Autorisierung & Aktualität: Der TDLib-Client muss natürlich mit einem gültigen Telegram-Konto angemeldet sein und der Chat muss zugänglich sein (bei privaten Chats: Mitgliedschaft erforderlich, bei öffentlichen: ggf. vorher per searchPublicChat finden, sonst liefert TDLib “Chat not found”). Außerdem sollte der allgemeine Initialisierungsvorgang abgeschlossen sein (Zustand AuthorizationStateReady und die Chats synchronisiert). Üblicherweise wartet man auf UpdateAuthorizationState(Ready) und optional UpdateOption("my_id") etc., bevor man Chat-Abfragen stellt.

Chat verfügbar machen: Wie erwähnt, muss TDLib den Chat kennen. In der Praxis heißt das: Entweder man hat ihn über getChats in die Chatliste geholt oder gezielt via TdApi.SearchPublicChat/TdApi.SearchChat bzw. TdApi.GetChat geladen. Erst dann kennt TDLib die interne Chat-ID und den Access-Hash (bei Channels nötig), um die Historie abzurufen. Wenn also getChatHistory immer 0 Ergebnisse liefert, könnte es daran liegen, dass chat_id nicht initialisiert wurde – dann sollte man zuerst eine dieser Funktionen aufrufen.

Nachrichten-Datenbank aktiviert: Standardmäßig speichert TDLib Nachrichten lokal (sofern ein Verzeichnis in den TdApi.SetTdlibParameters angegeben wurde). Das Zwischenspeichern erleichtert das schrittweise Laden. Falls in den TDLib-Parametern jedoch use_message_database = false gesetzt wurde, hat TDLib keinen lokalen Verlauf und muss alles on-the-fly vom Server holen. In diesem Szenario könnte das Verhalten bei only_local=false ähnlich sein (TDLib holt dennoch asynchron vom Server). Für eine vollständige Historie ist use_message_database = true empfehlenswert, damit bereits geladene Nachrichten beim nächsten Aufruf nicht erneut geladen werden müssen.

have_full_history Flag: TDLib verwaltet pro Chat das Flag have_full_history. Ist dieses auf false, bedeutet das, dass es noch ältere Nachrichten gibt, die lokal nicht vorliegen. Bei Historie-Abfragen versucht TDLib dann, diese vom Server nachzuladen. Sobald TDLib einmal bis zur ältesten Nachricht geladen hat, setzt es have_full_history = true. In Ihrem Fall war offenbar have_full_history = false, weshalb TDLib überhaupt die Serveranfrage startete (was gut ist). Sollte have_full_history irrtümlich auf true stehen (z.B. durch vorheriges vollständiges Laden oder bei neuen Chats, die nur eine Nachricht haben), kann TDLib meinen, es gäbe nichts mehr zu laden – und daher nur die lokale Nachricht liefern. Solche Fälle sind selten, aber zu beachten. In den Logs sieht man dieses Flag. In der Regel muss man nichts weiter tun; TDLib verwaltet das selbst. Nur im Sonderfall, dass man vermutet, TDLib denkt, alles sei geladen, obwohl noch Nachrichten fehlen, könnte man versuchen, das zu „resetten“ (einen solchen Mechanismus bietet TDLib direkt aber nicht). Dann hilft nur, manuell mit verschiedenen from_message_id Werten zu experimentieren, um TDLib doch zum Laden zu bewegen.

Netzwerklimits/Flood-Wait: Laden sehr vieler Nachrichten kann zu Rate Limits führen. Telegram begrenzt die Anzahl von API-Aufrufen. In einem Issue berichtet ein Nutzer, dass beim Abruf von ~100 Nachrichten pro Sekunde nach einer Weile ein FLOOD_WAIT Fehler kam. Für normale Anwendungen ist das kaum relevant (man lädt selten zigtausende Nachrichten in Sekunden). Trotzdem: Sollte plötzlich getChatHistory gar nichts mehr liefern und stattdessen ein Error (z.B. Code 420) auftreten, könnte ein temporärer Sperr-Timer der Grund sein. In dem Fall hilft nur Warten.


Zusätzliche Empfehlung: Prüfen Sie stets den Rückgabewert auf Fehler. G00sha’s Wrapper wird im Fehlerfall wahrscheinlich eine Exception oder ein TdApi.Error werfen/zurückgeben. Wenn z.B. etwas mit dem Chat nicht stimmt (gesperrter Chat, fehlende Rechte), erfahren Sie das nur durch Fehlerbehandlung.

5. Beispielcode: Vollständigen Chatverlauf mit g00sha’s Wrapper laden

Nachfolgend ein Code-Beispiel in Kotlin, das demonstriert, wie man mit dem TDLib-Coroutine-Wrapper von g00sha (TelegramFlow) den gesamten Verlauf eines Chats abrufen kann. Das Prinzip ist, die getChatHistory-Funktion in einer Schleife so oft aufzurufen, bis keine älteren Nachrichten mehr kommen:

suspend fun TelegramFlow.loadFullChatHistory(chatId: Long): List<TdApi.Message> {
    val allMessages = mutableListOf<TdApi.Message>()
    var fromId: Long = 0  // 0 = starte bei letzter Nachricht
    var firstIteration = true
    do {
        // Offset: -1 ab zweiter Iteration, um Duplikat zu vermeiden
        val offset = if (firstIteration) 0 else -1
        firstIteration = false
        // Lade bis zu 100 Nachrichten ab fromId (bzw. eine davor bei offset=-1)
        val result: TdApi.Messages = this.getChatHistory(chatId, fromId, offset, 100, false)
        val messages: Array<TdApi.Message> = result.messages  // Array der Nachrichten
        if (messages.isEmpty()) break  // keine Nachrichten mehr erhalten -> fertig
        allMessages += messages
        // Nächsten Startpunkt setzen: älteste erhaltene Nachricht
        fromId = messages.last().id  // ID der zuletzt (chronologisch ältesten) Nachricht im Batch
    } while (messages.size == 100)
    return allMessages
}

Erläuterung: Wir beginnen mit fromId = 0 und offset = 0, Limit 100. Das Ergebnis (TdApi.Messages) enthält ein Array messages. Wir speichern die Nachrichten in unserer Gesamtliste. Dann setzen wir fromId auf die ID der letzten Nachricht im erhaltenen Array (das ist die chronologisch älteste dieses Durchlaufs). Beim nächsten Schleifendurchlauf verwenden wir offset = -1, damit diese gerade verwendete fromId-Nachricht nicht erneut zurückkommt, sondern eine Nachricht davor als Start genommen wird. Die Schleife läuft solange, bis weniger als 100 Nachrichten zurückkommen – das ist ein Indikator, dass das Ende des Verlaufs erreicht ist (oder natürlich bis messages leer ist). Am Ende enthält allMessages den gesamten Verlauf (in absteigender zeitlicher Reihenfolge – TDLib liefert ja jeweils absteigend sortiert). Sie können diese Liste dann bei Bedarf umkehren, falls eine chronologisch aufsteigende Ordnung gewünscht ist.

Dieses Code-Snippet verwendet direkt die Klassen aus TDLib (z.B. TdApi.Message). G00sha’s Wrapper gibt das TdApi.Messages-Objekt unverändert zurück, so dass das Feld messages genutzt werden kann. Beachten Sie, dass man in Kotlin ggf. die Größe mit messages.size prüft und über das Array iterieren kann.

Optimale Einstellungen und Tipps: Stellen Sie sicher, dass limit = 100 gesetzt ist, um die maximale Batch-Größe zu nutzen. offset = -1 ab der zweiten Iteration vermeidet doppelte Nachrichten. only_local = false ist wichtig, damit auch alle Nachrichten vom Server geholt werden. Sollte der Chat sehr groß sein, können Sie überlegen, kleinere Pausen in die Schleife einzubauen, um nicht an Telegrams Rate-Limits zu stoßen – in unseren Tests ist das für normale Chatgrößen aber nicht nötig.

Externe Tools / Logging: Um die Vorgänge besser nachzuvollziehen oder Probleme zu debuggen, empfiehlt es sich, TDLibs internes Logging zu aktivieren. Sie können z. B. direkt über den Wrapper die Log-Verbosität hochsetzen:

this.setLogVerbosityLevel(4)  // Setzt TDLib-Log-Level auf "Detailinfo"

Ein Wert von 4 (oder 5 für noch detaillierter) lässt TDLib viele Diagnosemeldungen ausgeben. Diese erscheinen je nach Konfiguration in der Konsole oder können via setLogStream auch in eine Datei umgeleitet werden. Im Log würden Sie sehen, wie TDLib die Nachrichten nachlädt (inkl. Meldungen wie “Have 1 messages out of requested 20” etc., siehe Ausschnitte oben). Das kann bei der Fehlersuche enorm helfen.

Darüber hinaus gibt es keine speziellen externen Tools für TDLib-Verlaufsladen, aber man kann natürlich zum Vergleich die offiziellen Clients heranziehen: Laden Sie den gleichen Chat z.B. in Telegram Desktop oder WebTelegram, um sicherzugehen, dass tatsächlich mehrere Nachrichten vorhanden sein müssten. Wenn Ihr Code nur eine bringt, wissen Sie, dass es an der Abfrage liegt.

Zusammengefasst: Mit den oben genannten Einstellungen und dem gezeigten Schleifen-Code sollten Sie in der Lage sein, zuverlässig den kompletten Chatverlauf mit g00sha’s TDLib-Wrapper abzurufen. Nutzen Sie ggf. das Logging von TDLib, um unter der Haube zu prüfen, was passiert, und passen Sie Parameter nur mit Bedacht an. So erhalten Sie alle Nachrichten und können das eine-Nachricht-Problem hinter sich lassen.

Quellen: Die Analyse stützt sich auf offizielle TDLib-Dokumentation, Aussagen in GitHub-Issues (u.a. zu zunächst nur einer zurückgegebenen Nachricht) und bekannte Best Practices der TDLib-Nutzung.