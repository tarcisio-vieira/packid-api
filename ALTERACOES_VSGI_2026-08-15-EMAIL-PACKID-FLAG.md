# VSGI Condomínio — e-mail no PackID e controle de notificações

## Alterações

### 1. E-mail quando uma encomenda é registrada no PackID
Ao registrar uma nova encomenda com bloco e apartamento, o backend localiza todos os condôminos ativos daquela unidade que possuem e-mail cadastrado e publica uma notificação após o commit da transação.

A mensagem informa:
- condomínio;
- bloco e apartamento;
- alteração: `Encomenda recebida`;
- código da encomenda;
- página do livro, quando informada;
- data/hora;
- usuário que realizou o registro.

O envio usa a conta Google oficial configurada no tenant, via Gmail API.

### 2. Flag de notificações em Configurações
Foi adicionado em **Configurações do condomínio** o controle:

`Enviar e-mails automáticos aos condôminos`

- Ativo: alterações da unidade e novas encomendas do PackID podem disparar e-mails.
- Inativo: todos os disparos automáticos para os condôminos são suspensos.
- O botão manual **Testar Gmail** continua funcionando mesmo com a flag desativada.

O valor é persistido em `condominium.email_notifications_enabled`.

### 3. Banco de dados
Nova migration Liquibase:

`db.changelog-0020-email-notification-settings.yaml`

A coluna é criada como `boolean NOT NULL DEFAULT true`, preservando o comportamento atual para instalações existentes.

## Observações
- O envio continua assíncrono e ocorre após o commit; uma falha do Gmail não desfaz o cadastro da encomenda.
- Somente condôminos ativos, não excluídos e com e-mail válido cadastrado recebem a mensagem.
- O log continua sendo gravado em `email_notification_log`.
