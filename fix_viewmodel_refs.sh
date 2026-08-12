#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "Creating backups..."
mkdir -p .bak/i18n_refs
cp app/src/main/java/com/pgpony/android/ui/exchange/ExchangeViewModel.kt .bak/i18n_refs/
cp app/src/main/java/com/pgpony/android/ui/keyring/KeyringViewModel.kt .bak/i18n_refs/
cp app/src/main/java/com/pgpony/android/ui/contacts/ContactsViewModel.kt .bak/i18n_refs/
cp app/src/main/java/com/pgpony/android/ui/encrypt/EncryptDecryptViewModel.kt .bak/i18n_refs/
cp app/src/main/java/com/pgpony/android/ui/backup/BackupViewModel.kt .bak/i18n_refs/

python3 << 'PYEOF'
import os

def patch_file(path, replacements):
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    for old, new in replacements:
        if old in content:
            content = content.replace(old, new)
            print(f"[OK] {os.path.basename(path)}: replaced")
        else:
            print(f"[WARN] {os.path.basename(path)}: '{old[:40]}...' not found")
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

# 1. ExchangeViewModel.kt
patch_file("app/src/main/java/com/pgpony/android/ui/exchange/ExchangeViewModel.kt", [
    ('"Key imported from QR code"', 'PGPonyApp.instance.getString(R.string.exchange_vm_success_qr_imported)'),
    ('"Key imported from key server"', 'PGPonyApp.instance.getString(R.string.exchange_vm_success_server_imported)'),
])

# 2. KeyringViewModel.kt
patch_file("app/src/main/java/com/pgpony/android/ui/keyring/KeyringViewModel.kt", [
    ('"Key pair imported"', 'PGPonyApp.instance.getString(R.string.keyring_vm_success_keypair_imported)'),
    ('"Public key imported"', 'PGPonyApp.instance.getString(R.string.keyring_vm_success_pubkey_imported)'),
    ('"Key imported successfully"', 'PGPonyApp.instance.getString(R.string.keyring_vm_success_imported)'),
])

# 3. ContactsViewModel.kt
patch_file("app/src/main/java/com/pgpony/android/ui/contacts/ContactsViewModel.kt", [
    ('"Key found and imported for $contactName"', 'PGPonyApp.instance.getString(R.string.contacts_vm_success_imported_format, contactName)'),
])

# 4. EncryptDecryptViewModel.kt
patch_file("app/src/main/java/com/pgpony/android/ui/encrypt/EncryptDecryptViewModel.kt", [
    ('"No signing key available. Generate or import a key pair first."', 'PGPonyApp.instance.getString(R.string.encdec_error_no_signing_key)'),
    ('"Could not import key: ${e.message}"', 'PGPonyApp.instance.getString(R.string.encdec_error_import_key_format, e.message ?: "")'),
])

# 5. BackupViewModel.kt — check import
bkp_path = "app/src/main/java/com/pgpony/android/ui/backup/BackupViewModel.kt"
with open(bkp_path, 'r', encoding='utf-8') as f:
    content = f.read()

if 'import com.pgpony.android.PGPonyApp' not in content:
    lines = content.split('\n')
    insert_idx = 0
    for i, line in enumerate(lines):
        if line.startswith('import '):
            insert_idx = i + 1
    lines.insert(insert_idx, 'import com.pgpony.android.PGPonyApp')
    content = '\n'.join(lines)
    print("[OK] BackupViewModel.kt: added PGPonyApp import")

content = content.replace('e.message ?: "Backup failed"', 'e.message ?: PGPonyApp.instance.getString(R.string.backup_error_failed)')
content = content.replace('e.message ?: "Restore failed"', 'e.message ?: PGPonyApp.instance.getString(R.string.backup_error_restore_failed)')

with open(bkp_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("[OK] BackupViewModel.kt: patched")

print("\n=== Done. Only Kotlin references fixed, XML untouched ===")
PYEOF

echo ""
echo "Backups in .bak/i18n_refs/"
