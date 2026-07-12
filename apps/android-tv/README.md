# BRasa para Android TV

Aplicativo nativo em Kotlin e Jetpack Compose for TV. Ele encontra o BRasa na rede local, realiza pareamento por código, respeita os perfis autorizados e reproduz a biblioteca diretamente do computador com Media3.

## Pré-requisitos

- BRasa em execução no computador com **Acesso pela rede** e **Permitir novos dispositivos** ativados.
- Computador e TV na mesma rede doméstica.
- JDK 17 e Android SDK 36 para compilar.

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

## Teste em dispositivo

Instale o APK com Android Studio ou ADB, valide navegação completa pelo controle remoto, retorno de foco, suspensão/retomada, troca de legenda/áudio e reprodução de arquivos MP4/MKV presentes na biblioteca. O servidor continua sendo necessário durante a reprodução.
