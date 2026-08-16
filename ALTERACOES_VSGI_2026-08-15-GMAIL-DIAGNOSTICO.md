# VSGI Condomínio - correção e diagnóstico da integração Gmail

## Problema observado
O Gmail retornava HTTP 401/403 durante o envio e o backend convertia qualquer um desses retornos para a mensagem genérica de que a conta deveria ser reconectada. Por isso, reconectar a conta não resolvia situações em que a Gmail API não estava habilitada no projeto Google Cloud ou quando o escopo gmail.send não havia sido concedido.

## Alterações
- A conexão da conta Google oficial agora valida explicitamente os escopos `drive.file` e `gmail.send` antes de considerar a integração ativa.
- Respostas 401/403 do Gmail passam a ser classificadas:
  - Gmail API desabilitada no projeto Google Cloud;
  - permissão `gmail.send` ausente;
  - autorização expirada/revogada;
  - outros bloqueios do Google.
- O motivo fica registrado em `tenant_google_account.last_error` e aparece em Configurações.
- Foi adicionado o botão **Testar Gmail** em Configurações. Ele envia um e-mail de teste para a própria conta oficial do condomínio.
- Em caso de sucesso, o erro anterior da integração é limpo.
- Não há nova migration de banco.

## Preparação externa obrigatória
A Gmail API deve estar habilitada no mesmo projeto Google Cloud usado pelo `GOOGLE_CLIENT_ID` do VSGI. Isso é configuração do projeto OAuth e não precisa de senha do Gmail nem de variáveis `VSGI_MAIL_*` na EC2.
