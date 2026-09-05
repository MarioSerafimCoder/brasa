# Validação de reprodução do BRasa TV

## Pré-requisitos

- JDK 17 configurado em `JAVA_HOME`.
- Android SDK 36 e `platform-tools` instalados.
- TV e computador na mesma rede privada.
- BRasa com acesso LAN ativo e TV pareada.

## Compilar

No PowerShell:

```powershell
cd C:\Users\Mario\Videos\Brasa\apps\android-tv
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

O APK de depuração será criado em:

```text
C:\Users\Mario\Videos\Brasa\apps\android-tv\app\build\outputs\apk\debug\app-debug.apk
```

## Instalar na TV

Ative a depuração ADB na TV e execute:

```powershell
adb connect IP_DA_TV:5555
adb install -r C:\Users\Mario\Videos\Brasa\apps\android-tv\app\build\outputs\apk\debug\app-debug.apk
```

## Acompanhar reprodução e buffer

```powershell
adb logcat -c
adb logcat -v time BRasaPlayback:D ExoPlayerImpl:I MediaCodecVideoRenderer:I AndroidRuntime:E *:S
```

Os logs `BRasaPlayback` mostram tempo até `STATE_READY`, primeiro frame, rebufferings, buffer atual, cache hit e falhas sem registrar o token.

Para ver abertura e cancelamento de streams no servidor, defina `BRASA_DEBUG=1` no `.env`, reinicie o BRasa e abra o launcher em modo debug. A contagem `ativos=` deve voltar a zero ao sair, trocar de mídia ou encerrar o APK.

## Roteiro manual obrigatório

Testar com MP4 1080p, MKV 1080p e um arquivo 4K de bitrate elevado:

1. Iniciar do começo e medir clique até primeiro frame.
2. Retomar no meio e confirmar a posição.
3. Avançar e retroceder; confirmar respostas `Range` sem streams abandonados.
4. Pausar, continuar, sair e reabrir.
5. Encerrar o APK durante a reprodução.
6. Abrir detalhes, aguardar o preload e sair sem assistir.
7. Navegar rapidamente entre duas mídias; deve existir somente um preload.
8. Reproduzir episódio até o fim e abrir o próximo.
9. Ativar e trocar legenda externa.
10. Revogar o token e confirmar erro sem retry infinito.
11. Remover o arquivo no PC e confirmar mensagem de indisponibilidade.
12. Encerrar o servidor durante o vídeo e religá-lo manualmente.
13. Simular Wi-Fi instável e medir quantidade/duração de rebufferings.
14. Testar com pouco armazenamento; o cache inativo deve ser limpo sem interromper o player ativo.
15. Durante 4K, testar internet em outro aparelho e observar latência/perda de pacotes.

## Resultado esperado

- Início normal entre 1 e 3 segundos em LAN saudável.
- Buffer de 20 a 45 segundos durante reprodução.
- Preload limitado a aproximadamente 4 segundos, após espera de 650 ms.
- Cache LRU limitado a 512 MB.
- Uma reprodução ou um preload ativo por vez.
- Saída e seek encerram a leitura anterior no servidor.
- Internet dos demais aparelhos permanece utilizável durante a reprodução.
