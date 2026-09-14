# Preview real do BRasa na TV

Capturas originais do APK instalado na TV TCL do usuário, conectado ao servidor BRasa. Não são montagens, telas HTML ou imagens geradas.

[Ver oportunidades de UI e UX](analise-ui-ux.md) · [Abrir todos os PNGs e metadados](android-tv/1.0.35/)

## Ambiente e limites

- Sessão: 14/09/2026, horário de Brasília; cada JSON registra o instante em UTC.
- APK: `com.brasa.tv`, versão **1.0.35 (36)**; código de referência: `d8f141cc890c7b12f3b787c88160c07c474a4e00`.
- TV física TCL; identificação Android `Smart_TV_Pro` / `BeyondTV4`, Android 11 (API 30).
- Captura: **1920 × 1080**, densidade Android 320 dpi. Isso descreve a captura, não a resolução nativa do painel.
- Configuração observada: escala **90%**, densidade **Confortável**. No print 17, 110% está apenas em foco; 90% continua selecionado. Nenhuma preferência foi alterada para produzir os prints.
- Navegação por eventos equivalentes às teclas do controle remoto via ADB; catálogo e perfis reais. Nenhum PIN ou token foi capturado.
- O vídeo sai preto na captura do player, mas **o usuário confirmou que a imagem aparece na TV**. Controles e miniatura são capturados normalmente. Não interpretar o fundo preto como defeito de reprodução.
- A passagem pelo player reproduziu parte do episódio e pode atualizar o ponto assistido. Não foi uma medição controlada de estabilidade, sincronização de progresso ou qualidade de imagem. Ao terminar, retornamos à tela inicial.
- Não foram avaliados outros aparelhos, todas as escalas, teclado/ditado em uso, perfis protegidos, catálogo vazio, rede indisponível ou fim de episódio. Os prints não certificam acessibilidade nem ausência de travamentos.

Cada PNG tem um JSON com versão instalada, dimensão, origem, teclas enviadas pela chamada de captura e SHA-256. Essas teclas não constituem um roteiro completo reproduzível: foco inicial, navegação anterior e tempo de carregamento também interferem. A numeração tem lacunas porque capturas intermediárias sem interface visível foram descartadas.

## Galeria

### Perfis

![Seleção de perfis: indicador PIN parcialmente cortado e ícone de encerrar sem glifo](android-tv/1.0.35/01-perfis.png)

### Início

![Início: destaque da série e continuar assistindo](android-tv/1.0.35/02-inicio.png)

### Detalhes da série

![Detalhes: ano sobreposto ao botão Voltar e ação Assistir do início encurtada](android-tv/1.0.35/03-detalhes-serie.png)

### Temporada e episódios

![Navegação dos episódios com foco no primeiro cartão](android-tv/1.0.35/04-detalhes-navegacao.png)

### Player e controles

O fundo preto abaixo é uma limitação da captura; a imagem estava visível na TV.

![Controles do player real](android-tv/1.0.35/05-player.png)

[Outra captura dos controles](android-tv/1.0.35/06-player-controles.png). O envio da tecla MEDIA_PAUSE nessa captura não comprova que a reprodução tenha sido pausada.

### Miniatura na barra de tempo

![Barra em foco com miniatura de 04:00 e posição escolhida de 04:06](android-tv/1.0.35/07-player-miniatura.png)

### Legendas

![Menu de legendas e opções de tamanho e estilo](android-tv/1.0.35/08-player-legendas.png)

### Saída do player

![Detalhes do episódio após sair do player](android-tv/1.0.35/12-saida-player.png)

[Retorno à tela inicial](android-tv/1.0.35/13-retorno.png).

### Biblioteca de filmes

![Biblioteca de filmes com filtros, ordenação e cartões](android-tv/1.0.35/14-filmes.png)

### Busca

![Busca com campo, entrada por voz e filtros de gênero](android-tv/1.0.35/15-busca.png)

### Configurações

![Conexão, biblioteca e reprodução](android-tv/1.0.35/16-configuracoes.png)

![Escala e densidade atuais, personalização e versão instalada](android-tv/1.0.35/17-configuracoes-interface.png)

[Estado final: Brasa na tela inicial, fora do player](android-tv/1.0.35/18-inicio-final.png).

## Como atualizar as capturas

Com a depuração autorizada, identifique o dispositivo em `adb devices`, abra o Brasa e navegue até a tela desejada. Substitua o serial no exemplo:

```powershell
node scripts/capture-android-tv-preview.mjs --serial SERIAL_DA_TV --current --name 01-inicio
```

O utilitário [capture-android-tv-preview.mjs](../scripts/capture-android-tv-preview.mjs) salva na pasta da versão instalada, valida que o Brasa está em primeiro plano e recusa sobrescritas sem `--replace`. Use `--adb` para outro caminho do ADB. Opcionalmente, `--keys DPAD_DOWN,DPAD_RIGHT` navega antes da captura; qualquer tecla de confirmação pode acionar a opção em foco, portanto confira o estado antes de usá-la. A opção `--page` é exclusivamente para APK debug em emulador e gera dados demonstrativos, não equivalentes a esta galeria.

Antes de publicar, confira visualmente cada PNG, nomeie pela tela efetivamente obtida, evite telas com credenciais e mantenha o campo `file` do JSON consistente se renomear. Não substitua a área preta do player por imagens artificiais.

## Verificação desta entrega

- `npm test`: **45/45 suítes aprovadas** nesta sessão.
- Sintaxe do utilitário de captura verificada com `node --check`.
- PNGs inspecionados visualmente; dimensões e hashes conferidos contra os JSONs.
- Nenhuma proposta de UI/UX foi implementada e nenhum novo APK foi instalado nesta etapa.
