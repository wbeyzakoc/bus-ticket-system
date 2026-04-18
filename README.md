# Bus Ticket System

BusGo frontend + Spring Boot backend + MySQL gelistirme ortami.

## Gereksinimler

- Java 21+ veya 23
- Maven
- Python 3
- Docker Desktop tavsiye edilir

## Ilk Kurulum

1. Repo'yu clone edin.
2. Isterseniz yerel ayarlar icin `.env.local.example` dosyasini `.env.local` olarak kopyalayin.
3. Asagidaki komutu calistirin:

```bash
./scripts/dev.sh up
```

Windows PowerShell icin:

```powershell
Set-Location "C:\path\to\bus-ticket-system"
.\scripts\dev.ps1 up
```

Windows CMD icin:

```cmd
cd /d C:\path\to\bus-ticket-system
scripts\dev.cmd up
```

Notlar:

- `3306` portunda bir MySQL yoksa ve Docker Compose varsa script otomatik olarak `docker-compose.yml` icindeki MySQL servisini kaldirir.
- Veritabani bilgileri varsayilan olarak:
  - DB: `busgo`
  - User: `busgo`
  - Password: `busgo123`
- Hibernate schema'yi otomatik olusturur/gunceller.
- Seed kullanicilar:
  - `user@busgo.com` / `1234`
  - `admin@busgo.com` / `1234`
  - `erzurum-admin@busgo.local` / `1234`
  - `sivas-admin@busgo.local` / `1234`
  - `van-admin@busgo.local` / `1234`

## Komutlar

```bash
./scripts/dev.sh up
./scripts/dev.sh down
./scripts/dev.sh restart
./scripts/dev.sh status
```

Windows PowerShell:

```powershell
.\scripts\dev.ps1 up
.\scripts\dev.ps1 down
.\scripts\dev.ps1 restart
.\scripts\dev.ps1 status
```

Windows CMD:

```cmd
scripts\dev.cmd up
scripts\dev.cmd down
scripts\dev.cmd restart
scripts\dev.cmd status
```

## MySQL Dump

Repo'ya koyulabilir public seed dump uretmek icin:

```bash
./scripts/db-export.sh
```

Bu komut `database/busgo.sql` dosyasini uretir. Varsayilan mod public seed'dir:

- `auth_tokens`, `payments`, `reservations`, `tickets` bos birakilir
- demo hesaplar korunur
- gercek e-posta ve telefonlar sanitize edilir

Ham local dump gerekiyorsa:

```bash
BUSGO_DB_EXPORT_MODE=private ./scripts/db-export.sh /tmp/busgo-private.sql
```

Clone alan kisi kendi MySQL'ine aktarmak icin:

```bash
./scripts/db-import.sh
```

Windows PowerShell:

```powershell
.\scripts\db-import.ps1
```

Windows CMD:

```cmd
scripts\db-import.cmd
```

Varsayilan akista script:

- `busgo` veritabanini olusturur
- `busgo` kullanicisini olusturur
- `database/busgo.sql` dosyasini import eder

Not:

- Import icin once MySQL calisiyor olmali.
- Repo icindeki `database/busgo.sql` dosyasi public paylasim icin sanitize edilmistir.
- `docker-compose.yml` ile gelen local MySQL kullanilacaksa:

```bash
docker compose up -d db
./scripts/db-import.sh
```

Windows PowerShell:

```powershell
docker compose up -d db
.\scripts\db-import.ps1
```

Windows CMD:

```cmd
docker compose up -d db
scripts\db-import.cmd
```

## Adresler

- Frontend: `http://127.0.0.1:8000`
- Backend API: `http://127.0.0.1:8080/api`

## Yerel Ayarlar

- Mail, iyzico ve DB override ayarlari `.env.local` icinden otomatik yuklenir.
- `.env.local`, `.run/`, `.mysql-data/`, `.mysql57-data/` ve `backend-app/target/` git disidir.

## Loglar

- Backend log: `.run/backend.log`
- Frontend log: `.run/frontend.log`
