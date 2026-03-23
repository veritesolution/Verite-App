/*
 * outputs.h — LED and motor control
 */

#ifndef OUTPUTS_H
#define OUTPUTS_H

#include "config.h"

void initOutputs() {
  pinMode(PIN_MOTOR, OUTPUT);
  pinMode(PIN_LED_R, OUTPUT);
  pinMode(PIN_LED_G, OUTPUT);
  pinMode(PIN_LED_B, OUTPUT);
}

void resetOutputs() {
  digitalWrite(PIN_MOTOR, LOW);
  digitalWrite(PIN_LED_R, LOW);
  digitalWrite(PIN_LED_G, LOW);
  digitalWrite(PIN_LED_B, LOW);
}

void bootBlink() {
  const int pins[] = { PIN_LED_R, PIN_LED_G, PIN_LED_B };
  for (int i = 0; i < 3; i++) {
    digitalWrite(pins[i], HIGH); delay(150);
    digitalWrite(pins[i], LOW);  delay(80);
  }
}

#endif