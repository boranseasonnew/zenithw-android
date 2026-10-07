# Android indirme motoru: hata incelemesi

İnceleme tarihi: 7 Ekim 2026.

## İncelenen uygulama

Kaynak: kullanıcının bilgisayarındaki `ZenithW-Mobile-1.3.1.apk`.
Dosya adı 1.3.1 olsa da APK bildirimi sürüm adını 1.3.0, sürüm kodunu 4 gösteriyor.
Paket: `space.zenithw.app`.
APK SHA-256: `43cad9e2e71f6a7f11be8da94d6e76584ebf8def90c14459ea9787b334c97919`.

Bu inceleme Android uygulamasına aittir. Web/AWS veya Windows uygulamasında aynı
hataların bulunduğu sonucuna varılmadı. Eski telefondaki APK değiştirilmedi.

## Doğrulanan sorunlar

### 1. Aria2c seçenekleri birbirini eziyor

Kurtarılan `l4/v.java`, bağlantı sayısını ve hız sınırını
`--downloader-args aria2c:...` ile gönderiyor. Kütüphanenin `YoutubeDL.execute`
metodu bunun ardından iki ayrı `--external-downloader-args` seçeneği ekliyor:
önce ilerleme raporu, sonra sertifika yolu.

yt-dlp bu iki seçenek adını aynı ayara çeviriyor. Aynı downloader için son değer,
öncekileri değiştiriyor. APK içindeki **2025.11.12** motorunun seçenek ayrıştırıcısı
ile gerçek sıra yeniden oluşturuldu. Sonuçta yalnızca sertifika yolu kaldı;
uygulamanın bağlantı/hız ayarları ve bir saniyelik ilerleme raporu kayboldu.

Bu hata ilerlemenin görünmemesini açıklayabilir. Kullanıcının telefonda gördüğü
her başlamama vakasının nedeninin bu olduğu doğrulanmadı; hatalı kaynak bağlantısı,
oturum ihtiyacı ve sunucu erişim kısıtları için cihaz hata kaydı gerekir.

Yeni motorda protokole göre tam native executable yolu kullanılıyor ve tüm aria2c
ayarları, sertifika denetimi korunarak tek argümanda gönderiliyor. Bu kullanım
kütüphanenin hatalı otomatik argüman ekleme koşuluna girmiyor.

### 2. Opus'ta kapak gömme engellenmiş

Kurtarılan isteğin ham kontrol akışında `WAV` ve `OPUS` için
`--embed-thumbnail` eklenmediği doğrulandı. Opus bu işlem için destekleniyor;
APK'nın Python paketinde gerekli mutagen de bulunuyor.

Yeni motor Opus'u desteklenen ses biçimleri arasına aldı. Ses biçimi seçeneği
video modundaki kapak kararını etkilemiyor.

### 3. Metadata ve kapak farklı işlemler

Metadata gömme kapağı kendiliğinden gömmez. Kaynağın kapak sağlamaması, resmin
indirilememesi veya seçilen kabın kapak desteği olmaması ayrıca ele alınmalıdır.
Eski kodun `--no-warnings` kullanımı bazı açıklayıcı uyarıları gizliyordu.

Yeni motor uyarıları bastırmıyor. Kullanılabilir kapak bulunamadığında işlem
sonucuna sabit, kullanıcıya uygun bir açıklama ekliyor; ham URL/cookie çıktısını
kalıcı iş geçmişine yazmıyor.

### 4. Seçilen video kabı her indirmeye uygulanmıyor

Eski istek yalnızca `--merge-output-format` kullanıyor. Bu seçenek, ayrı ses/video
akışlarının birleştirilmesi sırasında geçerlidir; tek birleşik dosyada seçilen
kabı zorunlu tutmaz. Yeni istek ayrıca `--remux-video` kullanıyor.
Bu bir yeniden kodlama garantisi değildir; uyumsuz codec/kab kombinasyonu
dönüştürme hatası verebilir.

WebM ve WAV, yt-dlp'nin mevcut kapak gömme işleminde desteklenmiyor. Yeni motor
bu biçimleri gizlice değiştirmiyor; kapak istenirse kaynakta resim bulunduğunda
ayrı resim kaydediyor ve bir açıklama ekliyor. Desteklenen biçimler için gömme
komutu ve gerektiğinde JPG dönüşümü gönderiliyor.

## Eklenen korumalar

- Aria2c yalnızca istendiğinde hazırlanıyor; hazırlama başarısızlığı normal
  indirme motorunu devre dışı bırakmıyor.
- HTTP/FTP isteklerinde Aria2c, HLS/DASH manifestlerinde native indirme kullanılıyor.
- Aria2c bağlantı ve okuma süreleri sınırlı; bağlantı sayısı ve hız limiti korunuyor.
- Aria2c'ye özgü indirme hatasında normal motorla yalnızca bir kez yeniden deneniyor.
  Kimlik doğrulama veya kapak işleme hatası bu kuralla yeniden denenmiyor.
- Motor güncellemesinin ağ istekleri indirme kilidinin dışında yapılıyor; doğrulanmış
  dosyanın kurulumu aktif indirme bitince yapılıyor.
- İlerleme henüz bilinmiyorsa hazırlık açıklaması kullanılıyor; kapak, metadata,
  birleştirme ve dönüştürme aşamaları ayrı gösteriliyor.
- `.aria2`, `.part`, `.ytdl`, oturum dosyaları ve geçici medya cihaza yayınlanmıyor.
- Yeni kaynaklara resmi **yt-dlp 2026.08.19** eklendi. SHA-256 resmi yayın
  checksum'u ile doğrulandı; çevrimdışı kurulum da bu sürümü kullanabilir.
  APK içindeki eski 2025.11.12 sürümü, mevcut telefondaki motorun sürümünü
  kanıtlamaz: uygulama çalışırken güncellenmiş olabilir.

## Gerçekleştirilen kontroller

- Kotlin 2.2.20 ile saf indirme kuralları derlendi; **35 odaklı kontrol geçti**.
- Motor/model kaynakları gerçek yt-dlp Android, FFmpeg ve Aria2c 0.18.1 sınıflarına
  karşı derlendi. Android Context/Uri/storage için geçici tip tanımları kullanıldı.
  Bu kontrol tam Android derlemesi veya cihaz testi değildir.
- Bilgisayarda oluşturulan Opus, M4A, MP3, FLAC ve MP4 dosyalarına yt-dlp'nin
  kapak işlemi uygulandı. Dosyalar tekrar okunarak metadata ve gömülü kapak
  alanlarının ikisi de doğrulandı.
- Yalnızca 127.0.0.1 üzerinde sunulan yapay bir MP4 ve JPG ile gerçek yt-dlp
  indirme → metadata → kapak işlem zinciri çalıştırıldı. Çıktı dosyasının başlığı
  ve gömülü kapağı tekrar okunarak doğrulandı.
- Herhangi bir herkese açık medya bağlantısı, kullanıcı cookie'si veya hesap
  bilgisi bu kontrollere dahil edilmedi.

`tests/DownloadPolicyCheck.kt` ve `tests/check_download_core.py` ilgili kontrolleri
içerir. Büyük geçici araçlar/örnek çıktılar ana projenin yok sayılan downloads
dizinindedir.

## Kalan işler ve sınırlar

- Yeni Android 2.0 önizleme APK'ları 7 Ekim 2026'da ARM64, ARM32 ve x86_64 için
  başarıyla derlendi. APK imzası, uygulama kimliği ve paket içindeki güncel yt-dlp
  dosyası kontrol edildi. Telefona kurulmadı ve herkese açık yayımlanmadı.
- Android SDK kuruldu ve arayüz tamamlandı. Gerçek cihaz denemesi hâlâ gerekiyor.
  Önizleme paketi: space.zenithw.app.preview; sürüm: 2.0.0-preview.
- Eski uygulamanın üzerine güncelleme kurulabilmesi için özgün imza anahtarı
  gerekecek. Önizleme paketi ayrı uygulama kimliği kullanacak.
- Aria2c'nin Android native çalıştırılması, iptal/yeniden deneme davranışı,
  HLS/DASH kaynakları, playlist'ler ve farklı oynatıcılarda kapağın görünmesi
  cihaz üzerinde ayrıca doğrulanmalıdır.
- Kaynakta kapak olmayan bir medyaya bu düzeltme kapak üretemez.
- Aria2c manifest indirme açığı 2026.06.09'da düzeltildi. Yeni gömülü sürüm bu
  düzeltmeyi içeriyor; eski APK'nın motorunun cihazda güncellenip güncellenmediği bilinmiyor.

## Kaynaklar

- [yt-dlp seçenekleri](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/options.py)
- [Android yt-dlp kütüphanesi](https://github.com/yausername/youtubedl-android/blob/master/library/src/main/java/com/yausername/youtubedl_android/YoutubeDL.kt)
- [yt-dlp kapak gömme işlemi](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/postprocessor/embedthumbnail.py)
- [Aria2c manifest güvenlik düzeltmesi](https://github.com/yt-dlp/yt-dlp/security/advisories/GHSA-vx4q-3cr2-7cg2)
- [Özgün Android projesi](https://github.com/boranseasonnew/zenithw-android)
