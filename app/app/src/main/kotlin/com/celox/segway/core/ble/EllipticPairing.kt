package com.celox.segway.core.ble

import com.celox.segway.core.crypto.EllipticCrypto
import com.celox.segway.core.data.PairingPrefs
import com.celox.segway.core.util.BleLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.security.KeyPair
import java.util.concurrent.atomic.AtomicInteger

/**
 * Orchestrates the Ninebot 2nd-gen ECDH pairing handshake.
 *
 * Two operating modes:
 *   • [PairingMode.FreshHandshake] – first-time setup. Performs ECDH, asks the user
 *     to press the scooter's power button (out-of-band auth), receives deviceInfo /
 *     deviceToken / beaconKey and persists them via [PairingPrefs].
 *   • [PairingMode.SessionResume] – with an already-stored set of tokens, derive
 *     the session key and start exchanging encrypted frames.
 *
 * NOTE: The exact HKDF-info / nonce-construction details aren't fully extractable
 * from the (incomplete-decompiled) SHU source. The implementation here matches what
 * is documented in the SHU ANALYSIS doc and what is consistent with the Ninebot
 * 2nd-gen literature. Confirming on real hardware is the next step. This file is
 * intentionally extension-friendly so per-firmware tweaks stay localised.
 */
class EllipticPairing(
    private val gatt: GattClient,
    private val pairingPrefs: PairingPrefs,
    private val mac: String,
    private val bleLog: BleLog? = null,
) {

    sealed interface PairingMode {
        data object FreshHandshake : PairingMode
        data class SessionResume(
            val deviceInfo: ByteArray,
            val deviceToken: ByteArray,
            val beaconKey: ByteArray
        ) : PairingMode
    }

    sealed interface PairingState {
        data object Idle : PairingState
        data object SendingHello : PairingState
        data object AwaitingPubKey : PairingState
        data object Handshake : PairingState
        data object PressPowerButton : PairingState
        data object Sealed : PairingState
        data class Failed(val reason: String) : PairingState
    }

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    /** AES key derived after a successful handshake (or session resume). */
    @Volatile private var sessionKey: ByteArray? = null
    /** AES key for response decryption. May be derived as a separate slot of HKDF output. */
    @Volatile private var responseKey: ByteArray? = null
    /** Stable token blob, used as part of the AES-CCM nonce. */
    @Volatile private var deviceToken: ByteArray? = null

    private val seqOut = AtomicInteger(0)
    private val seqIn = AtomicInteger(0)

    private var keyPair: KeyPair? = null
    private var listenerJob: Job? = null

    /** Launch the pairing flow within the given coroutine scope. */
    fun start(scope: CoroutineScope, mode: PairingMode) {
        listenerJob?.cancel()
        listenerJob = scope.launch {
            try {
                when (mode) {
                    is PairingMode.SessionResume -> resume(mode)
                    PairingMode.FreshHandshake -> performHandshake(scope)
                }
            } catch (t: Throwable) {
                Timber.e(t, "Pairing failed")
                _state.value = PairingState.Failed(t.message ?: t::class.simpleName ?: "error")
            }
        }
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        sessionKey = null
        responseKey = null
        deviceToken = null
        _sessionReady.value = false
        _state.value = PairingState.Idle
    }

    private suspend fun resume(mode: PairingMode.SessionResume) {
        bleLog?.note("Pair", "session resume for $mac")
        // For an already-paired vehicle, the deviceToken is enough to seed the
        // CCM nonce; the session key is HKDF(deviceToken, salt=deviceInfo, info="resume")
        val derived = EllipticCrypto.hkdf(
            ikm = mode.deviceToken,
            salt = mode.deviceInfo,
            info = "resume",
            length = 64
        )
        sessionKey = derived.copyOfRange(0, 16)
        responseKey = derived.copyOfRange(16, 32)
        deviceToken = mode.deviceToken
        _sessionReady.value = true
        _state.value = PairingState.Sealed
    }

    private suspend fun performHandshake(scope: CoroutineScope) {
        bleLog?.note("Pair", "fresh handshake start ($mac)")
        _state.value = PairingState.SendingHello
        gatt.send(EllipticCrypto.buildInitHello())

        // Generate our ECDH keypair and ship the raw public point
        val kp = EllipticCrypto.generateKeyPair()
        keyPair = kp
        val ourPub = EllipticCrypto.encodePublicKey(kp)
        gatt.send(byteArrayOf(0x01) + ourPub) // 0x01 = "client pubkey" envelope

        _state.value = PairingState.AwaitingPubKey

        // Receive scooter pubkey (envelope 0x02 in our convention)
        val scooterPub = withTimeout(15_000L) {
            gatt.incoming.first { it.firstOrNull() == 0x02.toByte() && it.size >= 65 }
        }.copyOfRange(1, 65)

        _state.value = PairingState.Handshake
        val shared = EllipticCrypto.ecdh(kp, EllipticCrypto.decodePublicKey(scooterPub))

        // Derive a 64-byte master from the shared secret + nonces.
        // NOTE: The exact info string is unconfirmed; "ninebot-2g-pair" is a sensible default.
        val masterKey = EllipticCrypto.hkdf(
            ikm = shared,
            salt = scooterPub.copyOfRange(0, 16),
            info = "ninebot-2g-pair",
            length = 64
        )
        sessionKey = masterKey.copyOfRange(0, 16)
        responseKey = masterKey.copyOfRange(16, 32)

        _state.value = PairingState.PressPowerButton

        // The scooter will send the deviceInfo/deviceToken/beaconKey blob when the user
        // confirms by pressing the power button. Frame envelope 0x03 in our convention.
        val sealed = withTimeout(60_000L) {
            gatt.incoming.first { it.firstOrNull() == 0x03.toByte() }
        }
        // Decrypt the inner payload and split into (deviceInfo | deviceToken | beaconKey)
        // Layout per SHU decompile: deviceInfo(16) | deviceToken(16) | beaconKey(16)
        val nonce = (scooterPub.copyOfRange(0, 8) + ByteArray(4)).copyOf(12)
        val plain = EllipticCrypto.aesCcmDecrypt(
            key = sessionKey!!,
            nonce = nonce,
            ciphertext = sealed.copyOfRange(1, sealed.size),
        )
        require(plain.size == 48) {
            "Unexpected sealed-payload length ${plain.size}; expected 48"
        }
        val deviceInfo = plain.copyOfRange(0, 16)
        val token = plain.copyOfRange(16, 32)
        val beaconKey = plain.copyOfRange(32, 48)
        deviceToken = token

        pairingPrefs.save(mac, deviceInfo, token, beaconKey)
        _sessionReady.value = true
        _state.value = PairingState.Sealed
    }

    /**
     * Wrap a raw command payload into an encrypted frame ready for [GattClient.send].
     *
     * If pairing is not yet sealed (e.g. when the user toggled the raw-mode flag
     * because the scooter was previously SHU-flashed and may accept plaintext),
     * we fall back to a *minimal* unwrapped frame: `0x55 0xAB | len-1 | seq[2] | payload | cs[2]`.
     * That at least gets bytes onto the wire so the user can observe in
     * Diagnostics whether the scooter responds at all.
     */
    fun encrypt(payload: ByteArray): ByteArray? {
        val key = sessionKey
        val token = deviceToken
        val seq = seqOut.incrementAndGet()
        return if (key != null && token != null) {
            val nonce = buildNonce(token, seq)
            val ct = EllipticCrypto.aesCcmEncrypt(key, nonce, payload + EllipticCrypto.randomBytes(4))
            FrameCodec.wrap(seq, ct)
        } else {
            // Pre-pairing path — wrap plaintext payload, useful as a probe / for
            // SHU-modded scooters that no longer enforce crypto.
            FrameCodec.wrap(seq, payload)
        }
    }

    /**
     * Decrypt a freshly received raw notification. Returns null on tag failure.
     */
    fun decryptResponse(frame: ByteArray): ByteArray? {
        val key = responseKey ?: return null
        val token = deviceToken ?: return null
        val parsed = FrameCodec.parse(frame) ?: return null
        if (!parsed.isModernCrypto) return parsed.encryptedBody // classic, no decryption
        val nonce = buildNonce(token, parsed.seq)
        seqIn.set(parsed.seq)
        return runCatching {
            EllipticCrypto.aesCcmDecrypt(key, nonce, parsed.encryptedBody)
        }.getOrNull()
    }

    private fun buildNonce(token: ByteArray, seq: Int): ByteArray {
        // 12-byte nonce: token[0..7] || 0x00 0x00 || seq_lo || seq_hi
        val nonce = ByteArray(12)
        System.arraycopy(token, 0, nonce, 0, minOf(token.size, 8))
        nonce[10] = (seq and 0xFF).toByte()
        nonce[11] = ((seq ushr 8) and 0xFF).toByte()
        return nonce
    }
}
