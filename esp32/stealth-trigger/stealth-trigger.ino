// stealth-trigger — passiver CAN-Sniffer für ZT3 Pro D mit Custom-Trigger-
// Erkennung und BLE-Notification an die Phone-App.
//
// Drei Patterns auf passiv-gelesenen CAN-Frames:
//   A) 3× Brake within 2 s          → STEALTH_LOCK
//   B) Throttle 5 s + Brake-Tap     → CRUISE_REQUEST
//   C) 5× Mode-Knopf within 3 s     → PROFILE_SWITCH
//
// Hardware: ESP32-S3 + SN65HVD230. Pin-Belegung in config.h.
//
// Build (Arduino IDE):
//   Board: "ESP32S3 Dev Module"
//   Partition: "Default 4MB with spiffs"
//   USB CDC On Boot: Enabled (für Serial-Debug)
//
// Build (arduino-cli):
//   arduino-cli compile --fqbn esp32:esp32:esp32s3 .
//   arduino-cli upload -p /dev/cu.usbmodem* --fqbn esp32:esp32:esp32s3 .

#include <Arduino.h>
#include "driver/twai.h"

#include "config.h"
#include "triggers.h"
#include "ble_server.h"

// ── Globals ─────────────────────────────────────────────────────────────────

static BleServer ble;
static BrakeBurstDetector            brake_burst;
static ThrottleHoldThenBrakeDetector cruise_pattern;
static ModeChangeBurstDetector       mode_burst;
static TriggerCooldown               cooldown;

static uint32_t can_frames_seen = 0;
static uint32_t can_errors_seen = 0;
static uint32_t last_status_log_ms = 0;

// ── CAN-Init ────────────────────────────────────────────────────────────────

static bool init_twai() {
  twai_general_config_t g_config = TWAI_GENERAL_CONFIG_DEFAULT(
      (gpio_num_t)CAN_TX_PIN,
      (gpio_num_t)CAN_RX_PIN,
      TWAI_MODE_LISTEN_ONLY);  // ★ wichtig: read-only, sendet keine ACKs
                               // Bei NORMAL würde der ESP32 jeden Frame ACK'en
                               // und den Bus möglicherweise stören.
  g_config.rx_queue_len = 64;

  twai_timing_config_t t_config = TWAI_TIMING_CONFIG_500KBITS();
  twai_filter_config_t f_config = TWAI_FILTER_CONFIG_ACCEPT_ALL();

  if (twai_driver_install(&g_config, &t_config, &f_config) != ESP_OK) {
    Serial.println("[twai] driver_install FAILED");
    return false;
  }
  if (twai_start() != ESP_OK) {
    Serial.println("[twai] start FAILED");
    return false;
  }
  Serial.println("[twai] running @ 500 kbit/s, listen-only");
  return true;
}

// ── Frame-Handler ───────────────────────────────────────────────────────────

static void on_frame(const twai_message_t& msg) {
  can_frames_seen++;

  // Wir interessieren uns nur für 0x100 (alles was wir brauchen ist da drin)
  if (msg.identifier != ID_VCU_STATUS) return;
  if (msg.data_length_code < 7) return;  // schützt vor short frames

  uint8_t throttle   = msg.data[0];
  uint8_t brake      = msg.data[1];
  uint8_t mode_label = msg.data[4];
  uint32_t now = millis();

  // Pattern A — Brake-Burst
  if (cooldown.ready(now) && brake_burst.feed(brake, now)) {
    Serial.println("[trigger] STEALTH_LOCK");
    ble.notify_trigger("STEALTH_LOCK");
    cooldown.mark_fired(now);
    return;  // nicht direkt nochmal feuern im selben Frame
  }

  // Pattern B — Throttle-Hold + Brake
  if (cooldown.ready(now) && cruise_pattern.feed(throttle, brake, now)) {
    Serial.println("[trigger] CRUISE_REQUEST");
    ble.notify_trigger("CRUISE_REQUEST");
    cooldown.mark_fired(now);
    return;
  }

  // Pattern C — Mode-Change-Burst
  if (cooldown.ready(now) && mode_burst.feed(mode_label, now)) {
    Serial.println("[trigger] PROFILE_SWITCH");
    ble.notify_trigger("PROFILE_SWITCH");
    cooldown.mark_fired(now);
    return;
  }
}

// ── Setup ───────────────────────────────────────────────────────────────────

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== ZT3 stealth-trigger ===");

  pinMode(STATUS_LED_PIN, OUTPUT);
  digitalWrite(STATUS_LED_PIN, HIGH);

  ble.begin();
  Serial.print("[ble] advertising as ");
  Serial.println(BLE_DEVICE_NAME);

  if (!init_twai()) {
    Serial.println("[fatal] CAN-init failed, halting");
    while (true) {
      digitalWrite(STATUS_LED_PIN, !digitalRead(STATUS_LED_PIN));
      delay(100);
    }
  }
}

// ── Main-Loop ───────────────────────────────────────────────────────────────

void loop() {
  // CAN drainen — bis zu 16 Frames pro Loop-Iteration
  twai_message_t msg;
  for (int i = 0; i < 16; i++) {
    esp_err_t r = twai_receive(&msg, 0);  // non-blocking
    if (r == ESP_OK) {
      on_frame(msg);
    } else if (r == ESP_ERR_TIMEOUT) {
      break;  // keine Frames mehr in der Queue
    } else {
      can_errors_seen++;
      break;
    }
  }

  // Status-Heartbeat alle 5 s
  uint32_t now = millis();
  if (now - last_status_log_ms > 5000) {
    last_status_log_ms = now;
    Serial.printf("[heartbeat] frames=%u errors=%u ble=%s\n",
                  can_frames_seen, can_errors_seen,
                  ble.connected() ? "connected" : "advertising");
    if (ble.connected()) {
      char buf[64];
      snprintf(buf, sizeof(buf), "%s:frames=%u",
               can_frames_seen > 0 ? "CAN_OK" : "CAN_SILENT", can_frames_seen);
      ble.notify_status(buf);
    }
    digitalWrite(STATUS_LED_PIN, !digitalRead(STATUS_LED_PIN));
  }

  // BLE-Stack atmen lassen
  delay(1);
}
