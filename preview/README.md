# Capturas reais do BRasa na TV

Esta galeria contém apenas capturas do APK **1.0.43 (44)** instalado na TV TCL em 27/09/2026. Os PNGs vieram do aparelho físico, sem montagem. Cada imagem tem um JSON com versão, origem e hash de verificação.

| Tela | Captura |
| --- | --- |
| Perfis | [Abrir](android-tv/1.0.43/profiles-remote.png) |
| Início em 110% | [Abrir](android-tv/1.0.43/home-110.png) |
| Continuar assistindo em 110% | [Abrir](android-tv/1.0.43/continue-catalog-110.png) |
| Detalhes e navegação por controle | [Abrir](android-tv/1.0.43/details-fixed-remote.png) |
| Detalhes de série em 110% | [Abrir](android-tv/1.0.43/details-witch-110.png) |
| Mais opções em 110% | [Abrir](android-tv/1.0.43/more-options-110.png) |
| Conta e conexão | [Abrir](android-tv/1.0.43/settings-account-remote.png) |
| Interface em 110% | [Abrir](android-tv/1.0.43/settings-interface-110.png) |
| Erro observado em uma mídia | [Abrir](android-tv/1.0.43/playback-error-110.png) |

A captura remota do player pode mostrar o vídeo preto mesmo quando há imagem na TV. A tela de erro registra o estado observado naquela mídia; por si só, não identifica a causa.

## Como atualizar

Com a depuração autorizada, identifique a TV em `adb devices`, abra o BRasa e navegue até a tela desejada. Substitua o serial no exemplo:

```powershell
node scripts/capture-android-tv-preview.mjs --serial SERIAL_DA_TV --current --name inicio
```

O [utilitário de captura](../scripts/capture-android-tv-preview.mjs) salva na pasta da versão instalada, verifica que o BRasa está em primeiro plano e não sobrescreve arquivos sem `--replace`. Antes de publicar, confira a imagem, o nome da tela e os metadados; evite capturar credenciais.
