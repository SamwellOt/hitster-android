#!/usr/bin/env python3
"""Generates the design-canvas artboards from the app's real design tokens (ui/theme/Theme.kt).
Portrait screens are 390x844; the landscape game screen is 844x390."""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))

# ---- tokens lifted from app/src/main/java/com/hitster/mobile/ui/theme/Theme.kt
INK, S1, S2, S3, OUT = "#0B0B10", "#15151D", "#1F1F2A", "#2A2A38", "#363646"
T1, T2, T3 = "#F7F7FA", "#A9A9BC", "#8A8AA0"
PINK, ORANGE, YELLOW, PURPLE, CYAN, GREEN, DANGER = "#FF2D8F", "#FF6B2B", "#FFD23F", "#8E44FF", "#00E5FF", "#23C36B", "#FF4757"
NEON = f"linear-gradient(90deg,{PINK},{ORANGE},{YELLOW})"
PURPLE_G = f"linear-gradient(90deg,{PURPLE},{PINK})"
GUTTER = 16
def decade(y):
    return ("#7A3E9D" if y < 1960 else "#9B59B6" if y < 1970 else "#6C4BE0" if y < 1980 else "#2D7DF6" if y < 1990
            else "#F5C542" if y < 2000 else "#FF7A1A" if y < 2010 else "#23C36B" if y < 2020 else "#FF2D8F")
def on_decade(y): return "#14110A" if 1990 <= y <= 2009 else "#FFFFFF"

HEAD = f"""<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Righteous&family=Poppins:wght@400;500;600;700;900&display=swap">
  <style>
    body {{ margin: 0; background: {INK}; font-family: Poppins, "Segoe UI", system-ui, sans-serif; color: {T1}; -webkit-font-smoothing: antialiased; }}
    a {{ color: {CYAN}; }} a:hover {{ color: {YELLOW}; }}
    .display {{ font-family: Righteous, "Arial Black", Impact, sans-serif; font-weight: 400; }}
    .neon {{ background: {NEON}; -webkit-background-clip: text; background-clip: text; color: transparent; filter: drop-shadow(0 0 10px rgba(255,45,143,.35)); }}
    .label {{ font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; color: {T3}; white-space: nowrap; }}
    .pill {{ display: inline-flex; align-items: center; padding: 4px 10px; border-radius: 999px; background: {S2}; font-size: 12px; font-weight: 700; letter-spacing: .8px; color: {T2}; white-space: nowrap; }}
    .btn {{ display: flex; align-items: center; justify-content: center; min-height: 56px; border-radius: 16px; font-weight: 700; font-size: 16px; letter-spacing: 1px; color: #fff; text-align: center; }}
    .ghost {{ display: flex; align-items: center; justify-content: center; min-height: 44px; padding: 0 18px; border-radius: 14px; background: {S2}; border: 1px solid {OUT}; font-weight: 700; font-size: 14px; color: {T2}; white-space: nowrap; }}
    .panel {{ background: {S1}; border: 1px solid {OUT}; border-radius: 22px; padding: 16px; display: flex; flex-direction: column; align-items: center; gap: 10px; min-height: 0; overflow: hidden; }}
    .card {{ width: 104px; height: 140px; border-radius: 12px; padding: 8px; box-sizing: border-box; display: flex; flex-direction: column; justify-content: space-between; align-items: center; text-align: center; flex: none; }}
    .card .a {{ font-size: 11px; font-weight: 700; line-height: 13px; }}
    .card .y {{ font-family: Righteous, "Arial Black", Impact, sans-serif; font-size: 30px; line-height: 32px; }}
    .card .t {{ font-size: 10px; font-weight: 600; line-height: 12px; opacity: .92; }}
    .mystery {{ width: 104px; height: 140px; border-radius: 12px; box-sizing: border-box; background: linear-gradient(135deg,#1B1B26,#101017); position: relative; display: flex; align-items: center; justify-content: center; flex: none; }}
    .mystery::before {{ content: ""; position: absolute; inset: 0; border-radius: 12px; padding: 2px; background: {NEON}; -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0); -webkit-mask-composite: xor; mask-composite: exclude; }}
    .slot {{ width: 44px; height: 140px; display: flex; flex-direction: column; align-items: center; justify-content: center; flex: none; }}
    .slot .line {{ width: 2px; height: 39px; background: {OUT}; }}
    .slot .dot {{ width: 8px; height: 8px; border-radius: 50%; background: {OUT}; }}
    .slot.on .line {{ background: rgba(255,210,63,.7); }}
    .slot.on .plus {{ width: 24px; height: 24px; border-radius: 50%; background: {S3}; border: 1px solid {YELLOW}; color: {YELLOW}; font-weight: 900; font-size: 15px; display: flex; align-items: center; justify-content: center; }}
    .token {{ width: 18px; height: 18px; border-radius: 50%; background: #111116; border: 2px solid {PINK}; color: {PINK}; font-weight: 900; font-size: 9px; display: flex; align-items: center; justify-content: center; box-sizing: border-box; }}
    .token.dim {{ border-color: {T3}; color: {T3}; background: {S2}; }}
    .avatar {{ border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 900; box-sizing: border-box; flex: none; }}
    .chip {{ width: 82px; box-sizing: border-box; border-radius: 14px; background: {S1}; border: 1.5px solid {OUT}; padding: 8px 6px; display: flex; flex-direction: column; align-items: center; gap: 4px; flex: none; }}
    .chip.cur {{ background: {S2}; }}
    .wave {{ display: flex; align-items: center; gap: 3px; height: 44px; }}
    .wave i {{ display: block; width: 4px; border-radius: 2px; background: {NEON}; }}
    .field {{ height: 56px; border-radius: 14px; background: {S1}; border: 1px solid {OUT}; display: flex; align-items: center; padding: 0 16px; box-sizing: border-box; font-size: 16px; }}
    .banner {{ width: 100%; box-sizing: border-box; border-radius: 12px; padding: 10px 14px; font-size: 14px; font-weight: 600; text-align: center; }}
    .timer {{ width: 38px; height: 38px; border-radius: 50%; background: {S2}; border: 2px solid {YELLOW}; display: flex; align-items: center; justify-content: center; font-weight: 900; font-size: 15px; color: {YELLOW}; flex: none; }}
    .tap {{ min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; }}
  </style>
</helmet>
"""
FOOT = "</x-dc>\n</body>\n</html>\n"

def svg(name, size=24, color="#fff"):
    paths = {
        "play": '<path d="M8 5v14l11-7z"/>',
        "pause": '<path d="M6 5h4v14H6zM14 5h4v14h-4z"/>',
        "replay": '<path d="M12 5V2L7 6l5 4V7a5 5 0 1 1-5 5H5a7 7 0 1 0 7-7z"/>',
        "skip": '<path d="M6 18l8.5-6L6 6zM16 6h2v12h-2z"/>',
        "share": '<path d="M18 16a3 3 0 0 0-2.2 1l-7-4.1a3 3 0 0 0 0-1.8l7-4.1A3 3 0 1 0 15 5a3 3 0 0 0 .1.7L8.1 9.8a3 3 0 1 0 0 4.4l7 4.1A3 3 0 1 0 18 16z"/>',
        "check": '<path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/>',
        "trophy": '<path d="M7 3h10v2h3v3a4 4 0 0 1-4 4h-.3A5 5 0 0 1 13 14.9V17h3v2H8v-2h3v-2.1A5 5 0 0 1 8.3 12H8a4 4 0 0 1-4-4V5h3zm-1 4v1a2 2 0 0 0 2 2V7zm12 0h-2v3a2 2 0 0 0 2-2z"/>',
        "note": '<path d="M12 3v10.6A4 4 0 1 0 14 17V7h4V3z"/>',
        "logout": '<path d="M16 13v-2H7V8l-5 4 5 4v-3zM20 3h-8v2h8v14h-8v2h8a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2z"/>',
    }
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="{color}" xmlns="http://www.w3.org/2000/svg">{paths[name]}</svg>'

def card(artist, year, title, w=104, h=140, extra=""):
    k = w / 104
    bg, fg = decade(year), on_decade(year)
    return (f'<div class="card" style="width: {w}px; height: {h}px; background: {bg}; color: {fg}; {extra}"><div class="a" style="font-size: {11*k:.0f}px; line-height: {13*k:.0f}px;">{artist}</div>'
            f'<div class="y" style="font-size: {30*k:.0f}px; line-height: {32*k:.0f}px;">{year}</div><div class="t" style="font-size: {10*k:.0f}px; line-height: {12*k:.0f}px;">{title}</div></div>')

def mystery(w=104, h=140, icon=None):
    inner = svg(icon, int(w * 0.42)) if icon else f'<span class="display" style="font-size: {int(w*0.38)}px; color: #fff;">?</span>'
    return f'<div class="mystery" style="width: {w}px; height: {h}px;">{inner}</div>'

def slot(on=False, h=140):
    if on: return f'<div class="slot on" style="height: {h}px;"><div class="line" style="height: {h*0.28:.0f}px;"></div><div class="plus">+</div><div class="line" style="height: {h*0.28:.0f}px;"></div></div>'
    return f'<div class="slot" style="height: {h}px;"><div class="line" style="height: {h*0.28:.0f}px;"></div><div class="dot"></div><div class="line" style="height: {h*0.28:.0f}px;"></div></div>'

def tokens(n, color=PINK, size=18):
    out = []
    for i in range(5):
        cls = "token" if i < n else "token dim"
        st = f"width: {size}px; height: {size}px; font-size: {int(size*0.5)}px;" + (f" border-color: {color}; color: {color};" if i < n else "")
        out.append(f'<div class="{cls}" style="{st}">{"H" if size >= 14 else ""}</div>')
    return f'<div style="display: flex; gap: 3px;">{"".join(out)}</div>'

def avatar(letter, color, size=32, ring=False):
    return (f'<div class="avatar" style="width: {size}px; height: {size}px; background: {color}22; '
            f'border: {"2px solid #fff" if ring else "1px solid " + color + "cc"}; color: {color}; font-size: {int(size*0.45)}px;">{letter}</div>')

PLAYERS = [("Ana", PINK, 2, 4), ("Bia", CYAN, 1, 3), ("Caio", GREEN, 3, 2)]

def chip(name, color, tk, cards, goal, cur=False, me=False):
    return (f'<div class="chip{" cur" if cur else ""}"{" style=\"border-color: " + color + ";\"" if cur else ""}>{avatar(name[0], color, ring=cur)}'
            f'<div style="font-size: 12px; font-weight: 700; color: {T1};">{"Você" if me else name}</div>'
            f'<div style="font-size: 11px; font-weight: 700; color: {YELLOW}; letter-spacing: 1px;">{cards}/{goal}</div>{tokens(tk, color, 12)}</div>')

def phone(body, w=390, h=844):
    return f'<div style="width: {w}px; height: {h}px; box-sizing: border-box; background: {INK}; display: flex; flex-direction: column; overflow: hidden; position: relative;">{body}</div>'

def hud(n=2, color=PINK):
    return (f'<div style="display: flex; align-items: center; gap: 6px; height: 36px; padding: 0 10px; border-radius: 999px; background: {S2}; border: 1px solid {color}cc;">'
            f'<div class="token" style="width: 20px; height: 20px; font-size: 10px; border-color: {color}; color: {color};">H</div>'
            f'<span style="font-size: 14px; font-weight: 700; letter-spacing: .5px; white-space: nowrap;">{n} fichas</span></div>')

def header(round_=3, deck=598, compact=False, tokens=2, count="4/10"):
    return (f'<div style="display: flex; align-items: center; gap: 8px; padding: {"2px" if compact else "30px"} 4px {"2px" if compact else "4px"} {GUTTER}px;">'
            f'<div class="display neon" style="font-size: {18 if compact else 20}px; letter-spacing: 1.2px;">HITSTER</div>'
            + (f'<span class="pill">R{round_} · {deck}</span>' if compact else '')
            + '<div style="flex: 1;"></div>'
            + (f'<span class="pill" style="color: {YELLOW};">{count}</span>' if compact else '')
            + hud(tokens)
            + (buy(small=True, compact=True) if compact else '')
            + f'<div class="tap">{svg("logout", 24, T2)}</div></div>')

def players(cur, data=PLAYERS):
    return (f'<div style="display: flex; gap: 8px; padding: 0 {GUTTER}px; overflow: hidden;">' +
            "".join(chip(n, c, t, k, 10, cur=(n == cur), me=(n == "Ana")) for n, c, t, k in data) + '</div>')

MY_CARDS = [("Tim Maia", 1970, "Azul da Cor do Mar"), ("Rita Lee", 1982, "Baila Comigo"), ("Skank", 1994, "Jackie Tequila"), ("Sia", 2015, "Cheap Thrills")]
BIA_CARDS = [("Cazuza", 1988, "Ideologia"), ("Backstreet Boys", 1997, "Everybody"), ("Anitta", 2013, "Show das Poderosas")]

def strip(cards, placing=False, selected_at=None, w=104, h=140, offset=0, highlight=None, show_from=0):
    """Timeline strip. `selected_at` draws the mystery card in that slot; `offset` shifts the strip left (px)."""
    row = []
    for i in range(len(cards) + 1):
        if selected_at == i: row.append(mystery(w, h))
        else: row.append(slot(on=placing, h=h))
        if i < len(cards):
            a, y, t = cards[i]
            row.append(card(a, y, t, w, h, "outline: 2px solid #fff; outline-offset: -2px;" if highlight == y else ""))
    return f'<div style="display: flex; align-items: center; padding: 0 12px; overflow: hidden;"><div style="display: flex; align-items: center; margin-left: -{offset}px; flex: none;">{"".join(row)}</div></div>'

def buy(can=False, small=False, compact=False):
    col = YELLOW if can else T2
    return (f'<div style="display: flex; align-items: center; gap: 4px; height: {44 if small else 50}px; padding: 0 12px; border-radius: 14px; background: {S2}; border: 1px solid {YELLOW if can else OUT}; {"" if can else "opacity: .6;"} flex: none;">'
            + "".join(f'<div class="token{"" if can else " dim"}" style="width: 16px; height: 16px; font-size: 8px;{" border-color: " + YELLOW + "; color: " + YELLOW + ";" if can else ""}">H</div>' for _ in range(3))
            + f'<span style="font-size: 12px; font-weight: 700; color: {col}; white-space: nowrap;">{"→ carta" if compact else "3 fichas → carta"}</span></div>')

def my_timeline(placing=False, selected_at=None, highlight=None, offset=0, cards_=MY_CARDS, count="4/10", round_=3, deck=598):
    bar = (f'<div class="btn" style="flex: 1; min-height: 50px; background: {NEON}; font-size: 14px;">CONFIRMAR NA 4ª POSIÇÃO</div>' if selected_at is not None
           else f'<div class="btn" style="flex: 1; min-height: 50px; background: {NEON}; opacity: .4; font-size: 14px;">ESCOLHA A POSIÇÃO</div>' if placing
           else '<div style="flex: 1;"></div>')
    return (f'<div style="display: flex; flex-direction: column; gap: 4px; padding-bottom: 16px; background: {INK};">'
            f'<div style="display: flex; align-items: center; gap: 8px; padding: 4px {GUTTER}px;"><span class="label">Sua linha do tempo</span><span class="pill" style="color: {YELLOW};">{count}</span><div style="flex: 1;"></div><span class="label" style="text-transform: none; letter-spacing: .5px;">Rodada {round_} · {deck} no baralho</span></div>'
            f'{strip(cards_, placing, selected_at, offset=offset, highlight=highlight)}'
            f'<div style="display: flex; gap: 10px; align-items: center; padding: 8px {GUTTER}px 0;">{bar}{buy()}</div></div>')

# ------------------------------------------------------------------ screens
def home():
    swatches = "".join(f'<div class="tap"><div style="width: {32 if c == PINK else 26}px; height: {32 if c == PINK else 26}px; border-radius: 50%; background: {c}; {"border: 3px solid #fff;" if c == PINK else ""}"></div></div>'
                       for c in [PINK, ORANGE, YELLOW, GREEN, CYAN, "#2D7DF6", PURPLE])
    session = (f'<div style="display: flex; align-items: center; gap: 12px; padding: 12px 14px; border-radius: 14px; background: {S1}; border: 1px solid {OUT};">'
               f'<div style="flex: 1; display: flex; flex-direction: column; gap: 2px;"><div style="font-weight: 700;">Sessão K7PM</div><div style="font-size: 12px; color: {T2};">Sessão de Bia · 192.168.0.12</div></div>'
               f'<div style="font-weight: 700; font-size: 14px; letter-spacing: .5px; color: {CYAN};">ENTRAR</div></div>')
    body = (f'<div style="display: flex; flex-direction: column; align-items: center; padding: 72px 20px 24px;">'
            f'<div class="display neon" style="font-size: 52px; letter-spacing: 3px; line-height: 1;">HITSTER</div>'
            f'<div style="margin-top: 8px; font-size: 12px; font-weight: 700; letter-spacing: 2px; color: {T2};">O JOGO DE MÚSICAS DO SEU TEMPO</div>'
            f'<div style="margin-top: 6px; font-size: 12px; color: {T3}; text-align: center; line-height: 18px;">Seja o primeiro a construir uma linha do tempo com 10 músicas.</div>'
            f'<div style="width: 100%; display: flex; flex-direction: column; gap: 6px; margin-top: 28px;"><span class="label">Seu nome</span><div class="field">Ana</div></div>'
            f'<div style="width: 100%; display: flex; flex-direction: column; gap: 8px; margin-top: 14px;"><span class="label">Sua cor</span><div style="display: flex; justify-content: space-between;">{swatches}</div></div>'
            f'<div class="btn" style="width: 100%; margin-top: 28px; background: {NEON};">CRIAR SESSÃO</div>'
            f'<div style="margin-top: 6px; font-size: 12px; color: {T3}; text-align: center; line-height: 18px;">Seu celular vira o anfitrião. Os outros entram pela mesma rede Wi‑Fi (ou pelo seu hotspot).</div>'
            f'<div style="width: 100%; display: flex; align-items: center; gap: 8px; margin-top: 22px;"><div style="flex: 1; height: 1px; background: {OUT};"></div><span class="label" style="text-transform: none; letter-spacing: 1px;">ou entre em uma sessão</span><div style="flex: 1; height: 1px; background: {OUT};"></div></div>'
            f'<div style="width: 100%; display: flex; align-items: center; gap: 8px; margin-top: 14px;"><span class="label">Sessões por perto</span><span class="pill" style="color: {GREEN};">1</span></div>'
            f'<div style="width: 100%; margin-top: 8px;">{session}</div>'
            f'<div class="tap" style="margin-top: 8px; font-size: 12px; font-weight: 700; letter-spacing: .5px; color: {CYAN};">Não apareceu? Entrar pelo endereço</div>'
            f'</div>')
    return phone(body)

def lobby():
    row = lambda n, c, sub, me=False: (f'<div style="display: flex; align-items: center; gap: 12px; padding: 8px 12px; border-radius: 14px; background: {S1};">{avatar(n[0], c, 32)}'
                                       f'<div style="flex: 1; display: flex; flex-direction: column;"><div style="font-weight: 600; font-size: 15px;">{n}{" (você)" if me else ""}</div><div style="font-size: 12px; color: {T2};">{sub}</div></div></div>')
    deck = lambda name, sub, on: (f'<div style="display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 14px; background: {S2 if on else S1}; border: 1px solid {PINK if on else OUT};">'
                                  f'<div style="flex: 1; display: flex; flex-direction: column;"><div style="font-weight: 700; font-size: 15px;">{name}</div><div style="font-size: 12px; color: {T2};">{sub}</div></div>'
                                  f'<div style="width: 26px; height: 26px; border-radius: 8px; background: {PINK if on else S2}; border: 1px solid {PINK if on else OUT}; display: flex; align-items: center; justify-content: center;">{svg("check", 18) if on else ""}</div></div>')
    body = (f'<div style="display: flex; flex-direction: column; padding: 36px {GUTTER}px 20px; gap: 14px;">'
            f'<div style="display: flex; align-items: center;"><div class="display neon" style="font-size: 22px; letter-spacing: 1.3px;">HITSTER</div><div style="flex: 1;"></div><div class="ghost">Sair</div></div>'
            f'<div style="display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 14px 16px; border-radius: 20px; background: {S1}; border: 1px solid {OUT};">'
            f'<span class="label">Código da sessão</span><div class="display" style="font-size: 48px; letter-spacing: 10px; line-height: 52px;">K7PM</div>'
            f'<div style="display: flex; align-items: center; gap: 4px;"><div style="font-size: 12px; color: {T2}; text-align: center; line-height: 18px;">Os outros abrem o app na mesma rede Wi‑Fi e tocam na sua sessão.<br>Entrada manual: <b style="color: {T1};">192.168.0.12:41234</b></div><div class="tap">{svg("share", 22, CYAN)}</div></div></div>'
            f'<div style="display: flex; flex-direction: column; gap: 6px;"><div style="display: flex; align-items: center; gap: 8px;"><span class="label">Jogadores</span><span class="pill">3/10</span></div>'
            f'{row("Ana", PINK, "Criou a sessão", True)}{row("Bia", CYAN, "Na sala")}{row("Caio", GREEN, "Na sala")}</div>'
            f'<div style="display: flex; flex-direction: column; gap: 6px;"><span class="label">Baralhos</span>{deck("Hitster", "Brasil · edição original · 308 cartas", True)}{deck("Hitster Lado B", "Brasil · Guilty Pleasures · 308 cartas", True)}{deck("Hitster Summer Party", "Brasil · Summer Party · 308 cartas", False)}</div>'
            f'<div style="display: flex; align-items: center; gap: 8px;"><span class="label">Opções</span><span style="font-size: 12px; color: {T2};">10 cartas para vencer · 12 s para desafiar</span><div style="flex: 1;"></div><div class="tap" style="min-height: 32px; font-size: 12px; font-weight: 700; color: {CYAN};">mostrar</div></div>'
            f'<div class="btn" style="background: {NEON};">INICIAR PARTIDA</div></div>')
    return phone(body)

def wave(active=True, h=44):
    hs = [14, 26, 38, 22, 44, 30, 18, 40, 28, 12, 36, 46, 24, 16, 34, 42, 20, 30, 44, 26, 14, 38, 22, 32]
    k = h / 46
    return f'<div class="wave" style="height: {h}px;">' + "".join(f'<i style="height: {max(4, round(v * k)) if active else 8}px;{"" if active else " background: " + OUT + ";"}"></i>' for v in hs[: (24 if h >= 40 else 18)]) + '</div>'

def listen_panel(compact=False):
    ctl = lambda icon, lbl, size, brush=None, dim=False, label=True: (f'<div style="display: flex; flex-direction: column; align-items: center; gap: 4px;{" opacity: .4;" if dim else ""}"><div style="width: {size}px; height: {size}px; border-radius: 50%; background: {brush or S2}; border: 1px solid {OUT}; display: flex; align-items: center; justify-content: center;{" box-shadow: 0 0 28px rgba(255,45,143,.35);" if brush else ""}">{svg(icon, size // 2)}</div>' + (f'<span class="label" style="text-transform: none;">{lbl}</span>' if label else '') + '</div>')
    claim_full = (f'<div style="width: 100%; box-sizing: border-box; display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 14px; background: rgba(255,210,63,.14); border: 1px solid {YELLOW};">'
                  f'<div class="token" style="width: 24px; height: 24px; font-size: 12px; border-color: {YELLOW}; color: {YELLOW}; flex: none;">H</div>'
                  f'<div style="display: flex; flex-direction: column; flex: 1;"><div style="font-weight: 700; font-size: 14px;">Sei o nome da música e o artista!</div><div style="font-size: 12px; color: {T3}; line-height: 18px;">Diga em voz alta. Os outros confirmam depois da revelação: +1 ficha.</div></div>{svg("check", 18, YELLOW)}</div>')
    claim_one = (f'<div style="width: 100%; box-sizing: border-box; display: flex; align-items: center; gap: 10px; min-height: 44px; padding: 4px 12px; border-radius: 14px; background: rgba(255,210,63,.14); border: 1px solid {YELLOW};">'
                 f'<div class="token" style="width: 24px; height: 24px; font-size: 12px; border-color: {YELLOW}; color: {YELLOW}; flex: none;">H</div>'
                 f'<div style="font-weight: 700; font-size: 14px; flex: 1;">Sei o nome e o artista (+1 ficha)</div>{svg("check", 18, YELLOW)}</div>')
    if not compact:
        return (f'<div class="panel" style="flex: 1; margin: 10px {GUTTER}px;">'
                f'<span class="label" style="color: {YELLOW};">Sua vez</span>'
                f'<div style="font-size: 20px; font-weight: 700; text-align: center;">Ouça e posicione a carta</div>'
                f'{wave(True, 44)}'
                f'<div style="width: 100%; height: 6px; border-radius: 3px; background: {S2}; overflow: hidden;"><div style="width: 42%; height: 6px; background: {NEON};"></div></div>'
                f'<div style="width: 100%; display: flex; justify-content: space-between; font-size: 11px; font-weight: 700; color: {T2}; letter-spacing: 1px;"><span>0:12</span><span>prévia · 30 s</span></div>'
                f'<div style="display: flex; align-items: center; gap: 12px;">{ctl("replay", "Recomeçar", 48)}{ctl("pause", "Pausar", 72, NEON)}{ctl("skip", "Pular · 1 ficha", 48)}</div>'
                f'<div style="font-size: 12px; color: {T2};">Pular custa 1 ficha HITSTER</div>{claim_full}'
                f'<div class="banner" style="background: rgba(35,195,107,.12); border: 1px solid rgba(35,195,107,.5); color: {GREEN};">Posição escolhida. Confirme abaixo quando estiver pronto(a).</div>'
                f'</div>')
    return (f'<div class="panel" style="flex: 1; margin: 0; padding: 8px 12px; gap: 8px; border-radius: 18px;">'
            f'<div style="width: 100%; display: flex; align-items: center; gap: 12px;">'
            f'<div style="display: flex; align-items: center; gap: 12px;">{ctl("replay", "", 40, label=False)}{ctl("pause", "", 56, NEON, label=False)}{ctl("skip", "", 40, label=False)}</div>'
            f'<div style="flex: 1; display: flex; flex-direction: column; gap: 4px;"><div style="display: flex; align-items: center;"><span class="label" style="color: {YELLOW};">Sua vez · ouça e posicione</span><div style="flex: 1;"></div><span style="font-size: 11px; font-weight: 700; color: {T2}; letter-spacing: 1px;">0:12 · prévia · 30 s</span></div>'
            f'{wave(True, 28)}<div style="width: 100%; height: 6px; border-radius: 3px; background: {S2}; overflow: hidden;"><div style="width: 42%; height: 6px; background: {NEON};"></div></div></div></div>'
            f'{claim_one}'
            f'<div class="btn" style="width: 100%; min-height: 44px; background: {NEON}; font-size: 14px;">CONFIRMAR NA 4ª POSIÇÃO</div>'
            f'</div>')

def game_turn():
    return phone(header() + players("Ana") + listen_panel() + my_timeline(placing=True, selected_at=3, offset=150))

def game_turn_landscape():
    pcol = "".join(
        f'<div style="display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 5px 8px; border-radius: 12px; background: {S2 if n == "Ana" else S1}; border: 1px solid {c if n == "Ana" else OUT};">'
        f'{avatar(n[0], c, 24, ring=(n == "Ana"))}<div style="display: flex; flex-direction: column; gap: 2px;"><div style="font-size: 12px; font-weight: 700;">{"Você" if n == "Ana" else n}</div>'
        f'<div style="display: flex; align-items: center; gap: 6px;"><span style="font-size: 11px; font-weight: 700; color: {YELLOW};">{k}/10</span>{tokens(t, c, 9)}</div></div></div>'
        for n, c, t, k in PLAYERS)
    body = (header(compact=True) +
            f'<div style="flex: 1; display: flex; gap: 10px; padding: 0 12px; min-height: 0;">{listen_panel(compact=True)}'
            f'<div style="width: 150px; display: flex; flex-direction: column; gap: 6px; padding: 4px 0; flex: none;">{pcol}</div></div>'
            f'<div style="padding: 2px 0 4px;">{strip(MY_CARDS, placing=True, selected_at=3, w=78, h=104)}</div>')
    return phone(body, 844, 390)

def game_challenge():
    panel = (f'<div class="panel" style="flex: 1; margin: 10px {GUTTER}px;">'
             f'<span class="label" style="color: {CYAN};">Bia posicionou a carta</span>'
             f'<div style="display: flex; align-items: center; gap: 6px;"><div class="timer">8</div><span style="font-size: 12px; color: {T2};">para gritar HITSTER</span></div>'
             f'<span class="label">Linha do tempo de Bia</span>'
             f'{strip(BIA_CARDS, selected_at=2, w=78, h=104, offset=60)}'
             f'<div class="btn" style="width: 100%; min-height: 60px; background: {NEON}; box-shadow: 0 0 30px rgba(255,45,143,.4);">GRITAR HITSTER!</div>'
             f'<div style="font-size: 12px; color: {T2}; text-align: center; line-height: 18px;">Acha que Bia errou? Pague 1 ficha, aponte a posição certa e roube a carta.</div>'
             f'<div class="ghost" style="width: 100%; box-sizing: border-box;">Não desafiar</div>'
             f'</div>')
    return phone(header() + players("Bia") + panel + my_timeline())

def result():
    panel = (f'<div class="panel" style="flex: 1; margin: 10px {GUTTER}px;">'
             f'<span class="label" style="color: {YELLOW};">Resultado</span>'
             f'<div style="display: flex; align-items: center; gap: 16px;"><div style="position: relative;">{card("Skank", 1994, "Jackie Tequila", 120, 160)}'
             f'<div style="position: absolute; top: 4px; right: 4px; width: 28px; height: 28px; border-radius: 50%; background: {GREEN}; display: flex; align-items: center; justify-content: center;">{svg("check", 18)}</div></div>'
             f'<div style="width: 150px; display: flex; flex-direction: column; gap: 2px;"><div style="font-size: 12px; font-weight: 700; letter-spacing: .8px; color: {T2};">Skank</div><div style="font-size: 16px; font-weight: 600;">Jackie Tequila</div><div class="display" style="font-size: 30px; color: {YELLOW};">1994</div></div></div>'
             f'<div style="font-size: 16px; font-weight: 600; text-align: center; color: {GREEN};">Você acertou! A carta fica na linha do tempo.</div>'
             f'<div style="font-size: 12px; color: {T2}; text-align: center;">Caio desafiou na posição 2 e errou: perdeu 1 ficha.</div>'
             f'<div style="display: flex; align-items: center; gap: 6px;"><div class="token" style="width: 20px; height: 20px; font-size: 10px; border-color: {YELLOW}; color: {YELLOW};">H</div><span style="font-size: 12px; color: {YELLOW};">Você ganhou 1 ficha por dizer o nome e o artista!</span></div>'
             f'<div style="display: flex; align-items: center; gap: 6px;"><div class="timer">11</div><span style="font-size: 12px; color: {T2};">para a próxima rodada</span></div>'
             f'<div class="btn" style="width: 100%; background: {NEON};">PRÓXIMA RODADA</div>'
             f'</div>')
    return phone(header(deck=597) + players("Ana", [("Ana", PINK, 3, 5), ("Bia", CYAN, 1, 3), ("Caio", GREEN, 2, 2)]) + panel +
                 my_timeline(highlight=1994, offset=150, count="5/10"))

def winner():
    rank = [("Ana", PINK, 10), ("Caio", GREEN, 7), ("Bia", CYAN, 6)]
    rows = "".join(f'<div style="display: flex; align-items: center; gap: 10px; padding: 4px 0;"><span style="font-weight: 900; color: {YELLOW if i == 0 else T2};">{i+1}º</span>{avatar(n[0], c, 28)}<span style="flex: 1;">{n}</span><span style="font-size: 12px; color: {T2};">{k} cartas</span></div>' for i, (n, c, k) in enumerate(rank))
    overlay = (f'<div style="position: absolute; inset: 0; background: rgba(11,11,16,.94); display: flex; align-items: center; justify-content: center; padding: 24px;">'
               f'<div style="width: 100%; box-sizing: border-box; padding: 24px; border-radius: 26px; background: {S1}; border: 2px solid {PINK}; display: flex; flex-direction: column; align-items: center; gap: 8px; box-shadow: 0 0 40px rgba(255,45,143,.25);">'
               f'{svg("trophy", 64, YELLOW)}<div class="display neon" style="font-size: 34px; letter-spacing: 2px;">HITSTER</div>'
               f'<div style="font-size: 20px; font-weight: 700; text-align: center;">Você é o(a) HITSTER!</div>'
               f'<div style="width: 100%; margin-top: 8px;">{rows}</div>'
               f'<div class="btn" style="width: 100%; margin-top: 10px; background: {NEON};">JOGAR DE NOVO</div>'
               f'<div class="ghost" style="width: 100%; box-sizing: border-box;">Sair da sessão</div></div></div>')
    final = [("Ana", PINK, 1, 10), ("Bia", CYAN, 0, 6), ("Caio", GREEN, 2, 7)]
    return phone(header(round_=24, deck=548) + players("Ana", final) + '<div style="flex: 1;"></div>' + my_timeline(count="10/10") + overlay)

SCREENS = [("Main", "Início", home, 390, 844), ("Lobby", "Sessão", lobby, 390, 844), ("SuaVez", "Sua vez", game_turn, 390, 844),
           ("Desafio", "Gritar HITSTER", game_challenge, 390, 844), ("Resultado", "Revelação", result, 390, 844),
           ("Vencedor", "Fim de jogo", winner, 390, 844), ("SuaVezDeitado", "Sua vez · deitado", game_turn_landscape, 844, 390)]

if __name__ == "__main__":
    boards, x = [], 0
    for stem, title, fn, w, h in SCREENS:
        with open(os.path.join(HERE, f"{stem}.dc.html"), "w") as f:
            f.write(HEAD + fn() + FOOT)
        if stem == "SuaVezDeitado":
            boards.append({"file": f"{stem}.dc.html", "x": 0, "y": 844 + 160, "w": w, "h": h, "title": title})
        else:
            boards.append({"file": f"{stem}.dc.html", "x": x, "y": 0, "w": w, "h": h, "title": title}); x += w + 90
    manifest = {
        "artboards": boards,
        "annotations": [
            {"id": "brief", "x": 0, "y": -170, "w": 560,
             "text": "Hitster Mobile — mockups estáticos gerados a partir dos tokens reais do app (Theme.kt): Ink #0B0B10, neon rosa→laranja→amarelo, cartas coloridas por década, Righteous + Poppins. Ordem: Início → Sessão → Sua vez → Gritar HITSTER → Revelação → Fim de jogo. Abaixo: a tela da vez em modo deitado (844×390, v1.0.1) — cabeçalho com fichas/contagem/troca, painel compacto, linha do tempo em largura total."},
        ],
        "launch": {"view": "canvas"},
    }
    json.dump(manifest, open(os.path.join(HERE, "canvas.json"), "w"), ensure_ascii=False, indent=1)
    print("wrote", len(boards), "artboards")
