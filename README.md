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

## Komutlar

```bash
./scripts/dev.sh up
./scripts/dev.sh down
./scripts/dev.sh restart
./scripts/dev.sh status
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
