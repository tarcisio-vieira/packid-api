# Correção da view do condômino

Corrigido erro 500 ao abrir a visão consolidada do apartamento.

## Causa
As consultas de visitas e entregas usavam parâmetros opcionais `fromTs`/`toTs` com a construção JPQL `:param is null or ...`.
No PostgreSQL, essa forma podia gerar `SQLState 42P18 - could not determine data type of parameter`.

## Correção
As consultas foram separadas em quatro cenários, evitando parâmetros nulos sem tipo:
- sem período;
- somente data inicial;
- somente data final;
- data inicial e final.

Arquivos alterados:
- `VisitorVisitRepository.java`
- `VisitorVisitService.java`
- `DeliveryRecordRepository.java`
- `DeliveryRecordService.java`

Nenhuma migration de banco é necessária.
