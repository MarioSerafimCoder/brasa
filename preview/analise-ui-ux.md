# Oportunidades de UI e UX — BRasa Android TV

Análise de 14/09/2026 sobre o APK **1.0.35 (36)** na TCL, escala 90%, cards Confortável. [Galeria e limites da avaliação](README.md).

**Status: diagnóstico e propostas; nenhuma mudança de interface implementada nesta etapa.** Prioridade alta significa tratar primeiro na próxima rodada de UI, não uma falha crítica de reprodução.

## Resumo priorizado

| Prioridade | Oportunidade | Evidência | Próxima alteração proposta |
| --- | --- | --- | --- |
| Alta | Detalhes sem cortes ou sobreposições | Print 03: ano/nota invadem o botão Voltar; “Assistir do início” aparece somente como “Assistir” | Layout que acompanhe o conteúdo e ações principais mais curtas |
| Alta | Entrada de foco adequada à tarefa | Prints 14, 15 e 16: foco inicial em Início, mesmo após abrir outra seção | Busca focar o campo; biblioteca e configurações focarem o conteúdo pertinente |
| Alta | Gêneros consistentes | Prints 14/15: “Acao” e “Ação” coexistem; termos em inglês aparecem ao lado dos portugueses | Normalização canônica dos gêneros antes de listar e filtrar |
| Média | Perfis com indicadores íntegros | Print 01: selo PIN cortado e símbolo de encerrar ausente | Separar selo do recorte circular e usar ícones vetoriais |
| Média | Legendas com nomes compreensíveis | Print 08: idioma duplicado e mistura de “Forced”/“Forçada” | Rótulos curtos em português e indicação clara de acessibilidade |
| Média | Retomada evidente na lista de episódios | Print 04: cartões exibem EP, título e sinopse, mas não distinguem visualmente o estado assistido/retomada | Progresso, estado assistido e destaque do episódio a continuar |
| Média | Configurações com menor esforço de navegação | Prints 16/17: longa coluna, grandes botões e opções alternáveis sem texto explícito de estado | Categorias e controles com “Ativado/Desativado” |
| Média | Busca e biblioteca com mais informação útil à primeira vista | Print 15: títulos dos cards ficam abaixo da área visível; print 14: duração truncada | Ajustar proporções e hierarquia de metadados |
| Baixa | Player com mais contexto e menos informação técnica | Prints 05–08: só título do episódio, qualidade/buffer e tempos de prévia distintos | Nome da série + T/E; detalhes técnicos opcionais; prévia vinculada à posição escolhida |

## 1. Detalhes da série: correção de layout antes de redesenhar

No [print 03](android-tv/1.0.35/03-detalhes-serie.png), ano e nota se sobrepõem à área de Voltar. A ação de reiniciar perde a parte que a distingue de continuar, aumentando o risco de escolher a ação errada. Não é apenas preferência estética.

O [DetailsScreen](../apps/android-tv/app/src/main/java/com/brasa/tv/feature/details/DetailsScreen.kt#L113) combina altura fixa (470 dp para séries), coluna centralizada de largura 680 dp, sinopse e várias linhas condicionais. É uma causa provável da pressão de espaço observada; a correção deve ser validada no aparelho, não apenas deduzida do código.

Proposta: separar cabeçalho/Voltar do fluxo do conteúdo; manter “Continuar”, “Episódios” e “Mais opções” como hierarquia a testar. Deixar temporada, episódio e tempo restante próximos ao botão, sem exigir que tudo faça parte de seu texto. Agrupar avaliações e ações de histórico em “Mais opções”, sem esconder a ação principal. A sinopse expandida pode abrir um painel próprio.

Aceite futuro: nenhum texto sobreposto e nenhum rótulo de ação ambíguo nas escalas 80/90/100/110%, com títulos longos, sinopse expandida e diferentes estados de progresso/favoritos. Todas as ações secundárias devem continuar alcançáveis pelo controle.

## 2. Foco e navegação entre seções

Na abertura capturada de [Filmes](android-tv/1.0.35/14-filmes.png), [Busca](android-tv/1.0.35/15-busca.png) e [Configurações](android-tv/1.0.35/16-configuracoes.png), “Início” recebe a borda de foco enquanto a seção aberta tem apenas o fundo de seleção. Em Busca, pressionar OK imediatamente tende a retornar ao início em vez de começar a pesquisar. O cabeçalho também muda os atalhos disponíveis entre telas.

Proposta: definir foco de entrada por tarefa, distinguir visualmente página ativa de elemento em foco e manter caminhos previsíveis entre conteúdo e cabeçalho. Na volta de detalhes, preservar o cartão já selecionado e a posição da lista; isso já tem infraestrutura no projeto e deve ser preservado, não refeito sem necessidade.

A recomendação de caminhos curtos e previsíveis segue a orientação oficial de [navegação na TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv). Os estados de selecionado e focado devem continuar distinguíveis, conforme o [sistema de foco do Android TV](https://developer.android.com/design/ui/tv/guides/styles/focus-system).

Aceite futuro: uma única indicação de foco inequívoca, OK executando a tarefa esperada ao abrir cada seção e retorno ao mesmo item após detalhe/atualização do catálogo. A navegação desta sessão não comprova todos os cenários de restauração.

## 3. Biblioteca e busca: normalizar dados e usar melhor o espaço

Os filtros repetem “Acao” e “Ação”; a Busca inclui “Action & Adventure”. A [LibraryScreen](../apps/android-tv/app/src/main/java/com/brasa/tv/feature/library/LibraryScreen.kt#L71) e a [SearchScreen](../apps/android-tv/app/src/main/java/com/brasa/tv/feature/search/SearchScreen.kt) deduplicam textos literais, não conceitos. Normalizar apenas o rótulo visível seria insuficiente: a filtragem precisa usar a mesma identidade canônica, sem unir gêneros semanticamente diferentes por engano.

No print 15, os cabeçalhos e filtros ocupam a maior parte da metade superior, enquanto os pôsteres excedem a altura restante e seus títulos ficam abaixo da tela. No print 14, há boa densidade de seis pôsteres, mas durações ficam truncadas e títulos em uma/duas linhas geram alturas diferentes.

Proposta: preservar legibilidade, reduzir a área do cabeçalho da busca, alinhar alturas dos cards e reservar espaço previsível para título/ano/duração. No campo de busca, substituir largura fixa por espaço disponível. A largura fixa de 720 dp mais o botão Limpar e a busca por voz é um risco a testar em outras escalas; **não houve corte desses controles no estado vazio capturado**. Testar também buscas recentes extensas, teclado aberto e ausência de microfone.

Aceite futuro: um filtro por gênero canônico; títulos e metadados essenciais legíveis no cartão em foco; nenhuma ação da busca perdida ao digitar ou aumentar a escala.

## 4. Perfis: corrigir o selo e o ícone

O [print 01](android-tv/1.0.35/01-perfis.png) confirma o recorte do PIN no avatar e o quadrado de caractere ausente em Encerrar aplicativo. O [ProfileScreen](../apps/android-tv/app/src/main/java/com/brasa/tv/feature/profiles/ProfileScreen.kt#L115) coloca o selo dentro da área circular com sombra/recorte; o ícone de encerrar é um caractere Unicode dependente da fonte.

Proposta: avatar recortado em uma camada própria, selo fora desse recorte e ícone vetorial com descrição acessível. Preservar o foco forte, os nomes legíveis e a indicação KIDS já presentes.

Aceite futuro: selo inteiro em repouso e em foco, ícone desenhado corretamente na TCL e em outro aparelho, descrição de perfil protegido acessível sem expor o PIN.

## 5. Legendas e player: clareza sem aumentar a quantidade de controles

O [menu de legendas](android-tv/1.0.35/08-player-legendas.png) oferece várias faixas, mas repete idiomas e termos técnicos. O [TrackSelection](../apps/android-tv/app/src/main/java/com/brasa/tv/feature/player/TrackSelection.kt) compõe partes do idioma, nome e indicadores; normalizá-las evitaria “português · Portuguese (Brazil)” e “[Forced] · Forçada”.

Proposta de rótulos: “Português (Brasil)”, “Português (Brasil) · Acessibilidade” e “Português (Brasil) · Apenas trechos estrangeiros”, quando os metadados sustentarem essa distinção. Não inferir características que a faixa não informa. Mostrar explicitamente a faixa atual e testar foco inicial nela, preservando fácil acesso a “Sem legenda”.

Na [barra de tempo](android-tv/1.0.35/07-player-miniatura.png), a orientação de setas/OK já é clara. A posição escolhida é 04:06 e a miniatura corresponde a 04:00. Essa diferença é compatível com uma prévia aproximada, não prova de erro de seek. Proposta: aproximar miniatura/tempo do ponto selecionado e explicitar que a imagem é aproximada. No título, acrescentar série e T/E; mover o buffer técnico para detalhes opcionais e usar mensagens de estado simples.

O fundo preto dos prints **não é um achado de UI ou reprodução**: a imagem aparecia na TV, conforme confirmação do usuário. Houve tentativas intermediárias de navegação com controles ocultos; a captura não determina sozinha a causa nem comprova falha da tecla Voltar. Validar separadamente Voltar/OK/Pausar com controle físico, controles ocultos e menu aberto antes de propor alterações no tratamento de teclas.

## 6. Episódios e início: facilitar continuar assistindo

Os [cartões de episódios](android-tv/1.0.35/04-detalhes-navegacao.png) têm bom foco e sinopses legíveis, mas não deixam evidente qual já foi assistido e qual deve continuar. Proposta: barra de progresso e selo de estado, mantendo título e EP como informação principal. Ao entrar em episódios pela primeira vez, avaliar foco no episódio de retomada; depois preservar a escolha do usuário.

No [início](android-tv/1.0.35/02-inicio.png), o destaque ajuda a retomar rapidamente, mas ocupa boa parte da tela. Uma variante mais compacta poderia mostrar mais catálogo sem rolagem. É **hipótese de design**, não defeito confirmado: comparar lado a lado antes de reduzir imagens ou fontes. Não remover a ação de continuar nem a informação de tempo restante.

## 7. Configurações: categorias e estado explícito

Os [prints 16](android-tv/1.0.35/16-configuracoes.png) e [17](android-tv/1.0.35/17-configuracoes-interface.png) mostram uma coluna longa com ações de manutenção e preferências. “Priorizar estabilidade” e “Próximo episódio automático” desligados não dizem “Desativado”; a distinção depende do estilo e da ausência de um sinal de seleção. A escala escolhida (90%, laranja) e a escala em foco (110%, branco) também pedem uma indicação textual adicional.

Proposta: separar Reprodução, Aparência e Manutenção; usar linhas com nome, estado atual e descrição curta; reservar destaque forte para a ação pertinente. Preservar a explicação honesta de estabilidade: pode reduzir nitidez, usar conversão e não evita perda total da conexão. Separar ações de apagar dados das opções de conforto e validar confirmações onde necessário.

Aceite futuro: identificar o estado de cada preferência sem depender somente de cor; alcançar Reprodução/Aparência por caminho curto; não mudar uma opção ao apenas percorrer o foco.

## Ordem recomendada para a próxima etapa

1. Corrigir sobreposição, rótulos cortados, PIN e ícone; criar testes visuais para esses estados.
2. Ajustar foco inicial e normalização de gêneros; verificar ida/volta com controle físico.
3. Simplificar detalhes, busca e configurações; comparar propostas visuais antes de implementar mudanças amplas.
4. Refinar identificação de episódios, rótulos de faixas e contexto da miniatura.
5. Repetir capturas na TCL e em outra TV, com escalas diferentes e leitura à distância de uso.

Os 45 conjuntos de testes automatizados passaram nesta sessão, mas os defeitos visuais acima permanecem reproduzidos nas imagens. A próxima rodada deve combinar esses testes com comparação de telas e tarefas reais no controle remoto; não há ganho medido de UX ou estabilidade a declarar nesta entrega documental.
