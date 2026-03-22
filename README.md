# Vérité App

Vérité is a multi-feature health and productivity Android application designed to work with BioWearable hardware.

## Features

- **MindSet Pro**: Voice-activated dashboard for tasks, habits, and sleep tracking.
- **Hardware Diagnostic**: A comprehensive dashboard for testing BioWearable sensors (EEG, EMG, Pulse) and hardware controls (LEDs, Motors).
- **Voice Assistant**: Integrated "Hey Vérité" wake-word detection for hands-free interaction.
- **AI Insights**: Personalized feedback based on sensor data and user habits.

## New Hardware Diagnostic Tool

The application now includes a dedicated Hardware Diagnostic activity (`BioWearableDiagnosticActivity`) which allows developers to:
- Scan and connect to BioWearable devices via BLE.
- View live sensor data for EEG, EMG, and Pulse.
- Monitor MPU6050 IMU values (Accelerometer, Gyroscope).
- Control on-board LEDs and Vibration motors.
- Run automated diagnostic sequences to verify hardware integrity.

## Recent Improvements

- **Refactored Codebase**: Cleaned up imports, extracted constants to companion objects, and improved class organization.
- **Enhanced BLE Stability**: Added detailed GATT transition logging and improved MTU/service discovery handling.
- **Resource Management**: Moved hardcoded strings and colors to regional resources for better maintainability.
- **Robustness**: Improved lifecycle management and added bounds checking for sensor data packet parsing.

## Getting Started

1. Clone the repository.
2. Open in Android Studio.
3. Build and run on an Android device with BLE support.
4. Grant necessary permissions (Bluetooth, Location, Audio) when prompted.
