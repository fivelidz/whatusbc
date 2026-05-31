"""What USB-C? — command-line interface."""

from __future__ import annotations

import sys

from . import engine


C = {
    "reset": "\033[0m",
    "bold": "\033[1m",
    "dim": "\033[2m",
    "green": "\033[32m",
    "yellow": "\033[33m",
    "blue": "\033[34m",
    "cyan": "\033[36m",
    "magenta": "\033[35m",
    "red": "\033[31m",
}


def c(text, color):
    if not sys.stdout.isatty():
        return text
    return f"{C.get(color, '')}{text}{C['reset']}"


def main(argv=None):
    a = engine.assess()
    print(c("\n  What USB-C?  ", "bold") + c("— desktop\n", "dim"))

    if a.has_typec:
        for p in a.ports:
            print(c(f"  ● Type-C port: {p.name}", "bold"))
            _kv("Power role", p.power_role)
            _kv("Data role", p.data_role)
            _kv("PD mode", p.power_op_mode)
            _kv("Orientation", p.orientation)
            _kv("PD revision", p.pd_revision)
            if p.partner_type:
                _kv("Connected device", p.partner_type)
                _kv("  device USB mode", p.partner_usb_mode)
            if p.cable_present:
                print(c("    ── Cable eMarker (read from chip!) ──", "green"))
                _kv("  Cable vendor", p.cable_vendor)
                _kv("  Construction", p.cable_type)
                _kv("  Plug type", p.cable_plug_type)
                _kv(
                    "  Data speed",
                    f"{engine.speed_label(p.cable_speed)} "
                    f"({engine.speed_label(p.cable_speed, short=True)})",
                )
                _kv("  Charging", engine.current_label(p.cable_current))
            else:
                print(c("    (no eMarked cable identity exposed)", "dim"))
            print()
    else:
        print(c("  No USB-C Type-C Port Manager on this machine.", "yellow"))
        print(
            c(
                "  (Desktop USB-C ports often don't expose PD/eMarker to the OS.)\n",
                "dim",
            )
        )

    # USB device enumeration (the data-side truth)
    if a.usb_devices:
        print(c("  Connected USB devices (negotiated link speed):", "bold"))
        for d in sorted(a.usb_devices, key=lambda x: -x["speed_mbps"]):
            sp = _speed_str(d["speed_mbps"])
            print(f"    {sp:>10}  {d['name']}")
        print()

    for n in a.notes:
        print(c("  ℹ ", "cyan") + c(n, "dim"))
    print()


def _kv(k, v):
    if v:
        print(f"    {k:<18} {c(v, 'cyan')}")


def _speed_str(mbps):
    if mbps >= 20000:
        return c("20 Gbps", "magenta")
    if mbps >= 10000:
        return c("10 Gbps", "magenta")
    if mbps >= 5000:
        return c("5 Gbps", "magenta")
    if mbps >= 480:
        return c("480 Mbps", "blue")
    if mbps >= 12:
        return c(f"{mbps} Mbps", "dim")
    return c(f"{mbps} Mbps", "dim")


if __name__ == "__main__":
    main()
