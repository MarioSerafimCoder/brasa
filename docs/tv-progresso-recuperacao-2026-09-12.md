# BRasa TV 1.0.34 — progresso seguro e retorno ao aplicativo

## As quatro melhorias

1. **Progresso resistente a falhas.** Cada ponto é gravado primeiro em um diário local, fora do cache e separado por servidor, pareamento, perfil e título. O envio é refeito ao voltar ao app, restaurar a sessão e a cada 30 segundos enquanto o processo estiver ativo. Uma confirmação antiga não remove um ponto mais recente. Há aviso nos controles e nas configurações quando o envio está pendente ou a gravação local falha. O diário é conservado após fechar o app; desinstalar, limpar os dados ou esquecer o servidor remove essa proteção.
2. **Gravações simultâneas protegidas.** Uma fila compartilhada cobre a leitura, alteração e gravação de perfis pelas rotas da TV, navegador e administração. Falhas de escrita não inutilizam a fila. Preferências no navegador enviam apenas os campos alterados; a importação antiga é aditiva. Substituir um perfil inteiro com uma cópia desatualizada retorna conflito, e progresso reenviado com data anterior não substitui o mais recente.
3. **Recuperação conforme a causa.** Arquivo original ausente, falta de permissão, resposta inválida e dados malformados recebem orientação específica, sem reconexão automática inútil. Falhas temporárias de rede e servidor têm tentativas limitadas; trechos HLS expirados podem renovar a fonte uma vez no ponto assistido. Formato não suportado pode solicitar conversão. A política também alcança as tentativas internas do Media3.
4. **Pausa ao sair ou perder o áudio.** Home, troca de aplicativo, saída do primeiro plano e perda de foco de áudio pausam e salvam o ponto. Recuperações agendadas são canceladas, e o próximo episódio não inicia em segundo plano. O pré-carregamento é liberado ao sair. Voltar ao aplicativo não retoma automaticamente: pressione Play.

## Validação

- 81 testes Android aprovados; análise debug com zero erros, 51 avisos e uma dica preexistentes.
- 44/44 suítes Node aprovadas.
- Diário local: reinício do processo, consolidação do último ponto, isolamento de perfil/servidor/pareamento, confirmação concorrente, reenvio automático, gravação interrompida e erro de armazenamento visível.
- Perfis: 40 transações concorrentes sem perda; recuperação após falha de escrita e arquivo corrompido; atualização offline antiga rejeitada sem impedir um reinício explícito recente.
- Servidor HTTP real com biblioteca temporária: 12 progressos da TV, 12 favoritos do navegador e duas alterações de preferências em paralelo, sem perda. Importação preserva dados recentes e cópia antiga integral retorna 409. Nenhum perfil pessoal é usado nesse teste.
- Witch Hat Atelier S01E01, a partir de 10:00: HLS preparado, 10 segmentos decodificados e 60 segundos recebidos e decodificados através do servidor HTTP. Não equivale a uma sessão prolongada na TV física.
- Nenhuma TV conectada por ADB. Ainda é necessário confirmar Home, disputa de áudio e rede instável no modelo real após instalar a atualização.

Nos testes Windows, apenas a operação interna de renomear do AtomicFile é adaptada à substituição atômica POSIX usada pelo Android. O restante da implementação real permanece em execução; a gravação de produção também confirma os bytes persistidos e não reconhece uma gravação que falhou. Referência: [AtomicFile do Android](https://developer.android.com/reference/android/util/AtomicFile).

## Publicação

APK release assinado **1.0.34 (35)**, 3.245.911 bytes. SHA-256: `9F7E2A996AF0486151434AF6CD4DC83F71D2893FA0F7449B227D0171745B6743`.

O servidor local foi reiniciado com o código novo e o pacote foi publicado para atualização pela TV pareada. A instalação exige confirmação no Android; não há instalação silenciosa. APK, credenciais e dados pessoais não são enviados ao GitHub.

Consulta autenticada confirmou a oferta de 1.0.34 para um cliente 1.0.33. O download completo conferiu com o tamanho e SHA-256 do manifesto; um cliente já em 1.0.34 não recebeu nova oferta. O dispositivo temporário foi removido ao concluir essa verificação.

## Limites e teste no aparelho

- Queda total da rede, arquivo corrompido ou equipamento insuficiente ainda podem interromper o vídeo. Essas mudanças protegem o progresso e tornam a recuperação mais previsível; não prometem ausência absoluta de travamentos.
- A ordenação de gravações offline usa a data capturada na TV. Mantenha data e hora automáticas nos aparelhos. A proteção de concorrência do servidor pressupõe uma única instância do BRasa sobre a mesma biblioteca.
- A sincronização não requer reprodução em segundo plano: se o Android encerrar o processo, ela continua quando o aplicativo for aberto novamente. Falta de espaço é informada e não é tratada como gravação bem-sucedida.
- Para conferir na TV: assistir por um minuto, desligar temporariamente a conexão do computador, continuar, apertar Home, reabrir o BRasa e reconectar o computador. Conferir o ponto preservado, o aviso de sincronização e a retomada somente após Play. Repetir com outro aplicativo usando áudio e com dois aparelhos alterando favoritos/progresso.

## Repetir automaticamente

```powershell
npm test
cd apps/android-tv
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug -PbrasaRobolectricRuntimeDir=C:/Users/Public/BRasaTVTestRuntime
```
