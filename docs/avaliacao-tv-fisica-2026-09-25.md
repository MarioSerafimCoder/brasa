# Avaliação de UI e UX na TV física

Teste iniciado em 21/09 e retomado em 25 e 26/09/2026. Aparelho Smart TV Pro (BeyondTV4), Android 11, captura 1920 × 1080 e densidade física 320 dpi. Versão inicial 1.0.37 (38), versão final instalada 1.0.40 (41), escala observada 90%.

## Problemas reproduzidos

- O menu da página inicial cortava Configurações e não acomodava todos os destinos. A versão 1.0.38 usa rolagem horizontal acompanhando o foco, preservando a largura dos rótulos.
- O fundo dos detalhes de Witch Hat Atelier e Hércules não era escurecido. As camadas decorativas usavam preenchimento em um eixo sem limite de altura na lista. Agora acompanham o tamanho final do cabeçalho, mantendo contraste também com sinopse extensa.
- Ao entrar nos detalhes, a rolagem para o botão de reprodução afastava o título da tela. As ações foram colocadas antes da sinopse.
- O seletor de legendas repetia Inglês/English e Português (Brasil)/Portugues (Brasil). Os nomes equivalentes são normalizados, preservando rótulos distintos de faixas, como Dublagem e Comentário.
- A busca mantinha as setas no campo de texto depois de fechar o teclado. Na versão 1.0.39, cima/baixo encerram a edição e movem o foco para os controles vizinhos. O teste inclui digitação seguida da navegação para Todos.
- Ao editar a consulta, uma requisição cancelada podia exibir uma mensagem técnica mesmo com o resultado correto já na tela. Na versão 1.0.40, cancelamentos são propagados, buscas substituídas não alteram o estado atual e erros reais recebem uma mensagem compreensível.

## Percursos observados na versão 1.0.37

- Busca: entrada no campo, teclado aberto por OK, consulta Witch e teclado fechado com Voltar. Resultado legível sem rolar até o título.
- Configurações: acesso pelo menu, categorias e painel de escala, com 90% identificado como ativo.
- Player de Hércules: início, pausa, painel técnico, retorno ao player e seletor de legendas. O painel informa modo Original, H264, AC3 e 1920 × 1072.
- O vídeo aparece preto nas capturas remotas. Isso não permite confirmar a imagem exibida pelo decodificador na TV, nem comprovar falha de reprodução. A observação presencial solicitada ao usuário não foi recebida até este registro.

## Validação das correções

Os testes de busca, Configurações e menu usam 960 × 540 dp com densidade 2, equivalente à captura física 1920 × 1080. Cobrem 80%, 90%, 100% e 110%, navegação por setas e rótulos sem estouro. Um teste verifica que a camada de contraste acompanha toda a altura do cabeçalho dos detalhes.

A versão 1.0.38 foi instalada com sucesso preservando os dados. Na TV foram conferidas as quatro escalas da categoria Interface e a navegação até Configurações em 110%, com o rótulo completo e foco visível. O contraste e a posição das ações foram conferidos nos detalhes de Lanternas. Ao fim da verificação de escalas, a preferência foi restaurada para 90%.

Em 26/09, na versão 1.0.39, foi confirmado o percurso Witch Hat Atelier → temporada 1 → episódio 2 → player → Voltar. O retorno preservou o episódio focado e as posições vertical e horizontal da lista. Os cartões mostraram o selo Assistido no episódio 1, Continuar no episódio 2, barra de progresso e tempo restante. O player identificou série, temporada e episódio. Com controles ocultos, o primeiro Voltar reabre os controles; o seguinte retorna aos detalhes.

Na versão final 1.0.40, instalada por atualização sem apagar dados, foi repetida a digitação e edição de Witch, fechamento do teclado, duas setas para baixo, abertura do resultado e retorno à busca. A consulta permaneceu preenchida, o resultado voltou focado e nenhuma mensagem de cancelamento apareceu.

A compilação assinada passou com 117 testes, zero falhas e zero erros de lint (50 avisos e uma sugestão permanecem). O APK foi publicado no distribuidor local de atualizações. SHA-256: `70BEB4EFF63ABF4446218981A28A7706B6DC500FC746F90C802B5D2A6A0904E3`.

Evidências locais, sem edição das imagens:

- [Menu original com texto cortado](../preview/android-tv/1.0.37/review-home-initial.png).
- [Detalhes originais sem contraste](../preview/android-tv/1.0.37/review-details-top.png).
- [Menu em 110% com Configurações focado](../preview/android-tv/1.0.38/menu-110-settings-focused.png).
- [Configurações em 110%](../preview/android-tv/1.0.38/interface-110-after.png).
- [Detalhes após correção](../preview/android-tv/1.0.38/details-80-after.png).
- [Episódio selecionado após voltar do player](../preview/android-tv/1.0.39/final-episode-return.png).
- [Busca editada sem mensagem técnica](../preview/android-tv/1.0.40/search-edited-query.png).
- [Resultado alcançado com as setas](../preview/android-tv/1.0.40/search-result-focused.png).
- [Detalhes na versão final](../preview/android-tv/1.0.40/details-final.png).

## Melhorias seguintes

- Tornar mais evidente que o menu possui opções adicionais quando há rolagem horizontal.
- Reduzir a altura do diálogo de legendas ou separar aparência e sincronização para diminuir a rolagem necessária.
- Validar imagem e som presencialmente em 4K/HDR, avanço/retomada de legendas e suspensão do aparelho.
- Expandir os testes para outros modelos de TV e títulos com nomes extensos.
