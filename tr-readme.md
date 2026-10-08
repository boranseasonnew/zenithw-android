<div align="center">

# ⚡ Zenith Android 2.1.1

### **Hızlı. Native. Yeniden İnşa Edildi.**

Orijinal **ZenithW Android** uygulamasının **Kotlin + Jetpack Compose** ile yeniden oluşturulmuş sürümü.

**Video • Ses • Altyazı • yt-dlp • Aria2c**

---

> 🧩 **Android kaynak kodu geri döndü.**  
> Eski hesabın banlanmasının ardından orijinal proje dosyalarına erişim kaybedildi ve hesap geri açılamadı.  
> Uygulama, mevcut APK üzerinden **JADX GUI** kullanılarak kurtarıldı; ardından kodlar temizlendi, yeniden düzenlendi, gerektiği yerlerde tekrar yazıldı ve modern bir Android projesine dönüştürüldü.

</div>

---

## ✨ Neler var?

### 2.1.1 değişiklikleri

- Ana ekrandaki eski URL kartı ve düğmeler korunur; tanıtım yazıları kaldırılır.
- Motor, cookie/tarayıcı, video/ses, altyazı, SponsorBlock, Aria2c, proxy/ağ, oynatma listesi, dosya/arşiv, profil ve günlük için tıklanabilir ayar kategorileri geri gelir.
- Cookie oturumunda hazır site kısayolları yerine istediğiniz HTTPS adresini girebilirsiniz.
- Türkçe, İngilizce, Almanca, Fransızca ve Rusça; tercihler yeniden açılışta korunur.
- Stable/nightly güncellemeleri resmi sürüm adresinden alınır; gerektiğinde resmi GitHub API kullanılır ve geçici bağlantı hatalarında yeniden denenir.
- Nightly sürüm numarası tam olarak okunur; motor kurulmadan önce checksum doğrulanır.
- İndirme seçenekleri ve seçili şifreli oturum uygulama yeniden açıldığında korunur.
- Yayın öncesinde Android 13 emülatöründe ayar kategorileri, motorun gerçek çalışması, kanal geçişi, zorla kapatma sonrası ayarlar ve ana ekran kontrol edilir.

### Stable APK imzası

Stable sürümün paket kimliği `space.zenithw.app.stable`; eski uygulama ve preview
yanına ayrı kurulur. Eski uygulamanın imza anahtarı bulunamadığından ayarları
taşınmaz. Release derlemesi `.signing/preview.keystore` içindeki mevcut anahtarı
kullanır; Actions bunu `ANDROID_SIGNING_KEY_BASE64` secret değerinden yükler.
Anahtar yoksa release derlemesi durur, yeni anahtar üretilmez. Güncellemeler için
bu anahtarı saklayın. Yalnızca açık sertifika özeti `release-certificate.sha256`
dosyasında tutulur; özel anahtar Git'e eklenmez.

Actions üç APK'nın imzasını, sertifikasını, paketini, sürümünü ve mimarisini
yayından önce kontrol eder. Yayınlamadan denemek için Android release akışında
`publish_release` seçeneğini kapatıp `android-signed-apks` çıktısını indirin.
v2.0.0 imza sorunu düzeltildi. v2.1 aynı stable paketini ve anahtarı kullanır;
mevcut uygulamanın üzerine kurulduğunda verileri korunur.

ZenithW 2.0 sadece görsel bir yenileme değil.

Android uygulaması, orijinal uygulamanın temel özelliklerini korurken daha temiz ve sürdürülebilir bir native yapı etrafında yeniden inşa edildi.

### 🎨 Arayüz

- 🖤 Mat **siyah / koyu gri** tasarım
- 📱 Native **Jetpack Compose** arayüzü
- 👆 Daha rahat kullanım için büyük dokunma alanları
- 🧭 Alt gezinme çubuğu
- 📑 Native bottom sheet yapıları
- ✨ Hafif seçim animasyonları
- 📐 Tek elle kullanıma uygun yerleşim

### 🔗 URL işlemleri

- 📋 Bağlantıyı doğrudan yapıştırma
- 🧹 URL temizleme butonu
- 📤 Android **Paylaş** menüsünden link alma
- 🔎 İptal edilebilir medya inceleme
- 🌐 Daha sade URL giriş akışı

### 🎬 İndirme seçenekleri

Önemli ayarlar doğrudan görünürken, daha az kullanılan seçenekler açılır bölümlerde tutulur.

**Ana seçenekler:**

- 🎞️ Video kalitesi
- 🎵 Ses formatı
- 💬 Altyazılar

**Gelişmiş seçenekler:**

- ⚡ Aria2c
- 🚦 İndirme hız sınırı
- ⏰ Zamanlanmış indirmeler
- 📶 Sadece ölçülmeyen ağlarda indirme

### 📥 İndirme sistemi

- 📚 İndirme kuyruğu
- 🔔 Android bildirimleri
- 📂 İndirilen dosyaları açma
- 📤 İndirilen dosyaları paylaşma
- ⏱️ Zamanlanmış indirmeler
- 📡 Wi-Fi / ölçülmeyen ağ kısıtlamaları
- 🖼️ Küçük resim ve kapak işleme geliştirmeleri
- ⚙️ Aria2c kullanılamadığında native fallback sistemi

Aria2c ve küçük resim düzeltmeleriyle ilgili teknik ayrıntılar için:

[`DOWNLOAD_FIXES.md`](DOWNLOAD_FIXES.md)

---

## 🛠️ yt-dlp

ZenithW indirme motoru olarak **yt-dlp** kullanır.

Uygulamada:

- 🔄 Açılışta otomatik güncelleme kontrolü
- ✅ Doğrulanmış yt-dlp indirmeleri
- 📦 Düzenlenmiş çevrimdışı yt-dlp paketi
- 🔐 Sürüm ve checksum doğrulaması

bulunur.

Mevcut dahili sürüm:

```text
yt-dlp 2026.08.19
```

Kaynak, sürüm ve checksum bilgileri:

```text
yt-dlp-bundle.json
```

dosyasında tutulur.

---

## 🍪 Cookie sistemi

ZenithW 2.0 ile cookie sistemi de yeniden ele alındı.

- 🔒 Şifrelenmiş cookie profilleri
- 🌐 `https://` ile başlayan cookie tarayıcısı
- 💾 Kayıtlı oturum seçimi
- 📄 Netscape formatında cookie dosyası içe aktarma
- ⚡ Daha önce kaydedilmiş profilleri otomatik kullanma

---

# 🔨 Derleme

## Gereksinimler

Gerekli bileşenler:

- **JDK 17 veya daha yeni**
- **Android SDK 36**
- **Android Build Tools 36.0.0**
- Kabul edilmiş Android SDK lisansları

Android Studio önerilir ancak zorunlu değildir.

SDK yolunu Android Studio üzerinden ayarlayabilir veya:

```text
local.properties
```

dosyası oluşturup şunu ekleyebilirsin:

```properties
sdk.dir=/android/sdk/yolu
```

---

## 🐧 Linux / WSL

Derlemek için:

```bash
./gradlew :app:assembleDebug
```

---

## 🪟 Windows

Windows üzerinde:

```bat
gradlew.bat :app:assembleDebug
```

kullanılır.

> ⚠️ **Önemli**
>
> Projeyi doğrudan şu tarz bir WSL ağ yolundan derlemeyin:
>
> ```text
> \\wsl$\Ubuntu\...
> ```
>
> Gradle çalıştırmadan önce projeyi normal bir Windows klasörüne kopyalayın veya çıkartın.

Windows Gradle ortamı, WSL ağ sistemi üzerinden doğrudan derleme sırasında hata verebilir.

Örneğin:

```text
C:\Projects\ZenithW-Android
```

---

## 🔐 Preview imzalama

Gradle wrapper şu anda:

```text
Gradle 8.13
```

sürümünü sabitler.

Gradle dağıtımı **SHA-256** ile doğrulanır.

İlk preview derlemesi sırasında otomatik olarak yerel bir imza anahtarı oluşturulur:

```text
.signing/
```

### ⚠️ Bu anahtarı gizli tut

Gelecekteki preview APK'larının mevcut preview sürümünün üzerine güncelleme olarak kurulabilmesi için aynı anahtar gerekir.

`.signing` klasörü:

- 🚫 Git'e dahil edilmez
- 🚫 Kaynak kod arşivlerine eklenmez
- 🔒 Sadece yerelde tutulur

---

# 📦 APK sürümleri

Önceki 2.0 preview paket adı:

```text
space.zenithw.app.preview
```

Uygulama adı:

```text
ZenithW 2.0 Preview
```

Sürüm:

```text
2.0.0-preview
```

Version Code:

```text
20000
```

Minimum Android sürümü:

```text
Android 7.0
API 24
```

Preview sürümü, eski ZenithW uygulamasının yanına kurulabilmesi için özellikle farklı bir package ID kullanır.

---

## 🧱 Desteklenen mimariler

Preview APK'ları başarıyla şu mimariler için derlendi:

| Mimari | Durum |
|---|---|
| **ARM64 / arm64-v8a** | ✅ Derlendi |
| **ARM32 / armeabi-v7a** | ✅ Derlendi |
| **x86_64** | ✅ Derlendi |

Derleme tarihi:

**7 Ekim 2026**

APK imzaları ve paket içindeki **yt-dlp 2026.08.19** dosyası başarıyla doğrulandı.

> 🧪 Bu sürümler henüz fiziksel bir Android cihazda kapsamlı şekilde test edilmedi.

Üretilen APK'lar ve derleme raporları yerelde şu klasörde tutulur:

```text
ZenithW-Builds/Android-2.0
```

Bu klasör bilerek Git dışında tutulur.

---

# 📐 Android 16 KB sayfa boyutu uyumluluğu

Yeni Android cihazlarda **16 KB bellek sayfa boyutu** desteği giderek daha önemli hale geliyor.

ZenithW'nin ARM64 çalıştırılabilir dosyaları şu anda:

```text
✅ 16 KB hizalı
```

Ancak FFmpeg runtime paketinin içinde bulunan beş adet **WebP shared library** hâlâ:

```text
⚠️ 4 KB ELF hizalaması
```

kullanıyor.

Uyumluluğu artırmak için ZenithW, desteklenen cihazlarda Android'in **uygulama bazlı page-size compatibility mode** özelliğini açıkça etkinleştirir.

### Önemli

Bu uyumluluk modu, tam anlamıyla 16 KB destekleyen upstream native binary'lerin yerini tutmaz.

Aşağıdaki özellikler 16 KB kullanan gerçek cihazlarda hâlâ test edilmelidir:

- 📥 İndirmeler
- 🖼️ Thumbnail dönüştürme
- 🎬 Etkilenen kütüphaneleri kullanan FFmpeg işlemleri

Android dokümantasyonu:

https://developer.android.com/guide/practices/page-sizes

---

# 🔄 Orijinal ZenithW uygulamasını güncelleme

Orijinal Android uygulaması şu package ID'yi kullanır:

```text
space.zenithw.app
```

Android, mevcut bir uygulamanın üzerine güncelleme kurulabilmesi için yeni APK'nın **orijinal uygulamayla aynı imza sertifikasıyla** imzalanmasını zorunlu tutar.

Orijinal imza anahtarı şu anda **geri kazanılmış değil**.

Bu nedenle mevcut preview sürümü farklı bir paket kullanır:

```text
space.zenithw.app.preview
```

Böylece **ZenithW 2.0 Preview**, eski ZenithW sürümünün yanına kurulabilir.

### Stable release sürümleri

İmzalı release paketinin adı `space.zenithw.app.stable`. Mevcut stable anahtarıyla imzalanır; eski uygulamayı değiştirmeden stable sürümün üzerine güncellenir.

---

# 🧩 Kaynak kodun geri kazanılması

Eski Android kaynak kodu deposu, eski hesabın banlanmasından sonra erişilemez hale geldi ve hesap geri açılamadı.

Android sürümünü tamamen bırakmak yerine mevcut APK, **JADX GUI** ile incelendi.

Kurtarılan uygulama mantığı daha sonra projenin yeniden oluşturulmasında referans olarak kullanıldı.

Bu repository bu nedenle **sadece ham bir JADX çıktısı değildir**.

Kaynak kodun önemli bölümleri:

- 🧹 Temizlendi
- 🧱 Yeniden düzenlendi
- ✍️ Tekrar yazıldı
- 🎨 Yeniden tasarlandı
- ⚙️ Modern Android API'leriyle yeniden uygulandı
- 🧪 Gelecekteki geliştirmelere uygun hale getirildi

Sonuç olarak ortaya sürdürülebilir ve geliştirilebilir bir **Kotlin + Jetpack Compose** projesi çıktı.

---

# 📚 Üçüncü taraf bileşenler

ZenithW Android çeşitli açık kaynak bileşenler kullanır.

### yt-dlp

Resmî ve doğrulanmış sürüm kullanılır.

Kaynak, sürüm ve checksum bilgileri:

```text
yt-dlp-bundle.json
```

dosyasında bulunur.

### Native indirme bileşenleri

ZenithW şu bileşenleri kullanır:

```text
youtubedl-android 0.18.1
FFmpeg Android paketleri
Aria2c Android paketleri
```

### Android kütüphaneleri

Bunlara şunlar dahildir:

- Jetpack Compose
- AndroidX
- WorkManager
- Kotlin Coroutines
- Coil

---

# ⚖️ Lisans

Orijinal ZenithW Android projesi:

**GNU Affero General Public License v3.0**

lisansı altındadır.

Detaylar için:

[`LICENSE`](LICENSE)

Üçüncü taraf kütüphaneler kendi lisans ve bildirim şartlarına tabidir.

Orijinal Android projesinden ve bağımlılıklardan gelen lisans / notice şartları geçerliliğini korur.

---

# 🚧 Proje durumu

> **Zenith Android 2.1 kararlı sürümdür.**

Proje başarıyla derlenmektedir ancak daha fazla cihaz testi ve uyumluluk çalışması planlanmaktadır.

Şu anki öncelikler:

- 📱 Fiziksel cihaz testleri
- 🧪 Farklı Android sürümlerinde test
- 📐 Tam 16 KB native library uyumluluğu
- ⚡ İndirme kararlılığı geliştirmeleri
- 🐛 Hata düzeltmeleri
- 🎨 Arayüz iyileştirmeleri
- 🚀 GitHub Actions üzerinden imzalı stable sürümler

---

<div align="center">

## ⚡ ZenithW

**Gereksiz kalabalık olmadan indir.**

`Android • Kotlin • Jetpack Compose • yt-dlp`

</div>
