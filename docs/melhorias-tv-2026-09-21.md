# Melhorias de reprodução e navegação na TV

Implementação local de 21/09/2026. Servidor e aplicativo Android TV precisam ser atualizados juntos para usar todas as melhorias. A versão assinada não foi publicada nem instalada em um aparelho durante este trabalho.

## Navegação

- A página de detalhes e o conteúdo em reprodução têm estados separados.
- Reproduzir um episódio, avançar para o próximo ou abrir um título semelhante mantém a página de origem.
- Ao voltar, a temporada, o cartão e a posição das listas são restaurados. O progresso assistido atualiza a continuação da série sem trocar sua identidade.
- “Assistir do início” limpa a conclusão anterior. Pedidos de reprodução são cancelados ao sair dos detalhes para impedir que uma resposta atrasada abra o player.

## Original, conversão, 4K e HDR

- A existência de progresso salvo não obriga mais a converter vídeo compatível, incluindo HEVC/HDR/Dolby Vision compatíveis com o aparelho.
- A resolução física de saída é considerada separadamente da resolução da interface. Os limites do decodificador continuam sendo respeitados.
- Quando só o áudio ou o contêiner exige adaptação, o vídeo pode ser copiado sem redução de qualidade. A retomada procura um quadro-chave anterior e mantém a diferença na posição local do player.
- Se não houver ponto seguro, se a preparação falhar ou se a TV não conseguir decodificar o vídeo copiado, a conversão continua disponível como recuperação. Renovar um trecho expirado não obriga uma nova conversão.
- O modo opcional de prioridade à estabilidade continua aplicando seus limites de qualidade.

## Conexão

A interface diferencia TV sem rede local, computador/servidor indisponível, autorização revogada, acesso negado e resposta incompatível do servidor. Uma indisponibilidade do computador não apaga o pareamento. Há ações para tentar novamente, conferir o endereço ou parear novamente.

Não é possível concluir apenas pela falta de resposta que o computador foi desligado: o texto também considera BRasa fechado, endereço alterado e problemas de acesso.

## Sincronização de legenda

No player, abra **Legendas → Sincronização**. Use **− 0,25 s** para adiantar ou **+ 0,25 s** para atrasar, e escolha **Aplicar ajuste**. **Zerar** restaura o tempo original depois de aplicar.

O ajuste vai de −60 a +60 segundos e é salvo neste aparelho por servidor, perfil e mídia. Ao aplicar, a fonte de reprodução é recarregada na posição atual; isso pode causar uma breve recarga. A linha do tempo considera reprodução direta, HLS iniciado no meio do vídeo, retomada e avanço/retrocesso. A compensação do início do HLS é aplicada às legendas externas; legendas incorporadas mantêm a referência do próprio fluxo.

## Editar abertura e créditos

No painel do computador, abra **Biblioteca → filme ou episódio → Pular abertura e créditos**.

1. Se necessário, clique em **Analisar mídia** e reabra o conteúdo.
2. Ative o trecho desejado e informe início e fim no formato `minutos:segundos.milissegundos`, por exemplo `1:30.000`.
3. Clique em **Salvar marcações** e reabra o vídeo na TV.

Desmarcar os dois trechos e salvar desativa os botões de pular para essa mídia. **Usar capítulos automáticos** remove a personalização e volta aos capítulos reconhecidos do arquivo. As marcações são compartilhadas entre perfis e gravadas em `data/playback-markers.json`; os arquivos de vídeo não são alterados.

O servidor rejeita tempos negativos, invertidos, sobrepostos ou fora da duração. O fim dos créditos pode ficar antes da cena pós-créditos. Se o arquivo for substituído, marcações personalizadas antigas não são aplicadas à nova versão sem revisão.

## Validação realizada

- **47/47 suítes Node aprovadas**, incluindo persistência das marcações, remoção/restauração, gravações simultâneas, troca do arquivo, autenticação e CSRF, retomada de remux e recuperação após falha.
- **102/102 testes Android aprovados**, sem testes ignorados. Incluem volta à temporada e ao episódio fora da posição inicial, isolamento da página de origem, reinício de conteúdo concluído, classificação de conexão, tempos de legenda direta/HLS/incorporada, persistência e controles do menu.
- **FFmpeg real com vídeo sintético:** retomada em 20,75 segundos encontrou o quadro-chave de 18 segundos; o primeiro segmento foi decodificado mantendo o vídeo original.
- **Compilação e análise Android aprovadas:** APK debug gerado; análise sem erros, com 51 avisos e 1 indicação já presentes na avaliação inicial.
- `git diff --check` sem problemas de espaços.

Comandos principais: `node scripts/run-tests.mjs`; `node scripts/test-remux-ffmpeg.mjs`; Gradle `testDebugUnitTest lintDebug assembleDebug`.

Nesta máquina, o runtime do Robolectric foi copiado para uma pasta temporária sem espaços e indicado pela propriedade `brasaRobolectricRuntimeDir`, evitando o problema de caminho codificado encontrado na avaliação inicial. Não foi necessário alterar a configuração de produção.

## Conferência no aparelho

A implementação não substitui a validação de saída HDR e decodificação nos modelos reais de TV. Antes de publicar a atualização assinada, conferir um título 4K/HDR compatível, um formato que exija conversão, perda e retorno da rede, retomada com legenda ajustada e navegação pelo controle remoto.

APK de teste: `apps/android-tv/app/build/outputs/apk/debug/app-debug.apk`. Este artefato usa a assinatura de depuração, não a assinatura de atualização da versão instalada.
