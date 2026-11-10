<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="96" height="96" alt="ZenithW logosu">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  Medyayı kaydet. Sade tut.<br>
  Açık kaynaklı Android medya indiricisi. Reklam, abonelik veya zorunlu hesap yok.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest"><img src="https://img.shields.io/github/v/release/boranseasonnew/zenithw-android?style=for-the-badge&amp;label=Android&amp;labelColor=151922&amp;logo=android&amp;logoColor=white&amp;color=2E7D32" alt="En güncel Android sürümü"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&amp;labelColor=151922&amp;logo=android&amp;logoColor=white" alt="Android 7.0 ve üzeri">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0--only-64748B?style=for-the-badge&amp;labelColor=151922&amp;logoColor=white" alt="AGPL-3.0-only lisansı"></a>
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases"><img src="https://img.shields.io/github/downloads/boranseasonnew/zenithw-android/total?style=for-the-badge&amp;labelColor=151922&amp;label=Android%20downloads&amp;logo=github&amp;logoColor=white&amp;color=2E7D32" alt="Tüm GitHub yayınlarının Android indirme sayısı"></a>
</p>

<p align="center"><sub>İndirme rozeti GitHub yayın dosyalarının indirilme sayısını gösterir; tekil kullanıcıları veya diğer mağazalardaki kurulumları değil.</sub></p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">APK indir</a> ·
  <a href="https://zenithw.space">Web sitesi</a> ·
  <a href="https://github.com/boranseasonnew/zenithw-android/issues">Sorun bildir</a> ·
  <a href="README.md">English</a>
</p>

<p align="center"><strong><a href="https://github.com/boranseasonnew/zenithw/blob/main/why_zenithw.md">✦ ZenithW neden var? Hikâyemizi oku</a></strong></p>

ZenithW; Kotlin, Jetpack Compose, yt-dlp, FFmpeg ve Aria2c ile geliştirilmiş yerel bir Android uygulamasıdır. İndirme ve medya işleme, ZenithW'nin web indirme servisi üzerinden değil, cihazının kendi bağlantısıyla cihazında gerçekleşir.

## Özellikler

- **İndirme kontrolü:** mevcut video ve ses biçimlerini, kaliteyi, altyazıları, metaveriyi ve kapak görselini seç.
- **Kuyruk ve geçmiş:** birden fazla bağlantıyı ve oynatma listelerini yönet, ilerlemeyi takip et, işleri yeniden dene ve günlükleri incele.
- **Medya araçları:** desteklenen dosyaları cihazında dönüştür, kırp ve yönet; GIF/WebP ve altyazı iş akışlarını kullan.
- **Kişiselleştirme:** indirme profilleri, temalar, SponsorBlock, depolama ve ağ seçenekleri.

Uygulama **Türkçe, İngilizce, Almanca, Fransızca ve Rusça** dillerini destekler.

## İndir

En güncel APK için [GitHub Releases](https://github.com/boranseasonnew/zenithw-android/releases/latest) sayfasını aç. **Android 7.0 veya üzeri (API 24+)** gerekir.

Modern telefonların çoğu için `arm64-v8a`, eski 32 bit ARM cihazlar için `armeabi-v7a`, uyumlu cihaz ve emülatörler için `x86_64` dosyasını seç. Dosyayı yayınla birlikte sunulan SHA-256 sağlama toplamlarıyla doğrulayabilirsin. [İmza sertifikası parmak izi](release-certificate.sha256) de herkese açıktır.

## Belgeler

- [Sürümler ve yenilikler](https://github.com/boranseasonnew/zenithw-android/releases) · [Geliştirme ve test kapsamı](docs/android-3.1.0-report.md)
- [F-Droid paketleme durumu](docs/FDROID.md) · [Yerel bağımlılıklar ve sınırlar](docs/native-curl-NOTICES.md)

**16 KB bellek sayfası** gerektiren cihazlarla tam uyumluluk henüz doğrulanmadı. İndirme ve biçim seçenekleri kaynağın desteğine ve erişim koşullarına bağlıdır.

## Kaynak koddan derle

JDK 17, Android SDK Platform 36 ve Build Tools 36.0.0 gerekir.

```sh
python3 scripts/package_native_curl.py
./gradlew :app:assembleDebug
```

Windows'ta `python scripts/package_native_curl.py` ve `.\gradlew.bat :app:assembleDebug` komutlarını kullan. İmzalı sürümler [GitHub Actions](.github/workflows/android-release.yml) üzerinden oluşturulur.

## Katkı ve lisans

Hata bildirimleri, çeviriler ve kod katkıları bekliyoruz. Cookie veya özel bağlantı paylaşmadan [sorun bildirebilirsin](https://github.com/boranseasonnew/zenithw-android/issues).

ZenithW [AGPL-3.0-only](LICENSE) lisansıyla yayımlanır. Kullanılan açık kaynak bileşenler için [üçüncü taraf bildirimlerine](THIRD_PARTY_NOTICES.md) ve [Seal karşılaştırmasına](docs/seal-comparison.md) bakabilirsin. Yalnızca sahibi olduğun veya indirme iznine sahip olduğun içerikleri indir.
