# Integração Android TV — API local v1

O aplicativo Android TV usa apenas a API local e nunca acessa diretamente os arquivos internos do catálogo. Todas as respostas JSON usam o envelope `{ "ok": true, "data": ... }`.

## Descoberta e compatibilidade

Quando o acesso LAN está ativo, o servidor anuncia `_brasa._tcp.local` com TXT `api=1`. A descoberta é uma conveniência: o usuário sempre pode informar um IPv4 privado ou nome `.local`. Antes do pareamento, `GET /api/v1/bootstrap` informa nome, versão da API e capacidades.

## Pareamento e autenticação

- `POST /api/device-pairing/start`
- `GET /api/device-pairing/status/:requestId`
- `GET /api/tv/profiles`

Após aprovação no computador, o token é entregue uma única vez. O Android o cifra com uma chave não exportável do Android Keystore. Requisições autenticadas enviam `X-BRasa-Device-Token` somente para a mesma origem pareada. Resposta `401` encerra a sessão local e exige novo pareamento.

## Catálogo e reprodução

- `GET /api/v1/tv/home?profileId=:id`: linhas genéricas da Home.
- `GET /api/tv/catalog?profileId=:id`: catálogo completo e fallback compatível.
- `GET /api/v1/tv/playback/:mediaKey?profileId=:id`: URL relativa de stream, MIME, retomada, legendas e próximo episódio.
- `GET|HEAD /api/tv/stream/:mediaKey?profileId=:id`: mídia com suporte a Range.
- `PUT /api/tv/profiles/:profileId/progress/:mediaKey`: progresso em segundos.
- `PUT|DELETE /api/tv/profiles/:profileId/favorites/:mediaKey`: Minha lista.
- `POST /api/tv/profiles/:profileId/verify-pin`: validação de perfil protegido.

Os endpoints `/api/v1` são aditivos. A interface web e as rotas de TV existentes continuam funcionando. Clientes fora de endereços privados são recusados, e o acesso remoto depende da configuração LAN do BRasa.
