# AuraGlass

**AuraGlass**, Jetpack Compose ve Material Design 3 tabanlı, **Liquid Glass** (akışkan buzlu cam) tasarım diline sahip; uçtan uca şifreli bulut kasası (**AES-256-GCM**), çevrimdışı çalışma modu, gelişmiş veri analitiği raporları, çok dilli arayüz ve **Firebase AI Logic** destekli çok modlu Gemini stüdyosunu bir araya getiren modern bir Android uygulamasıdır.

---

## Temel Özellikler

### Tasarım ve Kullanıcı Deneyimi
- **Liquid Glass Arayüz**: Nefes alan ambiyans arka planı, ışık kırılımlı gradient kenarlıklı cam kartlar (`LiquidGlassCard`), minimalist ikonlar ve yay (spring) tabanlı dokunma animasyonları.
- **Karanlık Mod & Uyarlanabilir Düzen**: Sistem, Karanlık ve Aydınlık tema seçenekleri; kompakt ekranlarda `NavigationBar`, tablet/geniş ekranlarda (`>= 600dp`) `NavigationRail` ile duyarlı düzen.
- **Çok Dilli Destek**: Uygulama içi **Türkçe (`tr`)**, **English (`en`)**, **Deutsch (`de`)** ve **Español (`es`)** dil desteği ve Gemini yanıtlarının seçili dile uyarlanması.

### Güvenlik, Bulut Senkronizasyonu ve Çevrimdışı Mod
- **Uçtan Uca Şifreleme (E2EE)**: Kasa kayıtları buluta gönderilmeden önce cihaz üzerinde `PBKDF2WithHmacSHA256` ile türetilen anahtar ve **AES-256-GCM** (12 bayt rastgele IV + bütünlük SHA-256 parmak izi) kullanılarak şifrelenir.
- **Bulut Senkronizasyonu & Zero-Trust Kurallar**: Jetpack `CredentialManager` üzerinden Google ile Giriş ve kullanıcı bazlı izole **Cloud Firestore** koleksiyonları (`/users/{userId}/...`).
- **Çevrimdışı Çalışma Modu**: Bağlantı kesildiğinde veya manuel çevrimdışı mod açıldığında şifreli kayıtlar yerel **Room** kuyruğuna (`offline_vault_queue`) yazılır ve bağlantı sağlandığında tek dokunuşla buluta senkronize edilir.

### Çok Modlu Gemini AI Stüdyosu (Firebase AI Logic)
Tüm bulut yapay zeka çağrıları **Firebase AI Logic** (`com.google.firebase:firebase-ai`) ve **Firebase App Check** koruması üzerinden yürütülür:
- **Çok Turlu Zeka Sohbeti**:
  - `gemini-4-hydrogen` (Genel Amaçlı)
  - `gemini-4-argon` (Karmaşık Analiz)
  - `gemini-4-carbon` (`ThinkingLevel.HIGH` ile düşünme süreci özeti)
  - `gemini-3.1-flash-lite` (Düşük Gecikme)
  - **Google Arama** ve **Google Harita** Grounding doğrulama kaynakları
- **Görsel Oluşturma ve Düzenleme**: `gemini-nano-banana-2.1` ve `gemini-3-pro-image-preview` (`1K` / `2K` / `4K`, 8 farklı en-boy oranı, Android Photo Picker ile fotoğraf düzenleme).
- **Ses, Müzik ve Video**:
  - Metinden Sese (TTS): `gemini-3.8-flash-tts`
  - Müzik Üretimi: `lyria-3-clip-preview` / `lyria-3-pro-preview`
  - Video Üretimi (`16:9` / `9:16`): `gemini-omni-1.2-flash`
  - Medya Analizi (Fotoğraf/Video): `gemini-4-argon`
  - Mikrofon Ses Deşifresi: `gemini-3.5-transcribe`
  - Canlı Sesli Sohbet (Live API): `gemini-3.8-live`

### Harita, Bildirimler ve Gelişmiş Veri Analitiği
- **Harita Keşfi**: `maps-compose`, `FusedLocationProviderClient` ve Google Harita Grounding ile yakın mekan arama ve şifreli olarak kasaya kaydetme.
- **Kişiselleştirilebilir Bildirimler**: Günlük Analitik Özeti, Bulut Senkronizasyon/Güvenlik ve Yapay Zeka Uyarıları için ayrı bildirim kanalları, **Sessiz Saatler** ayarları ve Android 13+ (`POST_NOTIFICATIONS`) çalışma zamanı izin yönetimi.
- **Veri Analitiği Raporları**: Canvas tabanlı gecikme trend grafiği, E2EE kapsama göstergesi, özellik kullanım dağılım çubukları ve tek dokunuşla `gemini-4-argon` yönetici analitik raporu oluşturma.

---

## Teknoloji Yığını

| Katman | Teknolojiler |
| :--- | :--- |
| **Dil & Arayüz** | Kotlin 2.2.10, Jetpack Compose (BOM 2024.09.00), Material 3, Navigation Compose |
| **Mimari** | MVVM + Repository Pattern (`ViewModel`, `StateFlow`, Kotlin Coroutines & Flow) |
| **Bulut & Kimlik** | Firebase Auth (Google Sign-In / Credential Manager), Cloud Firestore, Firebase App Check |
| **Yapay Zeka** | Firebase AI Logic (`firebase-ai`, Gemini 4, Lyria 3, Omni, Live API) |
| **Yerel Depolama** | Room (`offline_vault_queue`), Jetpack DataStore Preferences (`auraglass_prefs`) |
| **Güvenlik** | `javax.crypto` AES/GCM/NoPadding (256-bit), PBKDF2WithHmacSHA256 |
| **Harita & Konum** | Maps Compose (`maps-compose`), Play Services Maps & Location |
| **Test** | JUnit 4, Robolectric, Firebase Firestore Emulator Security Rules Testleri (`node --test`) |

---

## Proje Yapısı

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # App Check başlatma ve edge-to-edge giriş noktası
├── auth/
│   └── GoogleAuthManager.kt         # Credential Manager Google Sign-In & oturum akışı
├── data/
│   ├── ai/AuraAiService.kt          # Firebase AI Logic (Chat, Grounding, Studio, Live, Analitik)
│   ├── local/
│   │   ├── OfflineDatabase.kt       # Room çevrimdışı şifreli senkronizasyon kuyruğu
│   │   └── UserPreferencesStore.kt  # DataStore tema, dil, çevrimdışı ve bildirim tercihleri
│   ├── model/AuraModels.kt          # Firestore veri modelleri
│   ├── remote/FirestoreErrorHandler.kt
│   └── repository/AuraRepository.kt # Firestore + Room senkronizasyon katmanı
├── security/
│   ├── AppCheckInitializer.kt       # Debug & reCAPTCHA App Check yapılandırması
│   └── E2eeCryptoManager.kt         # AES-256-GCM şifreleme/çözme ve PBKDF2 anahtar türetme
├── ui/
│   ├── AuraApp.kt                   # Tip güvenli Navigation Compose ve uyarlanabilir kabuk
│   ├── components/                  # LiquidGlassComponents & AnalyticsCharts
│   ├── i18n/AppStrings.kt           # Türkçe, İngilizce, Almanca ve İspanyolca yerelleştirme
│   ├── screens/                     # SignIn, Vault, Chat, Studio, Maps, Analytics, Settings
│   ├── theme/                       # Obsidian Frost & Glacier Mist tema paletleri + tipografi
│   └── viewmodel/AuraViewModel.kt   # Uygulama durum yönetimi
└── util/                            # Bağlantı izleyici, bildirim yöneticisi, PCM/WAV ses yardımcıları
```

---

## Kurulum ve Yapılandırma

### Gereksinimler
- Android Studio (AGP `9.1.1` / JDK 11+ uyumlu)
- Android SDK `compileSdk 36`, `minSdk 24`, `targetSdk 36`
- Firebase projesi (Authentication, Cloud Firestore, Firebase AI Logic & App Check etkin)

### 1. Ortam Değişkenleri (`.env`)
Projede Google Maps SDK harita karoları için isteğe bağlı `MAPS_API_KEY` değişkeni **Secrets Gradle Plugin** ile yönetilir. `.env.example` dosyasını referans alarak `.env` dosyanızı oluşturun (veya Google AI Studio kullanıyorsanız **Secrets** panelinden ekleyin):

```properties
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

> **Not:** Tüm Gemini yapay zeka özellikleri **Firebase AI Logic** üzerinden çalıştığı için `GEMINI_API_KEY` tanımlamasına ihtiyaç duymaz. `app/google-services.json` dosyanızı Firebase Console üzerinden projenize eklemeyi unutmayın.

### 2. Derleme ve Testler

Debug APK derlemek için:
```bash
./gradlew assembleDebug
```

Birim ve Robolectric güvenlik kuralı testlerini çalıştırmak için:
```bash
./gradlew :app:testDebugUnitTest
```

Firestore güvenlik kurallarını Firebase Emulator üzerinde doğrulamak için:
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

---

## Güvenlik ve Gizlilik
- Kullanıcı notları istemci tarafında **AES-256-GCM** ile şifrelenmeden buluta aktarılmaz.
- Firestore erişimleri `firestore.rules` ile sıkı biçimde yalnızca kimliği doğrulanmış kullanıcıya (`request.auth.uid == userId`) kısıtlanmıştır.
- Fotoğraf/video seçimleri depolama izni gerektirmeyen **Android Photo Picker** (`PickVisualMedia`) üzerinden yapılır.
