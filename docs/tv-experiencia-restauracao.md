# Melhorias de experiência da TV e ponto de restauração

## Estado preservado

- Commit funcional: `8d0fc1b7724cf2dc627729dba560902cf9e6f615`.
- Tag: `brasa-tv-restauracao-2026-09-05`.
- Branch de implementação: `codex/tv-experiencia-segura`.
- Cópia externa: `C:\Users\pc mario\Pictures\BRasa-restauracao-2026-09-05`.
- APK publicado preservado: 1.0.28, versionCode 29.
- SHA-256 do APK preservado: `4AC603FE34B4CDCB2DD93BB3497210AE3720C0AE1C1E384B036C1777F8F13C52`.

O ZIP guarda o código e o catálogo; o bundle guarda o histórico Git. Filmes,
chave de assinatura e configurações privadas permanecem nas pastas originais.
Essas cópias não são um backup dos arquivos de vídeo nem dos dados privados.

## Escopo

1. Atualizações comuns podem ser adiadas e erros na consulta não bloqueiam a biblioteca.
   A indicação explícita `mandatory` do manifesto continua respeitada.
2. Busca no tempo usa o player para arquivos seekable com Range, mesmo fora do buffer.
   HLS preserva o fallback existente ao sair da janela preparada ou ocorrer uma falha.
3. Áudio e legenda são selecionados por faixa, não apenas por idioma. Sem legenda é
   uma opção explícita. Preferências de idioma e aparência são locais e por perfil.
9. Busca, filtros, ordenação, posição das listas e cartão selecionado são preservados
   ao voltar de detalhes. A biblioteca oferece ordem por nome, ano e data de adição,
   e filtro de não assistidos (inclui em andamento, exclui concluídos ou >=95%).

O servidor, o processamento FFmpeg, as capacidades HDR, os buffers, o pareamento
e os arquivos de vídeo não são modificados por estas melhorias.

## Verificações automatizadas

- Servidor: `npm test`, 38/38 suítes aprovadas.
- Android: 47/47 testes de unidade e de interface simulada aprovados; análise lint,
  APK debug e compilação dos testes instrumentados concluídos com sucesso.
  Sem instalação em uma TV física.
- Cobertura nova: regras de atualização obrigatória/opcional, saída após erro,
  seek direto e HLS, seleção individual de faixas, desligar/religar legenda,
  isolamento de preferências, menu de legenda, ordenação, filtro de assistidos,
  retorno ao cartão e posição da biblioteca após rolagem.
- Durante esta validação, a versão e o manifesto publicados não foram substituídos.
  A publicação posterior deve usar uma versão superior; o APK debug é somente de
  validação e não deve ser instalado sobre o release.

Para reproduzir as verificações Android, com o SDK configurado:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

No Windows, o carregador nativo do Robolectric pode falhar quando o caminho do
cache contém espaços. Nesta máquina foi usada uma cópia do JAR de runtime Android
35 em `C:\Users\Public\brasa-tv-test-runtime`, acrescentando
`-PbrasaRobolectricRuntimeDir=C:/Users/Public/brasa-tv-test-runtime` ao comando.
Essa opção afeta somente os testes e não o aplicativo. Em outras máquinas, pode
ser omitida ou apontar para outro diretório de runtime Robolectric.

## Recuperação sem perder as melhorias

Para abrir o código funcional em outra pasta:

```powershell
git worktree add -b codex/restauracao-tv ../brasa-tv-restaurado brasa-tv-restauracao-2026-09-05
```

Não use reset destrutivo e não extraia o ZIP por cima do trabalho atual.
Para recuperar sem o repositório original, clone o arquivo `historico-git.bundle`
para uma pasta nova e selecione a tag de restauração.

O Android normalmente recusa instalar um versionCode inferior sobre o atual.
Após instalar uma versão nova, o rollback recomendado é compilar a tag funcional
com versionCode maior que o instalado, usando a mesma assinatura. Não é necessário
desinstalar o aplicativo nem apagar o pareamento. Peça as senhas somente no formulário
`ASSINAR ATUALIZACAO BRASA TV.vbs`, aberto por `explorer.exe`.

## Validação de aceitação na TV

- Atualização comum e erro de consulta: entrar na biblioteca com Continuar/Voltar.
- Atualização explicitamente obrigatória: não ignorar a exigência.
- MP4/MKV direto: avançar para fora do buffer e voltar dez segundos.
- HLS: buscar dentro e fora da janela, incluindo antes do ponto de retomada.
- Falha de decoder e interrupção de rede: confirmar que a recuperação permanece.
- Desativar e reativar legenda várias vezes; selecionar duas faixas do mesmo idioma.
- Trocar perfil: conferir isolamento das preferências de áudio e legenda.
- Buscar, filtrar, rolar e abrir detalhes: Voltar retorna ao mesmo cartão.
- Repetir retorno em Início, infantil, Filmes, Séries, Coleções e Veja mais.

Os testes locais e de interface simulada não substituem a validação dos codecs,
HDR e controle remoto no aparelho físico. O ponto de restauração preserva o release
anterior; o APK debug não substitui o release assinado.
