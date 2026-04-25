# Komponenten-Inventar (Activities, Services, Receiver, Provider)

Aus `decompiled/apktool/AndroidManifest.xml`. Alle Klassen sind nur **deklariert** – die tatsächlichen Implementierungen liegen verschlüsselt in `assets/nedata.db` und werden vom NetEase-Wrapper zur Laufzeit dekodiert.

## Activities (197 Total)

### App-Entry-Points (Launcher / exported)

| Activity | Rolle |
|---|---|
| `cn.ninebot.ninebot.mainshell.SplashActivity` | Splash, exported, Theme `Theme.Ninebot6.Splash` |
| `cn.ninebot.ninebot.mainshell.MainActivity` | Hauptbildschirm |
| `cn.ninebot.ninebot.mainshell.MainOverseaActivity` | Hauptbildschirm (Overseas-Build) |
| `cn.ninebot.ninebot.mainshell.UserAgreementActivity` | First-run Agreement |
| `cn.ninebot.ninebot.mainshell.AdDisplayActivity` | Anzeigen-Display |
| `cn.ninebot.ninebot.mainshell.ShareDataReceiveActivity` | Share-Intent-Handler (exported) |
| `cn.ninebot.ninebot.mainshell.ActionViewReceiveActivity` | Deep-Link-Handler (`ninebot://`) |

### Account / Auth (20 Activities)

```
cn.ninebot.account.StartActivity
cn.ninebot.account.login.LoginActivity
cn.ninebot.account.login.quicklogin.MoreLoginWayActivity
cn.ninebot.account.login.verificationlogin.LoginVerificationInputActivity
cn.ninebot.account.bio.BioLoginActivity
cn.ninebot.account.bio.BioPromptActivity
cn.ninebot.account.bind.BindAccountActivity
cn.ninebot.account.bind.BindNewAccountActivity
cn.ninebot.account.bind.BindVerificationInputActivity
cn.ninebot.account.bind.verify.VerifyAccountActivity
cn.ninebot.account.bind.verify.VerifyAccountCodeInputActivity
cn.ninebot.account.password.{ChangePasswordActivity, FindPasswordActivity, SetNewPasswordActivity, SetPasswordActivity}
cn.ninebot.account.areacode.SelectAreaCodeActivity
cn.ninebot.account.accountmanager.AccountManagerActivity
cn.ninebot.account.accountmanager.UserRightsCenterActivity
cn.ninebot.account.selfauth.FaceDetectionActivity        ← Liveness/KYC
cn.ninebot.account.selfauth.SelfAuthInputActivity

# Overseas-Variante (com.business)
com.business.accountoversea.StartActivity
com.business.accountoversea.login.{LoginAccountActivity, LoginByRegisterActivity, LoginMainActivity}
com.business.accountoversea.register.{RegisterAccountActivity, RegisterByLoginActivity, RegisterMainActivity, RegisterReverseVerifyActivity}
com.business.accountoversea.bind.{BindAccountActivity, BindAccountPasswordActivity}
```

### Device-Management (`cn.ninebot.device.*`, 77 Activities)

#### Hauptansichten
- `cn.ninebot.device.MyDeviceListActivity` – Meine Geräte
- `cn.ninebot.device.detail.SubDetailActivity` – Geräte-Detailseite
- `cn.ninebot.device.info.DeviceInfoActivity`
- `cn.ninebot.device.DeviceUnbindActivity`
- `cn.ninebot.device.MotorSubDetailActivity` – Motorrad-Detail
- `cn.ninebot.device.scooter.ScooterSubDetailActivity` – **Scooter-Detail (relevant für ZT3 Pro)**

#### BLE-Pairing-Pfade (Air-Lock / Beacon)
Multiple BLE-Pairing-Implementierungen je nach Anwendungsfall:

| Pfad | Bedeutung |
|---|---|
| `cn.ninebot.device.motor.beacon.ble.ui.BleUnlockActivity` | BLE-AirLock (Auto-Unlock per Proximity) |
| `cn.ninebot.device.motor.beacon.ble.ui.BleUnlockPermissionActivity` | Permission-Handler |
| `cn.ninebot.device.sps.beacon.ble.ui.SpsBleUnlockActivity` | SPS (Self-Propelling-System) BLE-Unlock |
| `cn.ninebot.device.sps.beacon.ble.ui.SpsBleUnlockPermissionActivity` | dito |
| `cn.ninebot.device.motor.beacon.hfp.ui.HfpActivity/HfpPairActivity` | HFP (Hands-Free-Profile) Bluetooth |
| `cn.ninebot.device.motor.beacon.hid.ui.HidActivity/HidPairActivity` | HID-Pairing |
| `cn.ninebot.device.motor.beacon.hidbr.{HidBrActivity, HidBrPairActivity, HidBrPermissionActivity, HidBrRssiAdjustActivity, HidBrRssiSettingActivity, MediaBtPairActivity}` | HID-Bluetooth-Classic (`br` = Basic Rate) |
| `cn.ninebot.device.scooter.beacon.hidbr.*` | dasselbe für Scooter |
| `cn.ninebot.device.motor.beacon.HandleAirlockNotificationActivity` | AirLock-Notification-Handler |

#### Scan / Bind
```
cn.ninebot.device.scan.ScanDeviceListActivity
cn.ninebot.device.scan.ScanDeviceList2Activity   ← V2-UI
cn.ninebot.device.scan.DevicePasswordActivity
```

#### Navigation / Dash-Display
```
cn.ninebot.device.motor.navi.DashNaviActivity                – Hauptnavi (Dash-Anzeige)
cn.ninebot.device.motor.navi.DashNaviLocationActivity
cn.ninebot.device.motor.navi.DashNaviRouteActivity
cn.ninebot.device.motor.navi.MobileDashNaviActivity          – Mobile-only-Navi
cn.ninebot.device.motor.navi.MobileDashNaviLocationActivity
cn.ninebot.device.motor.navi.MobileDashNaviRouteActivity
cn.ninebot.device.motor.navi.MapPickLocationActivity
cn.ninebot.device.motor.navi.SetLocationActivity
cn.ninebot.device.motor.navi.LocationFavoriteListActivity
cn.ninebot.device.motor.navi.NaviSettingActivity
cn.ninebot.device.motor.navi.CruiseModeActivity
```

#### Track / Telemetrie
```
cn.ninebot.device.track.PlayTrackActivity                    – Track-Replay
com.ninebot.track.ui.*                                       – Track-Recording-UI
```

#### Setting / Calibration
```
cn.ninebot.device.motor.setting.HandlebarCalibrateActivity
cn.ninebot.device.motor.setting.HandlebarCalibrateWithKickStandActivity
cn.ninebot.device.motor.wifi.TftWifiConnectActivity          – Wi-Fi-Connect für Display-Firmware
cn.ninebot.device.motor.gear.EditGearNameActivity            – Gang-Namen
cn.ninebot.device.motor.gear.GearCustomActivity              – Gang-Anpassung
cn.ninebot.device.motor.SetPasswordActivity                  – Geräte-Passwort
```

#### Balance-Boards (Ninebot S/Plus)
```
cn.ninebot.device.balance.blackbox.BlackboxReadActivity      – „Black-Box"-Daten lesen (Crash-Logs)
cn.ninebot.device.balance.ninePlus.RemoteListActivity        – Remote-Zubehör
cn.ninebot.device.balance.ninePlus.RemoteSpeedIntroduceActivity
```

#### Dynamic / Sub-Pages (RN-Module)
```
cn.ninebot.device.dynamic.sub.{
  CommonDynamicPageActivity,
  DashboardActivity,
  DynamicDeviceInfoActivity,
  DynamicListActivity, DynamicList2Activity, DynamicList3Activity,
  ThirdPartsUnbindActivity
}
cn.ninebot.device.dynamic.shortcuts.modify.ModifyShortcutsActivity
```

#### App-Widget
- `cn.ninebot.device.appWidget.WidgetUnlockDialogActivity`
- `cn.ninebot.device.appWidget.widget.{Mini, Middle}Device{App,Trans}Widget` (4 Widget-Provider)

#### IoT
- `cn.ninebot.device.iot.wechat.WechatIotBindActivity`

### Community / Moment
```
cn.ninebot.moment.question.*  (4 Activities)
cn.ninebot.moment.manager.*  (3 Activities)
```

### Library-Module
```
cn.ninebot.library.album.ui.*    (10 Foto-Album-Activities)
cn.ninebot.library.googlepush.MyFirebaseMessagingService
cn.ninebot.library.here.*        (HERE-SDK-Komponenten)
cn.ninebot.library.map.google.*  (Google-Maps-Komponenten)
```

### CarLink (`net.easyconn.*`)
```
net.easyconn.carman.{music, media, speech, common}.MusicService/SpeechService/...
net.easyconn.carman.thirdapp.present.EcAppInfoActionService
net.easyconn.carman.TrueMirrorService           – Bildschirm-Mirroring
net.easyconn.carman.ecsocksserver.SocksService  – Lokaler SOCKS-Proxy
net.easyconn.carman.sdk.DataService             – exported, Custom-Permission `easyconn.sdk.permission.DATA_SERVICE`
net.easyconn.server.PackageService
net.easyconn.server.ReceiveScreenStatus
```

### USB-Carlink
- `cn.ninebot.device.sps.UsbCarLinkLauncherActivity` – exported, Theme `MyTransparent`, USB-Accessory-Filter

### Third-Party-Login-Activities
- Facebook: `CustomTabActivity`, `CustomTabMainActivity`, `FacebookActivity`
- Google: `SignInHubActivity`, `GoogleApiActivity`
- Alipay: 6 H5-Pay-Activities
- NetEase Quick-Login: `CmccLoginActivity`, `CtccLoginActivity`, `CuccLoginActivity` (China-Mobile, Telecom, Unicom)
- Huawei HMS Scankit: `ScanKitActivity`
- Huawei Wear-Engine: `ClientHubActivity`
- HERE SDK: `ConsentActivity`
- Google Places: `AutocompleteActivity`

### Sonstige
- `com.bytedance.applog.migrate.MigrateDetectorActivity` – ByteDance AppLog Migrate
- `com.lxj.xpopup.util.XPermission$PermissionActivity` – XPopup Permission-Handler
- `com.salesforce.android.chat.*` – Salesforce Chat (Customer-Service)

## Services (54 Total)

### Foreground-Services für BLE-Keepalive
| Service | foregroundServiceType |
|---|---|
| `cn.ninebot.device.motor.beacon.ble.keepalive.LocalForegroundService` | connectedDevice |
| `cn.ninebot.device.motor.beacon.ble.keepalive.RemoteForegroundService` | connectedDevice (Sub-Prozess `:beacon`) |
| `cn.ninebot.device.motor.beacon.ble.keepalive.KeepAliveJobService` | (JobService, exported) |
| `cn.ninebot.device.motor.beacon.ble.xiaomi.XiaoMiKeepAliveService` | connectedDevice (Xiaomi-spezifisch) |
| `cn.ninebot.device.motor.beacon.ble.huawei.HuaWeiKeepAliveService` | connectedDevice (Huawei-spezifisch) |

### Beacon-Scanning
- `org.altbeacon.beacon.service.BeaconService` (label="beacon")
- `org.altbeacon.beacon.service.ScanJob`
- `org.altbeacon.beacon.BeaconIntentProcessor`

### Telemetrie / Track
- `com.ninebot.track.TrackService` (foregroundServiceType=location)
- `com.ninebot.track.service.TrackUploadService` (BIND_JOB_SERVICE, exported)

### Navigation
- `cn.ninebot.device.motor.navi.keepAlive.DashNaviService` (location)
- `com.mapbox.navigation.core.trip.service.NavigationNotificationService`

### Capture / Recording
- `cn.ninebot.capture.CaptureService` (foregroundServiceType=location, exported)
- `com.hbisoft.hbrecorder.ScreenRecordService` (mediaProjection|microphone)

### Push
- `cn.ninebot.library.googlepush.MyFirebaseMessagingService`
- `com.google.firebase.messaging.FirebaseMessagingService`

### CarLink
- `net.easyconn.carman.TrueMirrorService` (mediaProjection)
- `net.easyconn.carman.{music,media}.MusicService` (jeweils exported)
- `net.easyconn.carman.speech.SpeechService`
- `net.easyconn.carman.ecsocksserver.SocksService` (Sub-Prozess `:socks`)
- `net.easyconn.carman.thirdapp.present.EcAppInfoActionService` (location, Sub-Prozess `:ec_app_remote`, exported)
- `net.easyconn.carman.common.ForegroundService` (mediaProjection)
- `net.easyconn.carman.sdk_communication.PXCKeepAliveService`
- `net.easyconn.carman.sdk.DataService` (Custom-Permission, Protection-Level `dangerous`)
- `net.easyconn.server.PackageService`

### Carbit-Map
- `com.carbit.map.sdk.db.MapTrackDataService` (exported)
- `com.carbit.map.server.MapDataService` (exported)

### Notification-Listener
- `cn.ninebot.device.motor.media.NBMediaListenerService` (BIND_NOTIFICATION_LISTENER_SERVICE)

### Salesforce Chat
- `com.salesforce.android.chat.core.internal.service.ChatService`
- `com.salesforce.android.service.common.liveagentlogging.internal.service.LiveAgentLoggingService`

### Misc
- `cn.ninebot.support.upgrade.UpgradeService` – App-Upgrade
- `com.liulishuo.filedownloader.services.FileDownloadService$SeparateProcessService` (Sub-Prozess `:filedownloader`)
- `com.liulishuo.filedownloader.services.FileDownloadService$SharedMainProcessService`
- `com.vivo.car.networking.sdk.nearby.NearbyService` (Custom Vivo-Permission)
- `com.coloros.ocs.carlink.inner.OplusCarReceiver` ist Receiver (s.u.)

### AndroidX/Google-Standard
- `androidx.work.impl.background.systemalarm.SystemAlarmService`
- `androidx.work.impl.background.systemjob.SystemJobService`
- `androidx.work.impl.foreground.SystemForegroundService`
- `androidx.room.MultiInstanceInvalidationService`
- `androidx.health.platform.client.impl.sdkservice.HealthDataSdkService` (exported)
- `com.google.firebase.components.ComponentDiscoveryService`
- `com.google.android.gms.measurement.AppMeasurement{Service,JobService}`
- `com.google.android.gms.auth.api.signin.RevocationBoundService`

## Receiver (30 Total)

| Receiver | exported | Zweck |
|---|---|---|
| `cn.ninebot.library.googlepush.PushBroadcastReceiver` | false | Push-Verarbeitung |
| `cn.ninebot.device.appWidget.widget.{Mini,Middle}Device{App,Trans}Widget` | true | App-Widget-Provider (4×) |
| `cn.ninebot.device.motor.beacon.AirlockNotificationBroadcastReceiver` | false | AirLock-Notifications |
| `cn.ninebot.react.modules.NotificationReceiver` | false | RN-Notification-Bridge |
| `com.ninebot.track.NetworkStateReceiver` | false | Track-Net-State |
| `com.coloros.ocs.carlink.inner.OplusCarReceiver` | true | Oppo Carlink (mit `com.oppo.permission.safe.BLUETOOTH`) |
| `net.easyconn.carman.music.RemoteControlReceiver` | true | Media-Remote-Control |
| `net.easyconn.server.ReceiveScreenStatus` | false | EasyConnect Screen-Status |
| `org.altbeacon.beacon.startup.StartupBroadcastReceiver` | true | iBeacon-Auto-Start |
| `com.facebook.CurrentAccessTokenExpirationBroadcastReceiver` | false | FB Token-Refresh |
| `com.facebook.AuthenticationTokenManager$CurrentAuthenticationTokenChangedBroadcastReceiver` | false | FB Auth-Token |
| `com.google.firebase.iid.FirebaseInstanceIdReceiver` | true (mit `com.google.android.c2dm.permission.SEND`) | FCM |
| `com.google.android.gms.measurement.AppMeasurementReceiver` | false | GA Measurement |
| `com.hbisoft.hbrecorder.NotificationReceiver` | false | Recorder-Stop |
| `androidx.work.impl.utils.ForceStopRunnable$BroadcastReceiver` | false | WorkManager |
| `androidx.work.impl.background.systemalarm.{Constraint,Reschedule}Proxy*` | false (5×) | WorkManager-Constraints |
| `androidx.work.impl.diagnostics.DiagnosticsReceiver` | true (mit `android.permission.DUMP`) | WorkManager Debug |

## Provider (14 Total)

| Authority | Klasse |
|---|---|
| `com.ninebot.segway.fileprovider` | `androidx.core.content.FileProvider` (← Konflikt: 2× registriert) |
| `com.ninebot.segway.fileprovider` | `com.reactnativecommunity.webview.RNCWebViewFileProvider` ⚠ |
| `com.ninebot.segway.network.track` | `cn.ninebot.ninebot.network.track.NetworkTrackContentProvider` (Network-Tracking) |
| `com.ninebot.segway.androidx-startup` | `androidx.startup.InitializationProvider` |
| `com.ninebot.segway.firebaseinitprovider` | `com.google.firebase.provider.FirebaseInitProvider` |
| `com.ninebot.segway.FacebookInitProvider` | `com.facebook.internal.FacebookInitProvider` |
| `com.ninebot.segway.vapp.init` | `cn.ninebot.nbvapp.provider.NbVAppServiceContentProvider` (multiprocess, **VirtualApp Container für CarLink**) |
| `com.google.android.libraries.places.api...` | (Google Places SDK) |

⚠ **Konflikt-Hinweis**: Zwei FileProvider mit gleicher Authority `com.ninebot.segway.fileprovider` ist normalerweise ein Fehler – ersterer würde im Manifest-Merger gewinnen. Möglicherweise ein Build-Bug.

## Custom Permissions

App registriert keine eigenen `<permission>`-Tags, aber CarLink-Komponenten erwarten:
- `easyconn.sdk.permission.DATA_SERVICE` (Protection-Level: dangerous)
- `com.vivo.car.networking.CAR_PERMISSION`
- `com.oppo.permission.safe.BLUETOOTH` (für OplusCarReceiver)
