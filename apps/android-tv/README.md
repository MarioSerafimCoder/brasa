# BRasa para Android TV

Aplicativo nativo em Kotlin e Jetpack Compose for TV. Ele encontra o BRasa na rede local, realiza pareamento por código, respeita os perfis autorizados e reproduz direct play ou HLS adaptativo do computador com Media3.

## Pré-requisitos

- BRasa em execução no computador com **Acesso pela rede** e **Permitir novos dispositivos** ativados.
- Computador e TV na mesma rede doméstica.
- JDK 17 e Android SDK 36 para compilar.
- FFmpeg e FFprobe no servidor para MKV, HEVC, 4K, áudio incompatível e arquivos pesados.

## Interface e controle remoto

A preferência local `uiScale` redimensiona toda a interface e oferece 80%, 90%, 100% e 110%; 90% é o padrão recomendado para TV. A preferência `density` é independente e altera apenas cards, espaçamento das grades e quantidade de conteúdo visível. O vídeo em tela cheia e as legendas do Media3 não são reduzidos pela escala da interface.

Busca e PIN não recebem foco de digitação ao abrir uma tela. O PIN usa um painel numérico próprio. Setas, Enter e Voltar navegam pelos controles; o player fecha antes do aplicativo e mantém o ponto de reprodução. Detalhes preservam a temporada e o episódio selecionado depois da reprodução, concentram ações menos usadas em **Mais opções** e mostram progresso, estado assistido e tempo restante nos episódios.

A busca usa cabeçalho compacto, campo adaptável, histórico horizontal e resultados com títulos visíveis. Gêneros equivalentes, como `Acao` e `Ação`, compartilham a identidade exibida **Ação**, enquanto categorias compostas continuam separadas. Configurações são divididas em Geral, Reprodução, Interface, Personalização e Conta, com estados **Ativado/Desativado** explícitos.

A página inicial é personalizada por perfil no computador servidor, com continuidade separada do histórico, recomendações locais, novos episódios e `Minha lista` para filmes e séries. O destaque permanece estável durante a navegação. A busca ignora acentos, tolera um pequeno erro, oferece histórico local e usa reconhecimento de voz somente quando a TV o disponibiliza. Consulte [`docs/android-tv-personalizacao.md`](../../docs/android-tv-personalizacao.md) para regras, controles e limites de validação.

## Reprodução adaptativa

O APK detecta decoders de vídeo por hardware, perfis, limites de bitrate/resolução e tipos HDR do Google TV e envia essas capacidades ao servidor. Um MKV grande realmente compatível usa direct play autenticado com Range; Dolby Vision direto fica restrito a MP4, enquanto perfil 8.1 com camada HDR10 compatível usa remux HLS preservando resolução e HDR. Incompatibilidade apenas de áudio usa remux HLS, e uma falha real do decoder aciona automaticamente HLS transcodificado. As telas mostram “Analisando mídia” e “Preparando reprodução” somente quando algum processamento é necessário.

Em servidores NVIDIA, o BRasa valida o NVENC com uma codificação prática e usa NVDEC/CUDA + NVENC. A conversão adaptativa oferece variantes até 720p em CPU e até 1080p com aceleração, respeitando a fonte e a TV. HDR incompatível passa por tone mapping para SDR. A reprodução original e o remux preservam a resolução e o HDR compatíveis; retomar um vídeo não força conversão. Ao retomar, o HLS começa perto da posição salva e o player continua exibindo o tempo absoluto do filme.

## Compilar e testar

No diretório `apps/android-tv`, use o comando de validação para Windows. Ele localiza o SDK incluído no projeto e prepara os runtimes do Robolectric em um caminho sem espaços:

```powershell
./scripts/test-windows.ps1
```

Em uma máquina nova sem runtime em cache, a primeira execução permite o download. Se o carregador nativo reclamar de `%20`, repita o comando para preparar o arquivo recém-baixado. O script de release também prepara o runtime sem espaços. Os testes usam renderização nativa para conferir textos e navegação em detalhes, busca e Configurações nas escalas 80%, 90%, 100% e 110%.

O APK de desenvolvimento é criado em `app/build/outputs/apk/debug/app-debug.apk`. Artefatos de build, `local.properties`, APKs e configurações locais permanecem fora do Git.

## Primeiro uso

1. Abra o BRasa no computador e ative o acesso LAN no painel administrativo.
2. Abra o app na TV e selecione o servidor encontrado. Se necessário, informe `IP_DO_COMPUTADOR:4173`.
3. Confirme no computador o código exibido na TV e escolha os perfis autorizados.
4. Escolha um perfil. Perfis protegidos pedem PIN antes de abrir a biblioteca.

O token do dispositivo fica cifrado pelo Android Keystore. O app não envia esse token a imagens ou endereços externos e permite esquecer o servidor nas configurações.

## Atualizações privadas

O BRasa TV pode receber APKs release diretamente do computador pareado, sem Play Store ou serviço público. A consulta é autenticada, o download fica restrito ao servidor BRasa e o aplicativo valida versão, pacote, tamanho, SHA-256 e certificado antes de abrir o instalador oficial do Android. A confirmação do usuário continua obrigatória; não há instalação silenciosa.

Antes do primeiro release, crie e proteja a chave permanente com:

```powershell
.\apps\android-tv\scripts\setup-release-signing.ps1
```

Depois, aumente a versão, gere o APK e publique-o localmente usando os scripts em `apps/android-tv/scripts`. O procedimento completo, incluindo a migração inicial do APK debug, backup da chave, reversão e recuperação de falhas, está em [`docs/android-tv-local-updates.md`](../../docs/android-tv-local-updates.md).

## Buscar novos títulos

O atalho **INICIAR BRASA TV** solicita uma busca em segundo plano em toda abertura,
mesmo quando o servidor já está ligado. Coloque episódios dentro da pasta da série
em `assets/series` (por exemplo, `assets/series/Lanternas/...S01E03.mkv`).

No APK 1.0.28 ou posterior, use **Configurações → Biblioteca → Buscar novos títulos**.
A TV pareada solicita a varredura no computador, acompanha seu andamento e recarrega
o catálogo e a página inicial ao concluir. O computador precisa estar ligado.
Buscas simultâneas compartilham a mesma operação. Uma falha permite tentar novamente;
fechar a tela não cancela a varredura no servidor.

As rotas `POST/GET /api/v1/tv/library/scan` exigem autorização da TV.
O inicializador usa `POST /api/library/scan` somente pelo endereço local.

## Diagnóstico de rede

Em **Configurações → Diagnóstico de rede**, o APK identifica Ethernet, Wi-Fi ou rede móvel e, quando o Android disponibiliza a informação, mostra a faixa Wi-Fi de 2,4, 5 ou 6 GHz. O teste de convivência usa tráfego sintético autenticado e controlado pelo servidor; ele não abre um filme nem tenta ocupar toda a rede. Escolha 1080p (12 Mbps), 4K equilibrado (25 Mbps) ou 4K alto (40 Mbps), use outros aparelhos durante os 60 segundos e confira a recomendação de bitrate ao final.

## Teste em dispositivo

Instale o APK com Android Studio ou ADB, valide navegação completa pelo controle remoto, retorno de foco, suspensão/retomada, troca de legenda/áudio e reprodução de arquivos MP4/MKV presentes na biblioteca. No player de episódios, confira série, temporada, número e título; o botão **Informações** abre resolução, codecs, modo, qualidade, bitrate e buffer sem interromper o vídeo. O servidor continua sendo necessário durante a reprodução.
