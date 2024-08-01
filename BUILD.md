# Build Android Device Manager

## Prerequisites
- Java SDK
  - min version 17; I'm using openjdk 22.0.1 2024-04-16
  - MacOSX -> Homebrew -> `brew install openjdk`
  - Linux - [link](https://www.digitalocean.com/community/tutorials/how-to-install-java-with-apt-on-ubuntu-22-04)
- Maven
  - MacOSX -> Homebrew -> `brew install maven`
  - Linux - [link](https://www.digitalocean.com/community/tutorials/install-maven-linux-ubuntu)
 
## Build
- sync this repo
  - `git clone https://github.com/jpage4500/AndroidDeviceManager.git`
- build
  - `mvn compile`
- run:
  - `mvn exec:java`

