# Refatoração estrutural da TV — 27/09/2026

## Avaliação do pedido

O objetivo faz sentido para o BRasa: o maior risco de manutenção estava na mistura entre ciclo de vida do player, recuperação, controles e operações concorrentes da aplicação. A implementação mantém Compose, Media3, o repositório e os contratos atuais. Não introduz outra arquitetura, novos formatos de biblioteca ou mudanças visuais.

## Decisões e resultados

### Player

`PlayerScreen.kt` coordena a mídia, progresso, navegação e composição. As responsabilidades extraídas são:

- `PlayerSessionController.kt`: aquisição, MediaSession, tela ligada, lifecycle e liberação do player.
- `PlaybackRecoveryController.kt`: estado de recuperação, retry, detecção de vídeo sem primeiro quadro e sem avanço.
- `PlayerEvents.kt`: eventos do Media3, fim prematuro, decisões de erro e encaminhamento da recuperação. Callbacks usam os valores atuais da composição.
- `PlayerSeekController.kt`: posição de preview, aceleração do controle remoto, deduplicação de busca remota e carregamento cancelável de miniaturas.
- `PlayerControlsState.kt` e `PlayerControlsEffects.kt`: estado observável, focos, posição, visibilidade, temporizadores e avisos.
- `PlayerNextPreparation.kt`: preparação oportunista do próximo episódio, subordinada à estabilidade da reprodução atual.
- `PlayerTracks.kt`: escolhas de áudio/legenda e persistência do ajuste de sincronização.
- `PlayerOverlay.kt`, `PlayerTechnicalInfo.kt`, `PlayerEndPanel.kt` e `PlayerPresentation.kt`: composição dos controles e painéis existentes.

O retry prepara novamente a instância possuída pela sessão, preservando sua posição local e intenção de reprodução. Reaquisição da mesma identidade pode devolver a instância que o efeito anterior está liberando; por isso a liberação pertence exclusivamente ao ciclo de vida da sessão. A posição local do player não recebe novamente o offset HLS. Renovação de fonte e conversão continuam passando pelas políticas existentes.

### Estado e operações

`BrasaUiState.kt` separa o contrato de estado do coordenador. `OperationState.kt` substitui o par universal `loading/message` por operações de sessão, home, catálogo, reprodução, cache e personalização. Busca e scan mantêm seus estados próprios.

Cada domínio contabiliza solicitações pendentes. Encerrar uma solicitação não encerra outra, mesmo no mesmo domínio. Reiniciar a geração ao trocar perfil impede que finalizadores antigos apaguem o estado de operações novas. Cancelamento é propagado, sem mensagem falsa de falha.

`PlaybackPreparation.kt` concentra o polling antes repetido em abertura, fallback e avanço remoto. Mantém posição absoluta, intenção de conversão, intervalo de um segundo e limite de 600 consultas. Respostas após cancelamento não são publicadas; o coordenador também confere perfil, geração e mídia.

`CatalogMutations.kt` atualiza favoritos pela identidade da mídia em detalhes, reprodução, busca, linhas, temporadas e coleções. Em falha, desfaz apenas o favorito, preservando seleção e progresso mais recentes. Alterações de favorito são serializadas. O catálogo é atualizado localmente; a home ainda é recarregada para recalcular recomendações. `signal()` mantém atualização de home e catálogo porque essas respostas dependem de regras do servidor.

Voltar à home reutiliza o catálogo e a home já carregados. Atualização explícita e scan continuam buscando dados novos. `BrasaViewModel` permanece coordenador para evitar fragmentar prematuramente o estado compartilhado.

### Navegação

`BrasaNavHost.kt` mantém configuração do grafo, atualização inicial e recuperação global de conexão. `Routes.kt`, `SessionRoutes.kt`, `CatalogRoutes.kt`, `PlaybackRoutes.kt` e `SettingsRoutes.kt` declaram os percursos por domínio, preservando destinos, callbacks de Voltar e regras de back stack. A lógica de negócio continua no coordenador/repositório.

### Servidor e sincronização

Novos módulos em `server/`:

| Módulo | Responsabilidade |
| --- | --- |
| `profile-contract.mjs` | Valores padrão, validação e representação pública de perfis |
| `profile-controller.mjs` | Rotas legadas e operações administrativas de perfis |
| `collections-controller.mjs` | Leitura, escrita e operações de coleções |
| `tv-catalog-service.mjs` | Perfis, catálogo, home, busca e regras de acesso da TV |
| `content-types.mjs` / `static-files.mjs` | Tipos MIME, arquivos, Range, HEAD, cache e encerramento de streams |
| `sync-movie-scanner.mjs` | Varredura de filmes e classificação de fontes indisponíveis |
| `sync-metadata-providers.mjs` | Consultas de metadados e escolha dos resultados de filmes; helpers compartilhados com séries |
| `sync-normalization.mjs` | Identificação por nome, normalização e funções de legendas |
| `sync-movie-artwork.mjs` | Capas e imagens de filmes |
| `sync-catalog-writer.mjs` | Persistência dos catálogos no formato existente |

`scripts/brasa-server.mjs` e `scripts/sync-movies.mjs` importam esses módulos. Serviços recebem suas dependências explicitamente; a fila compartilhada de gravação de perfis continua centralizada. O helper de escolha de resultados é exportado/importado também pela busca de séries, evitando uma referência indefinida após a extração.

As alterações de catálogo, imagens e identificação de séries que já estavam no workspace foram preservadas. Esta tarefa não executou sincronização da biblioteca real.

### CI

`.github/workflows/ci.yml` define verificações em push e pull request:

- Windows/Node 24: instalação sem gerar lockfile e `npm test`.
- Linux/Java 17/Android SDK 36: testes unitários, lint e APK debug.
- Relatórios Android são guardados como artefatos por sete dias.
- Execuções antigas da mesma referência são canceladas. Não exige TV, ADB, assinatura release ou secrets de publicação.

O workflow foi criado, mas sua execução no GitHub não foi verificada nesta etapa.

## Verificação local

- `npm test`: **47/47 suítes aprovadas**.
- `apps/android-tv/scripts/test-windows.ps1`: **127 testes Android**, zero falhas, erros ou ignorados; `lintDebug` e `assembleDebug` aprovados.
- Lint Android: **0 erros, 50 avisos e 1 sugestão**. Os avisos apontam dependências, recursos, configuração e componentes fora desta extração; não foram suprimidos.
- Verificação adicional de referências JavaScript nos módulos extraídos e nos dois scripts principais: nenhuma referência indefinida após a correção do helper compartilhado.
- Nenhum teste removido. Verificações que procuravam código no arquivo original foram adaptadas aos módulos.

Testes adicionados/ampliados:

- `OperationTrackerTest.kt`: concorrência entre domínios e no mesmo domínio, troca de geração, falha e cancelamento.
- `CatalogMutationsTest.kt`: rollback sem restaurar uma página antiga, preservação de progresso, coleções e episódios.
- `PlaybackPreparationTest.kt`: término, limite, posição absoluta e resposta tardia cancelada.
- `PlayerSeekControllerTest.kt`: aceleração, limites e deduplicação de busca remota.
- `scripts/test-http-range.mjs`: serviço extraído por HTTP real, Range, HEAD, ETag e arquivo ausente.
- `scripts/test-provider-recovery.mjs`: escolha por ano e fallback de metadados com rede simulada, sem credenciais reais.
- Fixtures de sincronização e verificações de UI em `test-new-episode-scan.mjs`, `test-browser-ui-regressions.mjs` e `test-android-tv-ui-regressions.mjs` atualizadas.

As telas Home, Pairing, Profile, PIN, Server e Settings foram ajustadas somente para consumir o estado da operação correspondente. `PlaybackNavigation.kt` deixa de depender da mensagem global removida.

## Limites e próximos trabalhos

1. Fazer uma rodada física na TCL/Android 11 antes de publicar uma versão release: reprodução direta, HLS, retomada, avanço fora do buffer, falha de rede, background/foreground, legendas, áudio, autoplay e retorno ao episódio/foco anteriores. Testes locais e APK debug não comprovam decodificação 4K/HDR no aparelho.
2. Observar a primeira execução do CI no GitHub, sobretudo download do SDK/Robolectric em ambiente limpo.
3. Extrair futuramente bootstrap e rotas de streaming restantes somente junto de testes de integração dessas fronteiras. Permanecem no servidor principal por compartilharem filas, sessões e encerramento do processo.
4. Separar progressivamente enriquecimento de séries, download de legendas e renomeação na sincronização. Seus helpers já estão separados; o fluxo com escrita na biblioteca permanece no script para reduzir risco de efeitos colaterais.
5. Avaliar controladores adicionais de sessão/scan apenas quando houver mudanças funcionais nessas áreas. A decomposição integral em vários ViewModels não trouxe benefício suficiente nesta etapa.
6. Tratar os avisos de lint em uma tarefa delimitada, sem atualizar dependências de reprodução junto com esta refatoração.

Esta etapa gerou APK debug local. Não publicou nem instalou um novo APK na TV.
