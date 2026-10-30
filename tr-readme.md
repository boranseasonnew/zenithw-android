<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="88" height="88" alt="ZenithW logosu">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  Çevrim içi medyayı Android'de indirmek ve yönetmek için gizliliğe önem veren, açık kaynaklı bir uygulama.<br>
  Reklam, abonelik veya zorunlu ZenithW hesabı yok.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest"><img src="https://img.shields.io/github/v/release/boranseasonnew/zenithw-android?style=flat&color=8b5cf6" alt="Son sürüm"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0 ve üzeri">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases"><img src="https://img.shields.io/badge/yayın%20indirmesi-173-6366f1?logo=github" alt="30 Ekim 2026 itibarıyla GitHub yayın dosyaları 173 kez indirildi"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/Lisans-AGPL--3.0--only-64748b" alt="AGPL-3.0-only lisansı"></a>
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">Son APK'yı indir</a> ·
  <a href="https://zenithw.space/downloads">Web sitesi ve Windows</a> ·
  <a href="https://github.com/boranseasonnew/zenithw-android/issues">Sorun bildir</a> ·
  <a href="README.md">English</a>
</p>

## ZenithW nedir?

ZenithW, çevrim içi video ve sesleri kişisel çevrim dışı erişim için kaydetmeye yarayan yerel bir Android uygulamasıdır. **Kotlin**, **Jetpack Compose**, **yt-dlp**, **FFmpeg** ve **Aria2c** teknolojilerini tek bir arayüzde bir araya getirir.

İndirme ve medya işleme, cihazının internet bağlantısıyla cihazında gerçekleşir. Medyan ZenithW web indirme hizmetinden geçmez. ZenithW açık kaynaktır; kullanmak için hesap, abonelik veya reklam destekli bir hizmet gerekmez.

## Neler yapabilirsin?

- **Ne indireceğini seç:** video kalitesini, mevcut formatları, sesi, altyazıları, metaveriyi ve kapak görselini belirle.
- **İndirmeleri takip et:** kapak görselini, süreyi, dosya boyutunu, hızı ve ilerlemeyi gör; kaydedilen dosyaları geçmişten aç veya paylaş.
- **Birden fazla bağlantıyla çalış:** en fazla 50 bağlantı satırını kuyruğa ekle veya oynatma listesindeki belirli öğeleri seç.
- **İşleri yönet:** yt-dlp seçenek şablonlarını kaydet, iş günlüklerini görüntüleyip kopyala ve başarısız işleri yeniden dene.
- **Tercihlerini düzenle:** kayıt klasörünü, temayı, indirme seçeneklerini, SponsorBlock'u, ağ ayarlarını ve geçmiş davranışını seç.
- **Oturumlarını kaydet:** dahili tarayıcıyla cookie kaydet veya Netscape cookie dosyası aktar. Cookie profilleri depolanırken şifrelenir.

Arayüz **Türkçe, İngilizce, Almanca, Fransızca ve Rusça** dillerinde kullanılabilir. GitHub sürümü, Ayarlar'dan doğrulanmış yt-dlp kararlı veya nightly güncellemelerini de kontrol edebilir. Motor güncellemeleri, uygulama güncellemelerinden ayrıdır.

## Uygulamayı indir

En güncel kararlı sürüm **[2.4.1](https://github.com/boranseasonnew/zenithw-android/releases/tag/v2.4.1)**. Uygulama **Android 7.0 veya üzerini (API 24+)** gerektirir. Cihazına uygun APK'yı seç:

| APK | Kullanıldığı cihazlar |
| --- | --- |
| [arm64-v8a](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-arm64-v8a.apk) | Güncel Android telefon ve tabletlerin çoğu |
| [armeabi-v7a](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-armeabi-v7a.apk) | Eski 32 bit ARM cihazlar |
| [x86_64](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-x86_64.apk) | Uyumlu Intel/AMD cihazlar ve emülatörler |

Dosya bütünlüğünü [SHA256SUMS.txt](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/SHA256SUMS.txt) ile kontrol edebilirsin. Güncelleme imzasını doğrulamaya yardımcı olan herkese açık [imza sertifikası parmak izi](release-certificate.sha256) de depoda bulunur.

## Proje durumu

**2.4.1** sürümü arşivlenmiş öğelerin işlenmesini, tamamlanan indirme geçmişini ve indirme panelinin davranışını iyileştirir. Ayrıntılar [sürüm notlarında](releases/2.4.1.md). [GitHub Actions doğrulamasında](https://github.com/boranseasonnew/zenithw-android/actions/runs/40908115943) Android 13 ve Android 7 emülatör kontrolleri, lint, JVM ve Python testleri ile imzalı paket kontrolleri geçti. Bu sürüm **fiziksel bir cihazda YouTube indirmesiyle doğrulanmadı**.

İndirmeler yt-dlp desteğine ve kaynağın erişim koşullarına bağlıdır. Paketlenen Android çalışma ortamında şu anda `curl_cffi` impersonation desteği bulunmuyor; bu nedenle cookie veya VPN erişim garantisi vermez. Yerel 16 KB bellek sayfası uyumluluğu tam olarak doğrulanmış değildir.

[F-Droid başvurusu](docs/FDROID.md) beklemededir.

## Kaynak koddan derleme

Debug APK oluşturmak için **JDK 17**, **Android SDK Platform 36** ve **Build Tools 36.0.0** gerekir. SDK yolunu Android Studio'dan veya `local.properties` dosyasından ayarlayıp şu komutu çalıştır:

```sh
./gradlew :app:assembleDebug
```

Windows'ta `.\\gradlew.bat :app:assembleDebug` komutunu kullan. APK, `app/build/outputs/apk/debug/` klasörüne yazılır. Resmî imzalı sürümler [GitHub Actions](.github/workflows/android-release.yml) tarafından derlenip yayımlanır.

## Katkı ve lisans

[boranseason](https://github.com/boranseasonnew) tarafından geliştirildi. ZenithW [AGPL-3.0-only](LICENSE) lisansı altındadır; bağımlılıklar kendi lisanslarını korur.

[Seal](https://github.com/JunkFood02/Seal) v1.13.1, indirme yapılandırması, ayarlar ve terminal iş akışları için referans oldu. Bu özellikler bağımsız olarak geliştirildi; Seal'in kaynak kodu veya görselleri uygulamaya dahil edilmedi. Ayrıntılar: [karşılaştırma](docs/seal-comparison.md) · [üçüncü taraf bildirimleri](THIRD_PARTY_NOTICES.md).

Bir sorunla karşılaşırsan uygulama ve Android sürümünü, sorunu tekrarlama adımlarını ve hassas bilgilerden arındırılmış hata mesajını [Issues](https://github.com/boranseasonnew/zenithw-android/issues) üzerinden paylaş. Cookie'leri ve özel bağlantıları herkese açık raporlara ekleme. ZenithW'yi yalnızca sahibi olduğun veya indirme iznine sahip olduğun medyalarda kullan.
