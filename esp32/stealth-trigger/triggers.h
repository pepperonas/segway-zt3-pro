// Pattern-State-Machines.
//
// Drei unabhängige Detector-Klassen, jeweils mit minimaler State.
// Aufruf-Konvention: feed() bei jedem relevanten CAN-Frame, gibt true
// zurück wenn das Pattern gerade gefeuert hat.

#pragma once

#include <Arduino.h>
#include "config.h"

// ────────────────────────────────────────────────────────────────────────────
// Pattern A: 3× Brake within window → STEALTH-LOCK
//
// Counted werden Press-Release-Zyklen, nicht einzelne Pressed-Samples.
// Hysterese via BRAKE_PRESSED_THRESHOLD / BRAKE_RELEASED_THRESHOLD.
// ────────────────────────────────────────────────────────────────────────────

class BrakeBurstDetector {
 public:
  // brake_value: 0x100 Byte 1 (0–255 analog brake pressure)
  // returns true exactly once when N presses have been counted
  bool feed(uint8_t brake_value, uint32_t now_ms) {
    bool was_pressed = pressed_;
    if (brake_value >= BRAKE_PRESSED_THRESHOLD) {
      pressed_ = true;
    } else if (brake_value <= BRAKE_RELEASED_THRESHOLD) {
      pressed_ = false;
    }

    // Rising edge = neuer Press
    if (!was_pressed && pressed_) {
      // Window-Reset wenn der erste Press oder zu lange her
      if (count_ == 0 || (now_ms - first_press_ms_) > PATTERN_BRAKE_WINDOW_MS) {
        count_ = 1;
        first_press_ms_ = now_ms;
      } else {
        count_++;
      }

      if (count_ >= PATTERN_BRAKE_COUNT) {
        count_ = 0;
        return true;  // FEUER!
      }
    }
    return false;
  }

  void reset() {
    count_ = 0;
    pressed_ = false;
    first_press_ms_ = 0;
  }

 private:
  uint8_t  count_ = 0;
  bool     pressed_ = false;
  uint32_t first_press_ms_ = 0;
};

// ────────────────────────────────────────────────────────────────────────────
// Pattern B: Throttle ≥THRESHOLD durchgehend für N ms, dann Brake → CRUISE-REQUEST
//
// State A: idle
// State B: throttle hold accumulating
// State C: throttle hold qualified (5s erreicht), warte auf Brake-Tap im
//          Grace-Window
// ────────────────────────────────────────────────────────────────────────────

class ThrottleHoldThenBrakeDetector {
 public:
  bool feed(uint8_t throttle_value, uint8_t brake_value, uint32_t now_ms) {
    bool throttle_hi = (throttle_value >= THROTTLE_HOLD_THRESHOLD);
    bool brake_hi    = (brake_value    >= BRAKE_PRESSED_THRESHOLD);

    switch (state_) {
      case State::Idle:
        if (throttle_hi) {
          state_ = State::Holding;
          hold_started_ms_ = now_ms;
        }
        break;

      case State::Holding:
        if (!throttle_hi) {
          state_ = State::Idle;
        } else if ((now_ms - hold_started_ms_) >= PATTERN_THROTTLE_HOLD_MS) {
          state_ = State::Qualified;
          qualified_at_ms_ = now_ms;
        }
        break;

      case State::Qualified:
        // Wenn Throttle weiter gehalten + Brake nicht: bleiben qualifiziert
        // Wenn Throttle losgelassen: Grace-Window startet jetzt
        // Wenn Brake getreten: TRIGGER!
        if (brake_hi) {
          state_ = State::Idle;
          return true;
        }
        if (!throttle_hi && (now_ms - qualified_at_ms_) > PATTERN_THROTTLE_BRAKE_GRACE_MS) {
          state_ = State::Idle;
        }
        break;
    }
    return false;
  }

  void reset() { state_ = State::Idle; }

 private:
  enum class State { Idle, Holding, Qualified };
  State    state_ = State::Idle;
  uint32_t hold_started_ms_ = 0;
  uint32_t qualified_at_ms_ = 0;
};

// ────────────────────────────────────────────────────────────────────────────
// Pattern C: 5 Mode-Wechsel in N ms → PROFILE-SWITCH
//
// Mode-Knopf-Druck ist nicht direkt sichtbar — wir zählen distinct Werte
// von 0x100[4] (Mode-Label) als Proxy für Knopf-Drücke.
//
// Knopf-Press → Mode wechselt → Counter +1.
// ────────────────────────────────────────────────────────────────────────────

class ModeChangeBurstDetector {
 public:
  // mode_label: 0x100 Byte 4 (Walk=05, Eco=0F, Drive=19, Sport=23)
  bool feed(uint8_t mode_label, uint32_t now_ms) {
    if (last_mode_ == 0xFF) {
      // First-time-init
      last_mode_ = mode_label;
      return false;
    }
    if (mode_label == last_mode_) return false;

    // Mode hat sich geändert
    last_mode_ = mode_label;

    if (count_ == 0 || (now_ms - first_change_ms_) > PATTERN_MODE_WINDOW_MS) {
      count_ = 1;
      first_change_ms_ = now_ms;
    } else {
      count_++;
    }

    if (count_ >= PATTERN_MODE_COUNT) {
      count_ = 0;
      return true;
    }
    return false;
  }

  void reset() {
    count_ = 0;
    last_mode_ = 0xFF;
    first_change_ms_ = 0;
  }

 private:
  uint8_t  count_ = 0;
  uint8_t  last_mode_ = 0xFF;  // sentinel = "noch nicht gesehen"
  uint32_t first_change_ms_ = 0;
};

// ────────────────────────────────────────────────────────────────────────────
// Cooldown — globaler Sperr-Timer nach beliebigem Trigger.
// Verhindert dass mehrere Patterns sich überlagern (z.B. wenn der User
// nach Stealth-Lock nochmal bremst — kein doppelter Lock-Trigger).
// ────────────────────────────────────────────────────────────────────────────

class TriggerCooldown {
 public:
  bool ready(uint32_t now_ms) const {
    return (now_ms - last_trigger_ms_) >= TRIGGER_COOLDOWN_MS;
  }
  void mark_fired(uint32_t now_ms) { last_trigger_ms_ = now_ms; }

 private:
  uint32_t last_trigger_ms_ = 0;
};
