# Security Model

## Layers

1. **Matrix E2EE (Olm + Megolm)**  
   Official implementation via matrix-rust-sdk / vodozemac (same as Element X).  
   This is the primary protection for messages.

2. **Local Vault (Bitwarden-style)**  
   - Key derivation: Argon2id (t=3, m=64 MiB, p=4)  
   - Encryption: AES-256-GCM  
   - Storage: EncryptedSharedPreferences (Android Keystore backed)  
   Protects access tokens, device keys and session material on disk.

3. **Application practices (inspired by MTProto 2.0)**  
   - Perfect Forward Secrecy where the protocol allows  
   - Sequence / nonce checks against replay  
   - Aggressive key rotation policy for Megolm  
   - No plaintext secrets in logs or backups

## What we do NOT do

- We do not replace Olm/Megolm with a custom ratchet.
- We do not claim "unbreakable" or "military-grade" marketing.
- Master password never leaves the device.

## Threat model (short)

| Threat                     | Mitigation                          |
|----------------------------|-------------------------------------|
| Network attacker           | Matrix E2EE                         |
| Malicious / compromised HS | Matrix E2EE                         |
| Device theft (locked)      | Vault + OS encryption               |
| Device theft (unlocked)    | Biometric lock (planned) + Keystore |

Report security issues via GitHub private advisory.
