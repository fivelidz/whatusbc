# USB-C Cable Identification — Research & Design Notes

This document records the research that shaped **What USB-C?** and, crucially,
the *hard limits* of what any Android app can know about a plugged-in cable.
Read this before changing the detection engine.

## 1. The "What USBC" app and the honest truth

- **"WhatUSBC"** is an **iOS-only** app. It is a **database lookup tool**, not an
  electronic interrogator. The user picks their cable model; it shows the spec
  sheet. No iPhone can read a cable's eMarker.
- **No consumer phone** (iOS or Android) can electronically read a *passive*
  cable, because passive cables contain **no electronics at all**.
- Android apps like **Ampere** only read live charging current/voltage via the
  public `BatteryManager` API. They measure the *system*, not the cable.

## 2. What actually distinguishes USB-C cables

| Class | Data | Charging | eMarker? |
|-------|------|----------|----------|
| USB 2.0 (most charge cables) | 480 Mbps | ≤3 A / 60 W | No |
| USB 3.2 Gen 1 | 5 Gbps | ≤3 A / 60 W | No (optional) |
| USB 3.2 Gen 2 | 10 Gbps | ≤3 A / 60 W | No (optional) |
| USB 3.2 Gen 2x2 | 20 Gbps | 3–5 A | If 5 A |
| USB4 Gen 3 | 40 Gbps | 5 A / 100 W (or 240 W EPR) | **Yes (mandatory)** |
| USB4 v2 | 80 Gbps | up to 240 W EPR | **Yes** |
| Thunderbolt 3/4 | 40 Gbps | 100 W | **Yes (Intel VID 0x8087)** |
| Thunderbolt 5 | 120 Gbps | 240 W | **Yes (active)** |

**Rule:** any cable rated above **3 A must contain an eMarker** (USB Type-C 2.2 /
PD 3.1). 5 A, USB4, Thunderbolt and all active cables are eMarked.

## 3. eMarker (SOP') — the ground truth, rarely reachable

The eMarker is a tiny IC in the plug, powered by VCONN, that answers a USB-PD
`Discover_Identity` request over the CC line. Its **Cable VDO1** encodes:

- bits [8:7] **USB Highest Speed**: 00=USB2, 01=USB3.2 G1, 10=USB3.2 G2, 11=USB4 G3
- bits [~12:11] **VBUS current**: 3 A vs 5 A
- **Cable Type**: passive / active re-driver / active re-timer
- **VID/PID** of the cable maker (0x8087 = Intel/Thunderbolt)

This is decoded in `detect/EmarkerParser.kt`.

## 4. What Android exposes (the layered reality)

| Tier | Mechanism | What you get | Permission |
|------|-----------|--------------|------------|
| 1 | `BatteryManager` + `ACTION_BATTERY_CHANGED` | live current, battery V, plug type, charger max V/I | **none** |
| 2 | sysfs via **Shizuku** (ADB shell domain) | `usb_type` (PD/PPS/DCP), VBUS V, PD active, power op mode, `port*-cable/identity` eMarker VDOs *if the kernel populated them* | Shizuku |
| 3 | sysfs via **root** | everything Tier 2 has, on OEMs that deny the shell domain | root |
| — | impossible | rated capability of a passive cable; eMarker if the kernel never ran SOP' discovery | — |

### SELinux reality
- Normal apps **cannot** read `/sys/class/typec/` or `/sys/class/power_supply/usb/*`.
- The ADB `shell` domain (= **Shizuku**) usually can — but **Xiaomi HyperOS denies
  even the shell domain** many nodes (verified on the Redmi Note 14 5G: `real_type`,
  `current_max`, `typec_mode`, all battery nodes → `Permission denied`). On such
  phones only **root** unlocks Tier 2/3, so the app must degrade to Tier 1.

## 5. Design consequences (why the app looks like it does)

1. **Three-tier engine** (`detect/`), degrading gracefully. Tier 1 always works.
2. **Confidence chips on every fact** — `from eMarker` / `from system` /
   `measured live` / `inferred` / `from database`. The app never lies about
   certainty.
3. **"Not readable" is a first-class result.** If we can't read data speed, we say
   so and offer the **cable database picker** (`data/CableDatabase.kt`) — the same
   strategy WhatUSBC uses, as an honest fallback.
4. **eMarker VDO parsing** is ready (`EmarkerParser`) for the phones/cables where
   `/sys/class/typec/portN-cable/identity` is populated.

## 6. eMarker VDO bit layout (verified against kernel)

Decoding in `EmarkerParser.kt` follows the Linux kernel header
`include/linux/usb/pd_vdo.h` (the authoritative source for the values exposed in
`/sys/class/typec/portN-cable/identity`):

- **ID Header VDO**: VID = bits[15:0]; Product Type (SOP') = bits[29:27]
  (3 = Passive Cable, 4 = Active Cable, 6 = VPD).
- **Passive Cable VDO1** (PD Rev3.0+): USB Highest Speed = **bits[2:0]**
  (0=USB2, 1=USB3.2 Gen1, 2=USB3.2/USB4 Gen2, 3=USB4 Gen3); VBUS current =
  **bits[6:5]** (1=3A, 2=5A); Connector type = bits[19:18].

Earlier drafts used the wrong bit offsets — corrected after cross-checking the
kernel macros `VDO_TYPEC_CABLE_SPEED`, `CABLE_CURR_*`, `PD_IDH_PTYPE`.

## 7. Robustness notes (connect/disconnect, subprocess safety)

- The ViewModel watches the **connected edge** (`plugged != 0 || charging`) and
  fires one-shot CONNECTED/DISCONNECTED events; deep sysfs is only read once on a
  fresh connect, not on every battery tick. On disconnect the cable snapshot is
  cleared so stale eMarker data never lingers.
- All shell execution (`ShellAccess`) is bounded by an 8 s timeout, drains stderr
  on a side thread (avoiding pipe-buffer deadlock), and the backend is probed once
  and cached (no `su` fork per read).

## 8. Reaching the PD chip — what we found on the Redmi Note 14 5G

The PD controller is real and present: I²C device `mt6375@34:tcpc`, driver
`mt6375-tcpc`, exposing a MediaTek `tcpc` class at:

```
/sys/class/tcpc/type_c_port0/{info, caps_info, pe_ready, vbus_level, rp_lvl, pd_test}
```

`caps_info` holds the negotiated source-capability PDOs and `info` the PD policy
engine state — exactly the chip-level data we want. **However**, these nodes are
SELinux-labelled to the charger domain and return `Permission denied` even to
`stat` from the `shell` domain (`u:r:shell:s0`) — so **Shizuku cannot read them**.
`getenforce` = **Enforcing**. Only **root** can reach them, and only if the kernel
SELinux policy allows root (or is set permissive).

The app now probes this MTK path (`SysfsReader.findMtkPort`) and surfaces
`caps_info`/`pe_ready` in DETAILS when a rooted MTK device grants access. On a
non-rooted HyperOS phone it's blocked; the app degrades to Tier-1.

### Why is it locked? (security, mostly)
- **Charging safety**: the PD state machine controls VBUS voltage (up to 48 V) and
  current. Userspace writing to `pd_test`/`rp_lvl` could mis-negotiate and damage
  hardware or a connected device. Read+write nodes are therefore gated.
- **SELinux least-privilege**: AOSP labels power/charging sysfs as
  `sysfs_batteryinfo`-style types granted only to system/charger domains. Apps
  (`untrusted_app`) and even ADB (`shell`) are denied by default.
- **No stable API contract**: vendor PD nodes are non-standard and change between
  kernels, so Google never exposed a public API — partly to avoid apps depending
  on unstable internals.

It is not a deliberate "block this feature" decision so much as the combination of
charging-safety gating + SELinux least-privilege + absence of a public API. A
rooted phone with permissive policy can read it; a Chromebook/Linux laptop exposes
it freely via `/sys/class/typec/`.

## 9. The "live data test" — proving data capability by USE (no chip needed)

Rather than *asking* the cable what it is (blocked), we observe the phone
*using* it and report what the link achieved. Confirmed working on the Redmi
Note 14 5G:

- **Port ceiling** from `getprop sys.usb.controller` → `musb-hdrc` = a USB-2.0-only
  device controller. So this phone's port maxes at 480 Mbps regardless of cable.
  (A SuperSpeed phone would show `dwc3`/`xhci`/`ssusb`.) `SystemProperties.get()`
  is app-readable for these non-protected props — **no root, no Shizuku**.
- **Live link** from `sys.usb.state` → `mtp,adb`: an active data function while
  attached to a host = the cable is carrying data right now (functional proof).
- A USB-2.0 cable physically cannot train a SuperSpeed link, so observing a
  SuperSpeed link *proves* a SuperSpeed cable.

Implemented in `detect/DataLinkReader.kt` + `model/DataLink.kt`. The DATA axis
flips from "Unknown" to e.g. "480 Mbps · measured live" once a link is observed.

**Requirement:** the cable must be plugged into a computer/host for the link to
exist — you can't test a cable lying loose. But this needs no charger and no
permissions, and gives a real, honest, measured answer.

The raw `current_speed` UDC node is permission-denied to apps/shell, so we use
the controller name (ceiling) + active-function state (live link) instead — both
app-readable.

## 10. References
- USB Type-C Specification 2.2
- USB Power Delivery Specification 3.1 (§6.4.5 VDOs)
- USB4 Specification v2.0
- Linux kernel `Documentation/driver-api/usb/typec.rst`
