#ifndef BLE_COMM_H
#define BLE_COMM_H
#include "config.h"
#include "outputs.h"

static bool ledR=false,ledG=false,ledB=false,motor=false;
QueueHandle_t  injectQueue      = NULL;
volatile bool  injectModeActive = false;
volatile bool  injectEEGMode    = false;  // true = inject to EEG1, false = inject to PULSE

class ServerCB:public BLEServerCallbacks{
    void onConnect(BLEServer*s)override{deviceConnected=true;Serial.println("[BLE] Connected");}
    void onDisconnect(BLEServer*s)override{
      deviceConnected=false;injectModeActive=false;injectEEGMode=false;
      Serial.println("[BLE] Disconnected");s->startAdvertising();
      resetOutputs();ledR=ledG=ledB=motor=false;
    }
};

class RxCB:public BLECharacteristicCallbacks{
    void onWrite(BLECharacteristic*c)override{
      String v=c->getValue();if(!v.length())return;v.trim();

      // Injection control commands
      if(v=="INJECT_START"){
        injectModeActive=true;injectEEGMode=false;
        Serial.println("[INJECT] PPG mode ON");return;
      }
      if(v=="INJECT_EEG"){
        injectModeActive=true;injectEEGMode=true;
        Serial.println("[INJECT] EEG mode ON");return;
      }
      if(v=="INJECT_STOP"){
        injectModeActive=false;injectEEGMode=false;
        Serial.println("[INJECT] OFF");return;
      }

      // Numeric sample
      bool isNum=true;
      for(int i=0;i<(int)v.length();i++)if(!isDigit(v[i])){isNum=false;break;}
      if(isNum&&v.length()){
        uint16_t s=(uint16_t)v.toInt();
        if(injectQueue)xQueueSend(injectQueue,&s,0);
        return;
      }

      // Single-char output commands
      char cmd=v[0];
      switch(cmd){
        case 'R':ledR=!ledR;digitalWrite(PIN_LED_R,ledR);break;
        case 'G':ledG=!ledG;digitalWrite(PIN_LED_G,ledG);break;
        case 'B':ledB=!ledB;digitalWrite(PIN_LED_B,ledB);break;
        case 'W':
          ledR=ledG=ledB=true;
              digitalWrite(PIN_LED_R,HIGH);
              digitalWrite(PIN_LED_G,HIGH);
              digitalWrite(PIN_LED_B,HIGH);
              break;
        case 'M':motor=true; digitalWrite(PIN_MOTOR,HIGH);break;
        case 'X':motor=false;digitalWrite(PIN_MOTOR,LOW); break;
        case 'O':resetOutputs();ledR=ledG=ledB=motor=false;break;
      }
    }
};

void initBLE(){
  injectQueue=xQueueCreate(1024,sizeof(uint16_t));
  BLEDevice::init("BioWearable");
  BLEServer*srv=BLEDevice::createServer();srv->setCallbacks(new ServerCB());
  BLEService*svc=srv->createService(BLEUUID(SERVICE_UUID),32);
  pTxCharacteristic=svc->createCharacteristic(
          CHARACTERISTIC_UUID_TX,BLECharacteristic::PROPERTY_NOTIFY);
  pTxCharacteristic->addDescriptor(new BLE2902());
  BLECharacteristic*rx=svc->createCharacteristic(
          CHARACTERISTIC_UUID_RX,
          BLECharacteristic::PROPERTY_WRITE|BLECharacteristic::PROPERTY_WRITE_NR);
  rx->setCallbacks(new RxCB());
  pProcCharacteristic=svc->createCharacteristic(
          CHARACTERISTIC_UUID_PROC,BLECharacteristic::PROPERTY_NOTIFY);
  pProcCharacteristic->addDescriptor(new BLE2902());
  svc->start();
  BLEAdvertising*adv=BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);adv->setScanResponse(true);
  adv->setMinPreferred(0x06);adv->setMaxPreferred(0x0C);
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Advertising as BioWearable");
}

void Task_BLE_Transmit(void*pv){
  SensorPacket rp;ProcessedPacket pp;
  for(;;){
    if(xQueueReceive(sensorQueue,&rp,pdMS_TO_TICKS(1))==pdPASS){
      SensorPacket t;while(xQueueReceive(sensorQueue,&t,0)==pdPASS)rp=t;
      if(deviceConnected&&pTxCharacteristic){
        pTxCharacteristic->setValue((uint8_t*)&rp,sizeof(SensorPacket));
        pTxCharacteristic->notify();
      }
    }
    if(xQueueReceive(processQueue,&pp,0)==pdPASS){
      if(deviceConnected&&pProcCharacteristic){
        pProcCharacteristic->setValue((uint8_t*)&pp,sizeof(ProcessedPacket));
        pProcCharacteristic->notify();
      }
    }
    taskYIELD();
  }
}
#endif