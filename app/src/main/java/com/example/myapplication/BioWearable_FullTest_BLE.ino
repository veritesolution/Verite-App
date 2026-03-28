/*
 * ============================================================
 *  BioWearable_FullTest_BLE.ino
 *  Full Hardware Diagnostic + BLE Phone Connection
 *  Based on schematic: brainwaves.kicad_sch
 * ============================================================
 *
 *  1. On boot → runs ALL hardware tests automatically
 *     (LEDs, Motor, Sensors, MPU6050)
 *  2. Advertises as "BioWearable_Test" over Bluetooth
 *  3. Connect with "Serial Bluetooth Terminal" app
 *     (Android / iOS — free)
 *  4. All test results + live sensor data stream to phone
 *
 *  ── COMMANDS FROM PHONE ────────────────────────────────────
 *  ?   Help menu
 *  9   Re-run ALL tests
 *  1   Test Red LED
 *  2   Test Green LED
 *  3   Test Blue LED
 *  4   RGB colour cycle
 *  5   Vibration motor
 *  6   Sensor snapshot
 *  7   Start live sensor stream (50 Hz CSV)
 *  8   Stop  live sensor stream
 *  R   Set Red   brightness  (send 'R' + byte value 0-255)
 *  G   Set Green brightness
 *  B   Set Blue  brightness
 *  V   Motor on/off          (send 'V' + 0 or 1)
 *  O   All outputs OFF
 *
 *  ── APP SETUP ──────────────────────────────────────────────
 *  App  : "Serial Bluetooth Terminal" (Android/iOS)
 *  Name : BioWearable_Test
 *  Baud : not needed (BLE)
 *  Set newline to NONE in app settings
 *
 *  Board : ESP32-S3 Dev Module  |  Serial Monitor: 115200 baud
 * ============================================================
 */

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>

// ── Pin Definitions ────────────────────────────────────────────────────────
#define PIN_EEG1    4
#define PIN_EEG2    5
#define PIN_PULSE   6
#define PIN_EMG     7
#define PIN_LED_R   10
#define PIN_LED_G   11
#define PIN_LED_B   12
#define PIN_MOTOR   15
#define PIN_I2C_SCL 8
#define PIN_I2C_SDA 9

// ── PWM ────────────────────────────────────────────────────────────────────
#define PWM_FREQ       5000
#define PWM_RESOLUTION 8      // 0 = off, 255 = full brightness

// ── BLE UUIDs ──────────────────────────────────────────────────────────────
#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_TX "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHARACTERISTIC_UUID_RX "12345678-1234-5678-1234-56789abcdef0"

// ── Globals ────────────────────────────────────────────────────────────────
BLECharacteristic *pTxCharacteristic = NULL;
volatile bool      deviceConnected   = false;
volatile bool      streaming         = false;

// ── Test result tracking ───────────────────────────────────────────────────
struct TestResult { const char *name; bool passed; char note[40]; };
TestResult results[12];
int        resultCount = 0;

void recordResult(const char *name, bool passed, const char *note = "") {
  if (resultCount >= 12) return;
  results[resultCount] = { name, passed, "" };
  strncpy(results[resultCount].note, note, 39);
  resultCount++;
}

// ── Output: send text to BOTH Serial and BLE phone ────────────────────────
void sendLine(const char *fmt, ...) {
  char buf[256];
  va_list args;
  va_start(args, fmt);
  vsnprintf(buf, sizeof(buf), fmt, args);
  va_end(args);

  // Serial Monitor
  Serial.print(buf);

  // BLE — split into 180-char chunks (MTU safe)
  if (deviceConnected && pTxCharacteristic != NULL) {
    int len = strlen(buf);
    for (int i = 0; i < len; i += 180) {
      int chunkLen = min(180, len - i);
      char chunk[181];
      strncpy(chunk, buf + i, chunkLen);
      chunk[chunkLen] = '\0';
      pTxCharacteristic->setValue(chunk);
      pTxCharacteristic->notify();
      delay(25);
    }
  }
}

// ── Output helpers ─────────────────────────────────────────────────────────
void allLedsOff() {
  ledcWrite(PIN_LED_R, 0);
  ledcWrite(PIN_LED_G, 0);
  ledcWrite(PIN_LED_B, 0);
}

void resetOutputs() {
  allLedsOff();
  digitalWrite(PIN_MOTOR, LOW);
}

void setLedPWM(int pin, uint8_t val) { ledcWrite(pin, val); }

void printBanner(const char *title) {
  sendLine("\r\n+------------------------------------------+\r\n");
  sendLine("|  %-40s|\r\n", title);
  sendLine("+------------------------------------------+\r\n");
}

// ══════════════════════════════════════════════════════════════════════════
//  TEST FUNCTIONS
// ══════════════════════════════════════════════════════════════════════════

// ── LED sweep ──────────────────────────────────────────────────────────────
void pwmSweep(int pin, const char *color) {
  sendLine("  Sweeping %s...\r\n", color);
  for (int i = 0;   i <= 255; i += 5) { ledcWrite(pin, i); delay(6); }
  for (int i = 255; i >= 0;   i -= 5) { ledcWrite(pin, i); delay(6); }
  allLedsOff();
  sendLine("  %s sweep done\r\n", color);
  delay(150);
}

void testLedR() {
  printBanner("TEST 1 - Red LED (GPIO 10)");
  pwmSweep(PIN_LED_R, "RED");
  recordResult("Red LED", true, "PWM sweep OK");
}

void testLedG() {
  printBanner("TEST 2 - Green LED (GPIO 11)");
  pwmSweep(PIN_LED_G, "GREEN");
  recordResult("Green LED", true, "PWM sweep OK");
}

void testLedB() {
  printBanner("TEST 3 - Blue LED (GPIO 12)");
  pwmSweep(PIN_LED_B, "BLUE");
  recordResult("Blue LED", true, "PWM sweep OK");
}

// ── RGB colour cycle ───────────────────────────────────────────────────────
void testLedRGB() {
  printBanner("TEST 4 - RGB Colour Cycle");

  struct { const char *n; uint8_t r, g, b; } colors[] = {
    {"WHITE",   255, 255, 255},
    {"RED",     255,   0,   0},
    {"GREEN",     0, 255,   0},
    {"BLUE",      0,   0, 255},
    {"YELLOW",  255, 200,   0},
    {"CYAN",      0, 255, 255},
    {"MAGENTA", 255,   0, 200},
  };

  for (auto &c : colors) {
    sendLine("  %s\r\n", c.n);
    ledcWrite(PIN_LED_R, c.r);
    ledcWrite(PIN_LED_G, c.g);
    ledcWrite(PIN_LED_B, c.b);
    delay(600);
    allLedsOff();
    delay(200);
  }
  sendLine("  RGB cycle done\r\n");
  recordResult("RGB Colour Cycle", true, "7 colours OK");
}

// ── Vibration motor ────────────────────────────────────────────────────────
void testMotor() {
  printBanner("TEST 5 - Vibration Motor (GPIO 15)");

  sendLine("  Pulse 1: 1.5s - feel for vibration...\r\n");
  digitalWrite(PIN_MOTOR, HIGH); delay(1500);
  digitalWrite(PIN_MOTOR, LOW);  delay(400);

  sendLine("  Pulse 2: 300ms\r\n");
  digitalWrite(PIN_MOTOR, HIGH); delay(300);
  digitalWrite(PIN_MOTOR, LOW);  delay(400);

  sendLine("  Pulse 3: 300ms\r\n");
  digitalWrite(PIN_MOTOR, HIGH); delay(300);
  digitalWrite(PIN_MOTOR, LOW);

  sendLine("  Motor test done\r\n");
  recordResult("Vibration Motor", true, "3 pulses fired");
}

// ── Analog sensor snapshot ─────────────────────────────────────────────────
bool sensorSnapshot(const char *name, int pin, const char *note) {
  const int N = 30;
  uint32_t sum = 0;
  uint16_t mn = 4095, mx = 0;

  for (int i = 0; i < N; i++) {
    uint16_t v = analogRead(pin);
    sum += v;
    if (v < mn) mn = v;
    if (v > mx) mx = v;
    delay(4);
  }

  float    avg   = (float)sum / N;
  float    volts = avg * 3.3f / 4095.0f;
  uint16_t range = mx - mn;

  bool alive  = (avg > 30 && avg < 4070);
  bool active = (range > 10);

  const char *status = !alive  ? "NO SIGNAL" :
                        active  ? "SIGNAL OK" : "FLAT/DC  ";

  sendLine("  %-6s GPIO%2d | avg=%4.0f rng=%3d %.3fV [%s]\r\n",
    name, pin, avg, range, volts, status);
  sendLine("         %s\r\n", note);

  return alive;
}

void testAnalogSensors() {
  printBanner("TEST 6-9 - Bio-Sensors (30 samples each)");
  sendLine("  Attach electrodes for best results.\r\n\r\n");

  bool ok;
  ok = sensorSnapshot("EEG1",  PIN_EEG1,  "BioAmp EXG Pill 1 (5V)");
  recordResult("EEG1 Sensor",  ok, ok ? "ADC responding" : "Check wiring/5V");

  ok = sensorSnapshot("EEG2",  PIN_EEG2,  "BioAmp EXG Pill 2 (5V)");
  recordResult("EEG2 Sensor",  ok, ok ? "ADC responding" : "Check wiring/5V");

  ok = sensorSnapshot("PULSE", PIN_PULSE, "HW-B27 (3.3V)");
  recordResult("Pulse Sensor", ok, ok ? "ADC responding" : "Check wiring/3.3V");

  ok = sensorSnapshot("EMG",   PIN_EMG,   "Muscle Sensor v3 (dual 9V BT1+BT2)");
  recordResult("EMG Sensor",   ok, ok ? "ADC responding" : "Check 9V batteries");
}

// ── MPU6050 (no external library — raw I2C) ────────────────────────────────
void testMPU6050() {
  printBanner("TEST 10 - MPU6050 IMU (SCL=8, SDA=9)");

  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  Wire.beginTransmission(0x68);
  byte err = Wire.endTransmission();

  if (err != 0) {
    sendLine("  NOT FOUND on I2C (err %d)\r\n", err);
    sendLine("  Check: VCC=3.3V SCL=GPIO8 SDA=GPIO9\r\n");
    recordResult("MPU6050 IMU", false, "Not found on I2C");
    return;
  }

  sendLine("  MPU6050 found at 0x68!\r\n\r\n");

  // Wake up chip
  Wire.beginTransmission(0x68);
  Wire.write(0x6B); Wire.write(0x00);
  Wire.endTransmission();
  delay(100);

  sendLine("  # |  AccX   AccY   AccZ |  GyroX  GyroY  GyroZ\r\n");
  sendLine("  --+---------------------+----------------------\r\n");

  float sumX = 0, sumY = 0, sumZ = 0;

  for (int i = 0; i < 5; i++) {
    Wire.beginTransmission(0x68);
    Wire.write(0x3B);
    Wire.endTransmission(false);
    Wire.requestFrom(0x68, 14, true);

    int16_t ax = (Wire.read() << 8) | Wire.read();
    int16_t ay = (Wire.read() << 8) | Wire.read();
    int16_t az = (Wire.read() << 8) | Wire.read();
    Wire.read(); Wire.read();  // skip temp
    int16_t gx = (Wire.read() << 8) | Wire.read();
    int16_t gy = (Wire.read() << 8) | Wire.read();
    int16_t gz = (Wire.read() << 8) | Wire.read();

    float fax = ax / 16384.0f * 9.81f;
    float fay = ay / 16384.0f * 9.81f;
    float faz = az / 16384.0f * 9.81f;
    float fgx = gx / 131.0f;
    float fgy = gy / 131.0f;
    float fgz = gz / 131.0f;

    sumX += fax; sumY += fay; sumZ += faz;

    sendLine("  %d | %+5.2f  %+5.2f  %+5.2f | %+6.2f %+6.2f %+6.2f\r\n",
      i+1, fax, fay, faz, fgx, fgy, fgz);
    delay(100);
  }

  float gMag = sqrt(sq(sumX/5) + sq(sumY/5) + sq(sumZ/5));
  sendLine("\r\n  Gravity: %.2f m/s2 (expected ~9.8)\r\n", gMag);

  bool sane = (gMag > 7.0f && gMag < 12.5f);
  sendLine(sane ? "  IMU healthy\r\n" : "  Unexpected value - check mounting\r\n");
  recordResult("MPU6050 IMU", sane, sane ? "Accel+Gyro OK" : "Abnormal accel");
}

// ── Summary ────────────────────────────────────────────────────────────────
void printSummary() {
  printBanner("TEST SUMMARY");

  int passed = 0, failed = 0;
  for (int i = 0; i < resultCount; i++) {
    if (results[i].passed) {
      sendLine("  PASS  %-22s %s\r\n", results[i].name, results[i].note);
      passed++;
    } else {
      sendLine("  FAIL  %-22s %s\r\n", results[i].name, results[i].note);
      failed++;
    }
  }

  sendLine("\r\n  Result: %d / %d passed\r\n", passed, resultCount);

  if (failed == 0)
    sendLine("  All components healthy - board is ready!\r\n");
  else
    sendLine("  Fix FAIL items above then re-run test 9.\r\n");
}

// ── Run all tests ──────────────────────────────────────────────────────────
void runAllTests() {
  resultCount = 0;  // reset results for fresh run

  sendLine("\r\n==========================================\r\n");
  sendLine("   BioWearable Full Hardware Test\r\n");
  sendLine("==========================================\r\n");

  testLedR();           delay(200);
  testLedG();           delay(200);
  testLedB();           delay(200);
  testLedRGB();         delay(200);
  testMotor();          delay(200);
  testAnalogSensors();  delay(200);
  testMPU6050();        delay(200);

  printSummary();

  sendLine("\r\nSend '7' to start live sensor stream.\r\n");
  sendLine("Send '?' for full command list.\r\n");
}

// ── Help menu ──────────────────────────────────────────────────────────────
void printHelp() {
  sendLine("\r\n=== BioWearable Commands ===\r\n");
  sendLine("1 = Test Red LED\r\n");
  sendLine("2 = Test Green LED\r\n");
  sendLine("3 = Test Blue LED\r\n");
  sendLine("4 = RGB colour cycle\r\n");
  sendLine("5 = Vibration motor\r\n");
  sendLine("6 = Sensor snapshot\r\n");
  sendLine("7 = Start live stream\r\n");
  sendLine("8 = Stop  live stream\r\n");
  sendLine("9 = Run ALL tests\r\n");
  sendLine("O = All outputs OFF\r\n");
  sendLine("? = This help menu\r\n");
  sendLine("===========================\r\n");
}

// ══════════════════════════════════════════════════════════════════════════
//  BLE CALLBACKS
// ══════════════════════════════════════════════════════════════════════════

class MyServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *pServer) {
    deviceConnected = true;
    streaming       = false;
    Serial.println(">> Phone connected!");
    delay(600);  // let BLE stack settle before sending
    sendLine("\r\nBioWearable connected!\r\n");
    sendLine("Send '9' to run all tests.\r\n");
    sendLine("Send '?' for commands.\r\n");
  }

  void onDisconnect(BLEServer *pServer) {
    deviceConnected = false;
    streaming       = false;
    resetOutputs();
    BLEDevice::startAdvertising();
    Serial.println(">> Phone disconnected. Advertising restarted.");
  }
};

class MyCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pCharacteristic) {
    String rx = pCharacteristic->getValue();
    if (rx.length() == 0) return;

    char    cmd = rx[0];
    uint8_t val = (rx.length() >= 2) ? (uint8_t)rx[1] : 0;

    Serial.printf("CMD: '%c'  VAL: %d\n", cmd, val);

    // Stop stream for any command except stream toggle
    if (cmd != '7' && cmd != '8') streaming = false;

    switch (cmd) {
      case '1': testLedR();        break;
      case '2': testLedG();        break;
      case '3': testLedB();        break;
      case '4': testLedRGB();      break;
      case '5': testMotor();       break;
      case '6': testAnalogSensors(); break;
      case '7':
        streaming = true;
        sendLine("Live stream started. Send '8' to stop.\r\n");
        sendLine("EEG1,EEG2,EMG,PULSE\r\n");
        break;
      case '8':
        streaming = false;
        sendLine("Live stream stopped.\r\n");
        break;
      case '9': runAllTests();     break;

      // Direct hardware control
      case 'R': setLedPWM(PIN_LED_R, val); sendLine("Red=%d\r\n",   val); break;
      case 'G': setLedPWM(PIN_LED_G, val); sendLine("Green=%d\r\n", val); break;
      case 'B': setLedPWM(PIN_LED_B, val); sendLine("Blue=%d\r\n",  val); break;
      case 'V':
        digitalWrite(PIN_MOTOR, val > 0 ? HIGH : LOW);
        sendLine("Motor %s\r\n", val > 0 ? "ON" : "OFF");
        break;
      case 'O':
        resetOutputs();
        sendLine("All outputs OFF\r\n");
        break;
      case '?': printHelp(); break;

      default:
        sendLine("Unknown: '%c'  Send '?' for help.\r\n", cmd);
        break;
    }
  }
};

// ── BLE Init ───────────────────────────────────────────────────────────────
void initBLE() {
  BLEDevice::init("BioWearable_Test");
  BLEDevice::setMTU(200);

  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // TX — notify phone
  pTxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID_TX,
    BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->addDescriptor(new BLE2902());

  // RX — receive commands from phone
  BLECharacteristic *pRx = pService->createCharacteristic(
    CHARACTERISTIC_UUID_RX,
    BLECharacteristic::PROPERTY_WRITE);
  pRx->setCallbacks(new MyCallbacks());

  pService->start();
  pServer->getAdvertising()->start();
  Serial.println("BLE advertising as 'BioWearable_Test'");
}

// ══════════════════════════════════════════════════════════════════════════
//  SETUP & LOOP
// ══════════════════════════════════════════════════════════════════════════

void setup() {
  Serial.begin(115200);
  delay(1500);

  Serial.println("\n=== BioWearable Full Test + BLE ===");

  // Init outputs
  pinMode(PIN_MOTOR, OUTPUT);
  digitalWrite(PIN_MOTOR, LOW);
  ledcAttach(PIN_LED_R, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(PIN_LED_G, PWM_FREQ, PWM_RESOLUTION);
  ledcAttach(PIN_LED_B, PWM_FREQ, PWM_RESOLUTION);
  allLedsOff();

  // Init ADC
  analogReadResolution(12);  // 12-bit: 0–4095

  // Boot blink — confirms power-on before BLE starts
  int bootPins[] = {PIN_LED_R, PIN_LED_G, PIN_LED_B};
  for (int i = 0; i < 3; i++) {
    ledcWrite(bootPins[i], 200); delay(150);
    ledcWrite(bootPins[i], 0);   delay(80);
  }

  // Start BLE
  initBLE();

  // Run all tests immediately on boot (output goes to Serial Monitor)
  // Results will also go to phone once it connects
  Serial.println("Running boot self-test...");
  runAllTests();

  Serial.println("\nWaiting for phone to connect...");
  Serial.println("Open 'Serial Bluetooth Terminal' app");
  Serial.println("Connect to: BioWearable_Test");
}

void loop() {
  // Live sensor stream — only runs when phone requests it (command '7')
  if (deviceConnected && streaming) {
    char buf[48];
    snprintf(buf, sizeof(buf), "%d,%d,%d,%d\r\n",
      analogRead(PIN_EEG1),
      analogRead(PIN_EEG2),
      analogRead(PIN_EMG),
      analogRead(PIN_PULSE));

    pTxCharacteristic->setValue(buf);
    pTxCharacteristic->notify();
    delay(20);  // 50 Hz
  }

  delay(10);
}
