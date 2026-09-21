# Avaliação do BRasa com prioridade em TV

Data: 21/09/2026. Código local: Android TV 1.0.35, versionCode 36.

## Parecer

O BRasa tem uma base funcional ampla para TV. O próximo investimento deve concentrar-se em previsibilidade do controle remoto, preservação de qualidade de vídeo, isolamento dos estados de navegação e reprodução e confiabilidade das autorizações. Uma reescrita geral não se justifica pela análise realizada.

Há recursos importantes já implementados: aplicativo Kotlin/Compose for TV, Media3, perfis, continuidade, recomendações locais, Minha lista, busca com tolerância, voz condicional, atualização privada com verificação de APK, cache, fila persistente de progresso, diagnóstico de rede, histórico de reprodução, miniaturas no avanço e próximo episódio automático. Pular abertura/créditos também já existe quando há capítulos explícitos confiáveis.

A revisão incluiu código Android e servidor, testes automatizados e capturas existentes da TCL. As capturas são de 14/09/2026; não fiz uma nova sessão de uso na TV nem medi reprodução real nesta avaliação. As alterações locais preexistentes foram preservadas. Somente este relatório foi acrescentado.

## Validação realizada

- Servidor/interface: **45/45 suítes aprovadas**.
- Android: **88/89 testes locais passaram**. Um falhou antes da execução da lógica, no carregamento nativo do Robolectric: caminho com espaço convertido em `pc%20mario`. Isso impede declarar a suíte completa aprovada, mas não demonstra falha do player.
- Android lint: **0 erros, 51 avisos e 1 indicação**. Os avisos incluem manutenção/dependências e uma consulta de disponibilidade da busca por voz sem declaração de visibilidade.
- O SDK foi localizado na pasta de ferramentas do próprio projeto e usado apenas no processo de validação.
- Catálogo atual: 460 filmes e 8 séries. Há 82 filmes/séries com gênero “Ação” e 1 com “Acao”; a duplicidade é real.
- Teste isolado de concorrência em dispositivos reproduziu uma revogação sendo desfeita. Usou dados temporários e uma função criptográfica simulada para controlar a ordem das operações; não acessou dispositivos reais. O defeito comprovado está na persistência, não na criptografia.

## Corrigir primeiro

### 1. Revogação de aparelho pode ser desfeita por outra requisição — alta

**Comprovado em reprodução isolada.** O armazenamento põe a escrita em fila, mas a leitura e alteração acontecem fora dela. Uma autenticação pode ler o aparelho ativo, aguardar a verificação do token, enquanto o administrador revoga o aparelho; depois, a autenticação salva seu estado antigo ao atualizar “último acesso” e restaura `revoked=false`.

Resultado observado: revogado antes de concluir a autenticação = verdadeiro; depois = falso. O mesmo padrão exige revisar mudanças de perfis permitidos e cadastro/remoção concorrentes.

**Melhoria:** serializar a transação inteira de leitura–alteração–gravação, revalidar autorização depois das operações assíncronas e permitir que a fila se recupere de falhas. Testar autenticação simultânea com revogação e edição de permissões.

**Aceite:** revogar sempre prevalece sobre atualizações de presença; falhas de disco não inutilizam todas as gravações seguintes.

Evidência: [device-store.mjs](<C:/Users/pc mario/Pictures/brasa-main/server/device-store.mjs:4>).

### 2. PIN não constitui uma autorização de perfil no servidor — alta

**Confirmado pela leitura do fluxo.** O aplicativo pede o PIN na interface. A rota de verificação devolve apenas verdadeiro/falso; as rotas de catálogo e reprodução verificam se o dispositivo tem permissão para aquele perfil, sem exigir uma sessão de desbloqueio obtida pelo PIN.

Consequência: um cliente já pareado e autorizado ao perfil pode consultar suas rotas diretamente sem ter passado pelo desbloqueio. Isso não significa acesso de um aparelho não pareado, nem prova que a navegação normal da criança contorne o PIN. Significa que o controle parental depende do cliente.

**Melhoria:** desbloqueio temporário vinculado a dispositivo e perfil, validado também no servidor para catálogo/reprodução/stream; expiração e limitação de tentativas. Aplicar a regra de forma consistente no HLS, que hoje verifica se algum perfil permitido ao dispositivo pode acessar a mídia.

**Aceite:** perfil protegido permanece indisponível na API até o PIN correto, inclusive para cliente pareado; trocar para perfil infantil encerra a autorização conforme política definida.

Evidências: [device-controller.mjs](<C:/Users/pc mario/Pictures/brasa-main/server/device-controller.mjs:59>), [catálogo e reprodução](<C:/Users/pc mario/Pictures/brasa-main/scripts/brasa-server.mjs:486>) e [tvVerifyPin](<C:/Users/pc mario/Pictures/brasa-main/scripts/brasa-server.mjs:640>).

### 3. Detalhes e player usam o mesmo item selecionado — alta

**Defeito identificável no fluxo de estado; falta teste de ponta a ponta em aparelho.** A tela de detalhes lê `state.selected`. Iniciar um episódio substitui esse campo pelo episódio. Ao sair do player, a navegação apenas volta à tela anterior, que agora recebe o episódio em lugar da série com suas temporadas. “Títulos semelhantes” também inicia reprodução e substitui o item que alimentava os detalhes.

**Melhoria:** separar o título dos detalhes da mídia em reprodução; passar IDs nas rotas; preservar série, temporada, posição e cartão em foco. Atualizar progresso ao retornar sem substituir a identidade da página.

**Aceite:** série → episódio → Voltar retorna à mesma série e temporada; assistir a título semelhante não altera a página de origem.

Evidências: [DetailsScreen.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/details/DetailsScreen.kt:83>), [BrasaViewModel.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/app/BrasaViewModel.kt:246>) e [BrasaNavHost.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/app/navigation/BrasaNavHost.kt>).

### 4. Detalhes podem cortar ações e sobrepor informações — alta

**Observado nas capturas existentes; estrutura problemática permanece no código.** O topo reserva 470 dp para série, com coluna central de largura fixa, sinopse expansível e várias linhas de ações. A captura mostra ano/nota invadindo Voltar e “Assistir do início” reduzido visualmente a “Assistir”.

**Melhoria:** cabeçalho separado; altura acompanhando o conteúdo; “Continuar” e “Episódios” como ações principais; avaliações e ações secundárias em “Mais opções”. Mostrar episódio e tempo restante próximos ao botão, sem alongar excessivamente seu rótulo.

**Aceite:** nenhum corte nas escalas 80/90/100/110%, inclusive título longo e sinopse expandida; todas as ações alcançáveis pelas setas.

Evidências: [DetailsScreen.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/details/DetailsScreen.kt:113>) e [captura dos detalhes](<C:/Users/pc mario/Pictures/brasa-main/preview/android-tv/1.0.35/03-detalhes-serie.png>).

### 5. Foco de entrada e destino de “Início” precisam ser previsíveis — alta

**Capturas existentes + código.** Busca e outras seções entram com foco em “Início”; a memória de foco ajuda na volta, mas não define a primeira ação de cada tela. Na busca, o botão chamado “Início” está ligado a `onBack`: se a busca foi aberta a partir de Filmes, ele volta a Filmes.

**Melhoria:** ao abrir Busca, focar o campo sem abrir o teclado até OK; definir entrada no conteúdo para biblioteca/configurações; “Início” sempre abrir a página inicial. Centralizar navegação entre seções para evitar empilhar destinos repetidos.

**Aceite:** cada botão leva ao destino que seu nome promete; abrir Busca e pressionar OK permite pesquisar; retorno de detalhes conserva posição.

Evidências: [SearchScreen.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/search/SearchScreen.kt:103>), [CatalogFocusMemory.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/designsystem/CatalogFocusMemory.kt>) e [captura da busca](<C:/Users/pc mario/Pictures/brasa-main/preview/android-tv/1.0.35/15-busca.png>). A orientação oficial também enfatiza navegação completa e previsível pelo [controle direcional](https://developer.android.com/training/tv/get-started/navigation).

## Melhorar reprodução e desempenho

### 6. A política de estabilidade pode converter demais — alta para qualidade de imagem

**Comportamento confirmado, com intenção conservadora explícita no código.** `stabilizeTvPlaybackPlan` converte todo plano de remux em transcodificação. Retomadas a partir de 5 segundos também convertem reprodução direta quando a fonte não é H.264 SDR de até 8 bits. Portanto, uma fonte HEVC/HDR compatível pode tocar original do começo e ser convertida ao continuar.

A escada adaptativa de transcodificação limita a saída a 720p em CPU e 1080p com encoder acelerado. O caminho HDR convertido faz tone mapping para SDR. Isso pode aumentar espera, ocupar o computador e reduzir nitidez/HDR.

**Melhoria:** manter o caminho conservador como recuperação; validar reprodução original e remux por combinação de aparelho/formato/arquivo antes de liberar os casos comprovadamente seguros. Explicar na TV “Original”, “Convertido para compatibilidade” ou “Reduzido por estabilidade”, com detalhes técnicos opcionais.

**Aceite:** arquivos compatíveis mantêm qualidade ao retomar quando validado; regressões de decoder continuam com recuperação automática. Comparar início, retomada e avanço em H.264, HEVC, HDR10 e Dolby Vision antes de alterar a política.

Evidências: [stabilizeTvPlaybackPlan](<C:/Users/pc mario/Pictures/brasa-main/server/transcoding-profiles.mjs:90>), [limites adaptativos](<C:/Users/pc mario/Pictures/brasa-main/server/transcoding-profiles.mjs:146>) e [uso efetivo do plano](<C:/Users/pc mario/Pictures/brasa-main/scripts/brasa-server.mjs:573>).

### 7. Capacidade de vídeo pode ficar limitada pela resolução da interface — média/alta

**Risco dependente do aparelho.** A detecção informa `maxWidth/maxHeight` a partir de `resources.displayMetrics`, embora também detecte limites dos decoders. Se a interface do aparelho expuser 1920×1080 num televisor capaz de vídeo 4K, o servidor recebe um teto global de 1080p e pode rejeitar reprodução original.

**Melhoria:** distinguir resolução da interface, modos físicos da tela e capacidades de vídeo. Validar a combinação real de resolução, taxa de quadros, perfil e HDR; não simplesmente escolher todos os máximos isoladamente. A API [Display.Mode](https://developer.android.com/reference/android/view/Display.Mode) oferece as dimensões físicas de cada modo.

**Aceite:** TV 4K com interface 1080p não perde a capacidade de reprodução 4K; uma TV limitada continua protegida de formatos excessivos.

Evidência: [PlaybackCapabilityDetector.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/core/playback/PlaybackCapabilityDetector.kt:23>).

### 8. Falta um estado de reconexão mais claro fora do player — média

**Confirmado no fluxo de abertura.** `restore()` transforma falhas de conexão, autorização e outras exceções em ausência de servidor restaurado. O app pode devolver o usuário pareado à tela de encontrar computador, mesmo quando o PC apenas está desligado ou a rede está indisponível. Em Configurações, “Conectado” depende de `paired`, que representa pareamento, não uma checagem recente.

**Melhoria:** estados distintos para PC desligado/indisponível, sem rede, endereço alterado, autorização revogada e servidor incompatível; botão “Tentar novamente” com servidor salvo visível; redescoberta com identidade confiável do servidor.

**Aceite:** desligar e religar o PC não exige um novo pareamento; interface não informa “Conectado” apenas por existir token.

Evidências: [BrasaRepository.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/data/repository/BrasaRepository.kt:51>) e [SettingsScreen.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/settings/SettingsScreen.kt:80>).

### 9. Catálogo completo e autenticação merecem medição de carga — média

A biblioteca transfere filmes, séries, episódios e coleções juntos, e várias entradas de tela fazem nova consulta. A busca no servidor constrói o catálogo do perfil. A autenticação de cada requisição lê o arquivo de dispositivos e deriva hashes até encontrar o token; segmentos HLS passam por esse caminho.

Não medi lentidão real nesta revisão. São pontos de crescimento a medir com biblioteca maior e duas TVs simultâneas, antes de introduzir otimizações.

**Melhoria:** catálogo paginado/detalhes sob demanda, revisão de catálogo para respostas condicionais, deduplicação de requisições e autenticação otimizada com invalidação imediata de revogação. Evitar resolver desempenho sacrificando permissões.

Evidências: [catálogo/home/busca](<C:/Users/pc mario/Pictures/brasa-main/scripts/brasa-server.mjs:486>), [BrasaRepository.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/data/repository/BrasaRepository.kt:122>) e [device-store.mjs](<C:/Users/pc mario/Pictures/brasa-main/server/device-store.mjs:6>).

## Refinamentos visíveis no uso diário

| Melhoria | Situação atual e proposta |
| --- | --- |
| Gêneros consistentes | “Ação” e “Acao” têm identidades distintas. Normalizar na origem e usar o mesmo identificador na exibição, busca e filtro. Traduzir categorias compostas sem fundir conceitos diferentes. |
| Episódios com estado visível | O cartão de episódio mostra imagem, EP, título e sinopse, mas não mostra barra de progresso/assistido. Acrescentar estado, tempo restante e destaque do episódio de continuidade. |
| Busca mais útil na primeira tela | Cabeçalho/filtros ocupam muita altura e os títulos dos cards ficam abaixo da tela capturada. Reservar espaço para os resultados; largura do campo adaptável; histórico horizontal rolável. |
| Voz detectada corretamente | A consulta `resolveActivity` não tem declaração correspondente de visibilidade no manifesto; lint confirma o aviso. Pode indicar indisponibilidade em aparelhos com reconhecedor. Declarar a consulta do intent realmente utilizado e tratar falha ao abrir. A documentação explica os efeitos da [visibilidade de aplicativos](https://developer.android.com/training/package-visibility/use-cases). |
| Textos legíveis à distância | Cards compactos usam título 13 sp e metadados 11 sp, sujeitos à escala de interface. Validar a 2–3 metros e dar mais espaço/informação ao item em foco. |
| Configurações mais rápidas | Já existem títulos de seção, mas em uma coluna longa. Criar navegação por categorias e estados “Ativado/Desativado” explícitos. |
| Legendas compreensíveis | Rótulo combina idioma traduzido com nome original da faixa, gerando repetições. Usar “Português (Brasil)” e indicadores de acessibilidade/trechos estrangeiros somente quando sustentados pelos metadados. |
| Identificação no player | Para episódios, exibir série + temporada/episódio, além do título. Deixar buffer e detalhes técnicos em painel opcional. |
| Ícones e perfil protegido | Substituir símbolos Unicode dependentes da fonte por vetores; colocar selo PIN fora do recorte do avatar. |

Evidências: [filtros da biblioteca](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/library/LibraryScreen.kt:71>), [busca e voz](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/search/SearchScreen.kt:82>), [EpisodeCard](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/details/DetailsScreen.kt:224>), [tipografia dos cards](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/designsystem/Components.kt:267>), [rótulos de faixas](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/player/TrackSelection.kt:23>) e [ProfileScreen.kt](<C:/Users/pc mario/Pictures/brasa-main/apps/android-tv/app/src/main/java/com/brasa/tv/feature/profiles/ProfileScreen.kt:115>).

## Recursos que ainda faltam ou podem ser ampliados

Prioridade sugerida pelo benefício no uso doméstico da TV; não são todos defeitos.

1. **Ajuste de sincronização de legendas**, salvo por mídia, com avançar/atrasar em passos pequenos. Já há idioma, tamanho e estilo; o ajuste de atraso não foi encontrado.
2. **Editor de marcações de abertura/créditos no painel**, com prévia e validação. O player já usa capítulos confiáveis; falta uma forma de completar marcações nos arquivos que não os têm. Preservar pós-créditos e evitar tempos inventados.
3. **Ações de episódio:** marcar/desmarcar assistido por episódio e temporada e reiniciar episódio claramente. Há ações gerais nos detalhes, mas a gestão granular pode melhorar.
4. **Pareamento assistido por QR/celular** e, como evolução separada, envio de texto para busca pelo celular. Exige canal autenticado; não apenas uma página aberta na rede.
5. **Integração com a tela inicial do Android/Google TV**, com atalhos para continuar títulos e abertura direta por ID. Não encontrei integração Watch Next/deep links no manifesto/código analisado.
6. **Acessibilidade específica da interface:** descrição de botões com ícone, estados selecionados/ativados anunciáveis e contraste ajustável; validar com recursos de acessibilidade do aparelho.
7. **Diagnóstico de reprodução consolidado**, aproveitando os registros já existentes: tempo até primeiro quadro, pausas por hora, qualidade efetiva e motivo da conversão, agrupados por aparelho/formato.
8. **Validação de cadência do vídeo e saída de áudio**, especialmente filmes de 24 fps e soundbars. O Media3 já possui comportamento próprio de ajuste de taxa; ausência de uma chamada explícita não comprova ausência da funcionalidade. Testar antes de criar uma implementação paralela.

## Qualidade do código e prevenção de regressões

A divisão por funcionalidades no Android e os módulos específicos do servidor são bons pontos de partida. Entretanto, há concentração de responsabilidades: PlayerScreen tem cerca de 850 linhas, o servidor principal 1.255 e a sincronização 1.612. Alguns componentes relevantes estão comprimidos em poucas linhas muito extensas; device-store inteiro tem oito linhas.

Recomendo separar navegação, estado de detalhes, estado de reprodução e tratamento de erros; extrair coordenação do player sem mudar sua política durante a refatoração; formatar os módulos de autenticação/persistência. A melhoria deve acompanhar correções concretas, evitando uma reestruturação ampla sem benefício verificável.

Os testes existentes cobrem bastante lógica. Parte dos testes chamados de regressão visual apenas procura trechos no código: passar nesses testes não demonstra que um botão cabe na tela ou recebe foco. Existem testes Compose de restauração, mas faltam percursos completos pelas setas e checagens de layout nos cenários identificados.

Próximos testes úteis:

- autenticação simultânea com revogação/edição e falha de gravação;
- perfil protegido acessado pela API antes/depois de desbloqueio;
- série → detalhes → episódio → player → Voltar;
- abrir Busca, usar OK e retornar ao destino correto;
- detalhes com texto longo nas quatro escalas e duas densidades;
- foco ao trocar filtros, atualizar/remover item e trocar perfil;
- reprodução em TV real: 1080p/4K, HDR, retomada, legendas, perda de rede e retorno do standby;
- tornar o ambiente Android de testes reproduzível em diretórios com espaço.

A documentação também precisa acompanhar o código: o README principal descreve início HDR em 480p; o README Android fala em mínimo 1080p; a política efetiva atual usa escadas distintas por aceleração. O documento de personalização ainda diz que pular abertura não foi ativado, enquanto o código já o habilita para capítulos explícitos.

## Ordem sugerida de execução

| Etapa | Entrega | Condição de conclusão |
| --- | --- | --- |
| 1 — confiabilidade | Corrigir transações de dispositivos, desbloqueio de perfil e identidade de detalhes/player | Testes de concorrência/autorização e retorno de episódio aprovados |
| 2 — experiência TV | Detalhes sem cortes, foco de entrada, navegação consistente, gêneros e episódios com progresso | Fluxos completos no controle físico e nas quatro escalas |
| 3 — qualidade de reprodução | Revisar limites 4K e exceções seguras à conversão; melhorar mensagens de conexão | Comparação medida em arquivos/aparelhos reais, mantendo recuperação |
| 4 — conveniência | Atraso de legenda, edição de marcações e configurações por categoria | Recursos utilizáveis sem teclado/mouse no fluxo da TV |
| 5 — expansão | QR/celular, integração com tela inicial e otimizações de biblioteca | Benefício e custo medidos no uso doméstico |

A experiência de TV deve orientar a aceitação: abrir, escolher, assistir, pausar e voltar com poucos comandos, mantendo qualidade e o lugar em que a pessoa estava.

