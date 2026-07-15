# BRasa

Servidor local de biblioteca de mídia com sincronização automática, perfis independentes e reprodução adaptativa por FFmpeg.

## Comandos

- `Abrir BRasa.vbs`: entrada normal no Windows; inicia ou reutiliza o servidor com terminal oculto, acompanha a sincronização e abre a aplicação quando a biblioteca estiver pronta.
- `Abrir BRasa.vbs /debug`: inicia com console visível para diagnóstico.
- `npm start`: alternativa para desenvolvimento; não é necessária no uso normal.
- `npm run sync`: sincroniza filmes e séries manualmente.
- `npm test`: executa todas as suítes de estabilização.

## Ambiente

- `BRASA_PORT`: porta local; padrão `4173`.
- `BRASA_SKIP_STARTUP_SYNC=1`: desativa somente a passagem inicial.
- `BRASA_WATCH_LIBRARY=0`: desativa o watcher; ativo por padrão.
- `BRASA_WATCH_STABILITY_INTERVAL`: intervalo entre verificações; padrão `2000` ms.
- `BRASA_WATCH_STABLE_CHECKS`: verificações consecutivas exigidas; padrão `3`.
- `BRASA_WATCH_TIMEOUT`: espera máxima por arquivo; padrão `600000` ms.
- `BRASA_DEBUG=1`: inclui detalhes controlados nos erros locais.

### Chaves das APIs

Execute `npm run setup:env`. O comando cria `.env` a partir de `.env.example` quando necessário, preserva valores existentes e informa somente os nomes das chaves que continuam vazias. Os valores nunca são exibidos.

Preencha no arquivo local `.env`:

```dotenv
OMDB_API_KEY=
OPENSUBTITLES_API_KEY=
TMDB_API_KEY=
TMDB_READ_TOKEN=
SUBTITLE_LANGUAGES=pt-br,en
```

- `OMDB_API_KEY`: metadados, IMDb e classificação.
- `OPENSUBTITLES_API_KEY`: pesquisa e download de legendas.
- `TMDB_API_KEY` ou `TMDB_READ_TOKEN`: pôsteres e backdrops.
- `SUBTITLE_LANGUAGES`: idiomas separados por vírgula.

As chaves são opcionais para iniciar. Sem elas, arquivos locais continuam sendo indexados, mas metadados, imagens ou legendas podem ficar incompletos. `npm start` cria o `.env` automaticamente quando faltar. O `.env` está no `.gitignore`; nunca coloque chaves reais em `.env.example`, JavaScript público ou commits.

O watcher inicia antes da sincronização inicial. Eventos recebidos durante uma passagem são consolidados pelo coordenador e geram uma única passagem adicional.

### Recuperação automática de metadados

No início de cada sincronização, o BRasa verifica OMDb, TMDb e OpenSubtitles uma única vez. Filmes que ainda estiverem sem identificação, descrição, pôster, backdrop ou legenda entram numa fila local de recuperação. As novas tentativas ocorrem automaticamente após 5 minutos, 30 minutos e 6 horas; também há uma revisão diária e reprocessamento imediato quando um serviço volta a responder.

O estado dessa fila e a saúde dos serviços aparecem no resumo da API do painel administrativo. Os arquivos de estado ficam em `data/metadata-retry.json` e `data/provider-health.json`, são ignorados pelo Git e nunca armazenam chaves de API.

FFmpeg e FFprobe são necessários para analisar MKV, HEVC, 4K, áudio incompatível e arquivos pesados. Execute `./scripts/install-ffmpeg.ps1` no Windows; o instalador baixa o pacote indicado pela página oficial do FFmpeg para `tools/ffmpeg`, pasta local ignorada pelo Git. MP4 H.264/AAC leve continua apto a direct play.

## Painel administrativo

Acesse `http://127.0.0.1:4173/admin`. No primeiro acesso, crie uma senha com pelo menos oito caracteres, uma letra e um número. Não existe senha padrão.

O painel permite acompanhar a automação, filtrar a biblioteca, revisar problemas, salvar correções de metadados, operar a fila de mídia, administrar perfis e coleções, alterar configurações e consultar logs.

- A senha é validada somente no servidor e armazenada como hash `scrypt` em `.brasa-admin.json`.
- A sessão usa cookie `HttpOnly` e `SameSite=Strict`, expira após 30 minutos sem atividade e possui duração máxima de 8 horas.
- Alterar a senha encerra todas as sessões administrativas.
- O cookie não usa `Secure` porque o servidor padrão é HTTP local. Não exponha a porta do BRasa à rede pública.
- Senhas, PINs, hashes, cookies, CSRF e chaves de API não são registrados nos logs.

### Recuperação de acesso

Não há senha mestra ou backdoor. Para redefinir o acesso: encerre o servidor, faça backup de `.brasa-admin.json`, remova esse arquivo e inicie o BRasa novamente. O painel voltará ao fluxo de configuração inicial; a biblioteca e os perfis não serão apagados.

### Arquivos locais do painel

`.brasa-admin.json`, `data/admin-logs.json`, `data/admin-settings.json` e `data/admin-overrides.json` são locais e ignorados pelo Git. O arquivo já existente `data/movie-overrides.json` continua versionado porque contém correspondências compartilhadas do catálogo; correções feitas pelo painel ficam em `data/admin-overrides.json`.

### Testes administrativos

- `npm run test:admin-auth`
- `npm run test:admin-api`
- `npm run test:admin-library`
- `npm run test:admin-profiles`
- `npm run test:admin-logs`

## Modo TV e rede local

O acesso pela rede é opcional e vem **desativado por padrão**. Sem ativação, o servidor continua escutando exclusivamente em `127.0.0.1` e todo o comportamento anterior permanece igual.

### Ativar e parear

1. Abra o BRasa normalmente e entre em `http://127.0.0.1:4173/admin`.
2. Acesse **Rede e dispositivos**.
3. Ative **Modo LAN**, confirme o nome do servidor e salve.
4. Reinicie o BRasa quando o painel solicitar. O servidor passará a escutar em `0.0.0.0`, mas aceitará a interface TV apenas de endereços IPv4 privados.
5. No navegador da TV ou TV Box, abra um dos endereços mostrados no painel, acrescentando `/tv`, por exemplo `http://192.168.0.15:4173/tv`.
6. Gere o código na TV. No painel, aprove o pedido e escolha os perfis permitidos. Nenhum perfil marcado significa acesso a todos.
7. A TV recebe o token uma única vez e o guarda localmente. O servidor persiste somente um hash `scrypt` do token.

Depois do pareamento, setas movem o foco, `Enter` abre ou confirma, espaço reproduz ou pausa e `Escape`/`Backspace` volta. O player usa a entrega com Range já existente e salva o progresso no perfil selecionado.

### Segurança e limitações

- APIs antigas e arquivos de vídeo diretos continuam restritos ao próprio computador quando a LAN está ativa.
- Dispositivos usam uma API separada e não podem acessar sincronização, logs, metadados, configurações ou administração.
- O streaming remoto usa `/api/tv/stream/:mediaKey`, valida dispositivo, perfil e audiência e não revela caminhos absolutos.
- O BRasa não altera o Firewall do Windows. Caso outro aparelho não conecte, permita manualmente o Node.js apenas em redes privadas.
- Esta versão não oferece acesso pela internet, Chromecast, AirPlay, WebSocket ou controle por celular. HLS e o aplicativo Android TV são integrados localmente ao BRasa.

### Streaming adaptativo HLS

O BRasa analisa a mídia com FFprobe antes de liberar o player. A ordem é direct play, remux, conversão de áudio, HLS e transcodificação completa. HLS é selecionado para arquivos acima de 12 GB, obrigatório acima de 20 GB, bitrate superior a 20 Mb/s, 4K, HEVC/H.265/VC-1/MPEG-2, DTS/TrueHD/E-AC3 ou falha de compatibilidade.

As playlists ficam em `data/prepared-media/hls`, usam segmentos de aproximadamente quatro segundos e nunca substituem o original. Playlists em processamento não recebem cache agressivo; segmentos finalizados podem ser armazenados localmente. Em máquinas sem encoder de hardware validado, o BRasa prioriza 720p por CPU para manter velocidade suficiente. Com NVENC, Quick Sync ou AMF válidos, publica a escada compatível de 720p, 1080p e 2160p sem upscale.

Configurações padrão: `autoAnalyze=true`, `autoPrepare=true`, `cpuFallback=true`, `acceleration=auto`, buffer inicial de 12 s, alvo de 45 s e máximo de 150 s. O cache HLS tem limite padrão de 160 GB. Consulte `GET /api/media/cache`; para apagar somente HLS, use `DELETE /api/media/cache/hls` no próprio computador ou remova a pasta com o servidor encerrado.

Para revogar um aparelho, use **Rede e dispositivos → Revogar**. Para desativar totalmente, mude **Modo LAN** para desativado, salve e reinicie o BRasa.

Os arquivos reais `data/network-settings.json`, `data/devices.json` e seus backups são locais e ignorados pelo Git. Os arquivos `.example.json` documentam apenas a estrutura segura, sem tokens ou hashes reais.

### Testes do modo TV

- `node scripts/test-network-tv.mjs`
- `node scripts/test-tv-focus.mjs`
- `npm test`

## Diagnóstico e convivência da rede

O painel **Rede e dispositivos** mostra a interface ativa do computador, tipo de conexão, velocidade do link, IP, MAC, gateway, máscara e URL do servidor. Use esses dados para criar uma reserva DHCP no roteador; o BRasa não altera a configuração do roteador. A reserva evita que o endereço usado pela TV mude após uma reinicialização.

O servidor aceita `BRASA_HOST` e `BRASA_PORT` no `.env`. Para uso na LAN, mantenha `BRASA_HOST=0.0.0.0`, porta `4173`, acesso LAN e pareamento ativados. As APIs do diagnóstico exigem o token do aparelho e aceitam somente endereços privados IPv4 ou IPv6 local/link-local.

Para liberar somente a rede local no Firewall do Windows, abra o PowerShell como administrador e execute:

```powershell
.\scripts\install-brasa-firewall.ps1 -Port 4173
```

A regra é idempotente, limitada a `LocalSubnet`, TCP e perfil `Private`. Para removê-la, execute `.\scripts\remove-brasa-firewall.ps1`. Não habilite a porta no perfil público.

No APK, abra **Configurações → Diagnóstico de rede**. O teste dura 60 segundos e envia dados sintéticos limitados a 12 Mbps (1080p), 25 Mbps (4K equilibrado) ou 40 Mbps (4K alto). Durante o teste, use os outros aparelhos da casa normalmente. O resultado informa velocidade, latência, falhas, oscilação, bitrate recomendado e se a rede está excelente, boa, limitada ou instável. Nenhum filme é carregado pelo teste.

Teste automatizado específico: `node scripts/test-network-diagnostics.mjs`.
