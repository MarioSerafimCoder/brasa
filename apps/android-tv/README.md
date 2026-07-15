# BRasa para Android TV

Aplicativo nativo em Kotlin e Jetpack Compose for TV. Ele encontra o BRasa na rede local, realiza pareamento por código, respeita os perfis autorizados e reproduz direct play ou HLS adaptativo do computador com Media3.

## Pré-requisitos

- BRasa em execução no computador com **Acesso pela rede** e **Permitir novos dispositivos** ativados.
- Computador e TV na mesma rede doméstica.
- JDK 17 e Android SDK 36 para compilar.
- FFmpeg e FFprobe no servidor para MKV, HEVC, 4K, áudio incompatível e arquivos pesados.

## Interface e controle remoto

A preferência local `uiScale` redimensiona toda a interface e oferece 80%, 90%, 100% e 110%; 90% é o padrão recomendado para TV. A preferência `density` é independente e altera apenas cards, espaçamento das grades e quantidade de conteúdo visível. O vídeo em tela cheia e as legendas do Media3 não são reduzidos pela escala da interface.

Busca e PIN não recebem foco de digitação ao abrir uma tela. O PIN usa um painel numérico próprio. Setas, Enter e Voltar navegam pelos controles; o player fecha antes do aplicativo e mantém o ponto de reprodução.

## Reprodução adaptativa

O APK não atribui o MKV original ao player enquanto o servidor analisa a mídia. As telas mostram “Analisando mídia” e “Preparando reprodução”; quando os primeiros segmentos estão prontos, o Media3 inicia HLS na qualidade automática. O menu do player informa qualidade e buffer e permite limitar manualmente as variantes disponíveis.

Em servidores NVIDIA, o BRasa valida o NVENC com uma codificação prática e usa NVDEC/CUDA + NVENC. Filmes HDR muito pesados podem oferecer inicialmente apenas 720p quando o FFmpeg do servidor precisar fazer o tone mapping na CPU; essa decisão preserva a reprodução contínua e não altera o arquivo original.

## Compilar e testar

No diretório `apps/android-tv`:

```text
gradlew.bat test lint assembleDebug
```

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

## Diagnóstico de rede

Em **Configurações → Diagnóstico de rede**, o APK identifica Ethernet, Wi-Fi ou rede móvel e, quando o Android disponibiliza a informação, mostra a faixa Wi-Fi de 2,4, 5 ou 6 GHz. O teste de convivência usa tráfego sintético autenticado e controlado pelo servidor; ele não abre um filme nem tenta ocupar toda a rede. Escolha 1080p (12 Mbps), 4K equilibrado (25 Mbps) ou 4K alto (40 Mbps), use outros aparelhos durante os 60 segundos e confira a recomendação de bitrate ao final.

## Teste em dispositivo

Instale o APK com Android Studio ou ADB, valide navegação completa pelo controle remoto, retorno de foco, suspensão/retomada, troca de legenda/áudio e reprodução de arquivos MP4/MKV presentes na biblioteca. O servidor continua sendo necessário durante a reprodução.
