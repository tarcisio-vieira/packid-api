# VSGI Condomínio — login separado e Área de lazer

Alterações desta versão:

- `/packid/colaborador`: entrada da secretaria/portaria por Google OAuth2.
- `/packid/user`: entrada do morador por condomínio, bloco, apartamento, usuário e senha.
- O login do morador carrega os condomínios ativos pelo endpoint público `GET /public/tenants`.
- A rota raiz `/packid/` é normalizada para `/packid/colaborador`.
- O retorno do OAuth2 e o logout dos colaboradores passam a voltar para `/colaborador`.
- Adicionado fallback SPA no back-end para `/colaborador` e `/user`.
- "Espaços e chaves" foi renomeado para "Área de lazer".
- "Área de lazer" foi incorporada como aba da Gestão do condomínio.
- Áreas disponíveis: Brinquedoteca, Sala de Jogos, Academia e Sauna.
- Sauna está disponível no portal do morador, notificações, filtros, relatório e histórico.
- Corrigido o erro PostgreSQL SQLState `42P18` do relatório de área de lazer, removendo parâmetros nulos usados em condições `:param is null`.
- Mantida a correção do cabeçalho `X-Content-Type-Options` no `ResidentPortalController`.

Observação: `space_type` já é `varchar(40)`, portanto a inclusão de `SAUNA` não exige alteração física da tabela.
