/*
 * BioWearable_BLE.ino — ESP32-S3 BLE Server  (STABLE VERSION)
 *
 * FIXES IN THIS VERSION:
 *   1. Watchdog uses new esp_task_wdt_config_t struct (ESP32 core 3.x fix)
 *   2. Auto re-advertise after disconnect
 *   3. MTU set to 185 — prevents silent Android disconnect
 *   4. Connection flags handled in loop() only — no BLE calls inside callbacks
 *   5. Blue LED blinks while advertising, Green solid while connected
 *
 * Board  : ESP32-S3
 * Core   : ESP32 Arduino Core 3.x (Espressif)
 * Library: ESP32 BLE Arduino (bundled with board package)
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <esp_task_wdt.h>

// ── Pin Definitions ────────────────────────────────────────────────────────
#define PIN_EEG1   4
#define PIN_EEG2   5
#define PIN_PULSE  6
#define PIN_EMG    7
#define PIN_MOTOR  15
#define PIN_LED_R  10
#define PIN_LED_G  11
#define PIN_LED_B  12

// ── PWM ────────────────────────────────────────────────────────────────────
#define PWM_FREQ       5000
#define PWM_RESOLUTION 8

// ── BLE UUIDs ──────────────────────────────────────────────────────────────
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define SENSOR_DATA_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define LED_CONTROL_UUID    "cba1d466-344c-4be3-ab3f-189f80dd7518"
#define MOTOR_CONTROL_UUID  "f9279c99-b7b3-4e9e-b0dd-e4e2c95e9ad3"

// ── Timing ─────────────────────────────────────────────────────────────────
#define SENSOR_INTERVAL_MS    20     // 50 Hz
#define LED_BLINK_INTERVAL_MS 500
#define WDT_TIMEOUT_SEC       10     // watchdog reboot timeout

// ── BLE State flags ────────────────────────────────────────────────────────
volatile bool deviceConnected   = false;
volatile bool pendingConnect    = false;
volatile bool pendingDisconnect = false;

BLEServer*         pServer      = nullptr;
BLECharacteristic* pSensorChar  = nullptr;
BLEAdvertising*    pAdvertising = nullptr;

// ── Timers ─────────────────────────────────────────────────────────────────
unsigned long lastSensorSend = 0;
unsigned long lastLedBlink   = 0;
bool          ledBlinkState  = false;

// ══════════════════════════════════════════════════════════════════════════
//  BLE Callbacks
// ══════════════════════════════════════════════════════════════════════════
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pSvr) override {
    pendingConnect    = true;
    pendingDisconnect = false;
    Serial.println("[CB] onConnect");
  }
  void onDisconnect(BLEServer* pSvr) override {
    pendingDisconnect = true;
    deviceConnected   = false;
    Serial.println("[CB] onDisconnect");
  }
};

class LedCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    if (val.length() >= 3) {
      ledcWrite(PIN_LED_R, (uint8_t)val[0]);
      ledcWrite(PIN_LED_G, (uint8_t)val[1]);
      ledcWrite(PIN_LED_B, (uint8_t)val[2]);
      Serial.printf("[LED] R=%d G=%d B=%d\n",
        (uint8_t)val[0], (uint8_t)val[1], (uint8_t)val[2]);
    }
  }
};

class MotorCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic* pChar) override {
    String val = pChar->getValue();
    if (val.length() >= 1) {
      bool on = ((uint8_t)val[0] != 0);
      digitalWrite(PIN_MOTOR, on ? HIGH : LOW);
      Serial.printf("[MOTOR] %s\n", on ? "ON" : "OFF");
    }
  }
};

// ══════════════════════════════════════════════════════════════════════════
//  Hardware Init
// ══════════════════════════════════════════════════════════════════════════
void initHardware() {
  pinMode(PIN_MOTOR, OUTPUT);
  digitalWrite(PIN_MOTOR, LOW);

  ledcAttach(PIN_LED_R, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(PIN_LED_G, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(PIN_LED_B, PWM_FREQ, PWM_RESOLUTION);
  ledcWrite(PIN_LED_R, 0);
  ledcWrite(PIN_LED_G, 0);
  ledcWrite(PIN_LED_B, 0);

  analogReadResolution(12);
  analogSetPinAttenuation(PIN_EEG1,  ADC_11db);
  analogSetPinAttenuation(PIN_EEG2,  ADC_11db);
  analogSetPinAttenuation(PIN_EMG,   ADC_11db);
  analogSetPinAttenuation(PIN_PULSE, ADC_11db);
}

// ══════════════════════════════════════════════════════════════════════════
//  Watchdog Init  —  FIXED for ESP32 Arduino Core 3.x
//
//  Old API (Core 2.x):  esp_task_wdt_init(timeout_sec, panic)
//  New API (Core 3.x):  esp_task_wdt_init(&config_struct)
// ══════════════════════════════════════════════════════════════════════════
void initWatchdog() {
  esp_task_wdt_config_t wdt_config = {
    .timeout_ms    = WDT_TIMEOUT_SEC * 1000,  // milliseconds in Core 3.x
    .idle_core_mask = 0,                       // don't watch idle tasks
    .trigger_panic  = true                     // reboot on timeout
  };
  esp_task_wdt_reconfigure(&wdt_config);       // use reconfigure (safe on Core 3.x)
  esp_task_wdt_add(NULL);                      // watch the current (main loop) task
  Serial.printf("[WDT] Watchdog enabled — %d second timeout\n", WDT_TIMEOUT_SEC);
}

// ══════════════════════════════════════════════════════════════════════════
//  BLE Stack Init
// ══════════════════════════════════════════════════════════════════════════
void initBLE() {
  Serial.println("[BLE] Starting stack...");

  BLEDevice::init("BioWearable");
  BLEDevice::setMTU(185);   // prevents silent Android disconnect

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  // SENSOR_DATA — NOTIFY (ESP32 → Android)
  pSensorChar = pService->createCharacteristic(
    SENSOR_DATA_UUID,
    BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ
  );
  pSensorChar->addDescriptor(new BLE2902());

  // LED_CONTROL — WRITE (Android → ESP32)
  BLECharacteristic* pLedChar = pService->createCharacteristic(
    LED_CONTROL_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  pLedChar->setCallbacks(new LedCallback());

  // MOTOR_CONTROL — WRITE (Android → ESP32)
  BLECharacteristic* pMotorChar = pService->createCharacteristic(
    MOTOR_CONTROL_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  pMotorChar->setCallbacks(new MotorCallback());

  pService->start();

  pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);   // 7.5 ms connection interval
  pAdvertising->setMaxPreferred(0x0C);   // 15 ms connection interval

  BLEDevice::startAdvertising();
  Serial.println("[BLE] Advertising as 'BioWearable' — waiting for phone...");
}

// ══════════════════════════════════════════════════════════════════════════
//  Restart Advertising after disconnect
// ══════════════════════════════════════════════════════════════════════════
void restartAdvertising() {
  delay(500);                      // let BLE stack clean up old connection
  pServer->startAdvertising();     // device is now visible to phone again
  Serial.println("[BLE] Re-advertising ✅ — phone can scan again");
}

// ══════════════════════════════════════════════════════════════════════════
//  Send Sensor Data
// ══════════════════════════════════════════════════════════════════════════
void sendSensorData() {
  uint16_t eeg1  = analogRead(PIN_EEG1);
  uint16_t eeg2  = analogRead(PIN_EEG2);
  uint16_t emg   = analogRead(PIN_EMG);
  uint16_t pulse = analogRead(PIN_PULSE);

  uint8_t buf[8];
  buf[0] = eeg1  & 0xFF;  buf[1] = (eeg1  >> 8) & 0xFF;
  buf[2] = eeg2  & 0xFF;  buf[3] = (eeg2  >> 8) & 0xFF;
  buf[4] = emg   & 0xFF;  buf[5] = (emg   >> 8) & 0xFF;
  buf[6] = pulse & 0xFF;  buf[7] = (pulse >> 8) & 0xFF;

  pSensorChar->setValue(buf, 8);
  pSensorChar->notify();

  esp_task_wdt_reset();   // tell watchdog: still alive
}

// ══════════════════════════════════════════════════════════════════════════
//  Setup
// ══════════════════════════════════════════════════════════════════════════
void setup() {
  Serial.begin(115200);

  uint32_t t0 = millis();
  while (!Serial && (millis() - t0 < 3000)) delay(10);

  Serial.println("\n╔══════════════════════════════════════╗");
  Serial.println(  "║  BioWearable BLE  — Core 3.x Build   ║");
  Serial.println(  "╚══════════════════════════════════════╝");

  initHardware();
  initWatchdog();   // uses new Core 3.x API
  initBLE();
}

// ══════════════════════════════════════════════════════════════════════════
//  Loop
// ══════════════════════════════════════════════════════════════════════════
void loop() {

  // ── Phone just connected ────────────────────────────────────────────────
  if (pendingConnect) {
    pendingConnect  = false;
    deviceConnected = true;
    ledcWrite(PIN_LED_B, 0);    // stop blue blink
    ledcWrite(PIN_LED_G, 80);   // solid green = connected
    Serial.println("[LOOP] ✅ Connected — streaming sensor data");
  }

  // ── Phone just disconnected ─────────────────────────────────────────────
  if (pendingDisconnect) {
    pendingDisconnect = false;
    ledcWrite(PIN_LED_G, 0);
    Serial.println("[LOOP] ❌ Disconnected — restarting advertising");
    restartAdvertising();   // key fix: board visible again after disconnect
  }

  // ── Stream sensor data while connected ─────────────────────────────────
  if (deviceConnected) {
    unsigned long now = millis();
    if (now - lastSensorSend >= SENSOR_INTERVAL_MS) {
      lastSensorSend = now;
      sendSensorData();
    }
  }

  // ── Blink blue LED while advertising ───────────────────────────────────
  else {
    unsigned long now = millis();
    if (now - lastLedBlink >= LED_BLINK_INTERVAL_MS) {
      lastLedBlink  = now;
      ledBlinkState = !ledBlinkState;
      ledcWrite(PIN_LED_B, ledBlinkState ? 80 : 0);
    }
    esp_task_wdt_reset();   // keep watchdog happy while idle
  }
}
