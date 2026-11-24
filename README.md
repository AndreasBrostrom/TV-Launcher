# TV Launcher

A simple home screen and file manager for Android TV, made for a Philips TPM171E
(Android 8.0). Plain Android views and no UI libraries, because the TV has little
free memory.

| Folder | What it is | Installed as |
| --- | --- | --- |
| `launcher/` | Home screen | TV Launcher (`app.tvlauncher`) |
| `files/` | File manager, its own app so the launcher has no storage access | Files (`app.tvfiles`) |
| `shared/` | Colors, theme and dialogs both use | |

## Launcher

### Top menu

Home, Play Store, Apps, then sources, TV settings, Google account, date and time.

### Home

Rows of apps. The default rows are in `HomeLayout.kt`: Streaming, Media, TV, Music,
Network and Tools. Apps that are not installed are skipped and appear once they are.

Long-press an app to open it, move it (arrows, OK when done), edit its row (rename,
move, delete), remove it from Home, see its info, force-stop or uninstall it. Once
Home has been changed on the TV, that layout is kept and new defaults are not added.

Tools has three tiles of ours: **Files**, **Network** (the TV's network screen) and
**Settings** (the same as the cog in the top menu).

### Apps

All installed apps by name, with Play Store, F-Droid and Flicky (an F-Droid client)
first. Long-press one to add it to Home. Preloaded apps without a TV entry are left
out, and Philips' How to... is hidden. At the bottom:

- **All apps:** Android's app list.
- **Stop all apps:** stops the listed apps that are in the background and clears the
  badges. Android spares apps in active use, and apps may start again on their own.
- **Background:** choose an image from `Pictures/Background`, or remove it, see below.
- **Show recently used:** only there until Usage access is on, see Setup.
- **Shut down, Reboot and Suspend:** the first two ask first.
- **Old launcher:** **Open** it once, enabling it first if needed, or **Disable** it. See
  Going back to the old launcher.

### Background

Plain dark grey unless you choose an image. Put images in `Pictures/Background` on the
internal storage (`/sdcard/Pictures/Background`; capitals do not matter), then go to
**Apps > Background**. Files makes the folder the first time the picker opens, and
images in subfolders are not shown. The image fills the screen, cropped if needed, and
is darkened so the text stays readable.

Files shows the picker and must be installed. The launcher gets only the image you pick
and keeps its own copy, so deleting the original changes nothing.

### Badge

A green corner marks apps opened in the last two hours, or since Stop all apps.
Android does not let apps see what is actually running.

### Cold boot

Philips opens the last TV input after a full start, even an empty one. If the launcher
is the default, it opens itself on top, unless 90 seconds have passed since the start. In
a test it took 17 seconds.

### Waking from standby

Google's `com.google.android.tvrecommendations` opens the old launcher by name soon after
the TV wakes from more than about 30 seconds of standby, whichever launcher is the
default. If that happens within 15 seconds of waking, the launcher opens itself on top,
so the old one may show briefly; in a test, for under a second. Inputs and other apps are
left alone. This needs Usage access, see Setup, and that the launcher is the default.

## Files

- **Left:** Home (internal storage), Drives, SD cards and USB drives when readable, then
  Download, Movies, Music and Pictures.
- **Drives:** every drive with space left / total. Selecting one opens it, or says it
  cannot be opened. Internal storage opens its shared part, the only part apps may see.
- **Right:** the file tree. OK or right opens a folder, left closes it or goes back.
- **Images:** a preview shows beside the tree. OK opens it full screen; arrows go to the
  next or previous image among the open folders, Back closes.
- **Other files** open in whichever app handles them.
- **Long-press:** Open, New folder, Rename, Delete (asks first, cannot be undone).

Android may only allow reading, not changing, files on SD cards and USB drives.
Android 11 and later would need a different storage permission.

## Build

    ./gradlew testDebugUnitTest assembleDebug lintDebug

Needs JDK 17 or 21 and Android SDK 35, with `sdk.dir=...` in `local.properties`.

## Install

Turn on network debugging on the TV, then:

    adb connect <tv-ip>:5555
    adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
    adb install -r files/build/outputs/apk/debug/files-debug.apk

Installing does not change the TV's launcher. After an update the old launcher may
show once; press Home to return.

## Setup

1. **Home:** open TV Launcher, go to **Apps > Use as home launcher** at the bottom, and
   pick it when Android asks. Or over adb:

       adb shell run-as app.tvlauncher pm enable app.tvlauncher/app.tvlauncher.HomeAlias
       adb shell cmd package set-home-activity app.tvlauncher/app.tvlauncher.HomeAlias

2. **Badge and waking:** **Apps > Show recently used**, then Special app access, Usage
   access, and turn on TV Launcher.
3. **Power buttons and Force stop:** use one of them and choose **Always allow** when
   the TV asks about debugging.

### About the adb access

Android reserves shutdown, reboot, suspend, force stop, and enabling or disabling the old
launcher for system apps. The launcher therefore sends these, and only these, to the TV's
own adb on `127.0.0.1:5555` (`AdbActions.kt`, `OldLauncher.kt`, `LocalAdb.kt`). Everything
else uses normal Android permissions.

- The approval gives the launcher full adb access to the TV. Its key stays in the app's
  private storage.
- Restarts and updates keep it. Uninstalling the app, clearing its data, revoking
  debugging authorizations, turning off debugging or a factory reset lose it.
- Without it, Reboot opens Android's About page, which should have Restart, and Force
  stop opens the app's Info page. Shut down and Suspend show why they failed.
- After a full shutdown the remote may not wake the TV. If so, use its power button or
  unplug it briefly.

## Going back to the old launcher

The old launcher (Google TV, `com.google.android.tvlauncher`) is never removed. **Never
uninstall it.** **Apps > Old launcher > Disable** turns it off together with
`com.google.android.tvrecommendations`, which opens it on every wake and would fail
without it. This stops the old launcher from showing at wake. It is only offered while TV
Launcher is the default, and needs the adb access above. Google's recommendations go with
it, and maybe the notification panel.

**While it is disabled there is no other home screen.** Enable it with **Open** before
uninstalling TV Launcher or clearing its data.

### On the TV

1. **Apps > Old launcher > Open**, so that it is enabled. TV Launcher is still the default,
   and the old launcher stays enabled until you disable it again.
2. To go back for good: the settings cog, then Apps, TV Launcher, **Uninstall**. Removes
   your Home layout too.

### From a computer

Works even if the launcher is broken or the screen is stuck. USB/network
debugging must be on.

    adb connect <tv-ip>:5555

First enable the old launcher, if it was disabled:

    adb shell pm enable com.google.android.tvlauncher
    adb shell pm enable com.google.android.tvrecommendations

Switch back but keep the app:

    adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity

Or remove the app completely:

    adb uninstall app.tvlauncher

Check which launcher is active, then open it:

    adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

The first should print `com.google.android.tvlauncher/.MainActivity`.

### Automatic

If the launcher crashes three times within a minute, it enables the old launcher if needed
and gives up the home role. If enabling fails, it keeps the role, as there would be no
other home screen.

## Disabling apps on the TV

Android settings hides **Disable** for apps signed with the TV's system key, such
as Alexa. Over adb they can be turned off without deleting them. Do **one at a
time** and check the TV after each, including that Sources still opens.

| Action | Command |
| --- | --- |
| Disable | `adb shell pm disable-user --user 0 <package>` |
| Re-enable | `adb shell pm enable <package>` |
| List disabled | `adb shell pm list packages -d` |

Re-enable everything that is disabled:

    for p in $(adb shell pm list packages -d | tr -d '\r' | sed 's/package://'); do
        adb shell pm enable "$p" </dev/null
    done

### Disabled on this TV

Disabled on 23 Nov 2026. Disabled apps also disappear from the launcher and come
back when re-enabled. Alexa's process keeps running even while it is disabled. Run `adb connect <tv-ip>:5555` first, then the lines you want.

Disable:

```sh
adb shell pm disable-user --user 0 org.droidtv.amazonalexa        # Amazon Alexa
adb shell pm disable-user --user 0 org.droidtv.demome             # Demo Me
adb shell pm disable-user --user 0 org.droidtv.nettv.market       # Philips TV Collection
adb shell pm disable-user --user 0 org.droidtv.nettvrecommender   # Top Picks
adb shell pm disable-user --user 0 com.plexapp.android            # Plex
adb shell pm disable-user --user 0 fr.bouyguestelecom.tv.bboxapi  # Remote Helper
adb shell pm disable-user --user 0 safewatch.tv.vpn               # Safe Watch TV
adb shell pm disable-user --user 0 tv.inscape.tvclient.inscapeacr # Inscape
adb shell pm disable-user --user 0 org.droidtv.usagelogger        # Usage logger
adb shell pm disable-user --user 0 com.opera.sdk.example          # Internet Browser
adb shell pm disable-user --user 0 org.droidtv.twittershare       # Twitter
adb shell pm disable-user --user 0 org.droidtv.dropboxprovider    # Dropbox
adb shell pm disable-user --user 0 org.droidtv.alhuewizard        # Ambilight+Hue
adb shell pm disable-user --user 0 com.google.android.videos      # Google Play Movies
adb shell pm disable-user --user 0 com.google.android.music       # Google Play Music
adb shell pm disable-user --user 0 com.google.android.play.games  # Google Play Games
adb shell pm disable-user --user 0 com.google.android.tvtutorials # Google TV tutorials
adb shell pm disable-user --user 0 com.google.android.katniss     # Google search and voice
```

Re-enable:

```sh
adb shell pm enable org.droidtv.amazonalexa        # Amazon Alexa
adb shell pm enable org.droidtv.demome             # Demo Me
adb shell pm enable org.droidtv.nettv.market       # Philips TV Collection
adb shell pm enable org.droidtv.nettvrecommender   # Top Picks
adb shell pm enable com.plexapp.android            # Plex
adb shell pm enable fr.bouyguestelecom.tv.bboxapi  # Remote Helper
adb shell pm enable safewatch.tv.vpn               # Safe Watch TV
adb shell pm enable tv.inscape.tvclient.inscapeacr # Inscape
adb shell pm enable org.droidtv.usagelogger        # Usage logger
adb shell pm enable com.opera.sdk.example          # Internet Browser
adb shell pm enable org.droidtv.twittershare       # Twitter
adb shell pm enable org.droidtv.dropboxprovider    # Dropbox
adb shell pm enable org.droidtv.alhuewizard        # Ambilight+Hue
adb shell pm enable com.google.android.videos      # Google Play Movies
adb shell pm enable com.google.android.music       # Google Play Music
adb shell pm enable com.google.android.play.games  # Google Play Games
adb shell pm enable com.google.android.tvtutorials # Google TV tutorials
adb shell pm enable com.google.android.katniss     # Google search and voice
```

### What you lose

For the ones where it may matter:

| Package | What it is | What you lose |
| --- | --- | --- |
| `tv.inscape.tvclient.inscapeacr` | Inscape viewing-data service | Nothing you use. The separate `inscape_fp` process keeps running |
| `org.droidtv.usagelogger` | Philips usage logger | Nothing you use |
| `com.opera.sdk.example` | Opera-based browser | The TV's web browser |
| `org.droidtv.twittershare` | Twitter sharing | Sharing to Twitter |
| `org.droidtv.dropboxprovider` | Dropbox support | Dropbox in Philips' media browser |
| `org.droidtv.alhuewizard` | Ambilight+Hue | Hue lights following Ambilight |
| `com.google.android.videos` | Google Play Movies / TV | That app |
| `com.google.android.music` | Google Play Music | Nothing, the service has shut down |
| `com.google.android.play.games` | Google Play Games | Game sign-in and saves |
| `com.google.android.tvtutorials` | Google TV tutorials | The tutorials |
| `com.google.android.katniss` | Google search and voice | Voice search on the remote |

### Do not disable

| Package | Why |
| --- | --- |
| `com.google.android.tvlauncher` | The old launcher, your way back. Use **Apps > Old launcher** instead |
| `com.google.android.tvrecommendations` | Goes with the old launcher, see above |
| `org.droidtv.leanbackcustomizer` | Customizes the old launcher |
| `com.android.tv.settings`, `org.droidtv.settings` | Settings |
| `org.droidtv.eum` (How to...) | **Sources stops working without it** (tested). The launcher hides it instead |
| `org.droidtv.channels`, `org.droidtv.playtv`, `com.mediatek.tvinput` | Sources, HDMI and TV |
| `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending` | Google services and Play Store |
| `com.google.android.webview` | Used by many apps |
| `com.google.android.apps.mediashell` | Chromecast |
| `com.google.android.packageinstaller` | Installs and uninstalls apps |
| `com.android.inputmethod.latin` | The on-screen keyboard |
| `com.google.android.tv.remote.service` | Phone remote control |
| `org.droidtv.contentexplorer`, `org.droidtv.nettvbrowser` | Run as part of the system |
| Any other `org.droidtv.*`, `com.mediatek.*`, `mtktvapi.*` or `com.android.*` | Mostly TV hardware and system parts |

## TV compatibility (Philips TPM171E)

Read from the TV over adb:

| What | Result |
| --- | --- |
| Android | 8.0 (API 26), 32-bit ARM, about 1.9 GB RAM |
| Screen | 1920x1080 at 320 dpi (960x540 dp) |
| Home role | Only the Google launcher handles it; Philips' own resolver is disabled |
| Sources | `org.droidtv.intent.action.SOURCE_LIST` (exported) |
| Settings | Philips setup menu |
| All apps, app info | Android TV settings |
| Force stop | Reserved for system apps, so it goes through adb, or the Info page |

## Not yet tested on the TV

- Suspend, Reboot, Shut down and Force stop
- Restarting the TV, and waking it, while the old launcher is disabled
- Going back with adb while the launcher is the default, and after repeated crashes,
  with the old launcher enabled and disabled. `adb uninstall` worked before it was the
  default.
- Waking after hours in standby (under 4 minutes works)
- Files: rename, delete, new folder, image preview, SD cards and USB drives

## License

MIT, see `LICENSE`.
