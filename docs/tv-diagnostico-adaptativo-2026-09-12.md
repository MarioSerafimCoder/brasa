# BRasa TV 1.0.33 — histórico de reprodução e qualidade adaptativa

## Entrega

- Configurações → Reprodução → Histórico de reprodução e pausas: sessões, duração das interrupções, pausas pelo controle, posição, buffer, qualidade, estimativa de rede, erros, quadros perdidos e recuperações.
- Uma sessão de diagnóstico acompanha a troca do original para HLS, sem fragmentar o histórico. Pausas voluntárias, preparação inicial e buscas não acionam a política de adaptação por interrupções.
- Contexto do servidor correlacionado por sessão HLS: encoder, estado, velocidade da conversão e segundos preparados. As explicações são **indícios**, não conclusões automáticas sobre a origem de cada falha.
- Histórico autenticado, isolado por TV e perfil. Até 100 sessões no computador, retidas por até 30 dias; últimos 600 eventos por sessão com totais preservados. Tokens, URLs e caminhos do arquivo não entram nos eventos.
- Envio em segundo plano, lotes de até 40 eventos, fila limitada a 240 eventos na TV, priorizando registros de falha sobre amostras. Falhas no envio não interrompem o player. Desligamento abrupto ou indisponibilidade prolongada podem deixar lacunas; uma interrupção sem encerramento é sinalizada no histórico. Não há recuperação retroativa dos travamentos anteriores à atualização.

## Adaptação de qualidade

- Conversão acelerada: 480p / 720p / 1080p; somente CPU: 480p / 720p. A resolução da fonte e os limites informados pela TV são respeitados; fontes menores podem ter menos alternativas. CPU é também a alternativa caso a aceleração falhe antes de publicar segmentos.
- As alternativas compartilham segmentos de aproximadamente 2 segundos e quadros-chave alinhados. O cache foi versionado para não reutilizar a antiga sessão de qualidade única.
- Seleção automática com reserva de 30% da estimativa de rede e exigência de 15 segundos de buffer para subir a qualidade. A qualidade efetiva aparece nos controles.
- Reprodução original permanece preservada enquanto está estável. Duas interrupções de pelo menos 1,5 segundo em até dois minutos solicitam HLS no ponto assistido. Essa migração inicial pode mostrar uma preparação; depois, a troca entre as variantes não recria a sessão. O seletor manual continua disponível como limite de qualidade.
- A política de capacidade limita resoluções e quantidade de encoders simultâneos por sessão; não é um medidor universal de carga do computador. Conexão indisponível, arquivos defeituosos ou hardware insuficiente ainda podem causar interrupções.

Referência da seleção adaptativa: [documentação oficial Media3](https://developer.android.com/reference/androidx/media3/exoplayer/trackselection/AdaptiveTrackSelection).

## Verificação

- Servidor: 42/42 suítes. Uma execução concorrente à compilação apresentou a flutuação de tempo do teste preexistente de monitoramento de arquivos (espera fixa de 60 ms); o teste isolado e a repetição integral passaram, sem alterar esse teste.
- Android: 67 testes aprovados em debug; teste do seletor real Media3 confirma 1080p → 480p com queda de banda, manutenção de 480p com buffer curto e retorno a 1080p após estabilização. Testes adicionais cobrem fila limitada, continuidade da sessão e política de recuperação.
- Análise Android debug: zero erros, 51 avisos preexistentes e uma dica.
- Witch Hat Atelier S01E01, a partir de 10:00: decodificados 24 segundos de **cada** variante. Verificados keyframes e timestamps alinhados nos primeiros quatro segmentos de todas as variantes. GPU: três variantes, aproximadamente 9,82× a velocidade de reprodução na amostra; CPU: duas variantes, 7,39×. São medições deste computador, não uma garantia para outros equipamentos.
- Caminho completo servidor → HTTP → decodificador: manifesto e playlists consistentes, 10 segmentos locais e 60 segundos via HLS. O restante do episódio foi preparado até o fim, em três variantes, com validação de duração e presença dos segmentos.
- API real: gravação, listagem e detalhes do histórico, exigência de autenticação e correlação com o encoder. Dispositivo temporário de teste removido ao terminar.
- Nenhuma TV física estava conectada por ADB. A navegação e a reprodução prolongada no aparelho real precisam ser confirmadas após instalar a atualização.

## Repetir os testes

APK release 1.0.33 (34), assinado e publicado no servidor local para atualização pela TV. SHA-256: `47ECA2C97006989AF468BA0C9ADD871972827706C118B81CC3CA1F602B2934DB`. O APK e os dados pessoais do histórico não são versionados no Git.

```powershell
npm test
node scripts/verify-adaptive-variants.mjs "caminho/do/video.mkv" 600
node scripts/verify-adaptive-variants.mjs "caminho/do/video.mkv" 600 --cpu
node scripts/verify-tv-playback.mjs "episode:ID" "Nome do perfil" 600000 --hls --diagnostics
```

O teste HTTP usa um dispositivo temporário, removido no encerramento. O teste das variantes usa uma pasta temporária e limpa somente os arquivos que ele próprio criou.
