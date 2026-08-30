#!/usr/bin/env python3
"""
Builds the Hitster card catalog for the app.

Pipeline:
  1. Read the official gameset database (hitster.jumboplay.com) and pick the Brazilian SKUs.
  2. For every Spotify track ID, scrape https://open.spotify.com/embed/track/<id>
     (no API key needed) -> title, artists, Spotify release date, 30s preview URL.
  3. Cross-check the ORIGINAL release year with iTunes Search and MusicBrainz,
     because Spotify's date is frequently the remaster / compilation date.
  4. Write catalog/<sku>.json (one file per deck) plus catalog/review.csv listing
     the tracks whose sources disagree so they can be reviewed by hand.

Usage: python3 build_catalog.py [--db gameset_database.json] [--out ../app/src/main/assets/catalog]
"""
import argparse, json, os, re, sys, time, urllib.parse, urllib.request, html, csv
from concurrent.futures import ThreadPoolExecutor

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

DECKS = {
    "aaaq0001": {"name": "Hitster",           "subtitle": "Brasil · edição original", "lang": "pt-BR"},
    "aaaq0002": {"name": "Hitster Lado B",    "subtitle": "Brasil · Guilty Pleasures", "lang": "pt-BR"},
    "aaaq0003": {"name": "Hitster Summer Party", "subtitle": "Brasil · Summer Party", "lang": "pt-BR"},
}

def http_get(url, timeout=15, retries=3, headers=None):
    h = {"User-Agent": UA, "Accept": "*/*"}
    if headers: h.update(headers)
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=h)
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read().decode("utf-8", "replace")
        except Exception as e:  # noqa
            last = e
            time.sleep(1.5 * (i + 1))
    raise last

NEXT_RE = re.compile(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', re.S)

def scrape_spotify_track(tid):
    page = http_get(f"https://open.spotify.com/embed/track/{tid}")
    m = NEXT_RE.search(page)
    if not m:
        raise RuntimeError("no __NEXT_DATA__")
    ent = json.loads(m.group(1))["props"]["pageProps"]["state"]["data"]["entity"]
    rd = (ent.get("releaseDate") or {}).get("isoString") or ""
    return {
        "spotifyId": tid,
        "title": ent.get("title") or ent.get("name"),
        "artists": [a["name"] for a in ent.get("artists", [])],
        "spotifyYear": int(rd[:4]) if rd[:4].isdigit() else None,
        "preview": (ent.get("audioPreview") or {}).get("url"),
        "playable": ent.get("isPlayable", True),
        "cover": ((ent.get("coverArt") or {}).get("sources") or [{}])[0].get("url"),
    }

# ---------- title / artist normalisation for fuzzy matching ----------
STRIP_RE = re.compile(
    r"\s*[\(\[][^\)\]]*(remaster|remasterizad|live|ao vivo|acoustic|ac[úu]stic|radio edit|version|vers[ãa]o|mix|edit|feat\.?|ft\.?|participa|bonus|mono|stereo|from|soundtrack|trilha)[^\)\]]*[\)\]]"
    r"|\s+-\s+.*$",
    re.I,
)
def norm(s):
    s = html.unescape(s or "").lower()
    s = STRIP_RE.sub("", s)
    s = re.sub(r"[^\w\s]", " ", s)
    return re.sub(r"\s+", " ", s).strip()

def similar(a, b):
    a, b = norm(a), norm(b)
    if not a or not b: return False
    return a == b or a in b or b in a

# ---------- iTunes ----------
def itunes_years(title, artist):
    q = urllib.parse.quote(f"{artist} {title}")
    try:
        data = json.loads(http_get(f"https://itunes.apple.com/search?term={q}&entity=song&limit=10&country=BR", retries=2))
    except Exception:
        try:
            data = json.loads(http_get(f"https://itunes.apple.com/search?term={q}&entity=song&limit=10", retries=1))
        except Exception:
            return []
    ys = []
    for r in data.get("results", []):
        if not similar(r.get("trackName", ""), title): continue
        if not similar(r.get("artistName", ""), artist) and norm(artist) not in norm(r.get("artistName", "")): continue
        rd = r.get("releaseDate", "")
        if rd[:4].isdigit(): ys.append(int(rd[:4]))
    return ys

# ---------- MusicBrainz ----------
def mb_years(title, artist):
    q = urllib.parse.quote(f'recording:"{title}" AND artist:"{artist}"')
    try:
        data = json.loads(http_get(f"https://musicbrainz.org/ws/2/recording?query={q}&fmt=json&limit=10",
                                   headers={"User-Agent": "HitsterMobileCatalog/1.0 (catalog builder)"}, retries=2))
    except Exception:
        return []
    ys = []
    for r in data.get("recordings", []):
        if r.get("score", 0) < 80: continue
        if not similar(r.get("title", ""), title): continue
        names = " ".join(c.get("name", "") for c in r.get("artist-credit", []))
        if not similar(names, artist) and norm(artist) not in norm(names): continue
        frd = r.get("first-release-date", "")
        if frd[:4].isdigit(): ys.append(int(frd[:4]))
    return ys

OVERRIDES_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "year_overrides.json")

def load_overrides():
    """{spotifyId: year} curated by hand – year_overrides.json plus overrides/<sku>.json."""
    # per-deck reviewer files first, then the master file (final adjudications) wins
    paths = []
    odir = os.path.join(os.path.dirname(OVERRIDES_PATH), "overrides")
    if os.path.isdir(odir):
        paths += [os.path.join(odir, f) for f in sorted(os.listdir(odir)) if f.endswith(".json")]
    paths.append(OVERRIDES_PATH)
    out = {}
    for p in paths:
        if not os.path.exists(p): continue
        data = json.load(open(p))
        for k, v in data.items():
            if k.startswith("_"): continue
            y = v.get("year") if isinstance(v, dict) else v
            if isinstance(y, int) and 1900 <= y <= 2100: out[k] = y
    return out

TITLE_JUNK = re.compile(
    r"\s*[-–]\s*(?:\d{4}\s*)?(?:remaster(?:ed|izad[oa])?|digital remaster|remix|radio edit|single version|album version|"
    r"(?:mono|stereo)(?: version)?|edit|re-?recorded|rerecorded|ao vivo|live|acoustic|acústico|version|vers[ãa]o)"
    r"(?:\s+\d{4})?(?:\s*[-–/].*)?$"
    r"|\s*[\(\[](?:\d{4}\s*)?(?:remaster(?:ed|izad[oa])?|digital remaster|radio edit|single version|album version|"
    r"(?:mono|stereo)(?: version)?|re-?recorded|rerecorded)(?:\s+\d{4})?[\)\]]",
    re.I,
)

def clean_title(title):
    """Strip 'Song - 2007 Remaster' / '(Remastered 2011)' suffixes so the card reads like the printed one."""
    t = title
    for _ in range(3):
        new = TITLE_JUNK.sub("", t).strip()
        if new == t or not new: break
        t = new
    return t or title

def decide_year(sp, it, mb):
    """Return (year, source, confidence). Spotify date is an upper bound (remasters are later)."""
    cands = []
    upper = sp or 2100
    for y in it:
        if 1900 <= y <= upper + 1: cands.append(("iTunes", y))
    for y in mb:
        if 1900 <= y <= upper + 1: cands.append(("MusicBrainz", y))
    if cands:
        src, y = min(cands, key=lambda c: c[1])
        others = {c[1] for c in cands}
        spread = max(others) - min(others)
        agree = (sp is not None and abs(sp - y) <= 1) or (len(others) == 1 and len(cands) >= 2)
        conf = "high" if agree else ("medium" if spread <= 1 else "low")
        if sp is not None and sp - y > 30: conf = "low"  # suspicious: probably a wrong match
        return y, src, conf
    if sp: return sp, "Spotify", "medium"
    return None, None, "none"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default="gameset_database.json")
    ap.add_argument("--out", default="../app/src/main/assets/catalog")
    ap.add_argument("--skus", default=",".join(DECKS.keys()))
    ap.add_argument("--skip-years", action="store_true", help="only scrape Spotify (fast)")
    args = ap.parse_args()

    db = json.load(open(args.db))
    os.makedirs(args.out, exist_ok=True)
    cache_path = os.path.join(args.out, "_cache.json")
    cache = json.load(open(cache_path)) if os.path.exists(cache_path) else {}

    def save_cache():
        json.dump(cache, open(cache_path, "w"), ensure_ascii=False, indent=0)

    review_rows = []
    for g in db["gamesets"]:
        sku = g["sku"]
        if sku not in args.skus.split(","): continue
        cards = sorted(g["gameset_data"]["cards"], key=lambda c: c["CardNumber"])
        print(f"== {sku} {DECKS.get(sku, {}).get('name')} ({len(cards)} cards)", flush=True)

        # 1) Spotify embed scrape (parallel, gentle)
        todo = [c["Spotify"] for c in cards if c["Spotify"] not in cache or "title" not in cache[c["Spotify"]]]
        def sp_job(tid):
            try:
                return tid, scrape_spotify_track(tid)
            except Exception as e:
                return tid, {"spotifyId": tid, "error": str(e)}
        with ThreadPoolExecutor(max_workers=4) as ex:
            for i, (tid, res) in enumerate(ex.map(sp_job, todo)):
                cache[tid] = {**cache.get(tid, {}), **res}
                if i % 25 == 0:
                    print(f"  spotify {i}/{len(todo)}", flush=True); save_cache()
        save_cache()

        # 2) Year cross-check (iTunes ~ 20 req/min is safe; MusicBrainz 1 req/s)
        if not args.skip_years:
            todo = [c["Spotify"] for c in cards if "year" not in cache.get(c["Spotify"], {}) and "title" in cache.get(c["Spotify"], {})]
            for i, tid in enumerate(todo):
                t = cache[tid]
                artist = t["artists"][0] if t["artists"] else ""
                it = itunes_years(t["title"], artist)
                time.sleep(3.2)   # iTunes: stay under ~20/min
                mb = mb_years(t["title"], artist)
                time.sleep(1.1)   # MusicBrainz: 1 req/s
                y, src, conf = decide_year(t.get("spotifyYear"), it, mb)
                t.update({"year": y, "yearSource": src, "confidence": conf, "itunes": sorted(set(it)), "mb": sorted(set(mb))})
                print(f"  [{i+1}/{len(todo)}] {y} ({src},{conf}) sp={t.get('spotifyYear')} it={sorted(set(it))} mb={sorted(set(mb))} | {artist} - {t['title']}", flush=True)
                if i % 10 == 0: save_cache()
            save_cache()

        # 3) Emit deck (manual year overrides win over the automatic guess)
        overrides = load_overrides()
        deck = {"sku": sku, **DECKS.get(sku, {"name": sku}), "cards": []}
        for c in cards:
            t = cache.get(c["Spotify"], {})
            if "title" not in t:
                print("  !! missing", c); continue
            auto_year = t.get("year") or t.get("spotifyYear")
            ov = overrides.get(c["Spotify"])
            card = {
                "n": int(c["CardNumber"]),
                "id": c["Spotify"],
                "title": clean_title(t["title"]),
                "artist": ", ".join(t["artists"]),
                "year": ov if ov else auto_year,
                "preview": t.get("preview"),
                "cover": t.get("cover"),
            }
            deck["cards"].append(card)
            if ov is None and t.get("confidence") in ("low", "none", "medium"):
                review_rows.append([sku, c["CardNumber"], card["artist"], card["title"], card["year"], t.get("spotifyYear"), t.get("itunes"), t.get("mb"), t.get("confidence")])
        with open(os.path.join(args.out, f"{sku}.json"), "w") as f:
            json.dump(deck, f, ensure_ascii=False, indent=1)
        print(f"  wrote {sku}.json with {len(deck['cards'])} cards", flush=True)

    with open(os.path.join(args.out, "review.csv"), "w", newline="") as f:
        w = csv.writer(f); w.writerow(["sku", "card", "artist", "title", "year", "spotifyYear", "itunes", "musicbrainz", "confidence"])
        w.writerows(review_rows)
    print("done; review rows:", len(review_rows))

if __name__ == "__main__":
    main()
