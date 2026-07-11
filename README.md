# BRasa

Servidor local de biblioteca de mídia com sincronização automática, perfis independentes e preparação opcional por FFmpeg.

## Comandos

- `npm start`: inicia o servidor.
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

O watcher inicia antes da sincronização inicial. Eventos recebidos durante uma passagem são consolidados pelo coordenador e geram uma única passagem adicional.

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
