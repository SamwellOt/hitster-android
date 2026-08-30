#!/usr/bin/env python3
"""Print one line per card of a deck for manual year review.
usage: python3 dump_for_review.py <sku> [--cache ../catalog/_cache.json] [--db gameset_database.json]
Columns: id | card# | artist - title | auto year | spotify | itunes | musicbrainz | confidence
"""
import argparse, json, sys
ap = argparse.ArgumentParser()
ap.add_argument("sku")
ap.add_argument("--cache", default="../catalog/_cache.json")
ap.add_argument("--db", default="gameset_database.json")
ap.add_argument("--only-unsure", action="store_true")
a = ap.parse_args()
cache = json.load(open(a.cache))
db = json.load(open(a.db))
g = next(g for g in db["gamesets"] if g["sku"] == a.sku)
for c in sorted(g["gameset_data"]["cards"], key=lambda c: c["CardNumber"]):
    t = cache.get(c["Spotify"], {})
    if "title" not in t: print(f"{c['Spotify']} | {c['CardNumber']} | MISSING"); continue
    conf = t.get("confidence", "?")
    if a.only_unsure and conf == "high": continue
    it = ",".join(map(str, t.get("itunes", []))) or "-"
    mb = ",".join(map(str, t.get("mb", []))) or "-"
    print(f"{c['Spotify']} | {int(c['CardNumber'])} | {', '.join(t['artists'])} - {t['title']} | auto={t.get('year')} | sp={t.get('spotifyYear')} it={it} mb={mb} | {conf}")
