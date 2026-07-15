# Changelog

## 1.0.9 — 2026-07-15

- Direct play passa a considerar somente decoders de vídeo por hardware, com limites de resolução, bitrate e perfil por codec.
- Dolby Vision em MKV deixa de ser enviado diretamente ao Media3 e usa transcodificação HLS compatível; Dolby Vision direto fica restrito a MP4 e tela/decoder compatíveis.
- Falhas de decoder ou contêiner no fluxo direto acionam automaticamente uma nova fonte HLS transcodificada.
- Player é recriado quando URL, modo ou revisão do arquivo muda; o cache progressivo não reutiliza mais arquivos substituídos.
- HLS deixa de usar a chave de cache progressivo, evitando a remoção acidental de segmentos ativos.
- Buffers foram ampliados para 4K/alto bitrate e para retomada de HLS; progresso de episódios é salvo sempre na chave correta.
- Media3 atualizado para 1.10.1 e fallback entre decoders habilitado.

## 1.0.8 — 2026-07-15

- Capacidades reais de contêiner, vídeo, áudio, HDR e resolução são detectadas no Google TV e enviadas ao servidor.
- Arquivos acima de 20 GB deixam de exigir HLS quando o dispositivo suporta o original; o streaming usa Range autenticado.
- Incompatibilidade apenas de áudio ou contêiner usa remux HLS com cópia de vídeo e conversão AAC quando necessária.
- Transcodificação sob demanda começa com uma única variante, segmentos de dois segundos e quatro segundos de buffer no servidor.
- Buffer inicial do Media3 reduzido para três segundos em rede local.
- Fallback de hardware para CPU permanece em `preparing` e não encerra mais o acompanhamento do APK com falso erro.
- Sessões HLS registram modo, encoder, decoder, pipeline, primeiro quadro, instante reproduzível e motivo do fallback.

## 1.0.7 — 2026-07-14

- Corrigida a sessão do Superman que avançava pelo áudio sem produzir segmentos de vídeo.
- HEVC/H.265 em CUDA passa a selecionar explicitamente `hevc_cuvid`, necessário para o arquivo Dolby Vision testado.
- GOP e IDR do NVENC fixados em aproximadamente quatro segundos para liberar segmentos HLS progressivamente.
- A preparação interrompe e aciona fallback quando nenhum quadro de vídeo é decodificado nos primeiros 20 segundos.
- Teste real confirmou dois segmentos disponíveis em 10,7 segundos e reprodução simultânea da playlist por cinco segundos.

## 1.0.6 — 2026-07-14

- Corrigido o falso negativo de NVENC causado pelo teste com resolução abaixo do limite aceito pela GPU.
- Detecção de FFmpeg/FFprobe com prioridade explícita, caminhos completos, cache atualizável e diagnóstico de driver, GPU, parâmetros e timeout.
- Pipeline HLS NVIDIA com NVDEC, `scale_cuda` e NVENC em perfil de baixa latência.
- Conteúdo HDR é reduzido na GPU antes do tone mapping; o estágio de tone mapping permanece na CPU porque o FFmpeg instalado não inclui `tonemap_cuda`.
- Perfil HDR pesado limitado inicialmente a 720p para manter a geração dos segmentos em tempo real neste computador.
- Painel administrativo com encoder selecionado, suporte H.264/HEVC/AV1 e ação **Testar novamente**.
- Testes reais realizados com CODA em 1080p e Superman em HEVC Main10 HDR 4K.

## 1.0.5 — 2026-07-14

- Análise obrigatória antes de entregar uma fonte ao player da TV.
- HLS adaptativo local com segmentos de quatro segundos, playlists progressivas e escrita atômica.
- Direct play, remux, conversão de áudio ou HLS conforme contêiner, codecs, bitrate, resolução e tamanho.
- Fallback de hardware para CPU e validação real de NVENC, Quick Sync e AMF.
- Estados de análise/preparação, progresso, buffer, qualidade e erros específicos no APK.
- Escala geral persistente e densidade de cards independente.
- Busca sem teclado automático e PIN com painel próprio para controle remoto.
- Cache HLS configurável e limpeza de arquivos parciais.

## 1.0.4 — 2026-07-14

- Interface compactada, correção do topo inicial, teclado e recuperação de rede no player.
