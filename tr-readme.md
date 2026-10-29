<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="88" height="88" alt="ZenithW logosu">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  Video ve sesleri doğrudan Android cihazına indir.<br>
  Açık kaynak. Reklam, abonelik veya zorunlu ZenithW hesabı yok.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest"><img src="https://img.shields.io/github/v/release/boranseasonnew/zenithw-android?style=flat&color=8b5cf6" alt="Son sürüm"></a>
  <a href="#kurulum"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 7.0 ve üzeri"></a>
  <a href="https://github.com/boranseasonnew/zenithw-android/releases"><img src="https://img.shields.io/github/downloads/boranseasonnew/zenithw-android/total?color=6366f1" alt="GitHub indirme sayısı"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-AGPL--3.0--only-64748b" alt="AGPL-3.0-only lisansı"></a>
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">APK indir</a> ·
  <a href="https://zenithw.space/downloads">Web sitesi ve Windows</a> ·
  <a href="https://github.com/boranseasonnew/zenithw-android/issues">Hata bildir</a> ·
  <a href="README.md">English</a>
</p>

ZenithW; **yt-dlp**, **FFmpeg** ve **Aria2c** kullanan, Kotlin ve Jetpack Compose ile geliştirilmiş bir Android uygulamasıdır. İndirme ve medya işleme cihazında, cihazının internet bağlantısıyla gerçekleşir. Medyan ZenithW web indirme sunucusundan geçmez.

## Kurulum

**Güncel kararlı sürüm: [2.4.1](https://github.com/boranseasonnew/zenithw-android/releases/tag/v2.4.1).** **Android 7.0+ (API 24)**, internet bağlantısı ve yeterli boş depolama alanı gerekir. Cihazına uygun imzalı APK'yı seç:

| APK mimarisi | Hangi cihazlar için? |
| --- | --- |
| [**arm64-v8a APK**](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-arm64-v8a.apk) | Güncel Android telefon ve tabletlerin çoğu |
| [**armeabi-v7a APK**](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-armeabi-v7a.apk) | 32 bit ARM Android çalıştıran eski cihazlar |
| [**x86_64 APK**](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/Zenith-2.4.1-stable-x86_64.apk) | Uyumlu Intel/AMD Android cihazları ve emülatörler |

APK'yı aç; Android isterse kullandığın tarayıcıya veya dosya yöneticisine yükleme izni ver. Mevcut stable kurulumunu aynı imzalı sürümle güncelleyebilirsin. İndirdiğin dosyayı [SHA256SUMS.txt](https://github.com/boranseasonnew/zenithw-android/releases/download/v2.4.1/SHA256SUMS.txt) ile doğrulayabilirsin; [imza sertifikasının parmak izi](release-certificate.sha256) de herkese açıktır.

## Neler yapabilirsin?

- **Video ve ses:** kalite, kaynak formatı ve ses seçeneklerini belirle; desteklendiğinde altyazı, metaveri ve kapak indir.
- **Canlı ilerleme:** ana ekranda kapak, süre, dosya boyutu, hız ve ilerlemeyi gör; tamamlanan dosyaları geçmişten aç veya paylaş.
- **Toplu indirme ve listeler:** **+** ile 50 bağlantı satırına kadar ekle veya oynatma listesinden istediğin öğeleri seç.
- **Terminal alanı:** yt-dlp seçenek şablonlarını kaydet; iş günlüklerini görüntüle, kopyala ve işleri yeniden dene.
- **Kişisel tercihler:** hedef klasörü, temayı, indirme seçeneklerini, SponsorBlock'u, ağ ayarlarını ve geçmiş davranışını düzenle.
- **Kayıtlı oturumlar:** dahili tarayıcıdan cookie kaydet veya Netscape cookie dosyası aktar. Profiller depolanırken şifrelenir.

Arayüz **Türkçe, İngilizce, Almanca, Fransızca ve Rusça** sunulur. GitHub sürümünde yt-dlp stable/nightly motorunu, uygulama güncellemesinden ayrı olarak Ayarlar'dan doğrulamalı biçimde güncelleyebilirsin.

## İlk indirme

1. Medya bağlantısını yapıştır veya başka bir uygulamadan ZenithW'ye paylaş.
2. Medya bilgileri yüklenirken indirme panelinden video, ses veya altyazıyı seç.
3. İndirmeyi başlat ve ilerlemeyi ana ekrandan takip et.

## 2.4.1 yenilikleri

- Arşivde kayıtlı videolar “No output files” hatası yerine atlandı olarak gösterilir. Tekrar dene, arşiv tercihini değiştirmeden videoyu yeniden indirir.
- Tamamlanan indirme kartları ana ekrandan kaldırılır; kaydedilen dosyalara geçmişten erişilir.
- İndirme panelinin açılma ve kapanma akışı düzeltildi; tekrar dokunulduğunda açık panel kullanılır ve indirmeye başlarken erken gelen gerçek başlık korunur.

Ayrıntılar: [sürüm notları](releases/2.4.1.md). [GitHub Actions doğrulamasında](https://github.com/boranseasonnew/zenithw-android/actions/runs/40908115943) Android 13/7 emülatör kontrolleri, lint, JVM ve Python testleri ile imzalı paket kontrolleri geçti. Bu düzeltme **fiziksel cihazda YouTube indirmesiyle doğrulanmadı**.

2.4.0'da gelen anında önizleme sisteminin ölçümleri ve test sınırları [performans raporunda](docs/metadata-performance-2.4.0.md) bulunur. Paketlenen yt-dlp sürümü **2026.10.21**; Ayarlar'dan güncellenen motorun sürümü uygulama sürümünden farklı olabilir.

## Dağıtım ve sınırlar

İndirmeler, yt-dlp desteğine ve kaynağın erişim koşullarına bağlıdır. Cookie veya VPN erişim garantisi vermez; paketlenen Android çalışma ortamında henüz `curl_cffi` impersonation desteği yoktur. Tam yerel 16 KB bellek sayfası uyumluluğu doğrulanmış değildir.

**F-Droid başvurusu beklemede.** Kaynak kodda, harici motor güncellemeleri kapalı ayrı bir paketleme çeşidi bulunur. Ayrıntılar: [F-Droid paketleme](docs/FDROID.md).

## Kaynak koddan derleme

**JDK 17**, **Android SDK Platform 36** ve **Build Tools 36.0.0** kullan. SDK yolunu Android Studio veya `local.properties` üzerinden ayarlayıp çalıştır:

```sh
./gradlew :app:assembleDebug
```

Windows'ta: `.\gradlew.bat :app:assembleDebug`. APK'lar `app/build/outputs/apk/debug/` altında oluşur. Debug kurulumunu güncelleyebilmek için üretilen `.signing/preview.keystore` dosyasını sakla. Resmî imzalı sürümler [GitHub Actions](.github/workflows/android-release.yml) üzerinde derlenip yüklenir.

## Emeği geçenler ve lisans

[boranseason](https://github.com/boranseasonnew) tarafından yapılmıştır. [AGPL-3.0-only](LICENSE) lisansıyla sunulur; bağımlılıklar kendi lisanslarını korur.

[Seal](https://github.com/JunkFood02/Seal) v1.13.1; indirme paneli, ayarlar ve terminal akışları için referans alındı. Bu akışlar bağımsız uygulandı; Seal kaynak kodu veya görselleri pakete dahil edilmedi. Ayrıntılar: [karşılaştırma](docs/seal-comparison.md) · [üçüncü taraf bildirimleri](THIRD_PARTY_NOTICES.md).

Sorun bulduysan uygulama/Android sürümünü, tekrarlama adımlarını ve hassas bilgilerden arındırılmış hata mesajını [Issues](https://github.com/boranseasonnew/zenithw-android/issues) üzerinden paylaş. Cookie'leri ve özel bağlantıları herkese açık raporlara ekleme. ZenithW'yi sana ait veya indirme iznin olan içerikler için kullan.
