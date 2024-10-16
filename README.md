# AndroidDeviceManager

## Description ##
Java desktop app to manage multiple Android devices via adb

## Features ##
- View all connected (and wireless) devices
- Populates device **phone number, free space, IMEI, carrier** (if available)
- **Remote control** selected devices (requires [scrcpy](https://github.com/Genymobile/scrcpy))
- Capture **screenshots** of selected devices
- **Drag and drop an apk** to **install** on selected devices
- **Drag and drop a file** to **copy** to selected devices
- **File Explorer** / Browse filesystem of device
  - download and view folders/files
  - delete folders/files
  - root mode supported
- View **Device Logs**
  - NOTE: this is a work in progress!
- **Restart** selected devices
- Run **user-defined adb commands**
- Set and display custom properties on each device
- Start an **adb shell** session with selected devices
- **View version** of user-defined list of apps

## Screenshots ##
<img src="resources/screenshot-main.jpg" width="600" alt="devices">

<details>
  <summary>More Screenshots</summary>
<img src="resources/screenshot-mirror.jpg" width="600" alt="devices">
<br>
<img src="resources/screenshot-browse.jpg" width="300" alt="file explorer">
<br>
<img src="resources/screenshot-logs.jpg" width="600" alt="logs">
<br>
</details>

## Setup / Prerequisites

- **Java** - min 17 but latest version should be fine
- **adb** - android debugging tools
- **scrcpy** - used to mirror a device ([link](https://github.com/Genymobile/scrcpy))

<details>
  <summary>Mac Setup</summary>

### Install Homebrew

```
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Install latest JRE

```
brew install openjdk
```

- add java to PATH

```
echo 'export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"' >> ~/.profile
```

- create a symlink in /Library/Java/JavaVirtualMachines which is where the Mac launcher looks for java

```
sudo ln -sfn $HOMEBREW_PREFIX/opt/openjdk/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk.jdk
```

### Install adb

```
brew install --cask android-platform-tools
```

- add adb to PATH

```
echo 'export ANDROID_HOME=$HOME/Library/Android/sdk' >> ~/.profile
echo 'export PATH="/opt/homebrew/bin:$ANDROID_HOME/platform-tools:$PATH"' >> ~/.profile
```

### Install scrcpy

```
brew install scrcpy
```

</details>

<details>
  <summary>Windows Setup</summary>

### Install latest JRE
download and install the JDK from [Oracle](https://jdk.java.net/) (22+ recommended)
- test java is in PATH by running “java --version” in a command window

### Install adb
download from [here](https://developer.android.com/tools/releases/platform-tools) and extract archive
- move extracted platform-tools/ folder to your <HOME DIR>/Program Files/Android/
- add the location to your PATH
- test this by running “adb” in a command window

### Install scrcpy

download and install from [here](https://github.com/Genymobile/scrcpy/blob/master/doc/windows.md)
- make sure “scrcpy” in in PATH
</details>

<details>
  <summary>Linux Setup</summary>

### Install latest JRE
```
sudo apt-get install default-jdk
```

### Install adb
```
sudo apt-get install adb
```
### Install scrcpy
see this [link](https://github.com/Genymobile/scrcpy/blob/master/doc/linux.md#latest-version)

</details>
  
## Run

- Download the latest release from here: [https://github.com/jpage4500/AndroidDeviceManager/releases](https://github.com/jpage4500/AndroidDeviceManager/releases)
  - Mac OSX Users:
    - get the packaged .app version: `AndroidDeviceManager-VERSION-OSX.zip`, extract and move to `/Applications` folder
  - Windows/Linux Users:
    - get the .jar version: `AndroidDeviceManager.jar`
    - run via command-line: `java -jar AndroidDeviceManager.jar`

## Build

[Build Instructions](BUILD.md)

## Use Cases ##

We want to manage a lot of Android devices and had previously used MDM (mobile device management) software such as **AirDroid** and **ScaleFusion**. These tools aren't free ($$) but more importantly trying to remote control/view an Android device was often a very slow and choppy experience.

So, instead we took a different approach. Instead of running MDM software on every individual Android device, we connected all of the devices to a single macbook laptop using multiple 16-port USB hubs. The Macbook is running [Splashtop](https://www.splashtop.com/) remote control software. I can now remote login and using Android Device Manager control all of the devices with very little to no lag.

---

Tested with 45 Android devices connected to 1 Macbook laptop (using multiple 16-port USB hubs)
