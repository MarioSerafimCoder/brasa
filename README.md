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
