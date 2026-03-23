/*
 * BioWearable_working.ino — Main entry point
 */

#include "config.h"
#include "outputs.h"
#include "dsp.h"
#include "sensors.h"
#include "ble_comm.h"

// Shared globals
BLECharacteristic *pTxCharacteristic   = NULL;
BLECharacteristic *pProcCharacteristic = NULL;
volatile bool      deviceConnected     = false;
QueueHandle_t      sensorQueue;
QueueHandle_t      processQueue;

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n=== BioWearable v5.0 ===");

  initOutputs();
  bootBlink();
  initBLE();

  sensorQueue  = xQueueCreate(4,    sizeof(SensorPacket));
  processQueue = xQueueCreate(4,    sizeof(ProcessedPacket));

  if (!sensorQueue || !processQueue) {
    Serial.println("[FATAL] Queue creation failed");
    while(1) delay(1000);
  }

  xTaskCreatePinnedToCore(Task_BLE_Transmit, "BLE_TX",   8192, NULL, 2, NULL, 0);
  xTaskCreatePinnedToCore(Task_Sensor_Read,  "SENS_DSP", 8192, NULL, 1, NULL, 1);

  Serial.println("[MAIN] All tasks started");
}

void loop() {
  vTaskDelete(NULL);
}
