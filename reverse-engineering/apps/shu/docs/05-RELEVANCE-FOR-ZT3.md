# Bedeutung der SHU-Analyse für die ZT3-Pro-Recherche

Dieses Dokument verbindet die SHU-Decompile-Erkenntnisse mit dem ursprünglichen Ziel: **Reverse Engineering der ZT3-Pro-BLE-Kommunikation**.

## Hypothese

Der Segway-Ninebot **ZT3 Pro** verwendet das **gleiche BLE-Protokoll wie alle modernen Ninebot-Crypto-Scooter** (G30 MAX, F-Series, F2, ZT3, …):

- **Service**: Nordic UART (`6e400001-b5a3-f393-e0a9-e50e24dcca9e`)
- **Pairing**: ECDH (secp256r1) + AES/CCM + HKDF-SHA-256 + HMAC-SHA-256
- **Frame-Magic**: `0x55 0xAB`
- **Beacon-Prefix**: `FF 4E 43` (NC = "Ninebot Crypto")

Quelle dieser Hypothese: vollständige Decompile von SHU, einer Open-Source-App, die genau diese Scooter-Klasse unterstützt.

## Verifikation – einfache Tests

### Test 1: Beacon-Sniffing (bestätigt Crypto-Variante)

Mit `nRF Connect for Mobile` (oder `bleak` + Python) den ZT3 Pro im Advertising-Modus scannen:

1. Manufacturer-Specific-Data des Adv-Frames betrachten
2. Erwartung: Bytes beginnen mit `4E 43` (NC) – bestätigt 2nd-Gen-Crypto-Familie
3. Wenn `4E 42` (NB) → klassisches Ninebot ohne Crypto (unwahrscheinlich für ein 2024er Modell)

### Test 2: GATT-Service-Discovery (bestätigt NUS)

1. Mit `nRF Connect` zum ZT3 Pro verbinden (ohne Pairing)
2. GATT-Liste aufnehmen
3. Erwartung: Service `6e400001-b5a3-f393-e0a9-e50e24dcca9e` mit zwei Characteristics (`…0002` Write, `…0003` Notify)

### Test 3: Modell-DB-Check

Lade das aktuelle SHU-Repo und prüfe, ob ZT3 Pro bekannt ist:

```bash
curl -o bootstrap.zip https://apps-content.cfw.sh/repo/v4/bootstrap.zip
unzip -p bootstrap.zip beacons.json | jq '.[] | select(.humanReadable | test("ZT3|Z3T"; "i"))'
```

Falls Ergebnis vorhanden → SHU unterstützt ZT3 Pro direkt → einfach App installieren und ausprobieren.

## Wenn ZT3 Pro nicht in SHU ist – Selbst-Reverse anhand SHU-Code

Selbst wenn die App den ZT3 Pro (noch) nicht in der Bootstrap-DB hat: das BLE-Protokoll ist **identisch zu dem, was SHU für G30/F-Series implementiert hat**. Damit ist der Code in `sh.cfw.utility.crypto.elliptic.*` direkt als **Referenz-Implementierung** nutzbar.

### Pairing-Flow als Python-Skelett

```python
import asyncio
from bleak import BleakClient, BleakScanner
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import EncodingType, PublicFormat, Encoding
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESCCM
import os
import string

NUS_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
NUS_RX      = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"   # write
NUS_TX      = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"   # notify

def build_init_hello() -> bytes:
    rnd = "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(10))
    return b"\x00" + b"blt.4.159" + rnd.encode()

def inv_sum_checksum(data: bytes) -> bytes:
    s = sum(b for b in data) & 0xFFFF
    cs = (~s) & 0xFFFF
    return bytes([cs & 0xFF, (cs >> 8) & 0xFF])

def wrap_frame(seq: int, encrypted_body: bytes) -> bytes:
    seq_bytes = bytes([seq & 0xFF, (seq >> 8) & 0xFF])
    payload = bytes([len(encrypted_body) + 4]) + seq_bytes + encrypted_body
    frame = b"\x55\xAB" + payload
    return frame + inv_sum_checksum(frame[2:])

async def pair_zt3(mac: str):
    async with BleakClient(mac) as client:
        # 1. enable notifications on TX
        rx_msgs = asyncio.Queue()
        await client.start_notify(NUS_TX, lambda c, d: rx_msgs.put_nowait(bytes(d)))

        # 2. ECDH key generation (secp256r1)
        priv = ec.generate_private_key(ec.SECP256R1())
        pub_pt = priv.public_key().public_bytes(
            Encoding.X962, PublicFormat.UncompressedPoint
        )
        # strip 0x04 prefix → 64 bytes raw X||Y
        pub_raw = pub_pt[1:]

        # 3. send init hello
        await client.write_gatt_char(NUS_RX, build_init_hello())
        scooter_pub_resp = await rx_msgs.get()  # parse: framed scooter pubkey

        # 4. send our pubkey
        # … (genaues Frame-Format der pubkey-Übergabe muss aus jadx --show-bad-code rekonstruiert werden)

        # 5. ECDH
        scooter_pub = ec.EllipticCurvePublicKey.from_encoded_point(
            ec.SECP256R1(), b"\x04" + scooter_pub_raw
        )
        shared = priv.exchange(ec.ECDH(), scooter_pub)

        # 6. HKDF derive
        derived = HKDF(
            algorithm=hashes.SHA256(),
            length=64,
            salt=salt_from_handshake,
            info=info_from_handshake,
        ).derive(shared)
        session_key = derived[0:16]   # AES-128
        response_key = derived[16:32]
        # … weitere abgeleitete Keys

        # 7. weitere Frames mit AESCCM (tag_length=4, weil 24 Bit MAC)
        ccm = AESCCM(session_key, tag_length=4)
        # nonce = …[12 bytes from deviceToken + counter]
```

Die mit `…` markierten Stellen brauchen entweder:

1. **Bessere Decompile**: jadx mit `--show-bad-code --comments-level debug` über `decompiled/apktool/smali/sh/cfw/utility/crypto/elliptic/h.smali` direkt smali analysieren.
2. **Live-Capture**: Pairing eines bekannten G30/F-Series mit der SHU-App und Mitschnitt via `btsnoop_hci.log`. Daraus lassen sich exakte Salt/Info-Strings und Nonce-Konstruktion ablesen.
3. **GitHub-Suche**: ScooterHacking-Org auf GitHub – manche Repos enthalten Python/JS-Implementierungen des gleichen Protokolls (z. B. https://github.com/scooterhacking/).

## Warum SHU für ZT3-Pro vermutlich besser geeignet ist als die offizielle Ninebot-App

| Aspekt | Ninebot Segway | SHU |
|---|---|---|
| Account-Pflicht | ja, Cloud-Login | nein, lokal |
| Cloud-Telemetry | ja | nein |
| Speedlimit-Override | nein | ja (über SHFW) |
| Custom Profile | nein | ja |
| Source verfügbar | nein, gepackt | ja, unverschleiert |
| Sicherheit | gepackt, Anti-Tamper | quelloffen |
| Funktioniert ohne Internet | nein (Login) | ja (nach 1× bootstrap) |
| ZT3-Pro-Support | offiziell ja | check `bootstrap.zip` |

## Empfohlene Vorgehensweise (für ZT3-Pro-Modding)

1. **Lade die aktuelle SHU APK** von `https://utility.cfw.sh/`
2. **Prüfe `bootstrap.zip`** – ist ZT3 Pro in `beacons.json` gelistet?
3. **Falls ja**: SHU installieren, Scooter pairen, SHFW-Profil flashen.
4. **Falls nein**: HCI-Snoop von der offiziellen Ninebot-App machen (Pairing eines ZT3 Pro), Capture mit Wireshark öffnen, Frames mit dem hier dokumentierten `0x55 0xAB`-Layout abgleichen. Wenn match → entweder Bootstrap-Eintrag selbst basteln (Community-PR an `scooterhacking`) oder einen Fork bauen.
5. **Backup zuerst**: Vor Custom-Firmware unbedingt Original-Firmware-Image dumpen!

## Rechtlicher Hinweis

ScooterHacking + SHU sind in vielen Ländern **legaler Hobbyist-Use** (insbesondere § 69e UrhG / EU-Software-Richtlinie für Interoperabilität). Allerdings gilt:

- Speedlimit-Override für Straßenzulassung (StVZO) **kollidiert mit der eKFV** in Deutschland → erlischt Betriebserlaubnis + KFZ-Versicherungsschutz
- Custom Firmware **schließt die Garantie aus**
- Für Privat-Gelände bzw. Reverse-Engineering-Zwecke ist das alles erlaubt

Die hier zusammengetragenen Informationen dienen dem **Verständnis des Protokolls**, nicht der Aufforderung zur Zulassungs-Manipulation.
