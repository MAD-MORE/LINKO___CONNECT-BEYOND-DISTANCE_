# Native build scaffolding for wireguard-go on Android

This folder will contain scripts and instructions to cross-compile wireguard-go for Android (arm64 and armeabi-v7a) and produce a shared library (.so) that the Android app can load via JNI.

scripts/build-wireguard-android.sh (placeholder):
- Uses gomobile or a cross-compile toolchain to build wireguard-go and produce libwireguard_android.so
- Outputs artifacts to android/app/src/main/jniLibs/<abi>/libwireguard.so

Notes:
- Building wireguard-go for Android requires Go toolchain and Android NDK; CI must provide these.
- This scaffolding is provided as a next-step. I will implement the actual build scripts and CI steps in the following commits.
