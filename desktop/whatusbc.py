#!/usr/bin/env python3
"""
What USB-C? desktop launcher.

Usage:
    ./whatusbc.py          # launch the GTK GUI
    ./whatusbc.py --cli    # text output in the terminal
"""

import sys

if "--cli" in sys.argv or "-c" in sys.argv:
    from whatusbc.cli import main
else:
    from whatusbc.gui import main

if __name__ == "__main__":
    main()
