/*
 * sensors.h v7
 * Handles three injection modes:
 *   1. PPG (injectQueue)      → rawPkt.pulse
 *   2. EEG (eegInjectQueue)   → rawPkt.eeg1
 *   3. RR direct (dsp.hrv.injectRR) — handled in ble_comm.h
 */
#ifndef SENSORS_H
#define SENSORS_H

#include "config.h"
#include "dsp.h"

extern QueueHandle_t injectQueue;
extern QueueHandle_t eegInjectQueue;
extern QueueHandle_t emgInjectQueue;
extern volatile bool injectModeActive;
extern volatile bool eegInjectModeActive;
extern volatile bool emgInjectModeActive;

static hw_timer_t    *samplingTimer=NULL;
static volatile bool  samplingFlag=false;

void IRAM_ATTR onSamplingTimer(){samplingFlag=true;}

struct EMAFilter {
  float value;bool initialized;
  EMAFilter():value(0),initialized(false){}
  uint16_t apply(uint16_t raw){
    if(!initialized){value=raw;initialized=true;}
    else value=FILTER_ALPHA*raw+(1.0f-FILTER_ALPHA)*value;
    return (uint16_t)(value+0.5f);
  }
};

static void mpuWrite(uint8_t reg,uint8_t val){
  Wire.beginTransmission(MPU_ADDR);Wire.write(reg);Wire.write(val);Wire.endTransmission();
}
static uint8_t mpuRead8(uint8_t reg){
  Wire.beginTransmission(MPU_ADDR);Wire.write(reg);Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)MPU_ADDR,(uint8_t)1);
  return Wire.available()?Wire.read():0;
}
static void mpuReadBurst(uint8_t reg,uint8_t *buf,uint8_t n){
  Wire.beginTransmission(MPU_ADDR);Wire.write(reg);Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)MPU_ADDR,n);
  for(uint8_t i=0;i<n&&Wire.available();i++)buf[i]=Wire.read();
}
static bool mpuInit(){
  Wire.begin(PIN_SDA,PIN_SCL,400000);delay(100);
  uint8_t who=mpuRead8(MPU_REG_WHO_AM_I);
  if(who!=0x68&&who!=0x70&&who!=0x72){
    Serial.printf("[MPU] not found 0x%02X\n",who);return false;
  }
  mpuWrite(MPU_REG_PWR_MGT1,0x00);delay(10);
  mpuWrite(MPU_REG_SMPLRT,0x07);mpuWrite(MPU_REG_CONFIG,0x03);
  mpuWrite(MPU_REG_ACCEL_CFG,MPU_ACCEL_FS_2G);mpuWrite(MPU_REG_GYRO_CFG,MPU_GYRO_FS_250);
  Serial.printf("[MPU] OK 0x%02X\n",who);return true;
}
static void mpuReadAll(int16_t &ax,int16_t &ay,int16_t &az,
                       int16_t &gx,int16_t &gy,int16_t &gz,int16_t &tmp){
  uint8_t r[14];mpuReadBurst(MPU_REG_ACCEL_X,r,14);
  ax=(int16_t)(r[0]<<8|r[1]);ay=(int16_t)(r[2]<<8|r[3]);az=(int16_t)(r[4]<<8|r[5]);
  tmp=(int16_t)(r[6]<<8|r[7]);
  gx=(int16_t)(r[8]<<8|r[9]);gy=(int16_t)(r[10]<<8|r[11]);gz=(int16_t)(r[12]<<8|r[13]);
}

void Task_Sensor_Read(void *pvParameters){
  SensorPacket rawPkt;ProcessedPacket procPkt;
  uint16_t seqCounter=0;uint32_t droppedRaw=0;
  uint32_t ppgCount=0,eegCount=0,emgCount=0;
  bool wasPPG=false,wasEEG=false,wasEMG=false;
  bool mpuOk=false;
  EMAFilter fEEG1,fEMG,fPulse;
  analogReadResolution(12);

  // Hardware timer 250Hz (ESP32 Core v3.x)
  samplingTimer=timerBegin(1000000);
  timerAttachInterrupt(samplingTimer,&onSamplingTimer);
  timerAlarm(samplingTimer,4000,true,0);
  Serial.println("[SENS] Hardware timer 250Hz OK");

  for(int attempt=0;attempt<3&&!mpuOk;attempt++){
    mpuOk=mpuInit();if(!mpuOk){Serial.println("[MPU] retry...");delay(200);}
  }
  if(!mpuOk)Serial.println("[MPU] Not found — zeros used");
  Serial.println("[SENS] Task running — PPG+EEG inject ready");

  for(;;){
    if(!samplingFlag){taskYIELD();continue;}
    samplingFlag=false;
    rawPkt.seq=seqCounter++;

    // ── EEG injection (priority: eeg over ppg) ────────────────────
    uint16_t eegSample=0;
    bool hasEEG=(eegInjectQueue&&xQueueReceive(eegInjectQueue,&eegSample,0)==pdPASS);

    // ── EMG injection ─────────────────────────────────────────────
    uint16_t emgSample=0;
    bool hasEMG=(emgInjectQueue&&xQueueReceive(emgInjectQueue,&emgSample,0)==pdPASS);

    // ── PPG injection ─────────────────────────────────────────────
    uint16_t ppgSample=0;
    bool hasPPG=(injectQueue&&xQueueReceive(injectQueue,&ppgSample,0)==pdPASS);

    if(hasEEG){
      if(!wasEEG){Serial.println("[EEG_INJECT] Started");wasEEG=true;}
      eegCount++;
      rawPkt.eeg1=eegSample;     // injected EEG sample
      rawPkt.eeg2=0;
      rawPkt.emg=2048;           // neutral
      rawPkt.pulse=2048;         // neutral

      // Force contact for HRV during EEG-only test
      if(eegInjectModeActive){
        dsp.hrv.contacted=true;dsp.hrv.varFull=true;dsp.hrv.dbgSwing=60.0f;
      }
      if(eegCount%500==0)
        Serial.printf("[EEG_INJECT] %lu samples  eeg1=%u  alpha=%u%%  beta=%u%%\n",
                      eegCount,eegSample,
                      dsp.eeg1.alpha_pct,dsp.eeg1.beta_pct);
    } else if(hasEMG){
      if(!wasEMG){Serial.println("[EMG_INJECT] Started");wasEMG=true;}
      if(wasEEG){Serial.println("[EEG_INJECT] Stopped");wasEEG=false;}
      emgCount++;
      rawPkt.emg=emgSample;       // injected EMG sample
      rawPkt.eeg1=2048;rawPkt.eeg2=0;
      rawPkt.pulse=2048;           // neutral pulse
      if(emgInjectModeActive){
        dsp.hrv.contacted=true;dsp.hrv.varFull=true;dsp.hrv.dbgSwing=60.0f;
      }
      if(emgCount%500==0)
        Serial.printf("[EMG_INJECT] %lu samples  emg=%u  rms=%u  mf=%u  active=%u\n",
                      emgCount,emgSample,dsp.emg.rms,dsp.emg.meanFreq,dsp.emg.activated);
    } else if(hasPPG){
      if(!wasPPG){Serial.println("[PPG_INJECT] Started");wasPPG=true;}
      if(wasEEG){Serial.println("[EEG_INJECT] Stopped");wasEEG=false;}
      if(wasEMG){Serial.println("[EMG_INJECT] Stopped");wasEMG=false;}
      ppgCount++;
      rawPkt.pulse=ppgSample;
      rawPkt.eeg1=2048;rawPkt.eeg2=0;rawPkt.emg=2048;
      if(injectModeActive){
        dsp.hrv.contacted=true;dsp.hrv.varFull=true;dsp.hrv.dbgSwing=60.0f;
      }
    } else {
      // Live sensors
      if(wasPPG){Serial.println("[PPG_INJECT] Stopped — live mode");wasPPG=false;}
      if(wasEEG){Serial.println("[EEG_INJECT] Stopped — live mode");wasEEG=false;}
      if(wasEMG){Serial.println("[EMG_INJECT] Stopped — live mode");wasEMG=false;}
      rawPkt.eeg1=fEEG1.apply(analogRead(PIN_EEG1));
      rawPkt.eeg2=0;
      rawPkt.emg=fEMG.apply(analogRead(PIN_EMG));
      rawPkt.pulse=fPulse.apply(analogRead(PIN_PULSE));
    }

    if(mpuOk){
      int16_t ax,ay,az,gx,gy,gz,tmp;
      mpuReadAll(ax,ay,az,gx,gy,gz,tmp);
      rawPkt.ax=ax;rawPkt.ay=ay;rawPkt.az=az;
      rawPkt.gx=gx;rawPkt.gy=gy;rawPkt.gz=gz;rawPkt.temp=tmp;
    } else {
      rawPkt.ax=rawPkt.ay=rawPkt.az=0;
      rawPkt.gx=rawPkt.gy=rawPkt.gz=0;rawPkt.temp=0;
    }

    dsp.processSample(rawPkt);

    if(xQueueSend(sensorQueue,&rawPkt,0)!=pdPASS){
      if(++droppedRaw%250==0)Serial.printf("[WARN] %lu raw dropped\n",droppedRaw);
    }

    if(seqCounter%125==0){
      dsp.fillPacket(procPkt);
      xQueueSend(processQueue,&procPkt,0);
      if(wasEEG||hasEEG){
        Serial.printf("[EEG_DSP] delta=%3u%% theta=%3u%% alpha=%3u%% beta=%3u%% gamma=%3u%%\n",
          procPkt.e1_delta,procPkt.e1_theta,procPkt.e1_alpha,
          procPkt.e1_beta,procPkt.e1_gamma);
      } else if(wasEMG||hasEMG){
        Serial.printf("[EMG_DSP] rms=%5u mf=%3uHz zcr=%3u active=%u fatigue=%3u%%  [EMG_INJECT]\n",
          procPkt.emg_rms,procPkt.emg_mf,procPkt.emg_zcr,
          procPkt.emg_active,procPkt.emg_fatigue);
      } else {
        Serial.printf("[DSP] BPM=%3u RMSSD=%3u SDNN=%3u %s\n",
          procPkt.bpm,procPkt.rmssd,procPkt.sdnn,
          (wasPPG||hasPPG)?"[PPG_INJECT]":"[LIVE]");
      }
    }
  }
}
#endif