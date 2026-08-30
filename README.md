# Hitster Mobile

Réplica do jogo de tabuleiro **HITSTER** inteiramente no smartphone: cada jogador usa o próprio
celular Android, todos entram numa **sessão** e jogam ao mesmo tempo, com as regras oficiais (linha
do tempo, fichas HITSTER, desafios, troca de 3 fichas por carta, 10 cartas para vencer).

* **Celular com celular, sem servidor na internet.** Quem cria a sessão vira o *anfitrião*: o app dele
  roda o jogo e os outros celulares conectam direto a ele pela rede local (mesmo Wi‑Fi, ou o hotspot
  de um dos celulares). Descoberta automática das sessões por perto; entrada manual por IP como reserva.
* **A música toca dentro do app**, só no celular de quem está jogando, usando o **preview de 30 s do
  Spotify** — o mesmo trecho que o jogo original toca. Não abre o Spotify nem precisa de conta.
  (Só é preciso internet no celular da vez para baixar o trecho; dados móveis servem.)
* **Baralhos oficiais brasileiros**: Hitster (308), Hitster Lado B (308) e Summer Party (308), com
  título/artista como no Spotify e ano de lançamento revisado carta por carta.
* **Em pé ou deitado**: gire o celular durante a partida — no modo paisagem a linha do tempo ocupa
  a largura toda, com o painel da vez e os jogadores em cima.
* **Design**: identidade neon do HITSTER (Righteous + Poppins, cartas coloridas por década), ícones
  vetoriais, alvos de toque ≥ 44 dp, animações de virar carta e de inserção na linha do tempo.
  Mockups de todas as telas em `design/` (gerados dos mesmos tokens do app).

```
hitster/
├── app/        Android (Kotlin + Jetpack Compose, minSdk 26) – inclui o anfitrião embutido
├── catalog/    Baralhos oficiais em JSON (empacotados no APK)
├── tools/      Pipeline que gera o catálogo a partir do banco oficial de cartas + revisão de anos
└── server/     (opcional) mesmo protocolo em Node.js, para jogar pela internet se um dia quiser
```

## Como jogar

1. Instale o mesmo APK nos celulares (`app/build/outputs/apk/debug/app-debug.apk`).
2. Todos na **mesma rede Wi‑Fi** (ou um celular liga o *hotspot* e os outros conectam nele).
3. Um jogador toca **CRIAR SESSÃO** — o celular dele vira o anfitrião e mostra o código (ex.: `K7PM`)
   e o endereço (ex.: `192.168.0.12:41234`).
4. Nos outros celulares a sessão aparece em **Sessões por perto** → toque em **ENTRAR**.
   Se não aparecer (alguns roteadores bloqueiam mDNS), use **entrar pelo endereço** com o IP:porta e o código.
5. O anfitrião escolhe os baralhos (pode combinar) e as opções e toca **INICIAR PARTIDA**.
6. Na sua vez o preview toca sozinho; ouça, toque em um **+** da sua linha do tempo e confirme.
   Marque **"Sei o nome da música e o artista"** e diga em voz alta para tentar ganhar 1 ficha.
7. Enquanto a carta está escondida, os oponentes têm alguns segundos para **GRITAR HITSTER!**
   (1 ficha) e apontar outra posição na sua linha do tempo.
8. Revelação: acertou → a carta fica; errou → descarte, a menos que um desafiante tenha acertado
   (ele rouba a carta). Os outros confirmam se título/artista estavam certos (+1 ficha, máx. 5).
9. A qualquer momento, **3 fichas → carta** coloca a carta do topo direto na sua linha do tempo.
10. Primeiro a chegar a **10 cartas** é o HITSTER. Equipes: um celular por equipe.

Toque no chip de qualquer jogador para ver a linha do tempo dele. O anfitrião deve manter o app
aberto durante a partida (a tela fica ligada sozinha). Se alguém cair, basta reabrir o app e entrar
de novo na sessão: o jogador volta com as mesmas cartas e fichas.

## Regras implementadas (manual)

* Preparação: 2 fichas HITSTER e 1 carta inicial com o ano virado para cada jogador.
* Vez: ouvir → posicionar → virar. Cartas do mesmo ano podem ficar em qualquer ordem entre si.
* Ficha 1 (na sua vez): pular a música; a carta vai para o final da pilha.
* Ficha 2 (na vez do adversário): gritar HITSTER **antes da revelação**, colocar 1 ficha em outra
  posição; não pode haver duas fichas na mesma posição; quem gritou primeiro coloca primeiro;
  se o dono errou e você acertou, rouba a carta. A ficha é descartada de qualquer forma.
* Ficha 3 (a qualquer momento): 3 fichas pela carta do topo, sem adivinhar o ano.
* Ganhar ficha: dizer título e artista corretamente, mesmo errando a posição. Máximo 5 fichas.
* Vitória: 10 cartas corretamente posicionadas (ajustável no lobby: 5–20).

## Arquitetura

| Peça | Arquivo | O que faz |
|---|---|---|
| Motor de regras | `app/.../host/GameEngine.kt` | Estado do jogo, turnos, desafios, votação, vitória. Puro Kotlin, testado na JVM. |
| Anfitrião embutido | `app/.../host/LocalHost.kt` | Servidor WebSocket dentro do app (Java‑WebSocket). Uma sala; envia a cada celular um *snapshot* personalizado — a carta da vez só é revelada depois do posicionamento (o jogador da vez recebe apenas o id do Spotify para tocar). |
| Descoberta | `app/.../host/Discovery.kt` | Anuncia/encontra sessões na rede (`_hitster._tcp`, Android NSD/mDNS) e obtém o IP local. |
| Cliente | `app/.../net/GameClient.kt` | Cliente WebSocket (OkHttp) com reconexão automática e *rejoin*. Usado por todos, inclusive o anfitrião (conecta em `127.0.0.1`). |
| Preview | `app/.../audio/PreviewResolver.kt`, `PreviewPlayer.kt` | Resolve e toca o MP3 de 30 s do Spotify com ExoPlayer. |
| UI | `app/.../ui/` | Compose, identidade neon do HITSTER, cartas coloridas por década (CANTOR(A) / ANO / MÚSICA). |

### Spotify (modo preview)

O Spotify removeu `preview_url` da API pública e desativou a criação de apps para novas contas.
O app usa a mesma fonte do player embed oficial: `https://open.spotify.com/embed/track/<id>` contém
`audioPreview.url` (MP3 de 30 s em `p.scdn.co`). Não precisa de chave, login ou Premium. O link
gravado no catálogo serve de *fallback*.

## Instalar

APK pronto em **Releases** deste repositório (`hitster-mobile-v1.0.0.apk`). Instale o mesmo arquivo em
todos os celulares (permitir "fontes desconhecidas" na primeira vez).

## Build

```bash
./gradlew :app:assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # motor de regras + partida completa contra o anfitrião embutido
./gradlew :app:assembleRelease      # → app/build/outputs/apk/release/app-release.apk (assinado se houver keystore.properties)
```

Requer JDK 17 e Android SDK 34 (`local.properties` → `sdk.dir`). Para assinar o release crie
`keystore.properties` na raiz (git‑ignorado) apontando para o seu `.jks` — veja `app/build.gradle.kts`.
Atualizações só instalam por cima se forem assinadas com a **mesma** chave; guarde o keystore.

## Catálogo

Fonte: banco oficial de cartas do app HITSTER (`hitster.jumboplay.com/hitster-assets/gameset_database.json`),
SKUs brasileiros `aaaq0001` (Hitster), `aaaq0002` (Lado B / *Guilty Pleasures*) e `aaaq0003`
(Summer Party). Pipeline em `tools/`:

1. `build_catalog.py` lê título/artista/preview do embed do Spotify e cruza o ano com iTunes Search e
   MusicBrainz (a data do Spotify costuma ser a do remaster/coletânea).
2. Os anos foram revisados carta por carta (`tools/overrides/<sku>.json`, com justificativa) e
   cruzados com os anos impressos nas cartas oficiais de outras edições; decisões finais em
   `tools/year_overrides.json`. Convenção: o ano em que a música foi lançada/estourou (single ou álbum);
   remasters e coletâneas usam o ano original; gravações ao vivo que são a versão famosa usam o ano
   do disco ao vivo.
3. `reconcile.py <sku>` lista divergências entre a revisão humana e o palpite automático.

```bash
cd tools && python3 build_catalog.py --db gameset_database.json --out ../catalog
```

## Protocolo (resumo)

Cliente → anfitrião: `create`, `join`, `setDecks`, `setOptions`, `start`, `restart`, `kick`, `leave`,
`action` com `{type: place|skip|claimTitle|challenge|pass|vote|buyCard|continue}`.
Anfitrião → cliente: `joined`, `room` (snapshot completo), `events` (toasts/animações), `error`.
O `server/` em Node.js fala exatamente o mesmo protocolo (útil para jogar à distância: rode-o em
qualquer host e digite `wss://…` na entrada manual).

## Licença / aviso

Projeto de fã para uso pessoal. HITSTER é marca da Jumbo / Galápagos Jogos; Spotify é marca do
Spotify AB. Os previews são servidos pelo próprio Spotify.
