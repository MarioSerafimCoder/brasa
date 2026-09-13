# BRasa TV 1.0.35 — navegação e conforto ao assistir

## Entrega

- **Miniaturas na linha do tempo:** ao mover a seleção, a TV espera 350 ms e pede uma imagem do intervalo de 10 segundos escolhido. São JPEGs 256 × 144, sem baixar o vídeo inteiro. Confirmar com OK continua sendo necessário para mudar o ponto; os controles não desaparecem enquanto a barra está focada.
- **Próximo episódio com limites:** com reprodução automática ativada, perto do fim e após 30 segundos estáveis, até 512 KiB do próximo arquivo original podem entrar no cache. Isso só acontece sem carregamento atual e com todo o restante do episódio já em buffer. Não cria outro player/decodificador, não inicia HLS/conversão ou análise antecipada, e cancela ao sair, buscar outro ponto ou voltar a carregar. A transferência tem prazo de três segundos. Títulos que ainda exigem preparação ficam para quando forem escolhidos.
- **Retorno ao cartão selecionado:** a seleção é identificada pela chave do título, não pelo índice. A lista rola até a posição atual antes de pedir foco. Funciona na biblioteca, busca, páginas de categorias, coleções, fileiras das páginas inicial/adulta/infantil e episódios nos detalhes. Se o título não existir mais, as grades voltam ao primeiro cartão disponível; os detalhes voltam ao botão Reproduzir. Perfil, filtros e ordenação continuam preservados.
- **Priorizar estabilidade:** opção local por perfil em Configurações → Reprodução. Para o próximo vídeo, solicita streaming adaptativo limitado a 720p (respeitando TVs com limites menores); o seletor mantém esse teto. A explicação informa menor nitidez, menor uso da rede e necessidade possível de conversão no computador. Desligada por padrão.
- **Pular abertura/créditos:** botões manuais baseados somente em capítulos do próprio arquivo, com rótulos explícitos como Opening, Abertura, OP, Credits, Créditos ou ED. Validação limita posição/duração e rejeita nomes genéricos, sobreposição e faixas inválidas. A busca usa tempo absoluto mesmo em HLS iniciado no meio do episódio. Créditos com capítulo posterior terminam nesse limite, preservando a cena seguinte; crédito até o fim leva à tela de conclusão.

## Proteções e limites

Miniaturas possuem uma única extração simultânea, limite de 2,5 segundos, uma thread de decodificação e cache de no máximo 128 imagens/16 MiB. A geração não começa — ou é cancelada — enquanto houver preparação/conversão no servidor. A TV só autoriza gerar com reprodução pausada ou buffer confortável e rede ociosa. Imagens já guardadas podem ser reutilizadas; se não houver folga, aparece uma mensagem sem bloquear o vídeo. O cache é invalidado pela revisão do arquivo, exige autenticação e respeita o perfil. Não existe envio de imagens para serviços externos.

A preparação antecipada é deliberadamente conservadora: não acelera todos os episódios, especialmente os que precisam de conversão. Ela não deve competir com o vídeo atual. Nem esta opção nem Priorizar estabilidade garantem reprodução sem pausas diante de perda total da rede, arquivo defeituoso ou computador insuficiente.

O arquivo local de **Witch Hat Atelier S01E01 não possui capítulos**. Sua miniatura em 10:00 foi extraída com sucesso (7.615 bytes), mas os botões de abertura/créditos não aparecem nele. Nenhum tempo foi inventado. Arquivos já analisados passam por atualização da análise na próxima reprodução para incorporar capítulos existentes.

## Verificações

- 89 testes Android aprovados, incluindo limites de pré-carregamento, resolução, persistência por perfil, capítulos e retorno a cartão fora da tela após reordenação/remoção.
- 45/45 suítes do servidor aprovadas, incluindo cache de miniaturas, prioridade, limite de memória, autenticação e isolamento de perfil. A API antecipada com `prepare=0` foi verificada sem iniciar análise nem conversão.
- Android debug: zero erros, 51 avisos preexistentes e uma dica.
- Extração real no arquivo de Witch Hat Atelier, sem alterar o vídeo.
- Servidor atualizado em execução: Witch Hat Atelier em modo original, miniatura recebida por HTTP autenticado e reutilizada com geração desativada; plano de estabilidade consultado sem iniciar conversão antecipada. Dispositivo temporário removido ao concluir.
- Nenhuma TV física conectada por ADB. Ainda precisam ser confirmados no aparelho real a visualização das miniaturas, os botões de pular em um arquivo com capítulos e a transição prolongada entre episódios.

Referências: [FFmpeg](https://ffmpeg.org/ffmpeg.html), [CacheWriter Media3](https://developer.android.com/reference/androidx/media3/datasource/cache/CacheWriter).

## Na TV

Depois de instalar 1.0.35, use Configurações → Reprodução para ativar Priorizar estabilidade. Na barra do player, escolha o ponto com as setas e confirme com OK. Os botões de pular aparecem nos controles durante um trecho identificado. Preparação limitada do próximo episódio acompanha a opção Próximo episódio automático.

## Publicação

Release 1.0.35 (código 36) assinada com o certificado de atualização registrado e publicada no servidor local. O download autenticado pela API retornou os mesmos 3.262.291 bytes e SHA-256 `1B20C4706200FCF5B4349996C9D1E4DD4F7B3D38502CA46F75BBE6E116AB49D1` do pacote assinado. A versão 1.0.34 recebe a oferta; a versão 1.0.35 não recebe uma atualização repetida. O dispositivo temporário de verificação foi removido. Disponibilidade confirmada, instalação no aparelho físico ainda não confirmada.

As atualizações automáticas de metadados em `data/movies.js` e `data/series.js`, geradas ao abrir o servidor, foram preservadas localmente e não fazem parte deste conjunto de melhorias.
