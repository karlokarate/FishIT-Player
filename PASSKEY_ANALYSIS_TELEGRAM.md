# Telegram Passkey Authentication - Analyse & Implementierung

**Date:** 2026-01-25  
**tdl-coroutines Version:** 7.0.0+ (erstmals verfügbar)  
**Status:** Optional Feature (nicht für unsere App relevant)

---

## Was sind Telegram Passkeys?

### 📱 Definition

**Passkeys** sind Telegrams Implementierung des **FIDO2/WebAuthn Standards** für **passwordlose biometrische Authentifizierung**.

**Einfach gesagt:**
- **Alternative zu Phone Number + SMS Code**
- **Biometrische Anmeldung** (Face ID, Fingerprint, Touch ID)
- **Hardware-basierte Sicherheit** (Secure Enclave, TPM)
- **Platform Authenticator** (iOS Keychain, Android Keystore, Windows Hello)

---

## Wie funktionieren Telegram Passkeys?

### 🔐 Standard-Login (aktuell in unserer App)

```text
1. User gibt Telefonnummer ein
2. Telegram schickt SMS Code
3. User gibt Code ein
4. Optional: 2FA Password
5. Session Token wird gespeichert
```

**Problem:** SMS können abgefangen werden, 2FA Password kann vergessen werden.

---

### 🆕 Passkey-Login (neue Alternative)

```text
1. User wählt "Login with Passkey"
2. OS zeigt biometrischen Prompt (Face ID / Fingerprint)
3. Passkey wird mit Hardware Key signiert
4. Telegram verifiziert Signatur
5. Session Token wird gespeichert
```

**Vorteil:**
- ✅ Kein SMS nötig (funktioniert offline)
- ✅ Phishing-sicher (Domain-gebunden)
- ✅ Hardware-geschützt (kann nicht kopiert werden)
- ✅ Schneller (1 Touch statt Code eingeben)

---

## DTO Struktur in tdl-coroutines

### Analysierte Klassen

```kotlin
// 1. Passkey Entity (repräsentiert einen gespeicherten Passkey)
data class Passkey(
    val id: String,                           // Unique Passkey ID
    val name: String,                         // User-friendly name ("iPhone 15 Pro")
    val additionDate: Int,                    // Unix timestamp when added
    val lastUsageDate: Int,                   // Unix timestamp last used
    val softwareIconCustomEmojiId: Long       // Optional: Custom emoji for device icon
)

// 2. Passkeys Collection (Liste aller User Passkeys)
data class Passkeys(
    val passkeys: List<Passkey>               // All registered passkeys for user
)

// 3. Suggested Action (Telegram schlägt vor, Passkey hinzuzufügen)
object SuggestedActionAddLoginPasskey : SuggestedAction

// Vermutlich auch (nicht direkt sichtbar, aber logisch nötig):
// - AddLoginPasskey (TDLib Request)
// - DeletePasskey (TDLib Request)
// - GetPasskeys (TDLib Request)
// - AuthorizationStateWaitPasskey (neuer Auth State?)
```

---

## Telegram Passkey Workflow

### 1️⃣ Passkey Registrierung (Setup)

```kotlin
// User ist bereits eingeloggt (mit Phone + SMS)
// Telegram schlägt vor: "Add Passkey for faster login"

// Flow in tdl-coroutines:
fun addPasskey(name: String): Flow<Passkey> {
    // 1. Request an Telegram: "User möchte Passkey erstellen"
    client.send(AddLoginPasskey(name = name))
    
    // 2. Telegram generiert Challenge
    // 3. TDLib ruft Platform Authenticator auf (iOS Keychain)
    // 4. User bestätigt mit Face ID / Fingerprint
    // 5. Public Key wird an Telegram geschickt
    // 6. Passkey wird gespeichert
    
    // 7. Telegram gibt Passkey-Objekt zurück
    return client.updates.filterIsInstance<UpdatePasskey>()
        .map { it.passkey }
}
```

**Platform Flow (iOS Beispiel):**
```swift
// iOS ruft automatisch auf:
import AuthenticationServices

ASAuthorizationController.performRequest {
    // Challenge von Telegram
    challenge: Data
    relyingParty: "telegram.org"
    userID: "<telegram_user_id>"
}

// User sieht Face ID Prompt
// Nach Erfolg: Private Key wird in iOS Keychain gespeichert
// Public Key geht an Telegram
```

---

### 2️⃣ Passkey Login (Auth)

```kotlin
// User öffnet App, möchte sich anmelden
// Telegram sieht: "Dieser User hat Passkeys"

// Authorization State Machine ändert sich:
sealed class AuthorizationState {
    // Bisherige States:
    object WaitPhoneNumber : AuthorizationState()
    object WaitCode : AuthorizationState()
    object WaitPassword : AuthorizationState()
    
    // NEU (vermutlich):
    data class WaitPasskey(
        val availablePasskeys: List<Passkey>  // Gespeicherte Passkeys für diesen User
    ) : AuthorizationState()
}

// Flow:
authorizationStateUpdates.collect { state ->
    when (state) {
        is WaitPasskey -> {
            // 1. Zeige "Login with Passkey" Button
            // 2. User tappt Button
            client.send(CheckAuthenticationPasskey(passkeyId = state.availablePasskeys.first().id))
            
            // 3. TDLib ruft Platform Authenticator auf
            // 4. User bestätigt mit Face ID
            // 5. Challenge wird signiert mit Private Key
            // 6. Telegram verifiziert Signatur
            // 7. Authorization Complete!
        }
    }
}
```

---

### 3️⃣ Passkey Management

```kotlin
// User kann seine Passkeys verwalten:

// Liste aller Passkeys abrufen
suspend fun getPasskeys(): Passkeys {
    return client.send(GetPasskeys())
}

// Passkey löschen (z.B. Device verloren)
suspend fun deletePasskey(passkeyId: String) {
    client.send(DeletePasskey(id = passkeyId))
}

// Passkey umbenennen
suspend fun renamePasskey(passkeyId: String, newName: String) {
    // Vermutlich: SetPasskeyName() oder ähnlich
}
```

**UI Beispiel:**
```
Settings → Security → Passkeys
├── iPhone 15 Pro (Last used: 2 hours ago) [Delete]
├── MacBook Pro (Last used: 3 days ago) [Delete]
└── + Add New Passkey
```

---

## Ist Passkey-Implementierung einfach?

### ⚠️ NEIN - Komplex & plattformspezifisch

#### Schwierigkeitsgrad: **8/10** 🔴

**Warum komplex?**

1. **Platform-spezifische APIs:**
   ```kotlin
   // Android
   import androidx.credentials.CredentialManager
   import androidx.credentials.CreatePublicKeyCredentialRequest
   import androidx.credentials.GetCredentialRequest
   
   // iOS (über KMP)
   import platform.AuthenticationServices.ASAuthorizationController
   
   // Desktop (nicht supported)
   // Windows Hello / macOS Touch ID über JNI
   ```

2. **Compose-multiplatform Support:**
   - Android: `androidx.credentials` (neu, komplex)
   - iOS: `AuthenticationServices` (Swift Interop nötig)
   - TV: ❌ **NICHT SUPPORTED** (keine Biometrie auf Fire TV!)

3. **TDLib Integration:**
   - Neue Auth States handhaben
   - Challenge/Response Flow implementieren
   - Error Handling (Passkey ungültig, Device verloren)

4. **UI/UX Komplexität:**
   - Passkey Setup Flow (Onboarding)
   - Passkey Selection (mehrere Devices)
   - Fallback auf SMS Code
   - Management UI (Liste, Löschen, Umbenennen)

5. **Security:**
   - FIDO2 Protocol korrekt implementieren
   - Replay Attacks verhindern
   - Domain Binding (telegram.org)

---

### Implementierungsaufwand: **~3-4 Wochen** 👨‍💻

| Phase | Aufwand | Tasks |
|-------|---------|-------|
| **Platform APIs** | 1 Woche | Android Credentials API, iOS ASAuthorization |
| **TDLib Integration** | 1 Woche | Auth State Machine, Request Handling |
| **UI/UX** | 1 Woche | Setup Flow, Management Screen |
| **Testing** | 1 Woche | Real Devices, Edge Cases, Fallbacks |

---

## Brauchen wir Passkeys für FishIT-Player?

### ❌ NEIN - Nicht sinnvoll für unsere App

#### Gründe:

1. **🔴 Fire TV / Android TV haben KEINE Biometrie**
   - Keine Face ID, kein Fingerprint
   - Remote Control hat keine biometrischen Sensoren
   - **Passkeys funktionieren auf TV NICHT**

2. **🔴 Unsere User-Gruppe:**
   - TV-Nutzer erwarten **keine** komplexe Authentifizierung
   - **QR-Code Login** ist für TV der beste Weg
   - Oder: **Einmaliges Login + Session speichern**

3. **🔴 Implementation Complexity vs. Benefit:**
   - 3-4 Wochen Arbeit
   - Funktioniert nur auf Mobile (nicht TV)
   - **Kein echter Mehrwert** für Media Player Use Case

4. **✅ Bessere Alternativen:**
   - **QR Code Login** (wie Telegram Desktop)
     - User scannt QR mit Phone App
     - Instant Login ohne Tippen
     - Funktioniert auf TV perfekt
   
   - **Session Persistence** (bereits implementiert)
     - User loggt sich einmal ein
     - Session bleibt aktiv (Monate/Jahre)
     - Kein Re-Login nötig

---

## Was bedeutet das für unser Upgrade?

### ✅ Passkey-Feature ist KEIN Blocker

**Beim Upgrade auf tdl-coroutines 8.0.0:**

1. **Passkey DTOs werden hinzugefügt** ✅
   - Aber: Wir NUTZEN sie nicht
   - Keine Breaking Changes für unsere Auth

2. **Unser Auth Flow bleibt unverändert** ✅
   ```kotlin
   // Bleibt exakt gleich:
   authorizationStateUpdates.collect { state ->
       when (state) {
           is WaitPhoneNumber -> showPhoneInput()
           is WaitCode -> showCodeInput()
           is WaitPassword -> show2FAInput()
           is Ready -> navigateToHome()
           
           // NEU (aber wir ignorieren es):
           is WaitPasskey -> {
               // Fallback: SMS Code benutzen
               client.send(RequestAuthenticationCode())
           }
       }
   }
   ```

3. **Kein zusätzlicher Code nötig** ✅
   - Passkey ist optional
   - TDLib fällt automatisch auf SMS zurück
   - Keine Änderungen an unserer Auth-Implementierung

---

## Zusammenfassung

### 📊 Passkey Quick Facts

| Aspekt | Details |
|--------|---------|
| **Was ist es?** | FIDO2 biometrische Auth (Face ID, Fingerprint) |
| **Verfügbar ab** | tdl-coroutines 7.0.0+ |
| **Platform Support** | iOS ✅, Android ✅, TV ❌, Desktop ⚠️ |
| **Complexity** | Hoch (8/10) |
| **Implementierung** | ~3-4 Wochen |
| **Für uns relevant?** | ❌ NEIN (TV hat keine Biometrie) |
| **Upgrade Blocker?** | ❌ NEIN (optional, backward compatible) |

---

### 🎯 Empfehlung

**✅ Upgrade auf tdl-coroutines 8.0.0 OHNE Passkey-Implementierung**

**Begründung:**
1. Passkey ist **optional** - wir können es ignorieren
2. Unser bestehender Auth Flow **funktioniert weiter**
3. **Kein Mehrwert** für TV Use Case
4. **Aufwand nicht gerechtfertigt** (3-4 Wochen für Feature, das auf TV nicht funktioniert)

**Bessere Alternativen:**
1. ✅ **QR Code Login** implementieren (wie Telegram Desktop)
   - Aufwand: ~1 Woche
   - Funktioniert auf TV perfekt
   - User scannt mit Phone App

2. ✅ **Session Persistence** optimieren (bereits vorhanden)
   - User bleibt eingeloggt
   - Kein Re-Login nötig

---

## Code-Beispiel: Wie Passkey ignoriert wird

```kotlin
// infra/transport-telegram/TelegramAuthClient.kt

suspend fun handleAuthorizationState(state: AuthorizationState) {
    when (state) {
        is AuthorizationStateWaitPhoneNumber -> {
            // User gibt Telefonnummer ein
            setAuthenticationPhoneNumber(phoneNumber)
        }
        
        is AuthorizationStateWaitCode -> {
            // User gibt SMS Code ein
            checkAuthenticationCode(code)
        }
        
        is AuthorizationStateWaitPassword -> {
            // User gibt 2FA Password ein
            checkAuthenticationPassword(password)
        }
        
        // NEU in tdl-coroutines 7.0.0+ (aber wir ignorieren es):
        is AuthorizationStateWaitPasskey -> {
            // Option 1: Fallback auf SMS
            requestAuthenticationCode()
            
            // Option 2: User informieren
            emit(AuthState.Error("Passkey login not supported. Please use phone number."))
        }
        
        is AuthorizationStateReady -> {
            // Auth erfolgreich
            emit(AuthState.Success)
        }
    }
}
```

**Ergebnis:**
- ✅ Upgrade funktioniert
- ✅ Keine Code-Änderungen nötig (außer optionalem Fallback)
- ✅ User können weiterhin mit Phone + SMS Code einloggen

---

## Wenn wir Passkey DOCH implementieren wollten (Zukunft)

### Prerequisites:

1. **Mobile App Version bauen** (nicht nur TV)
   - Android APK für Phones/Tablets
   - iOS App (via Kotlin Multiplatform)

2. **Platform APIs integrieren:**
   ```kotlin
   // Android
   implementation("androidx.credentials:credentials:1.3.0")
   implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
   
   // iOS (KMP)
   cocoapods {
       pod("AuthenticationServices")
   }
   ```

3. **TDLib Requests implementieren:**
   ```kotlin
   // Passkey hinzufügen
   suspend fun addPasskey(name: String): Passkey {
       return client.send(AddLoginPasskey(name))
   }
   
   // Passkeys abrufen
   suspend fun getPasskeys(): List<Passkey> {
       return client.send(GetPasskeys()).passkeys
   }
   
   // Mit Passkey authentifizieren
   suspend fun authenticateWithPasskey(passkeyId: String) {
       client.send(CheckAuthenticationPasskey(passkeyId))
   }
   ```

4. **UI Flows:**
   - Passkey Setup Wizard
   - Passkey Selection Screen
   - Management Settings

**Aufwand:** 3-4 Wochen Full-Time Development

---

## Fazit

**Passkey ist ein spannendes Feature, aber:**

❌ **Nicht für TV Use Case geeignet**  
❌ **Hoher Implementierungsaufwand**  
❌ **Kein Mehrwert für unsere User**  
✅ **Upgrade trotzdem safe** (Passkey ist optional)  
✅ **Wir können es ignorieren**

**👉 Empfehlung: Upgrade JETZT, Passkey IGNORIEREN**

Wenn wir in Zukunft eine Mobile App Version bauen, können wir Passkeys dann hinzufügen. Für die TV-App ist es irrelevant und nicht implementierbar.
