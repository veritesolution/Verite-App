#include <esp_task_wdt.h>
/*
 * Brain-Emotion v13.3 — ESP32-S3 BLE EEG Firmware
 * ================================================
 * Hardware: BioAmp EXG Pill (x2) + ESP32-S3
 * Channels: F3 (ADC1_CH0 = GPIO1) + F4 (ADC1_CH3 = GPIO4)
 * Output: BLE packets at 250 Hz to Kotlin app
 *
 * BLE Packet Format:
 *   10 samples × 2 channels × 4 bytes (float32) = 80 bytes per packet
 *   Sent every 40ms (10 samples at 250 Hz)
 *   Values in microvolts (µV) after ADC conversion
 *
 * BLE Service: 4fafc201-1fb5-459e-8fcc-c5c9c331914b
 * BLE Char:    beb5483e-36e1-4688-b7f5-ea07361b26a8
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ═══ HARDWARE CONFIG ════════════════════════════════════════════════════
#define PIN_F3          1       // ADC1_CH0 — BioAmp EXG Pill #1 output
#define PIN_F4          4       // ADC1_CH3 — BioAmp EXG Pill #2 output
#define SAMPLING_RATE   250     // Hz
#define SAMPLES_PER_PKT 10      // samples per BLE packet
#define ADC_BITS        12
#define ADC_VREF        3.3f
#define BIOAMP_GAIN     1100.0f

// ADC to microvolts conversion
#define ADC_TO_UV       ((ADC_VREF / (1 << ADC_BITS)) / BIOAMP_GAIN * 1e6f)

// ═══ BLE CONFIG ═════════════════════════════════════════════════════════
#define SERVICE_UUID    "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHAR_UUID       "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define DEVICE_NAME     "BrainEmotion-F3F4"

// ═══ GLOBALS ════════════════════════════════════════════════════════════
BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;

// Packet buffer: 10 samples × 2 channels × float32
float packetBuffer[SAMPLES_PER_PKT * 2];
int sampleCount = 0;

// Timing
hw_timer_t* sampleTimer = NULL;
volatile bool sampleReady = false;
unsigned long lastPacketTime = 0;

// ═══ BLE CALLBACKS ══════════════════════════════════════════════════════
class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* server) {
        deviceConnected = true;
        Serial.println("BLE: Connected");
    }
    void onDisconnect(BLEServer* server) {
        deviceConnected = false;
        Serial.println("BLE: Disconnected");
        // Restart advertising after 500ms delay
        delay(500);
        BLEDevice::startAdvertising();
        Serial.println("BLE: Re-advertising");
    }
};

// ═══ TIMER ISR — 250 Hz sampling ════════════════════════════════════════
void IRAM_ATTR onSampleTimer() {
    sampleReady = true;
}

// ═══ SETUP ══════════════════════════════════════════════════════════════
void setup() {
    Serial.begin(115200);
    Serial.println("\n🧠 Brain-Emotion v13.3 — ESP32-S3 EEG");

    // ADC setup
    analogReadResolution(ADC_BITS);
    analogSetAttenuation(ADC_11db);  // Full range 0-3.3V
    pinMode(PIN_F3, INPUT);
    pinMode(PIN_F4, INPUT);

    // BLE setup
    BLEDevice::init(DEVICE_NAME);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    BLEService* pService = pServer->createService(SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
        CHAR_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pCharacteristic->addDescriptor(new BLE2902());
    pService->start();

    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);  // iPhone connection fix
    BLEDevice::startAdvertising();

    // NOTE: BLE sends little-endian float32 — matches Android/ARM default
    Serial.printf("BLE: Advertising as '%s'\n", DEVICE_NAME);
    Serial.printf("Channels: F3=GPIO%d, F4=GPIO%d\n", PIN_F3, PIN_F4);
    Serial.printf("Sampling: %d Hz, %d samples/packet\n", SAMPLING_RATE, SAMPLES_PER_PKT);

    // Timer: 250 Hz = 4000 µs period
    sampleTimer = timerBegin(0, 80, true);  // 80 MHz / 80 = 1 MHz
    timerAttachInterrupt(sampleTimer, &onSampleTimer, true);
    timerAlarmWrite(sampleTimer, 4000, true);  // 4000 µs = 250 Hz
    timerAlarmEnable(sampleTimer);

    // Hardware watchdog — restart if main loop hangs >5s
    esp_task_wdt_init(5, true);
    esp_task_wdt_add(NULL);
}

// ═══ MAIN LOOP ══════════════════════════════════════════════════════════
void loop() {
    if (!sampleReady) return;
    esp_task_wdt_reset();  // feed watchdog
    sampleReady = false;

    // Read ADC
    int raw_f3 = analogRead(PIN_F3);
    int raw_f4 = analogRead(PIN_F4);

    // Convert to microvolts
    float uv_f3 = (float)(raw_f3 - 2048) * ADC_TO_UV;  // Center at mid-range
    float uv_f4 = (float)(raw_f4 - 2048) * ADC_TO_UV;

    // Store in packet buffer
    packetBuffer[sampleCount * 2]     = uv_f3;
    packetBuffer[sampleCount * 2 + 1] = uv_f4;
    sampleCount++;

    // Send packet when buffer full
    if (sampleCount >= SAMPLES_PER_PKT) {
        if (deviceConnected) {
            pCharacteristic->setValue(
                (uint8_t*)packetBuffer,
                SAMPLES_PER_PKT * 2 * sizeof(float)
            );
            pCharacteristic->notify();
        }
        sampleCount = 0;
    }
}
