# VSGI Condomínio — Ajuste do e-mail de encomenda

Para eventos `PACKID_RECEIVED`, o e-mail agora usa um modelo específico:

- Título: VSGI Condomínio
- Saudação: Olá,
- A descrição da encomenda aparece logo após a saudação
- A linha "Detalhes" foi removida da tabela
- A tabela mantém Condomínio, Unidade, Alteração, Data/Hora e Realizado por
- Os demais tipos de notificação continuam usando o modelo genérico anterior

Não há migration de banco nem alteração no frontend.
