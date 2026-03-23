/*
 * ble_comm.h v7
 * Supports two injection modes:
 *   PPG: "INJECT_START" then numeric lines "2048\n"
 *   EEG: "EEG_START"    then "E:2048\n" lines
 *        "EEG_STOP"     to end EEG injection
 * RR direct inject: "RR:650\n"
 */
#ifndef BLE_COMM_H
#define BLE_COMM_H

#include "config.h"
#include "outputs.h"

static bool ledR=false,ledG=false,ledB=false,motor=false;

QueueHandle_t  injectQueue    = NULL;  // PPG samples (uint16)
QueueHandle_t  eegInjectQueue = NULL;  // EEG samples (uint16)
QueueHandle_t  emgInjectQueue = NULL;  // EMG samples (uint16)
QueueHandle_t  rrInjectQueue  = NULL;  // RR intervals

volatile bool  injectModeActive    = false;
volatile bool  eegInjectModeActive = false;
volatile bool  emgInjectModeActive = false;

class ServerCB : public BLEServerCallbacks {
  void onConnect(BLEServer *s) override {
    deviceConnected=true;
    Serial.println("[BLE] Connected");
  }
  void onDisconnect(BLEServer *s) override {
    deviceConnected=false;
    injectModeActive=false;
    eegInjectModeActive=false;
    emgInjectModeActive=false;
    Serial.println("[BLE] Disconnected");
    s->startAdvertising();
    resetOutputs();
    ledR=ledG=ledB=motor=false;
  }
};

class RxCB : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    String v=c->getValue();if(v.length()==0)return;v.trim();

    // ── EMG injection control ─────────────────────────────────────
    if(v=="EMG_START"){
      emgInjectModeActive=true;
      Serial.println("[EMG_INJECT] Mode ON");return;
    }
    if(v=="EMG_STOP"){
      emgInjectModeActive=false;
      Serial.println("[EMG_INJECT] Mode OFF");return;
    }

    // ── EMG sample: "M:2048" ──────────────────────────────────────
    if(v.startsWith("M:")){
      uint16_t s=(uint16_t)v.substring(2).toInt();
      if(emgInjectQueue) xQueueSend(emgInjectQueue,&s,0);
      return;
    }

    // ── EEG injection control ─────────────────────────────────────
    if(v=="EEG_START"){
      eegInjectModeActive=true;
      Serial.println("[EEG_INJECT] Mode ON");return;
    }
    if(v=="EEG_STOP"){
      eegInjectModeActive=false;
      Serial.println("[EEG_INJECT] Mode OFF");return;
    }

    // ── EEG sample: "E:2048" ──────────────────────────────────────
    if(v.startsWith("E:")){
      uint16_t s=(uint16_t)v.substring(2).toInt();
      if(eegInjectQueue) xQueueSend(eegInjectQueue,&s,0);
      return;
    }

    // ── RR direct inject: "RR:650" ────────────────────────────────
    if(v.startsWith("RR:")){
      float rrMs=v.substring(3).toFloat();
    // --- TEMPORARY FIX FOR EEG TESTING ---
        // We commented out the injectRR line below so the ESP32 compiles.
        // You can uncomment this later when you go back to testing the Pulse Sensor!
        
        if(rrMs > 200.0f && rrMs < 2500.0f) {
            // dsp.hrv.injectRR(rrMs); 
        }
        // -------------------------------------
      return;
    }

    // ── PPG injection control ─────────────────────────────────────
    if(v=="INJECT_START"){injectModeActive=true;Serial.println("[INJECT] PPG ON");return;}
    if(v=="INJECT_STOP") {injectModeActive=false;Serial.println("[INJECT] PPG OFF");return;}

    // ── PPG numeric sample ────────────────────────────────────────
    bool isNum=true;
    for(int i=0;i<(int)v.length();i++){if(!isDigit(v[i])){isNum=false;break;}}
    if(isNum&&v.length()>0){
      uint16_t s=(uint16_t)v.toInt();
      if(injectQueue) xQueueSend(injectQueue,&s,0);
      return;
    }

    // ── LED/Motor commands ────────────────────────────────────────
    char cmd=v[0];
    switch(cmd){
      case 'R':ledR=!ledR;digitalWrite(PIN_LED_R,ledR);break;
      case 'G':ledG=!ledG;digitalWrite(PIN_LED_G,ledG);break;
      case 'B':ledB=!ledB;digitalWrite(PIN_LED_B,ledB);break;
      case 'W':ledR=ledG=ledB=true;
        digitalWrite(PIN_LED_R,HIGH);digitalWrite(PIN_LED_G,HIGH);digitalWrite(PIN_LED_B,HIGH);break;
      case 'M':motor=true; digitalWrite(PIN_MOTOR,HIGH);break;
      case 'X':motor=false;digitalWrite(PIN_MOTOR,LOW); break;
      case 'O':resetOutputs();ledR=ledG=ledB=motor=false;break;
      default:break;
    }
  }
};

void initBLE(){
  injectQueue    = xQueueCreate(1024,sizeof(uint16_t));
  eegInjectQueue = xQueueCreate(1024,sizeof(uint16_t));
  emgInjectQueue = xQueueCreate(1024,sizeof(uint16_t));

  BLEDevice::init("BioWearable");
  BLEServer *srv=BLEDevice::createServer();
  srv->setCallbacks(new ServerCB());
  BLEService *svc=srv->createService(BLEUUID(SERVICE_UUID),32);

  pTxCharacteristic=svc->createCharacteristic(
    CHARACTERISTIC_UUID_TX,BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic *rxChar=svc->createCharacteristic(
    CHARACTERISTIC_UUID_RX,
    BLECharacteristic::PROPERTY_WRITE|BLECharacteristic::PROPERTY_WRITE_NR);
  rxChar->setCallbacks(new RxCB());

  pProcCharacteristic=svc->createCharacteristic(
    CHARACTERISTIC_UUID_PROC,BLECharacteristic::PROPERTY_NOTIFY);
  pProcCharacteristic->addDescriptor(new BLE2902());

  svc->start();
  BLEAdvertising *adv=BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  adv->setMinPreferred(0x06);adv->setMaxPreferred(0x0C);
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Advertising — PPG+EEG+EMG+RR inject ready");
}

void Task_BLE_Transmit(void *pvParameters){
  SensorPacket rawPkt;ProcessedPacket procPkt;
  for(;;){
    if(xQueueReceive(sensorQueue,&rawPkt,pdMS_TO_TICKS(1))==pdPASS){
      SensorPacket tmp;
      while(xQueueReceive(sensorQueue,&tmp,0)==pdPASS)rawPkt=tmp;
      if(deviceConnected&&pTxCharacteristic){
        pTxCharacteristic->setValue((uint8_t*)&rawPkt,sizeof(SensorPacket));
        pTxCharacteristic->notify();
      }
    }
    if(xQueueReceive(processQueue,&procPkt,0)==pdPASS){
      if(deviceConnected&&pProcCharacteristic){
        pProcCharacteristic->setValue((uint8_t*)&procPkt,sizeof(ProcessedPacket));
        pProcCharacteristic->notify();
      }
    }
    taskYIELD();
  }
}
#endif