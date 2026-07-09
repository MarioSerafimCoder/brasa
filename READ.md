# BRasa

Uma plataforma pessoal para gerenciamento e reproducao da minha biblioteca de filmes.

## Objetivos

- Interface inspirada em Apple TV e Plex
- Biblioteca pessoal em 4K
- Player proprio
- Busca inteligente
- Colecoes
- Favoritos
- Continuacao automatica
- Metadados via OMDb
- Legendas via OpenSubtitles

## Abrir a aplicacao

De dois cliques em:

```text
Abrir BRasa.vbs
```

Ao abrir, o BRasa faz automaticamente:

1. Inicia o servidor local escondido.
2. Atualiza a biblioteca antes de abrir a interface.
3. Detecta filmes novos em `assets/movies`.
4. Busca metadados na OMDb.
5. Renomeia arquivos identificados para o padrao `Titulo (Ano) [imdbId].ext`.
6. Baixa capas e legendas quando disponiveis.
7. Abre o navegador no endereco local correto.

Para encerrar o servidor:

```text
Parar BRasa.vbs
```

## Configuracao

O arquivo `.env` na raiz deve conter:

```env
OMDB_API_KEY=sua-chave-da-omdb
OPENSUBTITLES_API_KEY=sua-chave-da-opensubtitles
SUBTITLE_LANGUAGES=pt-br,en
```

Se a OMDb retornar o filme errado, ajuste `data/movie-overrides.json` usando o nome exato do arquivo e o `imdbId` correto.

## Status

Em desenvolvimento.
