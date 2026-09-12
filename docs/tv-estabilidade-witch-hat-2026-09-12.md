# Revisão de reprodução da TV — 12/09/2026

## Investigação do episódio

Arquivo analisado: `assets/series/Witch Hat Atelier/Witch Hat Atelier S01E01 WEB-DL 1080p x264 DUAL 2.0.mkv`.

- Duração: 1.420,053 segundos; tamanho: 1.489.237.893 bytes.
- Vídeo H.264 High, 1920×1080, 8 bits; áudio AAC em português e japonês.
- Taxa média do arquivo: 8.389.759 bits/s.
- Decodificação completa de vídeo e de ambas as faixas de áudio com FFmpeg, `-v error -xerror`, terminou com código 0 e sem mensagens de erro.
- Não havia TV conectada por ADB. Sem registros do aparelho, não é possível atribuir as três pausas relatadas a uma causa específica, nem medir o Wi-Fi da TV a partir deste computador.

A leitura não encontrou corrupção decodificável no episódio. Isso não comprova ausência de problemas de rede, armazenamento temporário ou decoder no aparelho.

## Correções

1. **Carregamento e travamento são tratados separadamente.** O monitor antigo reiniciava após 12 segundos sem avanço mesmo em `STATE_BUFFERING`, inclusive antes dos 15 segundos de buffer pedidos para retomar HLS. Agora o avanço do buffer é levado em conta. Sem chegada de dados, a recuperação ocorre após 45 segundos; o carregamento contínuo sem reprodução tem limite de 120 segundos. Congelamento em estado pronto mantém o limite de 12 segundos. Pausa e supressão de reprodução não disparam recuperação.
2. **Memória do player limitada conforme a TV.** O buffer possui orçamento de 16–64 MiB (até 32 MiB em aparelhos de pouca RAM), considerando a classe de memória. A prioridade por tempo que podia ultrapassar o orçamento foi removida. O mínimo desejado sobe para 30 segundos em vídeo comum e 45 segundos em HLS/vídeo pesado, sempre subordinado ao limite de memória. O retrobuffer é reduzido e desativado para 4K/bitrate alto ou pouca RAM.
3. **Reconexão sem disputar a liberação do player.** A tentativa reaproveita e prepara novamente a instância possuída pela tela, preservando posição local e intenção de pausa. Não existe mais a sequência de readquirir a mesma identidade enquanto a composição anterior libera essa instância. A tela de erro direciona o foco ao botão de tentar novamente.
4. **Retomada de H.264 SDR compatível sem transcodificação forçada.** Um ponto salvo acima de cinco segundos não transforma automaticamente esse conteúdo em HLS. O fallback explícito permanece disponível. As regras anteriores para HDR e outros formatos foram mantidas.
5. **Retrocesso HLS reutiliza segmentos existentes.** Como o servidor publica playlists EVENT e conserva os segmentos anteriores, sair do retrobuffer em RAM não obriga criar outra conversão. Posições anteriores ao início da sessão e avanços fora da região disponível ainda usam o servidor.
6. **Controle remoto e pré-carregamento.** Pausar funciona também durante o carregamento; teclas separadas de reproduzir/pausar são reconhecidas. Uma instância pré-carregada respeita o ponto solicitado ao ser adquirida, inclusive “assistir do início”. Solicitações canceladas de preparação não publicam erros na reprodução seguinte; o ponto escolhido permanece fixo durante as consultas de preparação.
7. **Preparação HLS sem encoder duplicado.** Solicitações simultâneas do mesmo título são serializadas na criação da sessão. As gravações de estado também são serializadas. O cancelamento aguarda o encerramento antes de remover os arquivos. Uma falha de GPU após disponibilizar vídeo não substitui os segmentos já publicados por uma conversão de CPU sob os mesmos nomes.
8. **Cache em uso protegido.** A limpeza automática e manual preserva sessões em processamento e sessões acessadas nos últimos três minutos, incluindo arquivos temporários de segmentos. Sessões inativas continuam removíveis.
9. **Playlists HTTP consistentes.** Manifestos são enviados de uma única leitura em memória, com o tamanho do próprio conteúdo. A troca do arquivo pelo FFmpeg entre `stat` e abertura do stream não pode mais produzir um `Content-Length` antigo e truncar a resposta.
10. **Conversão de vídeo sem áudio.** O mapa de variantes HLS não referencia faixas de áudio inexistentes.

## Escopo e verificações

A revisão concentrou-se no aplicativo Android TV (player, recuperação, pré-carregamento, busca no tempo e coordenação das telas) e no caminho completo de reprodução do servidor (capacidades, seleção de formato, fila, HLS, cache, playlists e HTTP Range). A suíte geral também cobre catálogo, perfis, navegação/foco, atualizações, indexação, rede e APIs administrativas. Os testes não substituem uma sessão de reprodução na TV física.

- Servidor: 41/41 suítes aprovadas, incluindo a nova regressão de concorrência, falha de encoder e proteção de cache ativo.
- Sintaxe verificada em 141 arquivos JavaScript do servidor, scripts, interfaces e catálogo.
- Android: 62 testes aprovados; compilação debug e release concluídas. Lint sem erros, com 51 avisos e uma sugestão no relatório debug.
- Episódio original: decodificação integral sem erro.
- Reprodução HTTP direta desde 600.000 ms: Range `206` validado e trecho de 60 segundos decodificado com `-xerror`.
- Fallback HLS desde 600.000 ms: dez segmentos locais e 60 segundos via HTTP decodificados; maior segmento observado de 2,002011 segundos. A sessão completa do trecho restante foi preparada com NVDEC/CUDA + NVENC.
- Segundo teste HLS desde 900.000 ms: dez segmentos locais e 60 segundos via HTTP decodificados, com oito leituras HTTP do manifesto e das variantes durante a preparação; comprimentos e término das respostas validados.
- Servidor local reiniciado com as mudanças e resposta de inicialização confirmada.
- BRasa TV 1.0.32, código 33: APK release assinado com o certificado existente e publicado no atualizador local. SHA-256: `85F8656826D0C17036ABAF4C11048318C855FC5CFE677D03DDB95F26D0F8A3D3`.

O catálogo recebeu também atualizações automáticas de metadados enquanto o servidor estava aberto; elas foram preservadas. Os vídeos originais, perfis, pareamento e credenciais não são incluídos no commit.

## Validação restante no aparelho

Instalar a atualização 1.0.32 pelo Brasa na TV e assistir novamente ao episódio, incluindo pausar durante o carregamento, retomar e retroceder. Não foi instalada atualização remotamente porque não havia aparelho acessível. As correções removem causas concretas de interrupção no código; não garantem ausência absoluta de pausas em qualquer rede ou aparelho.

## Referências técnicas

- [Media3: buffers por tempo/tamanho e retrobuffer](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder).
- [Media3: busca no vídeo, quadros-chave e diagnóstico de reprodução](https://developer.android.com/media/media3/exoplayer/troubleshooting).
