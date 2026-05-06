// Active CAN-Sender für Speed-Limit-Override und Cruise-Control.
//
// ⚠ DIESE FUNKTIONEN AKTIVIEREN DEN ESP32 ALS BUS-SENDER (TWAI_MODE_NORMAL).
// Damit konkurriert er mit den OEM-ECUs (VCU, Display, BMS) um die gleichen
// CAN-IDs. Das ist nicht spezifiziert vom Hersteller — Risiken:
//
//   - VCU/MCU könnte Watchdog-Fault auslösen wenn doppelte ID detektiert
//   - Roller könnte spontan Error-State zeigen oder Power-Cycle machen
//   - Bei Cruise-Control: Roller fährt unkontrolliert weiter wenn ESP32 hängt
//
// Erst auf aufgebocktem Roller (Hinterrad in der Luft) testen.
// Default OFF (siehe config.h ENABLE_ACTIVE_MODE), explizit opt-in.

#pragma once

#include <Arduino.h>
#include "driver/twai.h"
#include "config.h"

// ────────────────────────────────────────────────────────────────────────────
// TWAI Mode-Switch
//
// Der Sketch startet im LISTEN_ONLY (sicher). Wenn aktive Funktionen genutzt
// werden sollen, vor begin() die Mode setzen.
// ────────────────────────────────────────────────────────────────────────────

class CanActuator {
 public:
  // Schaltet den TWAI-Treiber komplett neu auf — ruft twai_stop +
  // twai_driver_uninstall + neu install. Heavy-handed aber zuverlässig.
  bool enable_active_mode() {
    if (active_) return true;

    twai_stop();
    twai_driver_uninstall();

    twai_general_config_t g = TWAI_GENERAL_CONFIG_DEFAULT(
        (gpio_num_t)CAN_TX_PIN,
        (gpio_num_t)CAN_RX_PIN,
        TWAI_MODE_NORMAL);  // ★ aktiv: sendet ACKs UND eigene Frames
    g.rx_queue_len = 64;
    g.tx_queue_len = 32;

    twai_timing_config_t t = TWAI_TIMING_CONFIG_500KBITS();
    twai_filter_config_t f = TWAI_FILTER_CONFIG_ACCEPT_ALL();

    if (twai_driver_install(&g, &t, &f) != ESP_OK) return false;
    if (twai_start() != ESP_OK) return false;
    active_ = true;
    return true;
  }

  bool is_active() const { return active_; }

  // ── One-shot: Speed-Limit-Override ────────────────────────────────────────
  //
  // Setzt 0x342[6] (live-limit) und 0x20C[2] (live-limit×2) auf den
  // gewünschten Wert. Der Stock-Display-ECU sendet 0x342 nur 5 Hz und
  // 0x20C nur 10 Hz — wir feuern beide mit 20 Hz für 1 Sekunde, damit der
  // MCU unsere Werte als zuletzt-gesehen behält.
  //
  // Hinweis: nicht persistent. Sobald wir aufhören zu senden, fällt der
  // Cap nach <1s zurück auf den Stock-Wert.
  bool set_speed_limit(uint8_t kmh, uint16_t hold_ms = 1000) {
    if (!active_) return false;

    twai_message_t msg_342 = make_speed_limit_frame_342(kmh);
    twai_message_t msg_20c = make_speed_limit_frame_20c(kmh);

    uint32_t end_ms = millis() + hold_ms;
    while ((int32_t)(end_ms - millis()) > 0) {
      twai_transmit(&msg_342, pdMS_TO_TICKS(20));
      twai_transmit(&msg_20c, pdMS_TO_TICKS(20));
      delay(50);  // 20 Hz reicht zum „Outpacen" vom 5-/10-Hz-Stock-Sender
    }
    return true;
  }

  // ── Continuous: Cruise-Control ────────────────────────────────────────────
  //
  // Startet/stoppt eine FreeRTOS-Task die kontinuierlich 0x100-Frames mit
  // dem gespeicherten Throttle-Wert sendet. Der echte VCU sendet auch 0x100
  // (50 Hz) — wir feuern mit 100 Hz dazwischen, damit unsere Frames öfter
  // „die letzten" beim MCU sind.
  //
  // ⚠ Bei laufendem Cruise: Brake-Detection muss IMMER funktionieren. Wenn
  // ESP32 hängt während Cruise aktiv = Roller fährt unkontrolliert weiter.
  // Defensive: Watchdog auf max. Cruise-Dauer (60s), dann Auto-Disengage.
  void start_cruise(uint8_t throttle_value) {
    cruise_throttle_ = throttle_value;
    cruise_engaged_at_ms_ = millis();
    cruise_active_ = true;

    if (cruise_task_handle_ == nullptr) {
      xTaskCreatePinnedToCore(
          cruise_task_entry, "cruise", 4096, this, 5,
          &cruise_task_handle_, 0);
    }
  }

  void stop_cruise() {
    cruise_active_ = false;
    cruise_throttle_ = 0;
  }

  bool is_cruise_active() const { return cruise_active_; }
  uint8_t cruise_throttle() const { return cruise_throttle_; }

 private:
  bool                active_              = false;
  volatile bool       cruise_active_       = false;
  volatile uint8_t    cruise_throttle_     = 0;
  uint32_t            cruise_engaged_at_ms_ = 0;
  TaskHandle_t        cruise_task_handle_  = nullptr;

  static constexpr uint32_t CRUISE_MAX_DURATION_MS = 60000;  // safety watchdog

  static void cruise_task_entry(void* arg) {
    static_cast<CanActuator*>(arg)->cruise_task_loop();
  }

  void cruise_task_loop() {
    twai_message_t msg = {};
    msg.identifier = ID_VCU_STATUS;
    msg.data_length_code = 8;

    while (true) {
      if (cruise_active_) {
        // Watchdog: nach MAX_DURATION_MS automatisch ausschalten
        if (millis() - cruise_engaged_at_ms_ > CRUISE_MAX_DURATION_MS) {
          cruise_active_ = false;
          continue;
        }
        msg.data[0] = cruise_throttle_;  // Throttle
        msg.data[1] = 0x00;              // Brake = 0 (sonst widersprich man sich)
        msg.data[2] = 0x04;              // user-input-active
        msg.data[3] = 0x40;              // konstant in allen Captures
        msg.data[4] = 0x23;              // Mode-LABEL Sport (Platzhalter)
        msg.data[5] = 0x4F;              // Battery%? (konstant, vermutet)
        msg.data[6] = 0x64;              // Mode-B Sport
        msg.data[7] = 0x32;              // konstant
        twai_transmit(&msg, pdMS_TO_TICKS(5));
        delay(10);  // 100 Hz
      } else {
        delay(50);  // schlafen wenn idle
      }
    }
  }

  twai_message_t make_speed_limit_frame_342(uint8_t kmh) {
    // 0x342 Layout (aus FRAMES.md):
    //   Byte 0: Mode-Label  (Echo von 0x100[4])
    //   Byte 2: Mode-B      (Echo von 0x100[6])
    //   Byte 5: C4/E4       (?)
    //   Byte 6: LIVE-LIMIT  ← unser Override
    //   Byte 7: 34          konstant
    twai_message_t msg = {};
    msg.identifier = 0x342;
    msg.data_length_code = 8;
    msg.data[0] = 0x23;   // Sport-Label (passt nicht immer aber harmlos)
    msg.data[1] = 0x00;
    msg.data[2] = 0x64;   // Sport Mode-B
    msg.data[3] = 0x00;
    msg.data[4] = 0x00;
    msg.data[5] = 0xC4;
    msg.data[6] = kmh;    // ★ unser Limit
    msg.data[7] = 0x34;
    return msg;
  }

  twai_message_t make_speed_limit_frame_20c(uint8_t kmh) {
    // 0x20C[2] = LIVE-LIMIT × 2 (0.5 km/h Auflösung)
    twai_message_t msg = {};
    msg.identifier = 0x20C;
    msg.data_length_code = 8;
    msg.data[0] = 0x00;
    msg.data[1] = 0xC4;
    msg.data[2] = kmh * 2;  // ★
    msg.data[3] = 0x34;
    msg.data[4] = 0x1E;
    msg.data[5] = 0x07;
    msg.data[6] = 0x00;
    msg.data[7] = 0x00;
    return msg;
  }
};
