VSGI CONDOMINIO - BACKEND CONSOLIDADO - 2026-10-05

Base:
- vsgi-condominio-api-v10-ajustes-fotos

Inclui migrations ate:
- db.changelog-0039-registry-photo-visibility.yaml

Inclui, entre outros:
- reservas de ambientes e convidados
- login individual do morador
- comunicados
- biblioteca e RI / documentos gerenciados
- boletos / integracao bancaria
- configuracoes de visibilidade de fotos
- foto de documento em prestadores/entregadores

Ajuste posterior incorporado:
- upload Spring multipart: arquivo ate 30 MB
- request multipart: ate 35 MB

Em producao o Nginx tambem precisa aceitar o tamanho configurado (50 MB foi a
configuracao utilizada/recomendada no servidor app.vsgi.com.br).
