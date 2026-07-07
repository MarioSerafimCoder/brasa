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

## Stack

- HTML5
- CSS3
- JavaScript ES Modules
- OMDb API para metadados de filmes
- OpenSubtitles API para legendas

## Sincronizar filmes

1. Crie um arquivo `.env` na raiz com:

   ```env
   OMDB_API_KEY=sua-chave-da-omdb
   OPENSUBTITLES_API_KEY=sua-chave-da-opensubtitles
   SUBTITLE_LANGUAGES=pt-br,en
   ```

2. Adicione o video em `assets/movies`.

3. Rode na pasta do projeto:

   ```powershell
   cd "C:\Users\Mario\Videos\Brasa"
   .\scripts\sync-movies.ps1
   ```

Se a OMDb retornar o filme errado, ajuste `data/movie-overrides.json` usando o nome exato do arquivo e o `imdbId` correto.

## Botao Atualizar

Para usar o botao `Atualizar` no menu esquerdo, abra o BRasa pelo servidor local:

```powershell
cd "C:\Users\Mario\Videos\Brasa"
.\scripts\start-brasa.ps1
```

Depois acesse:

```text
http://127.0.0.1:4173/
```

Esse servidor libera o endpoint local `/api/sync`, que roda o mesmo processo do comando manual.

## Status

Em desenvolvimento.
