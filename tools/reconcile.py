#!/usr/bin/env python3
"""Compare the hand-curated years (overrides/<sku>.json) with the automatic pipeline guess.
usage: python3 reconcile.py <sku> [--cache ../catalog/_cache.json]
Prints disagreements and sanity flags so a final human pass can adjudicate.
"""
import argparse, json
ap = argparse.ArgumentParser()
ap.add_argument("sku")
ap.add_argument("--cache", default="../catalog/_cache.json")
ap.add_argument("--db", default="gameset_database.json")
a = ap.parse_args()
cache = json.load(open(a.cache))
db = json.load(open(a.db))
ov = json.load(open(f"overrides/{a.sku}.json"))
g = next(g for g in db["gamesets"] if g["sku"] == a.sku)
cards = sorted(g["gameset_data"]["cards"], key=lambda c: c["CardNumber"])
missing, diff, flags = [], [], []
for c in cards:
    tid = c["Spotify"]; t = cache.get(tid, {})
    o = ov.get(tid); oy = (o.get("year") if isinstance(o, dict) else o) if o is not None else None
    name = f"{', '.join(t.get('artists', []))} - {t.get('title')}"
    sp, auto = t.get("spotifyYear"), t.get("year")
    if oy is None:
        missing.append(f"{tid} | {int(c['CardNumber'])} | {name} | auto={auto} sp={sp}")
        continue
    if sp and oy > sp + 1:
        flags.append(f"LATER THAN SPOTIFY: {tid} | {name} | curated={oy} sp={sp}")
    if auto is not None and oy != auto:
        diff.append(f"{tid} | {int(c['CardNumber'])} | {name} | curated={oy} auto={auto} sp={sp} it={t.get('itunes')} mb={t.get('mb')} conf={t.get('confidence')} | {o.get('why','') if isinstance(o, dict) else ''}")
print(f"== {a.sku}: {len(cards)} cards, curated={len(cards)-len(missing)}, disagreements with auto={len(diff)}, flags={len(flags)}")
if missing: print("\n-- no curated year:"); print("\n".join(missing))
if flags: print("\n-- sanity flags:"); print("\n".join(flags))
if diff: print("\n-- curated != auto:"); print("\n".join(diff))
