# Changelog

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
