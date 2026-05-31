"""
What USB-C? — GTK3 desktop GUI.

Shows live Type-C / eMarker data when available, connected USB devices with
their negotiated link speeds, and a guided identifier — mirroring the Android
app's capability-first design.
"""

from __future__ import annotations

import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, GLib, Gdk  # noqa: E402

from . import engine  # noqa: E402


CSS = b"""
window { background-color: #0e1116; }
.title { font-size: 22px; font-weight: bold; color: #f2f5fa; }
.subtitle { color: #9aa4b2; font-size: 13px; }
.card { background-color: #171c24; border-radius: 14px; padding: 16px; }
.cardlabel { color: #9aa4b2; font-size: 11px; font-weight: bold; }
.big { color: #f2f5fa; font-size: 20px; font-weight: bold; }
.k { color: #9aa4b2; font-size: 13px; }
.v { color: #f2f5fa; font-size: 13px; font-weight: bold; }
.green { color: #34d399; }
.amber { color: #ffc542; }
.purple { color: #a78bfa; }
.blue { color: #60a5fa; }
.note { color: #9aa4b2; font-size: 12px; }
.pill { background-color: #1e2530; border-radius: 8px; padding: 4px 10px; color: #a78bfa; }
.emarker { background-color: #14241b; border-radius: 10px; padding: 12px; }
"""


def _card(title, accent="purple"):
    box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
    box.get_style_context().add_class("card")
    lbl = Gtk.Label(label=title, xalign=0)
    lbl.get_style_context().add_class("cardlabel")
    lbl.get_style_context().add_class(accent)
    box.pack_start(lbl, False, False, 0)
    return box


def _kv(parent, k, v, vclass="v"):
    row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
    kl = Gtk.Label(label=k, xalign=0)
    kl.get_style_context().add_class("k")
    kl.set_hexpand(True)
    vl = Gtk.Label(label=str(v), xalign=1)
    vl.get_style_context().add_class(vclass)
    vl.set_line_wrap(True)
    row.pack_start(kl, True, True, 0)
    row.pack_start(vl, False, False, 0)
    parent.pack_start(row, False, False, 0)


class WhatUSBCWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="What USB-C?")
        self.set_default_size(460, 720)
        self.set_border_width(0)

        provider = Gtk.CssProvider()
        provider.load_from_data(CSS)
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(),
            provider,
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION,
        )

        scroll = Gtk.ScrolledWindow()
        scroll.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        self.add(scroll)

        self.root = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        self.root.set_margin_top(16)
        self.root.set_margin_bottom(16)
        self.root.set_margin_start(16)
        self.root.set_margin_end(16)
        scroll.add(self.root)

        self.build()
        # auto-refresh every 2 seconds to catch plug/unplug
        GLib.timeout_add_seconds(2, self._tick)

    def _tick(self):
        self.refresh()
        return True

    def build(self):
        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        title = Gtk.Label(label="What USB-C?", xalign=0)
        title.get_style_context().add_class("title")
        title.set_hexpand(True)
        btn = Gtk.Button(label="↻")
        btn.connect("clicked", lambda *_: self.refresh())
        header.pack_start(title, True, True, 0)
        header.pack_start(btn, False, False, 0)
        self.root.pack_start(header, False, False, 0)

        self.subtitle = Gtk.Label(xalign=0)
        self.subtitle.get_style_context().add_class("subtitle")
        self.root.pack_start(self.subtitle, False, False, 0)

        self.body = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        self.root.pack_start(self.body, False, False, 0)

        self.refresh()

    def refresh(self):
        for c in self.body.get_children():
            self.body.remove(c)

        a = engine.assess()

        if a.has_typec:
            connected = any(p.cable_present or p.partner_type for p in a.ports)
            self.subtitle.set_text(
                "USB-C port detected"
                + (" · cable connected" if connected else " · nothing connected")
            )
            for p in a.ports:
                self.body.pack_start(self._port_card(p), False, False, 0)
        else:
            self.subtitle.set_text("No Type-C PD controller on this machine")

        # USB devices card (the data-side truth — works everywhere)
        if a.usb_devices:
            self.body.pack_start(self._devices_card(a.usb_devices), False, False, 0)

        # notes
        if a.notes:
            note_card = _card("GOOD TO KNOW", "amber")
            for n in a.notes:
                nl = Gtk.Label(label="• " + n, xalign=0)
                nl.get_style_context().add_class("note")
                nl.set_line_wrap(True)
                note_card.pack_start(nl, False, False, 0)
            self.body.pack_start(note_card, False, False, 0)

        self.body.show_all()

    def _port_card(self, p):
        card = _card(f"TYPE-C PORT · {p.name.upper()}", "blue")
        _kv(card, "Power role", p.power_role or "—")
        _kv(card, "Data role", p.data_role or "—")
        _kv(card, "PD mode", p.power_op_mode or "—")
        _kv(card, "Orientation", p.orientation or "—")
        if p.partner_type:
            _kv(card, "Connected device", p.partner_type)
            if p.partner_usb_mode:
                _kv(card, "Device USB mode", p.partner_usb_mode)

        if p.cable_present:
            em = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
            em.get_style_context().add_class("emarker")
            hdr = Gtk.Label(label="CABLE eMARKER — read from the chip", xalign=0)
            hdr.get_style_context().add_class("cardlabel")
            hdr.get_style_context().add_class("green")
            em.pack_start(hdr, False, False, 0)
            _kv(em, "Vendor", p.cable_vendor or "—")
            _kv(em, "Construction", (p.cable_type or "—").title())
            _kv(em, "Plug type", p.cable_plug_type or "—")
            _kv(
                em,
                "Data speed",
                f"{engine.speed_label(p.cable_speed)} "
                f"({engine.speed_label(p.cable_speed, True)})",
                "green",
            )
            _kv(em, "Charging", engine.current_label(p.cable_current), "green")
            card.pack_start(em, False, False, 6)
        else:
            dim = Gtk.Label(label="No eMarked cable identity exposed", xalign=0)
            dim.get_style_context().add_class("note")
            card.pack_start(dim, False, False, 0)
        return card

    def _devices_card(self, devices):
        card = _card("CONNECTED USB DEVICES · LINK SPEED", "purple")
        for d in sorted(devices, key=lambda x: -x["speed_mbps"]):
            mbps = d["speed_mbps"]
            sp = f"{mbps // 1000} Gbps" if mbps >= 1000 else f"{mbps} Mbps"
            cls = "purple" if mbps >= 5000 else ("blue" if mbps >= 480 else "k")
            _kv(card, d["name"], sp, cls)
        return card


def main():
    win = WhatUSBCWindow()
    win.connect("destroy", Gtk.main_quit)
    win.show_all()
    Gtk.main()


if __name__ == "__main__":
    main()
