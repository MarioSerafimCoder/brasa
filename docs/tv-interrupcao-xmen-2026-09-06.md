# Interrupção de X-Men ’97 — 06/09/2026

## Evidência local

- Último progresso observado: S02E05, 552,104 segundos; duração declarada 1.745,792 segundos.
- Arquivo: `F:\BRasa\Series\X-Men '97\Temporada 02\X-Men '97 - S02E05.mkv`.
- A pasta `assets/series` é uma junction para `F:\BRasa\Series`; não é uma segunda cópia.
- FFmpeg, lendo de 510s por 120s, informou EBML inválido no byte 362676561 e erro de decodificação H.264.
- FFprobe encerrou a leitura de pacotes por volta de 611,833s. Leituras em 620s e 650s também encontraram EBML inválido; conteúdo voltou a ser legível por volta de 662,792s. Amostras em 900s e 1600s foram decodificadas sem erro.
- Amostras de 4.096 bytes nos offsets 362676561, 370066927 e 380000000 estavam integralmente zeradas. Isso demonstra dados ausentes no arquivo, não apenas incompatibilidade do decoder da TV.
- Nenhum dispositivo estava disponível por ADB: não foi possível obter o log do aparelho para afirmar a sequência exata do incidente.

## Correção no aplicativo (1.0.30, código 31)

- Encerramento antes da duração esperada é detectado também em arquivos diretos; progresso não é marcado como concluído.
- Recuperação usa o ponto absoluto atual ao solicitar o streaming compatível, sem depender de uma gravação assíncrona de progresso.
- Congelamento é identificado pela posição do vídeo, inclusive quando `isPlaying` permanece verdadeiro. Pausa manual e supressão por foco de áudio não provocam reconexões.
- Recriação do player preserva o tempo local, inclusive após retroceder; apenas o efeito responsável pelo player faz sua liberação.
- Tentativas concorrentes são evitadas. O contador só reinicia após um minuto de avanço estável; falhas repetidas no mesmo trecho não criam ciclos automáticos infinitos.
- Uma solicitação tardia de pré-carregamento não pode substituir um player em uso.

## Limite e resolução do arquivo

Validação automatizada: 53 testes Android aprovados e verificação de regressões
da interface aprovada. Os novos casos cobrem travamento com reprodução ativa,
pausa/foco de áudio, retrocesso, limite de tentativas e fim antecipado do episódio.

O aplicativo pode recuperar falhas de conexão e reconhecer a interrupção inesperada,
mas não reconstruir dados de vídeo ausentes. O arquivo original foi preservado.
É necessário substituí-lo por uma cópia íntegra para assistir as cenas afetadas.
Nenhuma cena foi removida nem pulada silenciosamente. Não foi localizada outra cópia
do episódio nas pastas locais Tor, DOWNLOADS e BRasa consultadas.
