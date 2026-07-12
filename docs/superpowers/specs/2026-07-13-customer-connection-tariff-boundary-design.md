# Pemisahan Workspace Pelanggan, Sambungan, dan Tarif

## Latar Belakang

Route `/customers` dan `/connections` saat ini merender `MasterDataWorkspace` yang sama. Akibatnya kedua halaman menjalankan query, state, form, dan workflow pelanggan, sambungan, serta golongan tarif sekaligus. Form golongan tarif juga berada di workspace gabungan tersebut, padahal kepemilikannya adalah domain tarif. Tabel pelanggan memiliki enam header tetapi hanya lima sel data per baris karena kolom `Lock` tidak memiliki data.

## Tujuan

- Menjadikan setiap route sebagai workspace operasional dengan satu tanggung jawab domain.
- Menghindari query dan state domain lain berjalan pada halaman yang tidak membutuhkannya.
- Memindahkan pengelolaan golongan tarif ke halaman tarif.
- Menjaga kontrak API, otorisasi backend, audit reason, dan aturan workflow yang sudah ada.
- Membersihkan inkonsistensi tampilan yang dapat diverifikasi pada halaman frontend lain.

## Batas Scope

Perubahan mencakup frontend Next.js, test kontrak workspace, dokumentasi konteks, dan verifikasi browser. Perubahan tidak mencakup endpoint Spring Boot, skema database, permission baru, atau perubahan aturan bisnis.

## Pendekatan Terpilih

Gunakan workspace terpisah per domain. Pendekatan tab dalam satu halaman ditolak karena tetap mencampur tanggung jawab route. Pendekatan satu komponen dengan prop visibilitas ditolak karena query dan state yang tidak relevan berisiko tetap aktif.

## Arsitektur Komponen

### CustomerWorkspace

Route: `/customers`

Tanggung jawab:

- membaca daftar pelanggan dengan filter pencarian dan status;
- menampilkan ringkasan jumlah pelanggan;
- menampilkan detail pelanggan terpilih dan alamatnya;
- membuat pelanggan dengan validasi dan audit reason;
- menangani loading, error, empty, unauthenticated, pending, dan mutation error state.

Workspace ini tidak boleh memanggil query sambungan atau golongan tarif dan tidak boleh merender form sambungan atau tarif.

### ConnectionWorkspace

Route: `/connections`

Tanggung jawab:

- membaca daftar sambungan dengan filter pelanggan dan status;
- menampilkan ringkasan jumlah dan status sambungan;
- menampilkan detail sambungan terpilih;
- membuat sambungan menggunakan lookup pelanggan dan golongan tarif;
- menjalankan workflow aktivasi, suspensi, dan terminasi dengan audit reason;
- menangani seluruh state operasional secara eksplisit.

Query lookup pelanggan dan golongan tarif diperbolehkan sebagai dependensi input sambungan, tetapi halaman tidak boleh merender manajemen pelanggan atau golongan tarif.

### TariffWorkspace

Route: `/tariffs`

Tanggung jawab tambahan:

- membaca dan membuat golongan tarif;
- menampilkan ringkasan jumlah golongan tarif;
- mempertahankan pengelolaan versi, blok progresif, workflow aktivasi/arsip, dan simulasi tarif yang sudah ada.

Golongan tarif diperlakukan sebagai master referensi milik domain tarif. Pembuatan golongan tarif tidak lagi tersedia di halaman pelanggan atau sambungan.

### Komponen Bersama

Komponen presentasional seperti `Field`, `Section`, `SummaryCard`, tabel, dan helper format tetap dapat digunakan bersama. State, mutation, dan query domain tidak boleh ditempatkan kembali dalam satu komponen induk lintas route.

## Alur Data

1. Route merender workspace domain tunggal.
2. Workspace menjalankan query yang diperlukan oleh domain tersebut saja.
3. Filter lokal menjadi bagian query key TanStack Query yang sudah ada.
4. Mutation menggunakan hook API yang sudah ada dan menginvalidasi query domain terkait.
5. Detail hanya dimuat setelah operator memilih baris.
6. Otorisasi backend tetap menjadi sumber kebenaran; status autentikasi frontend hanya mengendalikan affordance tombol.

## Perbaikan Konsistensi

- Hapus header `Lock` yang tidak memiliki data dari tabel pelanggan.
- Gunakan istilah Indonesia pada judul, deskripsi, label, dan pesan workspace yang disentuh.
- Pastikan judul halaman dan konten utama sesuai route aktif.
- Audit seluruh `page.tsx` untuk route yang merender workspace identik secara tidak sengaja.
- Audit struktur tabel pada workspace operasi agar jumlah header dan sel data konsisten.
- Jangan memecah workspace finansial besar dalam perubahan ini kecuali ditemukan pencampuran route yang sama dan dapat diperbaiki tanpa mengubah workflow.

## State dan Edge Case

- Loading awal hanya menunggu query milik workspace aktif.
- Kegagalan query daftar tidak menutupi form lain yang masih dapat digunakan bila aman.
- Daftar kosong menampilkan tindakan berikutnya yang relevan.
- Detail kosong meminta operator memilih baris.
- Tombol mutation nonaktif ketika tidak terautentikasi atau mutation pending.
- Pemilihan detail yang tidak lagi ada pada hasil filter tetap ditangani oleh query detail berbasis ID.
- Terminasi tetap memakai gaya danger dan audit reason wajib.
- Mode terang/gelap dan responsivitas tidak boleh mengalami regresi.

## Strategi Test

- Tambahkan model kontrak route yang memetakan capability workspace pelanggan, sambungan, dan tarif.
- Test memastikan pelanggan tidak memiliki capability sambungan/tarif, sambungan hanya memiliki lookup dependensi, dan tarif memiliki pengelolaan golongan tarif.
- Jalankan `test:permissions`, typecheck, lint, dan build frontend.
- Bangun ulang container frontend dan smoke seluruh route HTTP.
- Audit browser desktop dan mobile untuk `/customers`, `/connections`, `/tariffs`, serta sampel halaman lain.
- Verifikasi judul, section heading, tidak ada konten lintas domain, tidak ada overflow, serta tidak ada error console.

## Kriteria Selesai

- `/customers` hanya menampilkan fungsi pelanggan.
- `/connections` hanya menampilkan fungsi sambungan dan dependensi lookup-nya.
- `/tariffs` memiliki pengelolaan golongan tarif dan fungsi tarif yang sudah ada.
- Tidak ada dua route utama yang merender workspace gabungan yang sama secara tidak sengaja.
- Tabel pelanggan memiliki struktur header dan baris yang sejajar.
- Semua quality gate dan audit browser lulus.
