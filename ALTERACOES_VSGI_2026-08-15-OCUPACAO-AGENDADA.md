# Correção de nova ocupação

- Permite nova ocupação no mesmo dia em que a anterior foi encerrada.
- Permite informar data futura; nesse caso a ocupação fica com status SCHEDULED (Agendada).
- Ao chegar a data agendada, a ocupação é ativada automaticamente quando a unidade é consultada ou utilizada.
- Evita criar uma segunda ocupação quando já existe uma ativa ou agendada.
- Mensagens de erro de regra de negócio agora são devolvidas ao frontend de forma legível.
