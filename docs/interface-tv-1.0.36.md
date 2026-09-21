# Interface de TV — versão 1.0.36

Esta versão reorganiza as telas alteradas para uso prioritário a distância e somente pelo controle remoto.

## Detalhes e episódios

- O cabeçalho cresce com o conteúdo, limita títulos e sinopses longas e distribui ações em mais de uma linha quando necessário.
- Continuar, Minha lista e Assistir do início permanecem diretos. Avaliação, ocultação e gestão do histórico ficam em **Mais opções**.
- Episódios mostram barra de progresso, **ASSISTIDO**, **CONTINUAR** e minutos restantes. A borda laranja identifica o episódio da continuidade.
- Voltar do player restaura série, temporada, episódio, foco e posição da lista.

## Busca, gêneros e configurações

- O cabeçalho da busca ocupa menos altura. Campo, voz e histórico se adaptam à largura; os resultados usam cards horizontais para manter os títulos visíveis.
- `Acao` e `Ação` têm a mesma identidade e rótulo. Categorias compostas, como **Ação e aventura**, continuam independentes.
- Configurações usam categorias laterais: Geral, Reprodução, Interface, Personalização e Conta. Preferências booleanas informam **Ativado** ou **Desativado** no próprio controle.

## Leitura e player

- Botões de busca, configurações, reprodução, pausa, avanço, retorno, informação, energia e lista usam desenhos vetoriais independentes da fonte da TV.
- O selo PIN fica fora do recorte do avatar. Títulos e metadados de cards receberam tamanhos maiores.
- Áudio e legenda priorizam idioma, região, acessibilidade e canais legíveis, removendo nomes genéricos como “Audio Track 1”.
- Episódios no player exibem série, temporada, número e título. Dados técnicos aparecem somente no painel **Informações**.

## Verificação

Os testes automatizados validam as quatro escalas de interface (80%, 90%, 100% e 110%), ações visíveis com texto longo, percurso horizontal do controle remoto, restauração depois da reprodução, identidade de gêneros e rótulos de faixas. O ambiente de release no Windows usa o SDK empacotado e copia o runtime do Robolectric para um caminho sem espaços.

Antes de distribuir em um novo modelo de TV, conferir manualmente detalhes com sinopse longa, busca com histórico cheio, Configurações em 110%, painel técnico durante vídeo 4K/HDR e retorno de um episódio à série.
