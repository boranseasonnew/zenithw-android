<p align="center">
  <img src="app/src/main/res/drawable/zenithw.png" width="96" height="96" alt="ZenithW logosu">
</p>

<h1 align="center">ZenithW Android</h1>

<p align="center">
  Medyayı cihaz üzerinde işleyen, açık kaynaklı video ve ses indirici.<br>
  Reklam, abonelik veya zorunlu ZenithW hesabı yok.
</p>

<p align="center">
  <a href="https://github.com/boranseasonnew/zenithw-android/releases/latest">APK indir</a> ·
  <a href="https://zenithw.space">Web sitesi</a> ·
  <a href="https://github.com/boranseasonnew/zenithw">Web ve Windows</a> ·
  <a href="README.md">English</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-2.3.0-222222" alt="Sürüm 2.3.0">
  <img src="https://img.shields.io/badge/Android-7.0%2B-444444" alt="Android 7.0 ve üzeri">
  <img src="https://img.shields.io/badge/license-AGPL--3.0--only-555555" alt="AGPL-3.0-only lisansı">
</p>

ZenithW Android; desteklenen çevrimiçi medyaları kaydetmek, ses ayıklamak ve indirmeleri yönetmek için Kotlin ve Jetpack Compose ile geliştirilmiş yerel bir Android uygulamasıdır. Medya bilgilerini almak ve indirmek için yt-dlp, işleme ve aktarım seçenekleri için FFmpeg ve Aria2c kullanır. İndirme ve medya işleme, genel ZenithW web indirme servisi yerine cihazda, cihazın ağ bağlantısıyla gerçekleşir.

## Kurulum

İmzalı APK dosyasını [en son GitHub sürümünden](https://github.com/boranseasonnew/zenithw-android/releases/latest) indir. **Android 7.0 (API 24) veya üzeri gerekir.** Cihazının mimarisine uygun paketi seç:

| Mimari | Uygun cihazlar | 2.3.0 APK dosyası |
| --- | --- | --- |
| `arm64-v8a` | Güncel Android telefon ve tabletlerin çoğu | `Zenith-2.3.0-stable-arm64-v8a.apk` |
| `armeabi-v7a` | 32 bit ARM Android çalıştıran cihazlar | `Zenith-2.3.0-stable-armeabi-v7a.apk` |
| `x86_64` | Uyumlu Intel/AMD Android cihazları ve emülatörler | `Zenith-2.3.0-stable-x86_64.apk` |

İndirdiğin APK'yı aç. Android isterse kullandığın tarayıcıya veya dosya yöneticisine uygulama yükleme izni ver. Dosya bütünlüğünü karşılaştırmak için sürümde `SHA256SUMS.txt`, bu depoda ise [imza sertifikasının herkese açık parmak izi](release-certificate.sha256) bulunur.

2.3.0 stable APK'ları, önceki stable sürümlerinin paket kimliğini ve imza sertifikasını korur; mevcut stable kurulumun üzerine güncelleme yapılabilir. Preview, F-Droid ve eski uygulama farklı paket kimlikleri kullanır. Ayarlar ve şifrelenmiş oturumlar bu sürümler arasında otomatik aktarılmaz.

## İlk indirme

1. Desteklenen bir medya bağlantısını yapıştır veya Android'in Paylaş menüsünden ZenithW'ye gönder.
2. Hemen açılan indirme ayarlarından video, ses veya altyazıyı seç. Medya bilgileri aynı panelde yüklenir; kaynak formatlarından birini seçebilir veya otomatik seçimi kullanabilirsin.
3. İşi kuyruğa ekle. İndirme devam ederken başka bir bağlantıyı hazırlayabilirsin.
4. Aktarım, son işleme ve kaydetme aşamalarını takip et. Kaydedilen dosyaları indirme geçmişinden açabilir veya paylaşabilirsin.

Kullanılabilir formatlar, altyazılar ve metaveri kaynağa bağlıdır. Kalite tercihi, kaynakta bulunmayan bir akışı oluşturmaz.

## Özellikler

| Alan | İşlevler |
| --- | --- |
| Video ve ses | Kalite, kapsayıcı ve gerçek kaynak formatı seçimi; codec, kare hızı ve boyut bilgileri; ses ayıklama, bit hızı, dosya adını değiştirme ve doğrulanan başlangıç/bitiş kesitleri. |
| Altyazı ve metaveri | Kaynakta varsa manuel veya otomatik altyazılar; metaveri, bölümler, kapak ve ayrı küçük resim dosyaları. |
| Toplu indirme | Ayarların solundaki terminal simgesinden açılır. **+ Satır ekle** ile **50 URL'ye kadar** bağlantı ekleme, satır silme ve yinelenen işleri ayıklama. |
| Komutlar ve günlük | Ayrı Toplu / Komutlar / Günlük çalışma alanı; isimli yt-dlp seçenek şablonları, desteklenen seçeneklerin doğrulanması, iş bazında çıktı, kopyalama, iptal ve yeniden başlatma. Bunlar yt-dlp işleridir; shell komutu çalıştırılmaz. |
| Oynatma listeleri | **200 girdiye kadar** listeleme ve kuyruğa eklemeden önce tek tek öğe seçimi. |
| Kuyruk ve geçmiş | En fazla **200 etkin veya bekleyen iş**, iş bazında iptal, zamanlanmış başlangıç, yalnızca Wi-Fi ayarı, bildirimler ve kaydedilen dosyalara erişim. Çalışma koşulları sağlanan işler kuyruk sırasıyla, tek tek yürütülür. |
| Gelişmiş ayarlar | SponsorBlock, Aria2c, hız sınırı, yeniden deneme, proxy/IP seçenekleri, format önceliği, dosya boyutu sınırı, uyumlu dosya adları, indirme arşivi ve motor güncelleme aralığı. |
| Dosyalar ve gizlilik | Android klasör seçicisiyle hedef klasör, isteğe bağlı Video/Audio alt klasörleri ve aktif kuyruğu koruyan geçmiş tercihi. |
| Oturumlar | Dahili HTTPS tarayıcısından veya Netscape cookie dosyasından alınan, depolanırken şifrelenen cookie profilleri. |
| Arayüz | Sade ana ekran, açılır indirme seçenekleri, sistem/açık/koyu tema, isteğe bağlı dinamik renkler, önizleme ve seçimleri hatırlama; Türkçe, İngilizce, Almanca, Fransızca ve Rusça. |

Uygulama açıldığında bekleyen işler yeniden zamanlanır. Zamanlanmış görevler ve arka planda çalışma, Android'in ağ, pil ve arka plan işlemi kısıtlamalarına tabidir.

### 2.3.0 ile gelen iyileştirmeler

- **Hemen açılan ayarlar:** indirme paneli metaveri çözümlemesi tamamlanmadan açılır. Otomatik indirme, ayrı bir medya inceleme isteği olmadan başlayabilir.
- **Süre sınırları olan çözümleme:** kayıtlı proxy, IP ve kullanıcı aracısı tercihleri medya incelemesinde de uygulanır. Takılan istekler için kısa sınırlar ve açıkça seçilen uzun yeniden deneme bulunur; 90 saniyelik metaveri önbelleği uygun tekrarları önler. Ölçülmüş bir hız artışı iddiası değildir.
- **Kaynak formatı seçimi:** gerçek formatlar, altyazı dilleri, dosya adı ve kesit ayarları sunulur; medya bilgileri geldiğinde yapılan seçimler korunur.
- **Toplu indirme, komutlar ve günlük:** **+ Satır ekle** ile bağlantı ekleme, tekrar kullanılabilen yt-dlp şablonları ve iş bazında çıktı takibi.
- **İşlevsel ayarlar:** görünüm, indirme onayı, seçimleri hatırlama, önizleme, geçmiş, hedef klasör ve motor güncelleme aralığı kalıcı tercihlerdir.

Ayrıntılar ve sürüm doğrulamasının kapsamı için [2.3.0 sürüm notlarına](releases/2.3.0.md) bakabilirsin.

### Seal'den esinlenilen akışlar ve atıf

Arayüz ve iş akışı incelemesinde resmî [Seal](https://github.com/JunkFood02/Seal) **v1.13.1** sürümünün [`a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1`](https://github.com/JunkFood02/Seal/tree/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1) kaynak commit'i referans alındı. Seal, [GNU GPL v3](https://github.com/JunkFood02/Seal/blob/a2d7e5d5819d0aa8b4f5dbb906731da283a28ed1/LICENSE) lisansıyla sunulur.

İndirme ayarlarını hemen açma, kompakt seçimler ve yt-dlp iş/günlük akışları gibi fikirler ZenithW'de bağımsız olarak uygulandı. Seal kaynak kodu, logosu, ikonları veya ekran görüntüleri pakete dahil edilmedi. ZenithW, Seal'in bir fork'u değildir ve Seal geliştiricilerinin onayladığı bir ürün değildir. Referanslar, farklar ve doğrulama sınırları [kaynak karşılaştırmasında](docs/seal-comparison.md) açıklanır; ZenithW'nin [AGPL-3.0-only lisansı](LICENSE) değişmez.

## Uygulama ve motor güncellemeleri

Uygulama güncellemesi ile yt-dlp güncellemesi birbirinden ayrıdır:

- **Uygulama:** stable sürümünün Ayarlar ekranından resmî Android GitHub sürümü kontrol edilebilir. Bu kontrol APK'yı kendiliğinden indirmez veya kurmaz.
- **İndirme motoru:** GitHub sürümü, sürüm ve checksum kontrolleriyle resmî yt-dlp stable/nightly güncellemelerini destekler. Geçersiz güncellemeler reddedilir ve çalışan motor korunur.
- **Dahili motor:** kaynak koddan yapılan derlemeler yt-dlp **2026.08.19** içerir. Kaynak adresi ve SHA-256 özeti [yt-dlp-bundle.json](yt-dlp-bundle.json) dosyasında kayıtlıdır. Uygulama içinden güncellenmiş motor daha yeni olabilir.

| Sürüm | Paket kimliği | Dağıtım ve güncelleme |
| --- | --- | --- |
| GitHub stable | `space.zenithw.app.stable` | GitHub Releases üzerinden imzalı APK; uygulama içi yt-dlp güncellemelerini destekler. |
| Preview / debug | `space.zenithw.app.preview` | Yerel test için ayrı kurulum. Sonraki preview sürümlerini üzerine kurabilmek için yerel imza anahtarı korunmalıdır. |
| F-Droid derleme çeşidi | `space.zenithw.app.fdroid` | Paketleme için ayrı, imzasız derleme. Harici motor güncellemeleri kapalıdır; dahili motor uygulama sürümleriyle yenilenir. |
| Eski uygulama | `space.zenithw.app` | İlk paket kimliğini kullanır. İmza anahtarı bulunamadığından bu APK'larla güncellenemez. |

Depoda F-Droid mağaza bilgileri ve ayrı bir derleme çeşidi bulunur. Bunlar F-Droid onayını veya mağazada bulunurluğu doğrulamaz. Derleme süreci ve inceleme gereksinimleri için [paketleme notlarına](docs/FDROID.md) bakabilirsin.

## Gizlilik ve oturumlar

Medya, ZenithW web sunucusuna yüklenmeden cihazda indirilir ve işlenir. Ayarlar ve etkinse geçmiş yerel olarak saklanır. Geçmiş kapalıyken biten işler diskte tutulmaz; aktif kuyruk kalıcıdır ve indirilmiş dosyalar silinmez. Kaynak siteler, GitHub güncelleme uç noktaları ve etkinleştirdiğin isteğe bağlı servisler, ilgili özelliklerin çalışması için gereken istekleri almaya devam eder. Yerel işleme, bu istekleri anonim hâle getirmez.

Cookie profilleri, Android Keystore anahtarıyla AES-GCM kullanılarak şifrelenmiş biçimde saklanır. Oturum gerektiren işlerde cookie'ler uygulamanın özel dizininde geçici bir dosyaya çözülür ve iş temizliğinde silinir. Cookie'ler hesaba erişim sağlayabilir; yalnızca gerektiğinde kullan. Cookie dosyalarını, özel bağlantıları veya erişim anahtarlarını herkese açık hata raporlarına ekleme.

## Uyumluluk ve sınırlar

- Kaynak desteği yt-dlp'ye ve ilgili platforma bağlıdır. Gizli veya silinmiş içerik, giriş gereksinimleri, bölge/yaş sınırları, hız kısıtlamaları ve platform değişiklikleri indirmeyi engelleyebilir. Cookie profili erişim garantisi vermez.
- Kalite, codec, altyazı ve işleme seçenekleri medyaya ve kapsayıcıya bağlıdır. Opus, Ogg, WebM ve WAV için güvenilir kare kapak gömme desteklenmez. Küçük kapağı büyütmek yeni görsel ayrıntı oluşturmaz.
- Tam yerel **16 KB bellek sayfası uyumluluğu doğrulanmış değildir**. Paketlenen yerel çalışma ortamında bilinen hizalama sınırları bulunur. Etkilenen FFmpeg işlemlerinin gerçek 16 KB sayfalı cihazlarda doğrulanması gerekir.
- O sürümde gerçekleştirilen kontroller için 2.3.0 sürüm notlarına ve GitHub Actions sonuçlarına bak. Derleme, lint ve emülatör testleri; her kaynakta başarılı çözümlemeyi, gerçek cihaz uyumluluğunu veya ölçülmüş hız artışını doğrulamaz.

Yalnızca sana ait veya indirme ve kullanma iznin olan içerikler için ZenithW kullan. Telif haklarına ve platform koşullarına uy. ZenithW, desteklenen platformlarla bağlantılı değildir.

## Kaynak koddan derleme

Gereksinimler: **JDK 17**, **Android SDK Platform 36**, **Build Tools 36.0.0** ve kabul edilmiş Android SDK lisansları. Android Studio isteğe bağlıdır. Depoda Gradle wrapper bulunur; ayrıca Gradle kurulması gerekmez.

Depoyu klonlayıp kök dizinini aç. SDK yolunu Android Studio üzerinden veya yerel `local.properties` dosyasıyla ayarla:

```properties
sdk.dir=/path/to/android/sdk
```

Debug/preview sürümünü derle:

```sh
# Linux / macOS / WSL
./gradlew :app:assembleDebug
```

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
```

Mimariye özel APK'lar `app/build/outputs/apk/debug/` altında oluşur. İlk debug derlemesi `.signing/preview.keystore` dosyasını üretir. Mevcut preview kurulumunu güncelleyebilmek için bu yerel anahtarı sakla. Windows Gradle kullanırken `\\wsl$` ağ yolu yerine yerel bir Windows dizininden derle.

İmzasız, evrensel F-Droid çeşidini derlemek için:

```sh
./gradlew --no-daemon assembleRelease -Pfdroid=true
```

Çıktı: `app/build/outputs/apk/release/app-release-unsigned.apk`. Kurulumdan önce imzalanması gerekir. Resmî stable sürümleri projenin korunan imza anahtarını kullanır. [Yayın iş akışı](.github/workflows/android-release.yml), anahtarı `ANDROID_SIGNING_KEY_BASE64` gizli değişkeninden alır ve yayın paketlerini doğrular. Özel imza anahtarları depoya dahil değildir.

### Kodun düzeni

Uygulama kaynakları [`app/src/main/java/space/zenithw/app/`](app/src/main/java/space/zenithw/app/) dizinindedir.

| Bileşenler | Sorumluluk |
| --- | --- |
| `ZenithUi`, `WorkspaceSheets`, `DownloadSheet`, `SettingsHub`, `TerminalWorkspace` | Compose ekranları, indirme kontrolleri ve komut/günlük alanı. |
| `ZenithViewModel`, `Models`, `InspectionPolicy` | Ekran durumu, medya inceleme, girdi doğrulama ve kuyruğa eklenecek işler. |
| `DownloadEngine`, `DownloadWorker`, `DownloadProgress`, `SquareCover` | yt-dlp entegrasyonu, arka plan işleri, ilerleme ve kapak işleme. |
| `LocalStore`, `CookieBrowserActivity`, `CookieSheets` | Yerel tercihler/geçmiş ve oturum yönetimi. |
| `BundledEngine`, `EngineUpdateSource`, `EngineArchive`, `AppReleaseChecker` | Dahili motor, doğrulanan motor güncellemeleri ve uygulama sürümü kontrolü. |

Proje; AndroidX WorkManager, Kotlin Coroutines, Coil ve youtubedl-android'in library, FFmpeg ve Aria2c bileşenlerini kullanır. Bağımlılık sürümleri [app/build.gradle.kts](app/build.gradle.kts) ve [build.gradle.kts](build.gradle.kts) dosyalarında tanımlanır.

## Katkı ve destek

Hataları ve önerileri [GitHub Issues](https://github.com/boranseasonnew/zenithw-android/issues) üzerinden bildirebilirsin. Uygulama sürümünü ve çeşidini, Android sürümünü, cihaz mimarisini, hatayı tekrarlama adımlarını ve hassas bilgilerden arındırılmış hata mesajını ekle. Çeviriler, belirli bir soruna odaklanan düzeltmeler ve gerçek cihaz uyumluluk raporları katkı için uygundur.

Geliştirme geçmişi ve bakım bilgileri için [indirme düzeltmelerine](DOWNLOAD_FIXES.md), [F-Droid paketleme notlarına](docs/FDROID.md) ve [güncelleme sertifikası notlarına](docs/update-ca.md) bakabilirsin. İlk kaynak deposuna erişim kaybedildikten sonra mevcut APK davranış referansı olarak kullanıldı ve proje, bakımı sürdürülen bu yerel kod tabanı olarak yeniden oluşturuldu.

## Lisans ve teşekkürler

Seal atfı ve gelecekte kaynak kodu alınırsa uygulanacak politika için [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dosyasına bakabilirsin.

ZenithW Android, [AGPL-3.0-only](LICENSE) lisansıyla sunulur. Üçüncü taraf bileşenler kendi lisanslarını korur. yt-dlp, FFmpeg, Aria2c, youtubedl-android ve ZenithW'nin kullandığı diğer açık kaynak projelerin geliştiricilerine teşekkürler.
