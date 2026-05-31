"""
What USB-C? — desktop detection engine.

On Linux desktops/laptops the USB Type-C connector class
(/sys/class/typec/) is world-readable, so unlike on a phone we can read the
*actual cable eMarker* (SOP') when the port has a Type-C Port Manager and an
eMarked cable is attached.

This module is pure-Python (no GUI deps) so it can power both the GTK app and a
CLI. VDO bit layouts are taken from the Linux kernel header
include/linux/usb/pd_vdo.h (the authoritative source for the sysfs values).
"""

from __future__ import annotations

import glob
import os
from dataclasses import dataclass, field
from typing import Optional


# ---------------------------------------------------------------------------
# Capability enums (mirrors the Android model)
# ---------------------------------------------------------------------------

DATA_SPEEDS = {
    "unknown": ("Unknown", "?"),
    "usb2": ("USB 2.0", "480 Mbps"),
    "usb3_gen1": ("USB 3.2 Gen 1", "5 Gbps"),
    "usb3_gen2": ("USB 3.2 Gen 2", "10 Gbps"),
    "usb3_gen2x2": ("USB 3.2 Gen 2x2", "20 Gbps"),
    "usb4_gen3": ("USB4 Gen 3", "40 Gbps"),
    "usb4_v2": ("USB4 v2", "80 Gbps"),
    "tb3": ("Thunderbolt 3", "40 Gbps"),
    "tb4": ("Thunderbolt 4", "40 Gbps"),
    "tb5": ("Thunderbolt 5", "120 Gbps"),
}

CURRENT_RATINGS = {
    "unknown": ("Unknown", 0),
    "3a": ("3 A (60 W)", 60),
    "5a": ("5 A (100 W)", 100),
    "epr": ("EPR 5 A @ 48 V (240 W)", 240),
}


def _read(path: str) -> Optional[str]:
    try:
        with open(path, "r") as f:
            return f.read().strip()
    except (OSError, IOError):
        return None


def _hex(s: Optional[str]) -> Optional[int]:
    if not s:
        return None
    s = s.strip()
    try:
        return int(s, 16) if s.lower().startswith("0x") else int(s, 16)
    except ValueError:
        try:
            return int(s)
        except ValueError:
            return None


# ---------------------------------------------------------------------------
# eMarker VDO decoding (kernel pd_vdo.h layout)
# ---------------------------------------------------------------------------

VENDORS = {
    0x8087: "Intel (Thunderbolt)",
    0x05AC: "Apple",
    0x04B4: "Cypress / Infineon",
    0x2109: "VIA Labs",
    0x0BDA: "Realtek",
    0x18D1: "Google",
    0x04E8: "Samsung",
    0x0BB4: "HTC",
    0x17EF: "Lenovo",
}


def decode_id_header(vdo: Optional[int]):
    """Return (vid:int|None, vendor_name|None, is_cable:bool)."""
    if vdo is None:
        return None, None, False
    vid = vdo & 0xFFFF
    ptype = (vdo >> 27) & 0x7  # bits[29:27]
    is_cable = ptype in (3, 4, 6)  # passive / active / VPD
    return vid, VENDORS.get(vid), is_cable


def decode_cable_vdo(vdo: Optional[int]):
    """Return (speed_key, current_key) per kernel passive/active cable VDO."""
    if vdo is None:
        return "unknown", "unknown"
    speed_bits = vdo & 0x7  # bits[2:0] USB highest speed
    speed = {
        0: "usb2",
        1: "usb3_gen1",
        2: "usb3_gen2",
        3: "usb4_gen3",
    }.get(speed_bits, "unknown")
    cur_bits = (vdo >> 5) & 0x3  # bits[6:5] VBUS current
    current = {1: "3a", 2: "5a"}.get(cur_bits, "unknown")
    return speed, current


# ---------------------------------------------------------------------------
# Assessment data structures
# ---------------------------------------------------------------------------


@dataclass
class PortInfo:
    path: str
    name: str = ""
    power_role: Optional[str] = None
    data_role: Optional[str] = None
    power_op_mode: Optional[str] = None
    orientation: Optional[str] = None
    pd_revision: Optional[str] = None
    # partner (the connected device)
    partner_type: Optional[str] = None
    partner_pd: Optional[str] = None
    partner_usb_mode: Optional[str] = None
    # cable (the eMarker — the prize)
    cable_present: bool = False
    cable_type: Optional[str] = None  # active / passive
    cable_plug_type: Optional[str] = None
    cable_vid: Optional[int] = None
    cable_vendor: Optional[str] = None
    cable_speed: str = "unknown"
    cable_current: str = "unknown"
    raw: dict = field(default_factory=dict)


@dataclass
class PdoInfo:
    """A parsed source-capability Power Data Object."""

    text: str


@dataclass
class Assessment:
    has_typec: bool = False
    ports: list = field(default_factory=list)  # list[PortInfo]
    usb_devices: list = field(default_factory=list)  # enumerated USB devices
    notes: list = field(default_factory=list)


# ---------------------------------------------------------------------------
# Readers
# ---------------------------------------------------------------------------


def read_port(port_path: str) -> PortInfo:
    name = os.path.basename(port_path)
    p = PortInfo(path=port_path, name=name)
    p.power_role = _strip_brackets(_read(f"{port_path}/power_role"))
    p.data_role = _strip_brackets(_read(f"{port_path}/data_role"))
    p.power_op_mode = _read(f"{port_path}/power_operation_mode")
    p.orientation = _read(f"{port_path}/orientation")
    p.pd_revision = _read(f"{port_path}/usb_power_delivery_revision")

    # Partner (connected device)
    partner = f"{port_path}-partner"
    if os.path.isdir(partner):
        p.partner_type = _read(f"{partner}/type")
        p.partner_pd = _read(f"{partner}/usb_power_delivery_revision")
        p.partner_usb_mode = _read(f"{partner}/usb_mode")

    # Cable (eMarker, SOP')
    cable = f"{port_path}-cable"
    if os.path.isdir(cable):
        p.cable_present = True
        p.cable_type = _read(f"{cable}/type")
        p.cable_plug_type = _read(f"{cable}/plug_type")
        idh = _hex(_read(f"{cable}/identity/id_header"))
        vdo1 = _hex(_read(f"{cable}/identity/product_type_vdo1"))
        vid, vendor, _ = decode_id_header(idh)
        p.cable_vid = vid
        p.cable_vendor = vendor or (f"0x{vid:04X}" if vid else None)
        p.cable_speed, p.cable_current = decode_cable_vdo(vdo1)
        p.raw["id_header"] = _read(f"{cable}/identity/id_header")
        p.raw["product_type_vdo1"] = _read(f"{cable}/identity/product_type_vdo1")
    return p


def _strip_brackets(s):
    """Type-C role files look like 'source [sink]' — return the selected one."""
    if not s:
        return s
    if "[" in s:
        a = s.find("[")
        b = s.find("]")
        if a != -1 and b != -1:
            return s[a + 1 : b]
    return s


def list_usb_devices():
    """Enumerate connected USB devices with their negotiated speed (Mbps)."""
    out = []
    for dev in sorted(glob.glob("/sys/bus/usb/devices/*")):
        speed = _read(f"{dev}/speed")  # in Mbps
        if speed is None:
            continue
        product = _read(f"{dev}/product")
        manuf = _read(f"{dev}/manufacturer")
        if not product and not manuf:
            continue  # skip bare root hubs without names
        vid = _read(f"{dev}/idVendor")
        pid = _read(f"{dev}/idProduct")
        out.append(
            {
                "name": " ".join(x for x in (manuf, product) if x) or f"{vid}:{pid}",
                "speed_mbps": int(speed) if speed and speed.isdigit() else 0,
                "vid": vid,
                "pid": pid,
            }
        )
    return out


def assess() -> Assessment:
    a = Assessment()
    port_paths = sorted(glob.glob("/sys/class/typec/port[0-9]*"))
    port_paths = [p for p in port_paths if "-" not in os.path.basename(p)]
    a.has_typec = len(port_paths) > 0
    for pp in port_paths:
        a.ports.append(read_port(pp))
    a.usb_devices = list_usb_devices()

    if not a.has_typec:
        a.notes.append(
            "No USB Type-C Port Manager found in /sys/class/typec. This machine's "
            "USB-C ports don't expose PD/eMarker data to the OS (common on desktops "
            "and some laptops). Connected USB devices are still shown below, and the "
            "guided identification still works."
        )
    else:
        any_cable = any(p.cable_present for p in a.ports)
        if not any_cable:
            a.notes.append(
                "Type-C ports found, but no eMarked cable identity is exposed right "
                "now. Plug a cable that has an eMarker (5 A / USB4 / Thunderbolt / "
                "active) into a Type-C port to read its electronic capabilities."
            )
    return a


def speed_label(key, short=False):
    lbl, sh = DATA_SPEEDS.get(key, DATA_SPEEDS["unknown"])
    return sh if short else lbl


def current_label(key):
    return CURRENT_RATINGS.get(key, CURRENT_RATINGS["unknown"])[0]


if __name__ == "__main__":
    # quick self-test dump
    a = assess()
    print("has_typec:", a.has_typec)
    for p in a.ports:
        print(p)
    print("usb devices:", len(a.usb_devices))
    for n in a.notes:
        print("NOTE:", n)
