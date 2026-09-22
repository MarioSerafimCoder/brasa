# Revisão das melhorias de TV — 21/09/2026

Revisão do commit f5dbdad (1.0.36), com correções publicadas na versão 1.0.37 (38).

## Entregas do dia

- Detalhes e player com estados separados; preservação de temporada, episódio e posição ao voltar.
- Preservação de vídeo original/remux na retomada; separação entre resolução física e resolução da interface; recuperação após incompatibilidade real.
- Mensagens distintas para falha de rede, servidor indisponível e autorização revogada.
- Sincronização de legendas por mídia/perfil/aparelho e editor de abertura/créditos no painel.
- Detalhes com ações secundárias em Mais opções, busca compacta e Configurações por categorias.
- Gêneros normalizados, progresso nos episódios, ícones vetoriais, selo PIN e textos mais legíveis.
- Identificação de série/temporada/episódio e painel técnico opcional no player.

## Problemas encontrados e corrigidos na revisão

- O foco inicial dos detalhes era solicitado antes da criação do botão; o estado era marcado como concluído mesmo após falha. Agora espera a composição e só registra sucesso quando o foco é aceito.
- O teste anterior forçava o foco manualmente antes de navegar, ocultando a falha de entrada. Agora valida a entrada automática e o retorno de Mais opções.
- A busca tinha Início associado a Voltar. Agora possui destino próprio e entrada no campo, sem abrir o teclado até OK.
- O painel técnico podia sumir após quatro segundos e concorrer com os controles. Agora permanece aberto, tem foco próprio, conteúdo rolável e devolve foco a Informações ao fechar.
- Episódios concluídos podiam exibir minutos restantes; o número também podia permanecer desatualizado após reprodução. Agora respeita o estado assistido, recalcula a partir do progresso e arredonda frações de minuto para cima.
- Uma série totalmente assistida podia deixar de oferecer episódio reproduzível; Assistir novamente passa a resolver o primeiro episódio disponível. A continuidade respeita também o indicador explícito de conclusão.
- Configurações redefine a rolagem ao trocar categoria e define o destino da seta direita para a primeira preferência.
- Ícones de seleção restantes no menu de faixas e no filtro de não assistidos foram convertidos para vetores.
- A validação Windows ganhou um comando que localiza SDK/JDK e prepara runtimes de testes fora de caminhos com espaços. Documentação de HLS e abertura/créditos foi alinhada ao código.

## Como validar

Na raiz: `node scripts/run-tests.mjs`.

Em `apps/android-tv`: `./scripts/test-windows.ps1`.

Os testes Compose de detalhes, busca e Configurações usam renderização nativa e modo de entrada por controle/teclado. Conferem quatro escalas, texto visível sem estouro nos controles examinados, retorno do player, navegação por setas, confirmação e restauração do foco.

Isso não comprova a saída HDR nem a decodificação física em todos os modelos de TV. A conferência em aparelho continua necessária, incluindo 4K/HDR, soundbar, suspensão, perda de rede e legendas durante retomada/avanço. Nenhum aparelho estava conectado por ADB na revisão.

## Resultado da validação e publicação

- Servidor: 47 suítes aprovadas.
- Android: 114 testes aprovados, sem falhas, erros ou testes ignorados.
- Análise estática: zero erros, 50 avisos e uma sugestão; os avisos permanecem como manutenção pendente.
- APK release 1.0.37 (38) gerado, assinado com o certificado registrado e publicado localmente em `data/android-tv-updates`.
- SHA-256 do APK: `A21C32DBA562F1456CDEECB2A3D95F40CAFD375102DED43CE68BF056AA22F571`.
- A publicação disponibiliza a versão ao atualizador da TV quando o servidor estiver em execução e acessível; não comprova instalação no aparelho.

## Próximas prioridades

1. Serializar operações completas de dispositivos: autenticação e atualização de presença ainda podem concorrer com revogação/permissões no armazenamento atual.
2. Autorizar o desbloqueio por PIN também no servidor, vinculado a aparelho e perfil, com expiração e limitação de tentativas.
3. Ampliar testes físicos e capturas por modelo de TV, com medição de primeiro quadro, interrupções e qualidade efetiva.
4. Gestão de assistido por episódio/temporada e integração Continuar assistindo na tela inicial do Android TV.

As prioridades 1 e 2 são pendências da arquitetura de autorização identificadas na avaliação inicial; não fazem parte das correções de interface desta revisão.
