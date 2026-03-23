/*
 * config.h — Pin definitions, BLE UUIDs, shared types & globals
 */

#ifndef CONFIG_H
#define CONFIG_H

#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Analog Inputs
const int PIN_EEG1  = 4;
const int PIN_EEG2  = 5;
const int PIN_PULSE = 6;
const int PIN_EMG   = 7;

// I2C
const int PIN_SDA = 9;
const int PIN_SCL = 8;

// Outputs
const int PIN_MOTOR = 15;
const int PIN_LED_R = 10;
const int PIN_LED_G = 11;
const int PIN_LED_B = 12;

// MPU-6050
#define MPU_ADDR          0x68
#define MPU_REG_WHO_AM_I  0x75
#define MPU_REG_PWR_MGT1  0x6B
#define MPU_REG_SMPLRT    0x19
#define MPU_REG_CONFIG    0x1A
#define MPU_REG_GYRO_CFG  0x1B
#define MPU_REG_ACCEL_CFG 0x1C
#define MPU_REG_ACCEL_X   0x3B
#define MPU_REG_TEMP_H    0x41
#define MPU_REG_GYRO_X    0x43
#define MPU_ACCEL_FS_2G   0x00
#define MPU_GYRO_FS_250   0x00

// Filter / PWM
const float FILTER_ALPHA   = 0.8f;
const int   PWM_FREQ       = 5000;
const int   PWM_RESOLUTION = 8;

// BLE UUIDs
#define SERVICE_UUID             "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_TX   "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHARACTERISTIC_UUID_RX   "12345678-1234-5678-1234-56789abcdef0"
#define CHARACTERISTIC_UUID_PROC "abcd1234-ab12-cd34-ef56-abcdef012345"

// Raw Sensor Packet (24 bytes)
struct __attribute__((packed)) SensorPacket {
  uint16_t seq;
  uint16_t eeg1, eeg2, emg, pulse;
  int16_t  ax, ay, az;
  int16_t  gx, gy, gz;
  int16_t  temp;
};

// Processed Packet (32 bytes)
struct __attribute__((packed)) ProcessedPacket {
  uint8_t  e1_delta, e1_theta, e1_alpha, e1_beta, e1_gamma;
  uint8_t  e2_delta, e2_theta, e2_alpha, e2_beta, e2_gamma;
  uint8_t  focus_x10;
  uint8_t  relax_x10;
  uint8_t  drowsy_x10;
  uint16_t emg_rms;
  uint8_t  emg_mf;
  uint8_t  emg_zcr;
  uint8_t  emg_active;
  uint8_t  emg_fatigue;
  uint8_t  bpm;
  uint8_t  rmssd;
  uint8_t  sdnn;
  uint8_t  pnn50;
  uint8_t  hrv_stress;
  int16_t  pitch_x10;
  int16_t  roll_x10;
  int16_t  yaw_x10;
  uint8_t  accel_mag_x10;
  uint8_t  flags;
};

// Shared Globals
extern BLECharacteristic *pTxCharacteristic;
extern BLECharacteristic *pProcCharacteristic;
extern volatile bool       deviceConnected;
extern QueueHandle_t       sensorQueue;
extern QueueHandle_t       processQueue;

#endif