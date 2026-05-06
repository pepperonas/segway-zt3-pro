// BLE NUS-Server — published Trigger-Events an die Phone-App.
//
// Custom Service-UUID, NICHT die Roller-NUS-UUIDs (die kollidieren sonst
// mit der Stock-App).
//
// Phone-Side: app subscribes auf TX-Char und bekommt Notify-Strings:
//   "TRIGGER:STEALTH_LOCK"
//   "TRIGGER:CRUISE_REQUEST"
//   "TRIGGER:PROFILE_SWITCH"
//   "STATUS:CAN_OK" / "STATUS:CAN_ERR"

#pragma once

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"

class BleServer {
 public:
  void begin() {
    BLEDevice::init(BLE_DEVICE_NAME);
    server_ = BLEDevice::createServer();
    server_->setCallbacks(new ConnCallbacks(this));

    BLEService* svc = server_->createService(BLE_SERVICE_UUID);

    tx_char_ = svc->createCharacteristic(
        BLE_CHAR_TX_UUID,
        BLECharacteristic::PROPERTY_NOTIFY);
    tx_char_->addDescriptor(new BLE2902());

    rx_char_ = svc->createCharacteristic(
        BLE_CHAR_RX_UUID,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
    // RX-Handler optional — für späteres Config-Update vom Phone

    svc->start();

    BLEAdvertising* adv = BLEDevice::getAdvertising();
    adv->addServiceUUID(BLE_SERVICE_UUID);
    adv->setScanResponse(true);
    adv->setMinPreferred(0x06);
    adv->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
  }

  bool connected() const { return connected_; }

  void notify(const char* msg) {
    if (!connected_ || !tx_char_) return;
    tx_char_->setValue((uint8_t*)msg, strlen(msg));
    tx_char_->notify();
  }

  void notify_trigger(const char* trigger_name) {
    char buf[64];
    snprintf(buf, sizeof(buf), "TRIGGER:%s", trigger_name);
    notify(buf);
  }

  void notify_status(const char* status) {
    char buf[64];
    snprintf(buf, sizeof(buf), "STATUS:%s", status);
    notify(buf);
  }

 private:
  class ConnCallbacks : public BLEServerCallbacks {
   public:
    explicit ConnCallbacks(BleServer* outer) : outer_(outer) {}
    void onConnect(BLEServer* s) override {
      outer_->connected_ = true;
    }
    void onDisconnect(BLEServer* s) override {
      outer_->connected_ = false;
      // Re-advertise damit Phone wieder reconnecten kann
      BLEDevice::startAdvertising();
    }
   private:
    BleServer* outer_;
  };

  BLEServer*         server_  = nullptr;
  BLECharacteristic* tx_char_ = nullptr;
  BLECharacteristic* rx_char_ = nullptr;
  bool               connected_ = false;
};
