#!/usr/bin/env bash
set -euo pipefail

DUMP_FILE="${1:-}"

if [[ -z "$DUMP_FILE" ]]; then
  printf 'Usage: %s <dump-file>\n' "$(basename "$0")" >&2
  exit 1
fi

if [[ ! -f "$DUMP_FILE" ]]; then
  printf 'Dump file not found: %s\n' "$DUMP_FILE" >&2
  exit 1
fi

# Remove session, payment and passenger data from the public seed.
perl -0pi -e 's|LOCK TABLES `auth_tokens` WRITE;\n/\*!40000 ALTER TABLE `auth_tokens` DISABLE KEYS \*/;\nINSERT INTO `auth_tokens` VALUES .*?/\*!40000 ALTER TABLE `auth_tokens` ENABLE KEYS \*/;\nUNLOCK TABLES;|LOCK TABLES `auth_tokens` WRITE;\n/*!40000 ALTER TABLE `auth_tokens` DISABLE KEYS */;\n/*!40000 ALTER TABLE `auth_tokens` ENABLE KEYS */;\nUNLOCK TABLES;|s' "$DUMP_FILE"
perl -0pi -e 's|LOCK TABLES `payments` WRITE;\n/\*!40000 ALTER TABLE `payments` DISABLE KEYS \*/;\nINSERT INTO `payments` VALUES .*?/\*!40000 ALTER TABLE `payments` ENABLE KEYS \*/;\nUNLOCK TABLES;|LOCK TABLES `payments` WRITE;\n/*!40000 ALTER TABLE `payments` DISABLE KEYS */;\n/*!40000 ALTER TABLE `payments` ENABLE KEYS */;\nUNLOCK TABLES;|s' "$DUMP_FILE"
perl -0pi -e 's|LOCK TABLES `reservations` WRITE;\n/\*!40000 ALTER TABLE `reservations` DISABLE KEYS \*/;\nINSERT INTO `reservations` VALUES .*?/\*!40000 ALTER TABLE `reservations` ENABLE KEYS \*/;\nUNLOCK TABLES;|LOCK TABLES `reservations` WRITE;\n/*!40000 ALTER TABLE `reservations` DISABLE KEYS */;\n/*!40000 ALTER TABLE `reservations` ENABLE KEYS */;\nUNLOCK TABLES;|s' "$DUMP_FILE"
perl -0pi -e 's|LOCK TABLES `tickets` WRITE;\n/\*!40000 ALTER TABLE `tickets` DISABLE KEYS \*/;\nINSERT INTO `tickets` VALUES .*?/\*!40000 ALTER TABLE `tickets` ENABLE KEYS \*/;\nUNLOCK TABLES;|LOCK TABLES `tickets` WRITE;\n/*!40000 ALTER TABLE `tickets` DISABLE KEYS */;\n/*!40000 ALTER TABLE `tickets` ENABLE KEYS */;\nUNLOCK TABLES;|s' "$DUMP_FILE"

# Replace public contact details with seed-safe values.
perl -0pi -e 's/one65one3\@gmail\.com/erzurum\@busgo.local/g; s/sivasadogru\@hotmail\.com/sivas\@busgo.local/g; s/one65one\@gmail\.com/van\@busgo.local/g; s/5516071726/5550000001/g; s/55555555555/5550000002/g;' "$DUMP_FILE"

# Keep only demo accounts in the shareable seed.
perl -0pi -e 's|LOCK TABLES `users` WRITE;\n/\*!40000 ALTER TABLE `users` DISABLE KEYS \*/;\nINSERT INTO `users` VALUES .*?/\*!40000 ALTER TABLE `users` ENABLE KEYS \*/;\nUNLOCK TABLES;|LOCK TABLES `users` WRITE;\n/*!40000 ALTER TABLE `users` DISABLE KEYS */;\nINSERT INTO `users` VALUES (UNHEX('\''aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'\''),'\''BusGo Admin'\'','\''admin\@busgo.com'\'','\''\$2a\$10\$lLtLe8V.ZgzjYxfHfMSgnepFQvWomLB0Ji5u9Nv6fJU/UtUiyH1.W'\'','\''ADMIN'\'','\''2026-04-12 08:33:15.539399'\'',5000.00,NULL,NULL,NULL),(UNHEX('\''bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'\''),'\''BusGo User'\'','\''user\@busgo.com'\'','\''\$2a\$10\$gIArsgcy.i5l2gYblKMhduTTB6iihqByC84lh7xvrEBOBXkDqYMLy'\'','\''USER'\'','\''2026-04-12 08:33:15.403356'\'',5000.00,NULL,NULL,NULL),(UNHEX('\''cccccccccccccccccccccccccccccccc'\''),'\''Erzurum Company Admin'\'','\''erzurum-admin\@busgo.local'\'','\''\$2a\$10\$lLtLe8V.ZgzjYxfHfMSgnepFQvWomLB0Ji5u9Nv6fJU/UtUiyH1.W'\'','\''ADMIN'\'','\''2026-04-12 08:33:15.539399'\'',5000.00,'\''erzurumseyahat'\'','\''Erzurum'\'','\''Admin'\''),(UNHEX('\''dddddddddddddddddddddddddddddddd'\''),'\''Sivas Company Admin'\'','\''sivas-admin\@busgo.local'\'','\''\$2a\$10\$lLtLe8V.ZgzjYxfHfMSgnepFQvWomLB0Ji5u9Nv6fJU/UtUiyH1.W'\'','\''ADMIN'\'','\''2026-04-12 08:33:15.539399'\'',5000.00,'\''sivasadogru'\'','\''Sivas'\'','\''Admin'\''),(UNHEX('\''eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee'\''),'\''Van Company Admin'\'','\''van-admin\@busgo.local'\'','\''\$2a\$10\$lLtLe8V.ZgzjYxfHfMSgnepFQvWomLB0Ji5u9Nv6fJU/UtUiyH1.W'\'','\''ADMIN'\'','\''2026-04-12 08:33:15.539399'\'',5000.00,'\''VanGölüSeyahat'\'','\''Van'\'','\''Admin'\'');\n/*!40000 ALTER TABLE `users` ENABLE KEYS */;\nUNLOCK TABLES;|s' "$DUMP_FILE"

printf 'Public seed sanitized: %s\n' "$DUMP_FILE"
