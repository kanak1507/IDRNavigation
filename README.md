# 🚗 IDRNavigation

### AI/ML-Based Intelligent Dead Reckoning for Seamless Navigation

**IDRNavigation** is an Android-based Intelligent Dead Reckoning (IDR) system designed to provide continuous vehicle navigation when GNSS/GPS signals become unavailable or unreliable.

The system uses **smartphone sensors, GNSS, machine learning, and sensor fusion** to estimate movement and maintain navigation during short GNSS outages.

---

## 🎯 Problem

GNSS/GPS-based navigation can become unreliable or completely unavailable in environments such as:

* 🚇 Tunnels
* 🅿️ Underground parking areas
* 🏙️ Dense urban environments
* 🌲 Dense forests
* ⛰️ Deep valleys
* 📡 Areas affected by signal interference

When GNSS becomes unavailable, conventional navigation systems can lose positioning information or experience significant drift.

---

## 💡 Our Solution

IDRNavigation turns a standard Android smartphone into an intelligent dead-reckoning navigation system using its built-in sensors.

### Normal GNSS Conditions

```text
GNSS + IMU
    ↓
Sensor Fusion
    ↓
Continuous Position
```

### During GNSS Outage

```text
IMU Sensors
    ↓
Feature Extraction
    ↓
ML Speed Prediction
    ↓
Dead Reckoning
    ↓
Position Estimation
```

### When GNSS Returns

```text
GNSS Position
      +
Dead-Reckoned Position
      ↓
   Fusion
      ↓
Drift Correction
      ↓
Corrected Position
```

---

## 🧠 Machine Learning

The application uses a trained **ONNX machine-learning model** to estimate movement speed from smartphone IMU data.

The model is packaged directly inside the Android application:

```text
app/
└── src/
    └── main/
        └── assets/
            ├── speed_predictor_raw_imu.onnx
            └── normalization_raw_imu.json
```

### Model Input

The prediction pipeline uses IMU-derived features such as:

* Accelerometer measurements
* Linear acceleration
* Gyroscope measurements
* Acceleration magnitude
* Smoothed sensor measurements
* Motion-related features

### Model Output

The ML model provides an estimated vehicle speed which is used by the dead-reckoning engine to estimate movement during GNSS outages.

---

## 📱 Android Application

The Android application collects real-time sensor data and processes it through the navigation pipeline.

### Main Components

| Component             | Responsibility                         |
| --------------------- | -------------------------------------- |
| `MainActivity.kt`     | Application UI and entry point         |
| `SensorCollector.kt`  | Collects smartphone sensor data        |
| `SensorData.kt`       | Represents sensor measurements         |
| `FeatureExtractor.kt` | Extracts ML features                   |
| `SpeedPredictor.kt`   | Runs the ONNX speed prediction model   |
| `IDREngine.kt`        | Core intelligent dead-reckoning engine |

---

## 🏗️ Project Structure

```text
IDRNavigation/
│
├── app/
│   ├── src/
│   │   ├── androidTest/
│   │   ├── main/
│   │   │   ├── assets/
│   │   │   │   ├── normalization_raw_imu.json
│   │   │   │   └── speed_predictor_raw_imu.onnx
│   │   │   │
│   │   │   ├── java/
│   │   │   │   └── com/example/idrnavigation/
│   │   │   │       ├── FeatureExtractor.kt
│   │   │   │       ├── IDREngine.kt
│   │   │   │       ├── MainActivity.kt
│   │   │   │       ├── SensorCollector.kt
│   │   │   │       ├── SensorData.kt
│   │   │   │       └── SpeedPredictor.kt
│   │   │   │
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── test/
│   │
│   └── build.gradle.kts
│
├── training/
│   └── [Training data and ML development files]
│
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

> The `training/` directory is intentionally excluded from Git because it contains a large number of training/data files.

---

## 🔄 Navigation Pipeline

```text
┌──────────────────┐
│ Smartphone IMU   │
│ Accelerometer    │
│ Gyroscope        │
│ Orientation      │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Sensor Collection│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Feature Extraction│
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ ML Speed         │
│ Prediction       │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Dead Reckoning   │
│ Position Update  │
└────────┬─────────┘
         │
         ▼
   GNSS Available?
      /       \
    YES        NO
     │          │
     ▼          ▼
┌─────────┐  ┌──────────┐
│ Fusion  │  │ Continue │
│ + Drift │  │ with DR  │
│Correction│ └──────────┘
└────┬────┘
     │
     ▼
Continuous Position
```

---

## 🛠️ Technology Stack

### Android

* **Kotlin**
* **Android SDK**
* **Gradle**
* Android Sensor APIs
* GNSS/GPS APIs

### Machine Learning

* Python
* NumPy
* Pandas
* Scikit-learn
* ONNX

### Development

* Android Studio
* VS Code
* Git
* GitHub

---

## 🚀 Getting Started

### Requirements

* Android Studio
* Android device with:

  * Accelerometer
  * Gyroscope
  * GNSS/GPS
* Android SDK
* JDK compatible with the project's Gradle configuration

### Clone the Repository

```bash
git clone https://github.com/kanak1507/IDRNavigation.git
```

### Open the Project

Open the cloned `IDRNavigation` folder in **Android Studio**.

Allow Gradle to synchronize and download the required dependencies.

### Build the Application

On Windows:

```powershell
.\gradlew assembleDebug
```

### Run

Connect an Android device with USB debugging enabled, or use an Android emulator with the required sensor support.

Then run the application from Android Studio.

---

## 📊 Testing GNSS Outages

The system can be evaluated by simulating periods where GNSS information is unavailable.

### Example

```text
GNSS Available
      ↓
Normal Navigation
      ↓
GNSS Outage
      ↓
IMU + ML Dead Reckoning
      ↓
GNSS Restored
      ↓
Fusion + Drift Correction
```

The main evaluation metrics include:

* Position error
* Speed estimation error
* Drift during GNSS outage
* Recovery after GNSS restoration
* Continuity of navigation

---

## 📈 Future Improvements

Planned improvements include:

* [ ] Improved IMU calibration
* [ ] Adaptive sensor fusion
* [ ] Better heading estimation
* [ ] Advanced GNSS/INS fusion
* [ ] Map matching
* [ ] Improved drift correction
* [ ] Longer GNSS-outage evaluation
* [ ] On-device ML optimization
* [ ] Real-world vehicle testing
* [ ] Battery and performance optimization

---

## 🔐 Data & Privacy

The application is designed to perform navigation processing using data collected from the device sensors.

Training datasets are **not included in the Git repository** due to their size.

Only the required trained model and normalization configuration are packaged with the Android application.

---

## 🎓 Project Context

This project is being developed as a prototype for the **Smart India Hackathon (SIH) 2026** problem statement:

> **AI/ML based Intelligent Dead Reckoning system for seamless navigation**

The objective is to explore how smartphones can provide more resilient positioning in environments where GNSS/GPS is temporarily unavailable.

---

## 👥 Contributors

**IDRNavigation Team**

Developed as a collaborative AI/ML + Android navigation project.

---

## 📄 License

This project is currently intended for **educational, research, and prototype development purposes**.

A formal open-source license may be added in a future release.

---

⭐ **If you find this project interesting, consider giving the repository a star!**
