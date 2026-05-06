// stealth-trigger config
//
// Pin assignments und Pattern-Schwellwerte. Hier zentral anpassen statt im
// Code verteilt suchen.

#pragma once

// ── Hardware ────────────────────────────────────────────────────────────────
//
// ESP32-S3 DevKit + SN65HVD230 CAN-Transceiver.
// SN65HVD230 ist 3.3V-compatible — direkt an ESP32-Pins, kein Level-Shifter.
//
// Wiring:
//   ESP32-S3 GPIO 4  → SN65HVD230 D (TXD)
//   ESP32-S3 GPIO 5  → SN65HVD230 R (RXD)
//   ESP32-S3 3.3V    → SN65HVD230 Vcc
//   ESP32-S3 GND     → SN65HVD230 GND
//   SN65HVD230 CANH  → Roller gelb (CAN-H)
//   SN65HVD230 CANL  → Roller grün (CAN-L)
//
// 120Ω Termination: Bus hat schon Termination (Display + VCU), keine extra
// nötig. Falls Bus-Errors, R-S/120Ω-Pin am SN65HVD230 prüfen.

constexpr int CAN_TX_PIN = 4;
constexpr int CAN_RX_PIN = 5;
constexpr uint32_t CAN_BITRATE = 500000;  // 500 kbit/s, ZT3-Standard

// ── Frame-IDs (aus can-bus/FRAMES.md) ───────────────────────────────────────

constexpr uint32_t ID_VCU_STATUS = 0x100;       // Throttle (B0), Brake (B1), Mode (B4+B6)
constexpr uint32_t ID_LIGHT_STATUS = 0x343;     // Light (B3+B6), Brake-Light (B4+B5)
constexpr uint32_t ID_DISPLAY_ECHO = 0x342;     // Mode-Echo, Live-Speed-Limit (B6)

// ── Pattern-Schwellwerte ────────────────────────────────────────────────────

// Bremse: Hysterese um Bouncing zu vermeiden
constexpr uint8_t BRAKE_PRESSED_THRESHOLD = 0x40;   // > 64 = "gedrückt"
constexpr uint8_t BRAKE_RELEASED_THRESHOLD = 0x10;  // < 16 = "losgelassen"

// Throttle: ab welchem Wert gilt als "voll gedrückt"
constexpr uint8_t THROTTLE_HOLD_THRESHOLD = 0x80;   // > 128 = "stark gedrückt"

// ── Pattern A: 3× Brake in 2 s → STEALTH-LOCK ───────────────────────────────
constexpr uint8_t  PATTERN_BRAKE_COUNT = 3;
constexpr uint32_t PATTERN_BRAKE_WINDOW_MS = 2000;

// ── Pattern B: Throttle 5 s halten + Brake → CRUISE-REQUEST ─────────────────
constexpr uint32_t PATTERN_THROTTLE_HOLD_MS = 5000;
constexpr uint32_t PATTERN_THROTTLE_BRAKE_GRACE_MS = 1500;  // ms nach Loslassen
                                                            // in denen Brake noch
                                                            // den Trigger auslöst

// ── Pattern C: 5× Mode-Knopf in 3 s → PROFILE-SWITCH ────────────────────────
constexpr uint8_t  PATTERN_MODE_COUNT = 5;
constexpr uint32_t PATTERN_MODE_WINDOW_MS = 3000;

// ── Cooldown nach Trigger ───────────────────────────────────────────────────
// Nach jedem Trigger eine Pause damit nicht direkt nochmal gefeuert wird.
constexpr uint32_t TRIGGER_COOLDOWN_MS = 3000;

// ── Active-Mode (CAN-Sender) ────────────────────────────────────────────────
//
// ⚠ Default OFF wegen Risiko VCU-Konflikt. Erst auf aufgebocktem Roller
// testen bevor live-aktivieren. Wenn auf true: ESP32 sendet aktiv Frames
// auf den Bus (Speed-Limit-Override + Cruise-Control).
constexpr bool ENABLE_ACTIVE_MODE = false;

// Cruise-Control: Default-Engage-Pattern verwendet die existierende
// Pattern B (5s Throttle-Hold + Brake-Tap), wird aber zu CRUISE_ENGAGE
// statt CRUISE_REQUEST wenn ENABLE_ACTIVE_MODE = true.
//
// Cruise-Disengage triggert auf JEDEN Brake-Press (universal Auto-Standard).
constexpr uint8_t CRUISE_DISENGAGE_BRAKE_THRESHOLD = 0x20;  // > 32 = "ernst gemeint"

// ── BLE Service ─────────────────────────────────────────────────────────────
//
// Eigene NUS-Instance, NICHT die Roller-NUS-UUIDs nachbauen sonst
// kollidiert es mit der Stock-App. Custom Vendor-Service, derselbe Aufbau
// wie NUS aber andere UUID.

constexpr const char* BLE_DEVICE_NAME = "ZT3-StealthTrigger";

// Custom Service-UUID (random generated)
constexpr const char* BLE_SERVICE_UUID = "8e7a0000-7c5e-4dc0-aa1f-7c3c43b1e3b1";
constexpr const char* BLE_CHAR_TX_UUID = "8e7a0001-7c5e-4dc0-aa1f-7c3c43b1e3b1";  // ESP32 → Phone (notify)
constexpr const char* BLE_CHAR_RX_UUID = "8e7a0002-7c5e-4dc0-aa1f-7c3c43b1e3b1";  // Phone → ESP32 (write, optional config)

// ── Status-LED ──────────────────────────────────────────────────────────────
// Eingebaute LED auf den meisten ESP32-S3 DevKits ist GPIO 48 (WS2812 RGB)
// oder GPIO 21 (mono). Kommentar bei Bedarf anpassen.
constexpr int STATUS_LED_PIN = 21;
