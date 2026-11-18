# BUSSID Auto Pilot — cockpit ROI calibration update

Kotlin / Android Studio • namespace `com.bussid.autopilot` • minSdk 26 • targetSdk 35 • compileSdk 35 • JDK 17

## Profil kamera dari video pengguna (cockpit BUSSID)

Rekaman uji yang dipakai sebagai referensi berukuran 1070×480 dan memperlihatkan kamera cockpit dengan dashboard menempati kira-kira 20% bagian bawah layar, HUD di sisi kiri/kanan, kaca depan di area tengah, serta kendaraan dan pepohonan yang kadang menutupi jalan. Untuk profil ini, `RoadDetector` memakai ROI trapezoid vertikal `topY=0.36` sampai `bottomY=0.79`, melebar dari `topHalfWidth=0.10` ke `bottomHalfWidth=0.35`. Ini sengaja mengecualikan langit bagian atas, sebagian HUD samping, dan dashboard bawah.

ROI adalah titik awal khusus rekaman tersebut, bukan kalibrasi universal. Jika resolusi, posisi cockpit, FOV, atau skala HUD berubah, parameter `RoadRoiProfile` harus dikalibrasi ulang. Video juga menunjukkan lalu lintas, persimpangan/perlintasan, dan kendaraan yang dapat menutup batas jalan. Pada kondisi tersebut detektor edge-geometri mungkin gagal; confidence rendah harus tetap menghentikan autopilot.

## Perubahan pada revisi ini

- `RoadDetector`: pemrosesan frame nyata pada resolusi kerja 192×108, grayscale, Sobel gradient, ROI trapezoid yang dapat dikonfigurasi dan dipersempit ke area kaca depan cockpit berdasarkan rekaman pengguna, serta Hough voting terbatas untuk mencari dua keluarga garis batas jalan. Kandidat diuji dengan posisi, lebar jalan dekat/jauh, dan dukungan edge sebelum confidence diberikan.
- `RoadAnalyzer`: stabilisasi temporal untuk offset dan kelengkungan. Satu frame gagal tidak dianggap sebagai deteksi valid; confidence rendah mengembalikan `UNKNOWN`.
- `SteeringController`: kontrol PID bounded dengan curvature feed-forward dan reset integral saat deteksi gagal.
- `SpeedController`: pembatasan throttle konservatif berdasarkan kelengkungan. Brake hanya digunakan pada kondisi kurva sangat tajam; saat deteksi gagal, gas/rem/kemudi semuanya nol.
- `AutoPilotEngine`: fail-safe terpusat untuk memblokir output saat jalan tidak terdeteksi atau confidence < 0.43.
- `ScreenCaptureService`: memakai `RoadAnalyzer`, membatasi pemrosesan ke sekitar 8 fps, menutup `Image`/Bitmap, menghentikan input otomatis ketika confidence turun atau pemrosesan frame gagal, serta menambahkan logcat diagnostics.
- Dashboard diperbarui berkala dan menampilkan arah jalan, confidence, center X, status keputusan, target speed, dan status input. Tombol Emergency Stop mematikan input otomatis.
- Modul dipisah menjadi `RoadDetector`, `RoadAnalyzer`, `SteeringController`, `SpeedController`, dan `AutoPilotEngine`.

## Batasan yang belum boleh dianggap selesai

1. Ini adalah computer vision berbasis edge geometry, bukan semantic road segmentation yang dilatih. Hough/Sobel lebih kaya daripada sampling warna/edge satu dimensi, tetapi marka jalan, bayangan, kendaraan, persimpangan, malam hari, perubahan kamera, dan HUD BUSSID tetap dapat menimbulkan false positive/negative.
2. Confidence merupakan skor heuristik dari dukungan garis dan konsistensi geometri, bukan probabilitas terkalibrasi.
3. Belum ada kumpulan screenshot/rekaman gameplay BUSSID yang disertakan, sehingga akurasi belum divalidasi pada berbagai map/cuaca/kamera. Sebelum kontrol otomatis dicoba, kumpulkan screenshot gameplay berlabel dan ukur precision/recall serta kestabilan centerline.
4. Aplikasi belum membaca speedometer atau telemetry kecepatan. `maxSpeed` hanya ditampilkan sebagai target konfigurasi dan **belum dapat menjamin batas km/jam**. Throttle tetap dibatasi konservatif.
5. Accessibility gesture bukan API gamepad/vehicle control BUSSID. Gesture multi-stroke dapat berbeda perilaku antar perangkat Xiaomi/Android dan versi game. Uji tombol individual tetap harus diverifikasi langsung di BUSSID.
6. Deteksi gagal akan mematikan auto-control sampai pengguna mengaktifkannya kembali. Ini fail-safe yang disengaja, bukan recovery otomatis.
7. Belum ada klaim bahwa APK telah berhasil dibangun atau diuji di perangkat fisik pada revisi ini.

## Langkah uji yang disarankan

1. Build dan install APK pada perangkat uji. Aktifkan Accessibility service dari pengaturan Android.
2. Mulai screen capture dan pastikan frame counter bertambah.
3. Rekam screenshot BUSSID dalam kondisi lurus, tikungan kiri/kanan, bayangan, siang/malam, jalan lebar/sempit, persimpangan, dan ada/tidaknya kendaraan.
4. Verifikasi apakah batas kiri/kanan dan center X mengikuti jalan, bukan kendaraan atau bayangan. Jangan aktifkan input otomatis selama tahap ini.
5. Kalibrasi dan uji setiap gesture satu per satu di garasi/area aman. Centang verifikasi hanya setelah tombol game benar-benar bereaksi.
6. Jika ingin menguji input otomatis, lakukan hanya di area latihan kosong, dengan Emergency Stop mudah dijangkau. Jangan gunakan saat berkendara sungguhan.

## Build

Workflow GitHub Actions `.github/workflows/build-apk.yml` membangun `:app:assembleDebug` dengan JDK 17 dan Gradle 8.9, lalu mengunggah `app-debug.apk`. Build lokal: `gradle :app:assembleDebug`.
