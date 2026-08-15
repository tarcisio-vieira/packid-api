# PackID - página, bloco e apartamento separados

A entrada manual da unidade segue agora:

- 3 primeiros dígitos: página do livro (001 a 999)
- 4º dígito: bloco (1 a 4)
- demais dígitos: apartamento (1º ao 12º andar)

Exemplos:

- `0992608` -> página `099`, bloco `2`, apartamento `608`
- `10141203` -> página `101`, bloco `4`, apartamento `1203`

No banco `pack_id`:

- `book_page`: página do livro
- `building_block`: bloco
- `apartment`: apartamento

A migration `0015` também converte os registros recentes do formato antigo em que `building_block` guardava a página e `residential_unit.code` guardava bloco+apartamento.

A visão consolidada do condômino passa a consultar PackIDs diretamente por `tenant_id + building_block + apartment`.
