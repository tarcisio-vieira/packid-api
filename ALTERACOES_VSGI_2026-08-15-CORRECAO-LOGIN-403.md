# Correção de login local - HTTP 403 / CORS

## Problema identificado

O `app.frontend-url` de desenvolvimento é `http://localhost:5173/packid/` porque essa URL também é usada no redirect do OAuth.
O `CorsConfig`, porém, estava usando essa URL completa como `allowedOrigins`.

O navegador envia o header:

`Origin: http://localhost:5173`

Como Origin não contém path, o Spring rejeitava a chamada com 403 antes de chegar ao controller. No frontend isso aparecia como falha de conexão.

## Correção

`CorsConfig` agora extrai somente `scheme://authority` da URL configurada:

- URL do frontend: `http://localhost:5173/packid/`
- Origin liberada: `http://localhost:5173`

Também foram liberados os headers `X-Actor` e `Accept`, usados pelas chamadas da aplicação.

## Seed de desenvolvimento

Foi corrigido o e-mail incorreto no `load/script.sql`:

- antes: `tarcisio.vieira.dom@google.com`
- agora: `tarcisio.vieira.dom@gmail.com`

Esse script não altera automaticamente um banco já existente. Para banco existente, confirme o cadastro em `app_user`.
