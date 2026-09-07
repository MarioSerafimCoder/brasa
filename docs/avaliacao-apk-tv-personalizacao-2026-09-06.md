# Avaliação do BRasa TV: personalização, descoberta e uso pelo controle remoto

Data: 06/09/2026. Código local: commit `9908bd4`, versão configurada 1.0.30 (31). Essa informação não confirma qual APK está instalado na TV.

## Parecer

A maior oportunidade é fazer a página inicial ajudar a escolher e retomar um título. O aplicativo já tem uma base aproveitável: interface nativa Kotlin/Compose, reprodução Media3, progresso por perfil, imagens, gêneros, coleções, filtros e memória de navegação. Recomendo evolução incremental dessa base.

Hoje existe personalização do estado — o que cada perfil assistiu e guardou — mas o caminho analisado não usa afinidade para escolher e ordenar recomendações. O destaque é aleatório e as fileiras principais são fixas. Antes de sofisticar a seleção, há inconsistências em favoritos e continuidade de séries, além de lacunas importantes nos metadados.

Escopo: código do APK em `apps/android-tv`, seus contratos e serviços no servidor, testes existentes e estatísticas agregadas do catálogo. Não foi feita instalação, publicação, reprodução em aparelho físico ou alteração de código de execução. Uma captura histórica foi inspecionada, mas diverge do código atual; ela não foi usada como prova da aparência instalada hoje. As observações de legibilidade precisam de validação na distância habitual do sofá.

## O que preservar

- Reprodução direta/HLS, detecção de capacidades, recuperação de falhas e posição absoluta de reprodução.
- Idioma e aparência de áudio/legendas por perfil, já implementados localmente.
- Filtros, ordenação e restauração do cartão selecionado. Há testes de interface simulada para retorno à biblioteca, inclusive após rolagem.
- Identidade escura com laranja, foco com borda e ampliação, interface infantil própria.
- Busca de novos arquivos pela própria TV e atualização local do APK.

Esses recursos já existem; não devem ser apresentados como novidades a construir.

## Achados e oportunidades, em ordem de prioridade

### 1. Tornar favoritos confiáveis antes de usá-los para aprender preferências

**Inconsistência confirmada com dados fictícios.** `tvSaveFavorite` grava identificadores como `movie:42`, mas `normalizeProfileState` só aceita favoritos que correspondam a uma expressão sem dois-pontos. A leitura de estado aplica essa normalização. Assim, um favorito criado pela TV pode desaparecer da resposta seguinte; uma gravação posterior pode persistir o estado já filtrado.

Reprodução isolada: normalizar `['movie:42', 'episode:serie-s1-e1', '42']` retorna apenas `['42']`. Nenhum dado real de perfil foi alterado para essa verificação.

Referências: `server/profile-state.mjs:9`; `scripts/brasa-server.mjs:588`, `:718` e `:729`.

**Proposta:** aceitar chaves canônicas válidas, preservar compatibilidade com IDs antigos e verificar o ciclo completo adicionar → recarregar → reiniciar → remover, com isolamento entre perfis. “Minha lista” representa intenção de assistir; deve ter peso menor que “Gostei” num futuro recomendador.

### 2. Fazer “Continuar” selecionar o episódio certo

**Comportamento confirmado no código.** Os detalhes abrem a primeira temporada e escolhem seu primeiro episódio para o botão principal, independentemente do episódio em andamento. O atalho de séries na página inicial também passa pelos detalhes. O servidor possui uma tentativa de resolução de `series:`, mas, sem episódio parcialmente assistido, recai no primeiro episódio; isso não resolve a situação de quem terminou E03 e deve começar E04.

Referências: `feature/details/DetailsScreen.kt:82`, `:86` e `:130`; `app/navigation/BrasaNavHost.kt:34`; `scripts/brasa-server.mjs:523`. Os caminhos Kotlin nesta avaliação são relativos a `apps/android-tv/app/src/main/java/com/brasa/tv`.

**Proposta:** uma única regra de continuidade compartilhada entre início, detalhes e reprodução. Retomar o episódio em andamento; após conclusão, oferecer o próximo disponível em ordem de temporada/episódio. Mostrar “Continuar T02 · E04 — faltam 18 min”, selecionar a temporada correspondente e manter “Episódios” e “Assistir do início” como ações acessíveis. Tratar série concluída, temporadas especiais e episódios ausentes explicitamente.

O catálogo de séries também não agrega conclusão no objeto da série. O filtro “Não assistidos” verifica apenas o progresso desse objeto, portanto precisa de uma regra própria para séries. Referências: `scripts/brasa-server.mjs:597`; `feature/library/CatalogOrdering.kt:9`.

### 3. Separar continuidade, histórico e novidades de séries

**Estado atual:** a primeira fileira é “Assistidos recentemente”. Sua função inclui qualquer progresso maior que zero, inclusive conteúdos concluídos. Isso serve como histórico, mas não equivale a uma fila de retomada.

Referências: `scripts/brasa-server.mjs:477`; `server/recently-watched.mjs:1`.

**Proposta:** “Continuar assistindo” primeiro, com conteúdo pendente, um cartão por série e ação direta de retomada. Oferecer “Remover desta fileira” e “Marcar como assistido”, sem confundir remoção com avaliação negativa. Preservar “Assistidos recentemente” como histórico separado, mais abaixo ou numa área própria. Acrescentar “Novos episódios das suas séries” quando novos arquivos realmente estiverem disponíveis.

### 4. Completar os dados do catálogo

Leitura agregada dos arquivos locais, incluindo registros de catálogo que podem não estar reproduzíveis:

| Informação | Filmes, de 460 | Séries, de 11 |
| --- | ---: | ---: |
| Gênero preenchido | 167 | 11 |
| Sinopse preenchida | 164 | 11 |
| Classificação preenchida no título | 131 | 0 |
| Duração ou duração em minutos no título | 164 | 0 |
| Campo de elenco preenchido | 0 | 0 |
| Campo de direção preenchido | 0 | 0 |
| Campo de palavras-chave preenchido | 0 | 0 |

Fontes: `data/movies.js` e `data/series.js`, pelas funções exportadas de leitura. Ausência de duração ou classificação no objeto da série não descreve necessariamente seus episódios. Há 457 filmes com campo de vídeo preenchido; isso não comprova existência física ou disponibilidade atual dos arquivos.

293 filmes, aproximadamente 64%, não têm gênero preenchido. Um recomendador que se baseie apenas nesse campo tende a favorecer a parcela bem catalogada e a esconder o restante.

**Proposta:** melhorar identificação e enriquecimento de metadados, medir cobertura sobre o catálogo autorizado/reproduzível e preservar correções manuais. Usar duração numérica consistente; depois acrescentar elenco, direção, temas e franquias. Quando faltarem dados, usar sugestões variadas e rotulagem honesta, sem inventar afinidade. Confirmar a política de classificação infantil antes de ampliar recomendações nesse perfil.

### 5. Criar uma página inicial que aprenda com cada perfil

**Estado atual:** `tvHome` monta fileiras fixas de recentes, novidades, filmes, séries e favoritos. `HomeScreen` mistura seus itens, elimina repetições e embaralha o destaque. Não há ordenação por preferência nesse caminho.

Referências: `scripts/brasa-server.mjs:477`; `feature/home/HomeScreen.kt:89`; `data/repository/BrasaRepository.kt:179`.

**Proposta inicial, calculada no computador servidor:** combinar “Gostei”, consumo significativo, conclusão, favoritos e metadados dos títulos. Preferências recentes podem pesar mais, mantendo alguma memória do gosto antigo. Acrescentar “Não é para mim” e “Ocultar sugestão”, com significados distintos.

O progresso atual é a última posição de reprodução, não o total efetivamente assistido: avançar até o final não significa gostar do filme. `tvSaveProgress` atualiza o mapa de progresso, mas não alimenta um histórico de sessões. Para aprender melhor, registrar eventos locais compactos de início, tempo efetivamente reproduzido, conclusão e avaliação, com deduplicação. Uma falha de rede, pausa ou simples passagem de foco não deve virar rejeição.

Referência: `scripts/brasa-server.mjs:587`; `app/BrasaViewModel.kt:308`.

Fileiras sugeridas:

1. Continuar assistindo.
2. Para você.
3. Novos episódios das suas séries.
4. Porque você gostou de [título].
5. Novidades que combinam com você.
6. Minha lista.
7. Explore algo diferente.

Essa é uma hierarquia proposta, não sete fileiras obrigatórias: esconder as vazias, limitar repetições e variar as explicações conforme a evidência. Dentro da sessão, manter posições estáveis enquanto a pessoa navega. Atualizar a seleção no retorno apropriado ou em nova sessão.

Para perfis novos, oferecer escolha opcional de alguns títulos ou gêneros e uma seleção diversificada se a pessoa pular. Para catálogo pequeno, reduzir fileiras em vez de repetir os mesmos filmes. Nunca rotular nota pública como probabilidade pessoal de gostar; não mostrar “98% compatível” sem calibração.

A Netflix descreve a personalização da escolha das fileiras, dos títulos e da sua ordem, usando interações e informações do catálogo. O BRasa pode aproveitar esse princípio numa escala doméstica, com regras transparentes e cálculo local. [Explicação oficial da Netflix](https://help.netflix.com/pt/node/100639).

### 6. Melhorar legibilidade e previsibilidade na TV

**Destaque estável:** a troca automática acontece a cada 12 segundos sem condição de foco. O título e a ação podem mudar enquanto o usuário está decidindo. Preferir destaque estável durante a sessão ou pausar a rotação enquanto ele estiver em foco; se houver movimento, oferecer opção de reduzi-lo. Referência: `feature/home/HomeScreen.kt:101`.

**Mais conteúdo útil na primeira tela:** o destaque ocupa 430 dp. Experimentar uma composição mais compacta que mostre a primeira fileira com clareza, preservando imagem e título. O tamanho ideal exige conferir a resolução lógica, escala e distância da TV, não apenas pixels de uma captura. Referência: `feature/home/HomeScreen.kt:125`.

**Grade confortável:** filmes usam oito colunas fixas e textos compactos de 13/11 sp; essa grade não altera a quantidade de colunas pela preferência de densidade. Oferecer “Confortável” e “Compacta”, ajustando também colunas e tipografia. Referências: `feature/library/LibraryScreen.kt:132`; `designsystem/Components.kt:255`.

**Informação útil no cartão:** episódio atual, tempo restante, “Assistido”, “Novo episódio” e indicação de estar na lista. Nos detalhes, acrescentar classificação, sinopse expandível e títulos semelhantes. O campo de classificação existe no contrato, mas o resumo `metadata` mostra somente ano, nota e duração. Evitar excesso de selos.

**Acesso à lista:** dar à “Minha lista” um destino próprio no menu. Hoje ela fica como fileira inferior e o botão dos detalhes é exclusivo de filmes. Suporte a séries exige mudar também servidor e contrato: `getTvMediaItem` atualmente resolve apenas filme/episódio. Referências: `feature/details/DetailsScreen.kt:136`; `scripts/brasa-server.mjs:592`.

**Controle remoto:** preservar foco ao retornar, evitar reordenar o cartão selecionado e verificar que menus, busca e ações sejam alcançáveis apenas com setas, OK e Voltar. Eficiência e foco previsível são princípios oficiais do Android TV. [Navegação no Android TV](https://developer.android.com/training/tv/get-started/navigation).

### 7. Fazer a busca funcionar com menos digitação

**Estado atual:** consulta por trecho de título, título original, sinopse e gênero. O servidor ignora caixa, mas não normaliza acentos. A tela sem consulta e sem filtro começa sem resultados; não há integração explícita de busca por voz no código analisado. O teclado do aparelho pode oferecer ditado por conta própria.

Referências: `scripts/brasa-server.mjs:497`; `feature/search/SearchScreen.kt:67`.

**Proposta:** aceitar “acao”/“ação” e pequenos erros; priorizar correspondência exata de título e depois prefixo; agrupar episódios sob a série; mostrar buscas recentes e sugestões antes de digitar. Distinguir carregando, falha e nenhum resultado. Depois integrar voz onde disponível, mantendo teclado e gêneros como alternativas. Busca por ator/diretor depende do enriquecimento de dados descrito acima.

### 8. Dar continuidade à experiência ao terminar um título

Já existe um painel de “Próximo episódio” ao término, com ação manual. Referência: `feature/player/PlayerScreen.kt:654`.

**Proposta:** reprodução automática opcional por perfil, com contagem regressiva cancelável e foco explícito. Ao terminar um filme, apresentar poucas sugestões relacionadas e um “Gostei/Não é para mim” dispensável. “Pular abertura” e antecipar próximo episódio nos créditos devem vir somente após existirem marcações confiáveis; um tempo fixo universal pode cortar conteúdo.

### 9. Abrir e atualizar a biblioteca com mais fluidez

Existe armazenamento de uma cópia da página inicial e um método `cachedHome`, mas não foi encontrada chamada desse método no fluxo principal. O arquivo de cache guarda apenas uma combinação servidor/perfil de cada vez. A página inicial busca também o catálogo completo, e o servidor monta sua resposta a partir dele.

Referências: `data/repository/BrasaRepository.kt:93`; `data/storage/TvCacheStore.kt:15`; `app/navigation/BrasaNavHost.kt:34`.

**Proposta:** após autorização do perfil, mostrar a última página válida desse servidor/perfil e atualizar em segundo plano, preservando foco. Exibir estado de reconexão e não prometer reprodução offline. Guardar caches separados por perfil, com validade e invalidação em troca de autorização. Medir tempo de abertura e tamanho das respostas antes de introduzir paginação; futuramente, carregar temporadas e detalhes sob demanda.

## Implementação recomendada

| Etapa | Entrega | Condição de conclusão |
| --- | --- | --- |
| 1 — Confiança e continuidade | Favoritos persistentes, episódio correto, fila de retomada e lista de séries | Adicionar/reabrir mantém a lista; concluir E03 oferece E04; trocar perfil não mistura estado |
| 2 — Dados e preferência | Metadados melhores, avaliações simples e eventos locais | Sinais explícitos persistem; falha técnica não vira desinteresse; cobertura do catálogo é conhecida |
| 3 — Descoberta personalizada | Para você, motivos, novidade relevante e diversidade | Perfis com gostos diferentes recebem ordens diferentes; ocultar título funciona; catálogo sem histórico continua útil |
| 4 — Acabamento da TV | Grade confortável, busca melhor, destaque estável, cache e pós-reprodução | Navegação completa pelo controle, texto legível do sofá e sem regressão de reprodução |

Recomendo concentrar o cálculo de afinidade num módulo separado do servidor e aproveitar o contrato de fileiras já existente. Acrescentar campos opcionais para motivo e destino de reprodução, com compatibilidade para APKs antigos. Atualizar também a montagem alternativa de página inicial no repositório Android, evitando regras contraditórias entre cliente e servidor. Não há necessidade inicial de API de IA, treinamento pesado ou dados de outros usuários.

Sugestões devem começar pelo acervo que o perfil pode assistir. “Novos títulos” fora da biblioteca seria um recurso separado de descoberta, com indicação inequívoca de indisponibilidade; não implica baixar ou adquirir conteúdo automaticamente.

## Como medir se a experiência melhorou

- Tempo entre abrir a página inicial e começar a assistir; separar tempo de decisão de tempo de carregamento do vídeo.
- Quantidade de ações para retomar uma série. Meta inicial proposta: no máximo duas ativações de OK a partir do cartão de continuidade.
- Retomada correta, persistência de favoritos e retorno ao mesmo cartão.
- Sugestões abertas que levam a consumo significativo e avaliações positivas; não otimizar só cliques ou horas assistidas.
- Variedade de títulos descobertos, repetições nas fileiras e uso de “Não é para mim”.

Registrar localmente apenas o necessário, por perfil, com opção de limpar/reiniciar personalização na TV. Em uso doméstico, comparar sessões e recolher percepção do usuário é mais útil do que prometer resultados estatísticos com poucos acessos. Tempos e taxas atuais não foram medidos nesta avaliação.

## Verificação executada e limites

Cinco scripts existentes passaram: contrato Android TV, API Android TV, regressões estruturais da interface, recém-adicionados e perfis. O contrato normalizou 471 títulos e seus episódios. O teste de perfis passou mesmo com a inconsistência de favoritos: seu caso usa IDs antigos, sem dois-pontos.

Também foram executadas leitura agregada do catálogo e reprodução isolada da normalização de favoritos. Não houve escrita em perfis, sincronização de biblioteca ou acesso a senhas.

Parte dos testes de regressão verifica texto do código, incluindo a presença de embaralhamento e rotação do destaque; isso não prova qualidade de UX. Esses testes precisarão acompanhar a mudança de comportamento. Existem testes Kotlin de unidade e interface simulada, mas eles não foram reexecutados, e não foi feita compilação ou sessão com controle remoto físico nesta avaliação.

Para uma futura entrega, verificar na TV: retorno após rolagem, troca de perfil, série parcialmente assistida, série concluída, imagens ausentes, busca sem acento, cancelamento do próximo episódio e estabilidade do foco durante atualização da página. Manter verificações de reprodução direta/HLS, áudio, legendas e retomada nas mudanças que tocarem esses fluxos.

Somente este relatório foi criado. As modificações preexistentes em `data/movies.js` e `data/series.js` foram preservadas.
