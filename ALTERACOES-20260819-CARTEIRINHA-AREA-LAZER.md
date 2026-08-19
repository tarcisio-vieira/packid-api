# Alterações 19/08/2026 — VSGI Condomínio / PackID API

## Banco de dados
- Liquibase 0030 adiciona workflow de validação da carteirinha: review_status, envio do laudo, validação, usuário validador e observações.

## Carteirinhas
- Status PENDING_REVIEW / APPROVED / REJECTED.
- Aprovação/reprovação somente por ADMIN/SECRETARY.
- Busca paginada com filtros de vencimento.
- Lista de laudos pendentes de análise.
- PDF bloqueado para carteirinha não aprovada ou vencida.

## Portal do morador
- Envio de laudo por sessão do morador, sem autenticação Google do morador.
- Arquivo é salvo usando a conta Google oficial conectada ao condomínio.
- Atualização de documento, telefone, e-mail, profissão e foto.
- Foto também é enviada pelo backend usando a conta Google oficial do condomínio.

## Área de lazer
- Endpoint paginado do histórico.
- Regularização manual de uma pendência de chave.
- Regularização em lote das pendências abertas, opcionalmente por área.
- Histórico preserva responsável, data/hora e anotação de auditoria.
- PORTER pode executar as ações operacionais de chave; validação de carteirinha continua restrita a ADMIN/SECRETARY.
