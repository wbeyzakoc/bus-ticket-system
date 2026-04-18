`busgo.sql` dosyasini bu klasorde tutun.
Repo'daki dosya public paylasim icin sanitize edilmis bir seed snapshot'tir.

Olusturmak icin:

```bash
./scripts/db-export.sh
```

Ham local dump gerekiyorsa:

```bash
BUSGO_DB_EXPORT_MODE=private ./scripts/db-export.sh /tmp/busgo-private.sql
```

Import etmek icin:

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
