# Personalização e continuidade no BRasa TV

Implementação validada em 06/09/2026 para o APK Android TV 1.0.30 (31). O servidor mantém compatibilidade com APKs anteriores: os novos campos são opcionais e as rotas antigas continuam válidas.

## Como usar na TV

- **Início** mostra, quando houver conteúdo, `Continuar assistindo`, `Para você`, `Novos episódios das suas séries`, recomendações explicadas, `Minha lista`, descoberta variada e histórico recente. As fileiras vazias são omitidas.
- **Minha lista** tem acesso próprio no menu e aceita filmes e séries. Favoritos antigos, por ID simples, continuam reconhecidos.
- Nos **detalhes**, o botão principal retoma o episódio parcial ou avança para o próximo episódio disponível. A temporada correspondente já abre selecionada. Também há `Assistir do início`, `Gostei`, `Não é para mim`, ocultação reversível, remoção da fileira de continuidade e marcação como assistido.
- Em **Configurações → Reprodução**, `Próximo episódio automático` ativa uma contagem regressiva cancelável por perfil.
- Em **Configurações → Interface da TV**, a grade pode ser `Compacta` ou `Confortável`.
- Em **Buscar**, títulos exatos vêm primeiro, acentos são ignorados e um erro curto de digitação é aceito de forma conservadora. Episódios aparecem agrupados sob a série. A tela mostra sugestões e buscas recentes; voz aparece somente quando o Android TV oferece um reconhecedor.
- Em **Configurações → Personalização**, é possível limpar buscas recentes ou reiniciar avaliações e sugestões ocultadas sem apagar favoritos nem progresso.

## Regras de continuidade

1. Um episódio parcialmente visto tem prioridade.
2. Sem episódio parcial, o primeiro episódio ainda não concluído depois do último concluído é selecionado.
3. Lacunas na numeração não bloqueiam o avanço.
4. Temporadas regulares vêm antes da temporada especial 0.
5. Uma série só é concluída quando não resta episódio disponível não concluído.
6. Um progresso concluído não é apagado por uma gravação tardia do player. `Assistir do início` grava uma reinicialização explícita e limpa a conclusão daquele item.

## Personalização local

O cálculo roda no computador servidor e não usa serviço pago. Ele diferencia:

- `Minha lista`: intenção de assistir;
- `Gostei`: sinal positivo forte;
- consumo significativo e conclusão: sinais positivos moderados;
- `Não é para mim`: avaliação negativa explícita;
- `Ocultar sugestão`: remove somente das sugestões;
- `Remover de Continuar assistindo`: remove somente da fileira, sem virar rejeição.

Eventos de consumo são compactos, deduplicados por avanço em faixas de 10% ou conclusão e isolados por perfil. Pausa, foco, falha de rede e simples posição final não geram rejeição. Perfis sem histórico recebem uma seleção variada e estável.

Metadados existentes de elenco, direção, temas e franquia passam a integrar busca e afinidade. A duração também é exposta numericamente em minutos quando o catálogo fornece um valor interpretável. Dados ausentes não são inventados, correções manuais são preservadas e perfis infantis continuam bloqueando classificação desconhecida.

## Cache e estabilidade

A última página inicial válida é armazenada por servidor e perfil durante sete dias. Depois da autorização ela pode aparecer imediatamente, com indicação de reconexão, enquanto a atualização ocorre em segundo plano. O cache não promete reprodução offline e é apagado ao esquecer o servidor.

O destaque é estável durante a sessão, a primeira fileira aparece mais cedo e a ordem vinda do servidor não é embaralhada. O foco e a posição continuam sendo guardados pelas chaves dos cartões.

## Validação executada

- 40/40 suítes JavaScript do servidor e da interface passaram.
- 55/55 testes Android locais passaram, incluindo continuidade, filtro de séries, restauração de foco e player.
- Compilação Kotlin, `lintDebug` e `assembleDebug` passaram.
- O APK foi verificado com assinatura debug válida nos esquemas v1 e v2.
- Não houve instalação, publicação nem substituição do APK atual.

O teste final em aparelho físico ainda deve confirmar distância de leitura, navegação completa por setas/OK/Voltar, retorno após rolagem, reconhecimento de voz oferecido pelo aparelho, troca de perfil e a contagem regressiva durante reprodução real.

## Dependências ainda reais

- `Pular abertura` e antecipação nos créditos não foram ativados: o catálogo não possui marcações confiáveis por episódio. Tempos fixos não são usados para evitar cortes.
- O formulário opcional de escolha inicial de gêneros não foi criado nesta etapa; perfis novos recebem diversidade automática e podem ensinar preferências com as ações dos detalhes.
- O enriquecimento só consome dados já confirmados pelos mecanismos atuais. Completar elenco, direção e temas dos títulos ainda vazios depende de novas correspondências confiáveis ou correção manual.

## APK e recuperação

APK de validação: `releases-local/brasa-tv-personalizacao-1.0.30-debug.apk`.

Antes das alterações foi criado o ponto de recuperação Git com a mensagem `codex recovery before tv personalization 2026-09-06`. Ele preserva também as alterações locais preexistentes em `data/movies.js`, `data/series.js` e o relatório de avaliação. Para recuperar, salve primeiro qualquer trabalho novo, localize essa mensagem com `git stash list` e aplique a referência correspondente com `git stash apply --index <referência>`.
