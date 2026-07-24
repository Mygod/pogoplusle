# PoGo+LE

[![Releases](https://img.shields.io/github/downloads/Mygod/pogoplusle/total.svg)](https://github.com/Mygod/pogoplusle/releases)
[![Android 9-17](https://img.shields.io/badge/Android-9--17-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)

[![Get it on Obtainium](https://github.com/ImranR98/Obtainium/raw/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.page/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522be.mygod.pogoplusplus%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252FMygod%252Fpogoplusle%2522%252C%2522author%2522%253A%2522%2522%252C%2522name%2522%253A%2522%2522%252C%2522additionalSettings%2522%253A%2522%257B%257D%2522%257D)

Automagically skips pairing dialog when connecting Pokémon GO Plus, and alerts you when things go wrong.

Context: Pairing dialog were introduced in [November 2020 security patch](https://source.android.com/security/bulletin/2020-11-01), for [fixing](https://android.googlesource.com/platform/system/bt/+/b3f12befdc4def7d695b6f1049cd02238eb1e4a8) [CVE-2020-12856](https://github.com/alwentiu/COVIDSafe-CVE-2020-12856) as discovered by [/u/BlueMysticNA](https://www.reddit.com/r/TheSilphRoad/comments/jujfm4/comment/gcdk4eb/).

This project is tested with [BrowserStack](https://email.browserstack.com/c/eJwljEtuwyAQQE9T77D4DeAFZ4mAGRyU2DSAZfX2Re36fcgLY0EIBSAW9BAB7VK8UeiU1hk0x_iQKsjItykZQkqKWy1sdmsvSK_yYbnR56JzsIzsu-LVKfQh2BHKqdjeiE6GYDeILm1MdgvjS3Oa-L3OtD-R-mtN9ViePrgtoI2UuBMpCjBSa0hWO4pamqyXt7_ve42t3p1aHyH9l80fP3vFPi4sdd73v_skv_bLR70).
See a table of tested compatibility [here](https://github.com/Mygod/pogoplusle/wiki/Device-compatibility-table-for-Bluetooth-pairing-assistant).

## Android platform API inventory

This is a concise audit index of app-owned non-SDK access. API restrictions are updated up to
[SHA-256 checksum `9102af02fe6ab68b92464bdff5e5b09f3bd62c65d1130aaf85d3296f17d38074`](https://github.com/Mygod/hiddenapi/commit/2f90e9da30976febeb0630cba48c4da0116c323d).
API qualifiers describe when this app uses each member.

Hidden whitelisted APIs:

* `Landroid/bluetooth/BluetoothDevice;->removeBond()Z,sdk,system-api,test-api`

Private APIs:

* `Landroid/bluetooth/BluetoothGatt;->mClientIf:I,unsupported`
* (API 28-30) `Landroid/bluetooth/BluetoothAdapter;->getBluetoothService(Landroid/bluetooth/IBluetoothManagerCallback;)Landroid/bluetooth/IBluetooth;,greylist`
* (API 31+) `Landroid/bluetooth/BluetoothAdapter;->getBluetoothService()Landroid/bluetooth/IBluetooth;,blocked`
* `Landroid/bluetooth/IBluetooth$Stub;->asInterface(Landroid/os/IBinder;)Landroid/bluetooth/IBluetooth;,unsupported`
* (API 28-30) `Landroid/bluetooth/IBluetooth;->setPairingConfirmation(Landroid/bluetooth/BluetoothDevice;Z)Z,greylist-max-o`
* (API 31-32 and 35+) `Landroid/bluetooth/IBluetooth;->setPairingConfirmation(Landroid/bluetooth/BluetoothDevice;ZLandroid/content/AttributionSource;)Z,blocked`
* (API 33-34) `Landroid/bluetooth/IBluetooth;->setPairingConfirmation(Landroid/bluetooth/BluetoothDevice;ZLandroid/content/AttributionSource;Lcom/android/bluetooth/x/com/android/modules/utils/SynchronousResultReceiver;)V,blocked`
* (API 33-34) `Lcom/android/bluetooth/x/com/android/modules/utils/SynchronousResultReceiver;->get()Lcom/android/bluetooth/x/com/android/modules/utils/SynchronousResultReceiver;,blocked`
* (API 33-34) `Lcom/android/bluetooth/x/com/android/modules/utils/SynchronousResultReceiver;->awaitResultNoInterrupt(Ljava/time/Duration;)Lcom/android/bluetooth/x/com/android/modules/utils/SynchronousResultReceiver$Result;,blocked`
* (API 33-34) `Lcom/android/bluetooth/x/com/android/modules/utils/SynchronousResultReceiver$Result;->getValue(Ljava/lang/Object;)Ljava/lang/Object;,blocked`

Xposed private APIs:

* (API 28-36) `Lcom/android/bluetooth/gatt/GattService;->onServerRegistered(IIJJ)V`
* (API 28-36) `Lcom/android/bluetooth/gatt/GattService;->onClientConnected(Ljava/lang/String;ZII)V`
* (API 28-30) `Lcom/android/bluetooth/gatt/GattService;->unregisterServer(I)V`
* (API 31-36) `Lcom/android/bluetooth/gatt/GattService;->unregisterServer(ILandroid/content/AttributionSource;)V`
* (API 28-35) `Lcom/android/bluetooth/gatt/GattService;->mClientMap:Lcom/android/bluetooth/gatt/GattService$ClientMap;`
* (API 28-35) `Lcom/android/bluetooth/gatt/GattService;->mServerMap:Lcom/android/bluetooth/gatt/GattService$ServerMap;`
* (API 36) `Lcom/android/bluetooth/gatt/GattService;->mClientMap:Lcom/android/bluetooth/gatt/ContextMap;`
* (API 36) `Lcom/android/bluetooth/gatt/GattService;->mServerMap:Lcom/android/bluetooth/gatt/ContextMap;`
* (API 28-36) `Lcom/android/bluetooth/gatt/ContextMap;->getById(I)Lcom/android/bluetooth/gatt/ContextMap$App;`
* (API 28-36) `Lcom/android/bluetooth/gatt/ContextMap;->getConnectedDevices()Ljava/util/Set;`
* (API 28-36) `Lcom/android/bluetooth/gatt/ContextMap$App;->name:Ljava/lang/String;`
* (API 37) `Lcom/android/bluetooth/gatt/GattServerManager;->onServerRegisteredFromNative(IILjava/util/UUID;)V`
* (API 37) `Lcom/android/bluetooth/gatt/GattServerManager;->onClientConnectedFromNative(Landroid/bluetooth/BluetoothDevice;IZII)V`
* (API 37) `Lcom/android/bluetooth/gatt/GattServerManager;->unregisterServer(Landroid/bluetooth/IBluetoothGattServerCallback;)V`
* (API 37) `Lcom/android/bluetooth/gatt/GattServerManager;->gatt:Lcom/android/bluetooth/gatt/GattService;`
* (API 37) `Lcom/android/bluetooth/gatt/GattServerManager;->getServerMap()Lcom/android/bluetooth/gatt/ContextMap;`
* (API 37) `Lcom/android/bluetooth/gatt/GattService;->getClientMap()Lcom/android/bluetooth/gatt/ContextMap;`
* (API 37) `Lcom/android/bluetooth/gatt/ContextMap;->getById(I)Lcom/android/bluetooth/gatt/ContextApp;`
* (API 37) `Lcom/android/bluetooth/gatt/ContextMap;->getByCallbackId(Landroid/os/IInterface;)Lcom/android/bluetooth/gatt/ContextApp;`
* (API 37) `Lcom/android/bluetooth/gatt/ContextMap;->getConnectedDevices()Ljava/util/Set;`
* (API 37) `Lcom/android/bluetooth/gatt/ContextApp;->getName()Ljava/lang/String;`
* (API 37) `Lcom/android/bluetooth/gatt/ContextApp;->getId()I`

## FAQ

Q: How to use it?  
A: Install it, launch it, turn on the first switch (optionally turn on the remaining switches and click other buttons) and enjoy! You probably won't need to launch this app ever again.

Q: What do the other two switches do?  
A: Those would allow PoGo+LE to post a notification when your device is disconnected, bag is full, storage is full, etc. You can manage these notifications in app settings.

Q: Is this app safe to use?  
A: Yes. This app uses `AccessibilityService` API to help you skip the pairing dialog. Since this app only interacts with Android system interface and system Settings but not the game, PoGo+LE is fully compliant with Niantic's terms of service and is safe to use.

Q: Not working?  
A: Feel free to send the following information to me via GitHub issues or else: app version and configuration, device model, Android version, and a screenshot demonstrating the issue. You can also try the Shizuku pairing mode if Shizuku is running on your device.

Q: What does the Xposed Bluetooth workaround do, and how do I enable it?

A: This workaround allows Pokémon GO to connect to a Nintendo Switch while another Bluetooth LE device is already
connected by filtering the initial GATT callbacks for pre-existing connections. Enable PoGo+LE in LSPosed and select
the system Bluetooth package. LSPosed recommends
`com.android.bluetooth` for AOSP and OEM-derived stacks and `com.google.android.bluetooth` for Google's Pixel
stack. You may select a different package for a vendor ROM: the module has no package allowlist and activates only
when that package contains one of the exact supported AOSP GATT implementations.

Q: Why should I use the Shizuku pairing mode?

A: It confirms pairing without the system pop-up. It requires a running Shizuku service and permission for PoGo+LE; if Shizuku is unavailable, Android's normal pairing dialog remains available.

Q: Why should I associate my Bluetooth accessory?

A: On Android 16 and later, a one-time system-approved companion-device association lets PoGo+LE use Android's
public API to unpair that accessory from the game notification without system privileges. PoGo+LE uses the
association only for unpairing; the pairing assistant and Shizuku pairing mode remain separate features. Repeat the
association step for each bonded accessory.
