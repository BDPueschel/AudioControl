"""Start the AudioControl server.

Usage:
    python run.py [--port 8080] [--mock]
"""
import argparse
import os
import uvicorn


def main():
    parser = argparse.ArgumentParser(description="AudioControl — miniDSP control panel")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--mock", action="store_true", help="Use mock device (no hardware needed)")
    args = parser.parse_args()

    if args.mock:
        os.environ["AUDIOCONTROL_MOCK"] = "1"

    uvicorn.run("backend.server:app", host="0.0.0.0", port=args.port, reload=False)


if __name__ == "__main__":
    main()
