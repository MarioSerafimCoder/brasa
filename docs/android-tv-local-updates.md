# Atualizações privadas do BRasa TV pela rede local

O BRasa TV não usa Play Store, GitHub Releases, Firebase ou qualquer distribuidor público. O computador publica um único APK release em `data/android-tv-updates/current/`, e somente uma TV pareada pode consultar ou baixar esse arquivo pela rede local.

A confiança combina origem igual ao servidor pareado, token do dispositivo, SHA-256 e certificado release. O Android também verifica a assinatura e sempre exige confirmação do usuário. Não existe instalação silenciosa.

## Fluxo normal

1. Alterar o código.
2. Aumentar a versão.
3. Compilar o release.
4. Publicar o release no servidor.
5. Ligar ou reiniciar o servidor.
6. Abrir o BRasa TV.
7. Atualizar pela televisão e confirmar no Android.

## Criar a chave uma única vez

```powershell
.\apps\android-tv\scripts\setup-release-signing.ps1
```

O script usa `keytool`, solicita as senhas ocultamente e cria uma chave RSA 4096 com validade de 30 anos em `%USERPROFILE%\.brasa\signing\brasa-tv-release.jks`. Ele para se o arquivo já existir. A chave privada nunca fica no repositório.

O fingerprint público é salvo em `apps/android-tv/release-certificate.sha256`. O arquivo contém somente o SHA-256 público e deve acompanhar todas as versões futuras.

Faça dois backups cifrados, em locais diferentes, da chave e das credenciais. Se a chave for perdida, instalações existentes não poderão receber novas versões. Nunca gere outra chave para substituir a anterior.

## Migração do APK debug

APK debug e APK release possuem assinaturas diferentes. A primeira migração exige:

1. Desinstalar o APK debug.
2. Instalar manualmente o primeiro APK release.
3. Abrir o BRasa TV.
4. Parear novamente a TV.
5. Usar apenas APKs release assinados pela mesma chave.

O package name permanece `com.brasa.tv` e não deve ser alterado para contornar assinatura.

## Versão, build e publicação

A versão fica em `apps/android-tv/version.properties`. Nunca reutilize um `VERSION_CODE` distribuído.

```powershell
.\apps\android-tv\scripts\bump-version.ps1 patch
.\apps\android-tv\scripts\bump-version.ps1 minor
.\apps\android-tv\scripts\bump-version.ps1 major
.\apps\android-tv\scripts\build-release.ps1
.\apps\android-tv\scripts\publish-release.ps1
```

O build solicita credenciais sem exibi-las, executa testes e lint, gera `apps/android-tv/app/build/outputs/apk/release/app-release.apk` e valida a assinatura. As variáveis aceitas são `BRASA_TV_KEYSTORE_PATH`, `BRASA_TV_KEYSTORE_PASSWORD`, `BRASA_TV_KEY_ALIAS` e `BRASA_TV_KEY_PASSWORD`; nunca as grave em arquivos ou logs.

O publicador confirma pacote, versão, certificado não-debug, fingerprint, hash e tamanho, solicita notas e troca a publicação de forma transacional. Uma versão anterior fica em `data/android-tv-updates/previous/`. APKs e o repositório local de releases são ignorados pelo Git.

```powershell
npm run android:release:status
```

O painel **Rede e dispositivos** mostra release e versões conhecidas das TVs. Não existe upload pelo navegador.

## Experiência na TV

O app consulta no máximo uma vez a cada 24 horas após autenticação, ou imediatamente por **Configurações > Verificar atualização**. Ele usa apenas `/api/v1/android-tv/update/apk` no servidor pareado, baixa para cache privado com extensão `.part`, mostra progresso e permite cancelar.

Antes do instalador são verificados tamanho, SHA-256, package name, versionCode superior e certificado contra o app instalado, o fingerprint compilado e o manifesto. Qualquer divergência apaga o download.

Se necessário, o app explica fontes desconhecidas e abre a tela oficial do Android. Ao voltar, verifica a permissão e exige novo clique em **Continuar**. O `PackageInstaller` grava e sincroniza o APK; o Android pede a confirmação final.

## Falhas e recuperação

- Servidor desligado, manifesto ausente ou download interrompido não afetam a versão instalada.
- Arquivos `.part` incompletos são apagados.
- Hash, pacote, downgrade, tamanho ou certificado inválido bloqueiam a instalação.
- Falta de espaço, permissão negada e cancelamento permitem tentar novamente.
- Servidor, token, perfil e progresso são preservados.
- Dispositivo revogado precisa ser pareado novamente; inventário de versão não autentica.

Para revogar um release, pare o servidor e mova `data/android-tv-updates/current` para uma quarentena fora de `android-tv-updates`. Sem `current`, a API informa que não há atualização.

Para restaurar a publicação anterior, pare o servidor, preserve a pasta `current` problemática fora do repositório e mova `previous` para `current`. Isso não faz downgrade de TVs já atualizadas; elas exigem um novo release com versionCode maior.

## Arquitetura

- `GET /api/v1/android-tv/update`: consulta autenticada e inventário mínimo.
- `GET|HEAD /api/v1/android-tv/update/apk`: APK fixo com ETag e Range.
- `data/android-tv-updates/`: repositório privado ignorado.
- `release-certificate.sha256`: fingerprint público, nunca chave privada.
- `PackageInstaller`: instalação completa com ação do usuário obrigatória.

O tráfego permanece HTTP somente dentro da LAN. URLs relativas e origem centralizada deixam a arquitetura pronta para HTTPS futuro.
