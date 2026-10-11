<div align="center">

# ⚡ ZenithW Android

**Android için yerel, açık kaynaklı video ve ses indirici.**

**Kotlin + Jetpack Compose** ile geliştirildi. **yt-dlp**, **FFmpeg** ve **Aria2c** ile çalışır.

![Sürüm](https://img.shields.io/badge/s%C3%BCr%C3%BCm-2.1.3-222222?style=for-the-badge)
![Android](https://img.shields.io/badge/Android-7.0%2B-444444?style=for-the-badge&logo=android)
![Lisans](https://img.shields.io/badge/lisans-AGPL--3.0-555555?style=for-the-badge)

**[🌐 Web sitesi](https://zenithw.space)** · **[📥 İndir](https://zenithw.space/downloads)** · **[🚀 GitHub Sürümleri](https://github.com/boranseasonnew/zenithw-android/releases/latest)** · **[🇬🇧 English README](README.md)**

**Gereksiz kalabalık olmadan indir.**

</div>

---

> [!IMPORTANT]
> **ZenithW Android kaynak koduyla geri döndü.** Önceki GitHub hesabının yasaklanmasının ardından eski Android projesine erişim kaybedildi. Eldeki APK, **JADX GUI** ile incelenerek uygulamanın davranışı referans alındı. Ardından proje yeniden oluşturuldu, önemli bölümleri tekrar yazıldı ve modern bir yerel Android mimarisine taşındı. Bu depo **ham bir decompile çıktısı değildir**.

## ✨ Özellikler

| | |
| --- | --- |
| 🎬 **Video ve ses** | Video kalitesi, ses formatı ve altyazı seçimi. |
| 🔗 **Kolay bağlantı girişi** | URL yapıştırma ve temizleme, Android'in Paylaş menüsünden bağlantı alma, medya incelemesini iptal etme. |
| 📚 **İndirme yönetimi** | Kuyruk, bildirimler, zamanlanmış işler, dosya açma ve paylaşma. |
| 🧩 **Gelişmiş seçenekler** | Oynatma listeleri, SponsorBlock, Aria2c, hız sınırları, hazır ayarlar, proxy/ağ seçenekleri ve günlükler. |
| 🍪 **Gizli oturumlar** | Şifrelenmiş cookie profilleri, özel HTTPS adresleriyle cookie tarayıcısı ve Netscape cookie içe aktarma. |
| ⚙️ **Motor yönetimi** | Dahili yt-dlp, GitHub sürümünde doğrulanmış stable/nightly güncellemeleri ve hatalı güncellemelerden korunma. |
| 🎨 **Yerel arayüz** | Mat siyah/gri tema, Jetpack Compose, alt gezinme çubuğu, yerel alt paneller ve rahat dokunma alanları. |
| 🌍 **Beş dil** | Türkçe, İngilizce, Almanca, Fransızca ve Rusça; seçilen dil yeniden açılışta korunur. |

Ayarlar; motor, cookie/tarayıcı, video, ses, altyazı, SponsorBlock, Aria2c, proxy/ağ, oynatma listeleri, dosyalar/arşiv, hazır ayarlar ve günlükler olarak kategorilere ayrılmıştır.

## 🚀 2.1.3 sürümünde neler değişti?

- **Daha güvenilir yt-dlp güncellemeleri.** Çalıştırılabilir Python ZIP dosyalarının sürümü, eski Android sürümlerinde de okunabilir. Dosyanın başlığını reddedebilen sistem ZIP okuyucusuna bağımlılık azaltıldı.
- **Nightly sürüm kalıcılığı.** Seçili motor ve doğrulanmış sürümü uygulama yeniden açıldığında korunur; başarısız güncelleme çalışan motorun yerini almaz.
- **Daha açıklayıcı hata kayıtları.** Sürüm uyuşmazlığı, checksum doğrulama ve ağ kaynaklı güncelleme hataları daha ayrıntılı kaydedilir.
- **Android 7 uyumluluk çalışmaları.** Resmî güncelleme sertifikaları için destek; motor bütünlüğü ve kayıtlı sürüm üzerinde odaklanmış testler.
- **F-Droid için ayrı derleme.** Harici motor güncellemelerini kapatan, kaynaktan üretilebilen, imzasız ve evrensel APK çeşidi. **Bu, uygulamanın F-Droid tarafından onaylandığı veya yayımlandığı anlamına gelmez.**

Önceki **2.1.1** yenilikleri arasında orijinal URL kartı, kapsamlı ayar kategorileri, özel HTTPS cookie adresleri, beş dil ve resmî stable/nightly güncelleme akışı bulunuyor.

## 📥 ZenithW'yi indir

İmzalı kararlı sürümleri **[GitHub Releases](https://github.com/boranseasonnew/zenithw-android/releases/latest)** üzerinden veya **[resmî indirme sayfasından](https://zenithw.space/downloads)** edinebilirsin.

GitHub'daki **2.1.3** sürümü şu mimariler için APK içerir:

| APK mimarisi | Uygun cihazlar |
| --- | --- |
| **arm64-v8a** | Modern Android telefonların çoğu |
| **armeabi-v7a** | Eski 32 bit ARM cihazlar |
| **x86_64** | Uyumlu emülatörler ve x86_64 cihazlar |

Minimum Android: **7.0 (API 24)**. Hedeflenen Android API düzeyi: **36**.

İndirdiğin APK dosyalarını doğrulamak için sürümdeki **SHA256SUMS.txt** dosyasını kullanabilirsin. Yalnızca güvendiğin kaynaklardan uygulama yükle.

### 📦 Sürümler ve güncellemeler

| Sürüm | Paket kimliği | Güncelleme davranışı |
| --- | --- | --- |
| **GitHub Stable** | `space.zenithw.app.stable` | Aynı sertifikayla imzalanırsa önceki Stable sürümünü güncelleyebilir. Doğrulanmış stable/nightly yt-dlp güncellemelerini destekler. |
| **Preview / Debug** | `space.zenithw.app.preview` | Testler için ayrı kurulur. Önceki preview üzerine güncelleme kurmak için aynı yerel imza anahtarı gerekir. |
| **F-Droid derleme çeşidi** | `space.zenithw.app.fdroid` | Ayrı paket. Dahili motor **yalnızca uygulama sürümleriyle** güncellenir; uygulama içinden harici motor indirilmez. |
| **Eski orijinal uygulama** | `space.zenithw.app` | Orijinal imza anahtarı bulunamadığı için yeni APK'larla doğrudan güncellenemez. |

Bu paketler aynı cihazda yan yana bulunabilir. Ancak ayarlar ve şifrelenmiş oturumlar **otomatik olarak birbirine aktarılmaz**.

**F-Droid durumu:** Paketleme, mağaza açıklamaları ve ayrı derleme iş akışı hazırlandı. Mağazaya kabul ve yayın süreci F-Droid'in bağımsız incelemesine bağlıdır. Ayrıntılar: **[docs/FDROID.md](docs/FDROID.md)**.

## 🛠️ İndirme motoru ve güvenlik

ZenithW, indirme motoru olarak **yt-dlp**, Android entegrasyonunda ise **youtubedl-android**, **FFmpeg** ve **Aria2c** kullanır.

- **Dahili yt-dlp:** `2026.08.19`. Kaynak adresi ve SHA-256 özeti **[yt-dlp-bundle.json](yt-dlp-bundle.json)** dosyasında kayıtlıdır. Bu, **kaynak koddan derlenen uygulamada bulunan sürümdür**; uygulama içinden yüklenebilen en yeni sürüm olmak zorunda değildir.
- **GitHub Stable:** Resmî **stable** ve **nightly** kanallarını destekler. İndirilen motorun sürümü ve checksum değeri kontrol edilir; gerektiğinde resmî GitHub uç noktaları kullanılır.
- **Hatalı güncelleme koruması:** Bozuk, doğrulanamayan veya beklenen sürümle uyuşmayan motor dosyaları reddedilir. Çalışan motor korunur.
- **F-Droid:** Elle zorlananlar da dâhil olmak üzere harici motor güncellemeleri devre dışıdır. Motor, yeni uygulama sürümleriyle yenilenir.

Cookie oturumları şifrelenmiş profillerde tutulur. Kaydedilen oturumlar yeniden seçilebilir ve Netscape biçimindeki cookie dosyaları içe aktarılabilir. Dışa aktarılan cookie dosyaları hesap erişimi sağlayabilecek hassas veriler içerebilir.

> [!NOTE]
> Desteklenen kaynak ve biçimler, yt-dlp'nin durumuna, ilgili servise ve içeriğe bağlıdır. Yalnızca indirme ve kullanma hakkına sahip olduğun medyalar için ZenithW kullan.

## 🔨 Kaynak koddan derleme

**Gereksinimler:** **JDK 17**, **Android SDK 36**, **Android Build Tools 36.0.0** ve kabul edilmiş Android SDK lisansları. Android Studio önerilir ama şart değildir.

Android Studio SDK yolunu ayarlamadıysa **local.properties** içine ekle:

~~~properties
sdk.dir=/path/to/android/sdk
~~~

**Linux / macOS / WSL:**

~~~bash
./gradlew :app:assembleDebug
~~~

**Windows:**

~~~bat
gradlew.bat :app:assembleDebug
~~~

Windows Gradle kullanıyorsan projeyi **\\wsl$** ağ yolu yerine normal bir Windows klasöründen derle.

**F-Droid için imzasız evrensel APK:**

~~~bash
./gradlew --no-daemon assembleRelease -Pfdroid=true
~~~

Çıktı: **app/build/outputs/apk/release/app-release-unsigned.apk**. Bu APK **imzasızdır**; güvenilir, imzalı bir dağıtım paketi olarak doğrudan kurulum için değil, F-Droid paketleme süreci için hazırlanmıştır. Ayrıntılar: **[F-Droid derleme belgesi](docs/FDROID.md)**.

### 🔐 İmzalama ve CI

- İlk debug/preview derlemesi **.signing/preview.keystore** konumunda **yerel** imza anahtarı üretir. Bu anahtarı gizli tut; önceki preview üzerine güncelleme yapabilmek için sakla.
- Resmî Stable derlemeleri, GitHub Actions'a **ANDROID_SIGNING_KEY_BASE64** gizli değişkeniyle aktarılan **mevcut** imza anahtarını kullanır. Anahtar eksikse yeni anahtar oluşturulmaz, release derlemesi başarısız olur.
- **[release-certificate.sha256](release-certificate.sha256)** genel sertifikanın parmak izini içerir. Özel anahtar Git deposuna eklenmez.
- **[Android yayın iş akışı](.github/workflows/android-release.yml)**, yayın öncesinde APK imzalarını, paket kimliklerini, sürümleri ve mimarileri kontrol eder; Android **13** ve **7** emülatörlerinde testler çalıştırır.
- **[F-Droid derleme iş akışı](.github/workflows/fdroid-build.yml)** ise imzasız paketi ve derleme bayraklarını ayrı olarak denetler.

## 📐 Uyumluluk ve bilinen sınırlamalar

- **Android 16 KB bellek sayfaları:** ARM64 çalıştırılabilir dosyalar için 16 KB hizalaması kontrol edildi. Bununla birlikte, FFmpeg paketindeki beş WebP paylaşımlı kütüphanesi hâlâ **4 KB ELF hizalaması** kullanıyor. Desteklenen cihazlarda Android'in uygulama bazlı uyumluluk modu yardımcı olur ancak **tam yerel 16 KB uyumluluğu sağlamaz**.
- İndirme, küçük resim dönüştürme ve ilgili FFmpeg işlemleri gerçek 16 KB sayfa boyutlu cihazlarda ayrıca doğrulanmalıdır.
- Emülatör testleri, farklı gerçek cihazlarla yapılacak kapsamlı testlerin yerini tutmaz.
- İnternet servisleri ve yt-dlp sürümleri ZenithW'den bağımsız olarak değişebilir.

Teknik belgeler: **[DOWNLOAD_FIXES.md](DOWNLOAD_FIXES.md)** · **[Android sayfa boyutu rehberi](https://developer.android.com/guide/practices/page-sizes)** · **[Güncelleme sertifikası notları](docs/update-ca.md)**.

## 🧱 Kullanılan teknolojiler

- **Kotlin**, **Jetpack Compose** ve AndroidX
- **WorkManager**, Kotlin Coroutines ve Coil
- **youtubedl-android 0.18.1** (library, FFmpeg ve Aria2c bileşenleri)
- **yt-dlp** (pakete dâhil edilen motor)

Üçüncü taraf bileşenler kendi lisans ve bildirim şartlarına tabidir.

## 🧩 Kaynak kod kurtarma ve geliştirme

Eski hesaba erişimin kaybolmasıyla önceki Android kaynak deposu da erişilemez hâle geldi. Eldeki APK, JADX GUI üzerinden incelenerek referans alındı. Kod tabanı yeniden düzenlendi, gerekli bölümler yeniden yazıldı ve temel indirme akışı korunarak güncel Android API'lerine uyarlandı.

**Geliştirme öncelikleri:** gerçek cihaz testleri, indirme kararlılığı, tam yerel 16 KB desteği, F-Droid kaynak/lisans incelemesi, hata düzeltmeleri ve arayüz iyileştirmeleri.

Hata mı buldun, önerin mi var? Android sürümünü, kullandığın APK çeşidini ve hatanın nasıl tekrarlandığını belirterek **[GitHub Issues](https://github.com/boranseasonnew/zenithw-android/issues)** üzerinden bildirebilirsin. **Cookie dosyalarını veya özel erişim bilgilerini hata raporlarına ekleme.**

## ⚖️ Lisans

ZenithW Android kaynak kodu **GNU Affero General Public License v3.0 (AGPL-3.0)** ile sunulur. Ayrıntılar: **[LICENSE](LICENSE)**. Üçüncü taraf bağımlılıklar kendi lisanslarına tabidir.

---

<div align="center">

### ⚡ ZenithW

**Android · Açık kaynak · Kotlin · Jetpack Compose · yt-dlp**

[Web sitesi](https://zenithw.space) · [İndir](https://zenithw.space/downloads) · [Sürümler](https://github.com/boranseasonnew/zenithw-android/releases)

</div>
