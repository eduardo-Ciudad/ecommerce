# 1. Resumo executivo

O backend apresenta boa organização básica e alguns controles relevantes — JWT, RBAC, validação HMAC de webhooks, optimistic locking em estoque, proteção contra IDOR em vários serviços e Flyway —, mas ainda não está seguro
para operar um e-commerce em produção sem ajustes importantes.

Foram identificados 22 problemas:

Severidade    Confirmados    Hipóteses    Total                                                                                                                                                                                  
━━━━━━━━━━━━  ━━━━━━━━━━━━━  ━━━━━━━━━━━  ━━━━━━━
Crítico                 2            0        2
────────────  ─────────────  ───────────  ───────
Alto                    7            1        8
────────────  ─────────────  ───────────  ───────
Médio                   7            1        8
────────────  ─────────────  ───────────  ───────
Baixo                   4            0        4

Principais riscos:

- possível cobrança duplicada quando o Mercado Pago confirma uma operação, mas a transação local falha;
- callback OAuth do Bling aceita ausência de state, permitindo substituição indevida da integração;
- estoque decrementado antes do pagamento e nunca devolvido em rejeição/cancelamento;
- pedidos pendentes retêm estoque indefinidamente;
- eventos de pagamento podem alterar estados sem respeitar a máquina de transições;
- rate limiting contornável por cabeçalhos fornecidos pelo cliente;
- configuração Docker documentada não fornece todas as variáveis obrigatórias;
- tokens OAuth do ERP ficam armazenados em texto legível;
- ausência de paginação, índices e testes reais de concorrência/transação.

Os fluxos mais afetados são pedido, estoque, pagamento, cancelamento, sincronização com ERP e autenticação.

———

# 2. Escopo analisado

## Tecnologias e módulos

- Java 17.
- Spring Boot 3.5.14.
- Spring MVC, Security, Data JPA/Hibernate, Validation e Mail.
- PostgreSQL 16.
- Flyway, com 21 migrations.
- JWT via JJWT 0.12.6.
- Mercado Pago SDK 2.1.29.
- AWS SDK S3 2.25.60, direcionado ao Cloudflare R2.
- Bucket4j/Caffeine.
- Integrações HTTP com Correios e Bling.
- Maven Wrapper 3.9.16.
- Docker/Docker Compose e GitHub Actions.

Foram inspecionados os 114 arquivos de src/main, aproximadamente 3.999 linhas, os 14 arquivos de teste, aproximadamente 2.015 linhas, migrations, configurações, Docker, CI, histórico recente do Git e relatórios existentes do
Surefire.

## Git

- Branch: main.
- HEAD: b62c790.
- Nenhuma alteração rastreada.
- .claude/ está não rastreado.
- .env, .idea, HELP.md e target já estavam ignorados.
- Nenhum arquivo foi alterado ou revertido.

## Comandos identificados

- Inicialização local: ./mvnw spring-boot:run.
- Inicialização Docker: docker compose up --build.
- Testes: ./mvnw test.
- CI: mvn clean verify -B.

## Validações executadas

Somente leitura:

- listagem e leitura de arquivos;
- git status, git log, git show, git blame e estatísticas de diff;
- buscas textuais;
- inspeção dos relatórios Surefire existentes;
- busca estática por padrões de secrets, exceptions e operações de risco.

Não executei Maven, testes, aplicação ou Docker porque esses comandos alterariam target, caches ou artefatos. Também não foram acessados PostgreSQL, Mercado Pago, Correios, SMTP, R2 ou Bling.

## Logs

Não existem logs operacionais ou stack traces no repositório. Há apenas relatórios Surefire antigos, de 26/07/2026, sem falhas. Esses relatórios cobrem 71 testes executados; a árvore atual possui 116 métodos anotados com
@Test, portanto não representam necessariamente o estado atual.

———

# 3. Arquitetura identificada

A aplicação é um monólito Spring organizado em:

Controller REST
→ Service de negócio
→ Repository JPA
→ PostgreSQL
→ Clientes externos
├── Mercado Pago
├── Correios
├── Bling
├── Cloudflare R2/S3
└── SMTP

Responsabilidades:

- controller: transporte HTTP e obtenção do usuário autenticado;
- service: regras de negócio e conversão para DTOs;
- domain: entidades e repositories;
- infra/security: JWT, autorização e rate limiting;
- infra/payment: validação HMAC;
- infra/shipping e infra/bling: clientes HTTP;
- email: mensagens assíncronas;
- Flyway: criação e evolução do schema.

## Fluxo de pedido, estoque e pagamento

1. O pedido é criado a partir do carrinho.
2. O frete é consultado sincronicamente nos Correios.
3. Os preços atuais das variantes são copiados para OrderItem.
4. O estoque é decrementado imediatamente.
5. O carrinho é esvaziado e o pedido fica PENDING.
6. Em outra requisição, o pagamento é criado no Mercado Pago.
7. O pedido recebe paymentId e paymentStatus.
8. O webhook consulta novamente o pagamento no Mercado Pago e altera o pedido.

Não foram encontrados fluxos implementados de expiração da reserva, restituição de estoque, estorno, devolução, emissão fiscal ou envio do pedido ao Bling.

———

# 4. Exceptions e causas-raiz

Não há ocorrências reais disponíveis para calcular frequência ou apontar uma primeira linha útil de stack trace. As situações abaixo são inferidas estaticamente.

## Grupo EX-01 — JWT inválido produz erro não normalizado

- Exception provável: JwtException, ExpiredJwtException ou MalformedJwtException.
- Primeira linha da aplicação: src/main/java/com/eduardo/ecomerce/infra/security/JwtFilter.java:39.
- Causa-raiz: parsing do JWT ocorre no filtro sem tratamento.
- Fluxo: qualquer endpoint com Authorization: Bearer ....
- Consequência provável: resposta 500 ou resposta gerada pelo container, em vez de 401 padronizado.
- Reprodução segura futura: MockMvc com JWT truncado, expirado e assinatura inválida.
- Correção recomendada: capturar apenas exceptions JWT esperadas, limpar o contexto e responder 401.
- Testes: filtro com token malformado, expirado, refresh e assinatura inválida.

Status: hipótese, porque o comportamento final depende da cadeia de filtros/configuração do runtime.

## Grupo EX-02 — Payload de webhook malformado

- Exceptions prováveis: ClassCastException e NullPointerException.
- Primeira linha: src/main/java/com/eduardo/ecomerce/controller/PaymentController.java:62.
- Causa-raiz: cast não validado de payload.data para Map.
- Condições: type=payment sem data, ou data com tipo diferente.
- Impacto: HTTP 500, ruído de logs e retries do provedor.
- Correção: DTO explícito e validação estrutural antes do cast.
- Testes: corpo sem data, data=null, array, ID ausente e tipos desconhecidos.

Status: confirmado pelo caminho de código; não há ocorrência real registrada.

## Grupo EX-03 — Respostas externas nulas ou incompatíveis

- Exceptions prováveis: NullPointerException, NumberFormatException.
- Locais:
    - src/main/java/com/eduardo/ecomerce/infra/shipping/CorreiosClient.java:73;
    - src/main/java/com/eduardo/ecomerce/service/ShippingService.java:57;
    - src/main/java/com/eduardo/ecomerce/service/BlingService.java:273;
    - src/main/java/com/eduardo/ecomerce/service/BlingService.java:465.

- Causa-raiz: uso de get(...).asText() sem validar resposta, campo ou formato.
- Consequência: frete convertido em erro 422 genérico; Bling tende a produzir 500.
- Correção: validação do contrato de resposta e exceptions de integração tipadas.
- Testes: respostas vazias, nulas, sem campos, números inválidos e erros HTTP.

Status: confirmado como caminho de falha; frequência desconhecida.

## Grupo EX-04 — Exceptions mascaradas

- Local: src/main/java/com/eduardo/ecomerce/service/PaymentService.java:162.
- Causa-raiz: catch (Exception) converte erros de domínio, parsing, banco e provedor na mesma BusinessException.
- Impacto: perda da semântica HTTP, diagnóstico difícil e retries incorretos.
- Correção: handlers específicos e preservação da causa/categoria.
- Testes: pagamento inexistente, external reference inválida, divergência de valor, timeout e erro de persistência.

Status: confirmado.

———

# 5. Problemas por severidade

## Críticos

### AUD-001 — Pagamento externo sem idempotência transacional

- Severidade/categoria/status: CRÍTICO, pagamento e consistência distribuída, confirmado.
- Localização: src/main/java/com/eduardo/ecomerce/service/PaymentService.java:42, especialmente linhas 44, 80 e 82–90.
- Evidência: paymentClient.create(...) executa dentro de @Transactional e antes de o paymentId ser persistido. Não há chave de idempotência enviada ao provedor.
- Causa-raiz: efeito externo e commit local não são atômicos.
- Cenário: Mercado Pago cria/aprova a cobrança, mas ocorre timeout, exception ou rollback antes de salvar paymentId. Um retry vê paymentId == null e cria outra cobrança.
- Impacto: cobrança duplicada, contestação, estorno manual e dano financeiro.
- Recomendação: adotar identificador idempotente persistido antes da chamada; separar estados PAYMENT_CREATING/PROCESSING; usar idempotency key do provedor e reconciliação por externalReference.
- Testes: timeout após criação remota, falha de commit, duplo clique concorrente e retry com mesma chave.
- Regressão: alta, pois altera o protocolo pedido–pagamento.
- Prioridade: P0, antes de tráfego real.

### AUD-002 — Callback OAuth do Bling aceita ausência de state

- Severidade/categoria/status: CRÍTICO, segurança OAuth/ERP, confirmado.
- Localização: src/main/java/com/eduardo/ecomerce/controller/BlingController.java:27, src/main/java/com/eduardo/ecomerce/infra/security/SecurityConfig.java:67, src/main/java/com/eduardo/ecomerce/service/BlingService.java:465.
- Evidência: callback público; state é opcional; quando ausente apenas gera warning e o código é trocado. saveToken substitui o token atual.
- Causa-raiz: validação CSRF OAuth tratada como opcional.
- Cenário: código OAuth de outra autorização é enviado ao callback sem state.
- Impacto: associação da loja a uma conta Bling indevida e posterior importação de catálogo/preço/estoque incorretos.
- Recomendação: tornar state obrigatório, de uso único, associado ao administrador/sessão e armazenado de forma compartilhada com expiração curta.
- Testes: callback sem state, state inválido, reutilizado, expirado e emitido em outra instância.
- Regressão: média.
- Prioridade: P0.

## Altos

### AUD-003 — Estoque nunca é devolvido em rejeição ou cancelamento

- Severidade/categoria/status: ALTO, estoque/pagamento, confirmado.
- Locais: src/main/java/com/eduardo/ecomerce/service/OrderService.java:105, src/main/java/com/eduardo/ecomerce/service/PaymentService.java:85, src/main/java/com/eduardo/ecomerce/service/OrderService.java:135.
- Evidência: estoque é baixado na criação; pagamento rejeitado ou cancelamento muda apenas o status.
- Impacto: estoque fantasma, produtos artificialmente indisponíveis e perda de vendas.
- Recomendação: modelar reserva separada da baixa definitiva e restituição idempotente.
- Testes: rejeição, cancelamento manual, cancelamento repetido e concorrência com nova compra.
- Regressão: alta.
- Prioridade: P0/P1.

### AUD-004 — Reservas PENDING não expiram

- Severidade/categoria/status: ALTO, disponibilidade de estoque, confirmado.
- Evidência: não existe campo de expiração, job de pedidos pendentes ou liberação automática; o único job limpa tokens de senha.
- Cenário: usuário cria pedido e abandona o pagamento.
- Impacto: retenção ilimitada do estoque.
- Recomendação: reserva com prazo, job de expiração e liberação transacional/idempotente.
- Testes: expiração, webhook chegando no limite e job concorrente com aprovação.
- Regressão: alta.
- Prioridade: P1.

### AUD-005 — Webhook ignora a máquina de estados e não bloqueia o pedido

- Severidade/categoria/status: ALTO, concorrência e integridade, confirmado.
- Local: src/main/java/com/eduardo/ecomerce/service/PaymentService.java:125.
- Evidência: não há @Transactional nem findByIdForUpdate; o status é atribuído diretamente como PAID/CANCELLED.
- Cenário: webhook concorrente com atualização administrativa, ou pagamento mudando após cancelamento.
- Impacto: lost update, pedido cancelado voltando a pago ou pedido pago tornando-se cancelado sem estorno.
- Recomendação: processar sob transação/lock, validar identidade do pagamento e aplicar uma máquina de estados específica de pagamento.
- Testes: webhooks paralelos, aprovado após cancelamento e rejeitado após pago.
- Regressão: alta.
- Prioridade: P1.

### AUD-006 — Rate limiting contornável e não distribuído

- Severidade/categoria/status: ALTO, segurança, confirmado.
- Local: src/main/java/com/eduardo/ecomerce/infra/security/RateLimitFilter.java:69.
- Evidência: X-Forwarded-For e X-Real-IP são aceitos diretamente; buckets residem em memória por instância.
- Cenário: cliente altera o cabeçalho ou distribui requisições entre réplicas.
- Impacto: bypass de proteção contra brute force e abuso de pagamento/pedido.
- Recomendação: confiar em forwarded headers apenas de proxies conhecidos e usar armazenamento compartilhado ou rate limit no gateway.
- Testes: cabeçalhos falsificados, cadeia de proxies e duas instâncias.
- Regressão: média.
- Prioridade: P1.

### AUD-007 — Refresh tokens não são revogáveis

- Severidade/categoria/status: ALTO, autenticação, confirmado.
- Locais: src/main/java/com/eduardo/ecomerce/service/AuthService.java:120, src/main/java/com/eduardo/ecomerce/infra/security/JwtService.java:31.
- Evidência: refresh tokens são stateless, válidos por sete dias e não possuem jti, versão de credencial ou registro de revogação.
- Impacto: token roubado continua renovando acessos mesmo após reset/troca de senha.
- Recomendação: rotação com detecção de reutilização e revogação por usuário/sessão; incrementar versão após mudança de senha.
- Testes: reutilização, logout, reset de senha e duas sessões.
- Regressão: média/alta.
- Prioridade: P1.

### AUD-008 — Tokens OAuth do Bling armazenados em texto legível

- Severidade/categoria/status: ALTO, secrets/banco, confirmado.
- Local: src/main/java/com/eduardo/ecomerce/domain/blingtoken/BlingToken.java:22, migration V19.
- Evidência: access_token e refresh_token são colunas TEXT, sem proteção aplicada pela aplicação.
- Impacto: leitura indevida do banco concede acesso ao ERP.
- Recomendação: criptografia de aplicação com chave fora do banco, rotação e privilégios mínimos.
- Testes: round-trip criptografado, rotação e falha de descriptografia.
- Regressão: média.
- Prioridade: P1.

### AUD-009 — Docker Compose omite variáveis obrigatórias

- Severidade/categoria/status: ALTO, configuração/disponibilidade, confirmado para o comando documentado.
- Localizações: docker-compose.yml:19, src/main/resources/application.properties:51.
- Evidência: Compose injeta banco, JWT, MP, storage, CORS e mail, mas omite FRONTEND_URL, credenciais Correios e toda a configuração obrigatória do Bling.
- Impacto: falha de inicialização por placeholders não resolvidos ou ambiente parcialmente configurado.
- Recomendação: declarar todas as variáveis exigidas, validar configuração por perfil e manter exemplo completo sem valores reais.
- Testes: inicialização limpa do Compose com credenciais simuladas e teste de binding.
- Regressão: baixa.
- Prioridade: P1.

### AUD-010 — Respostas do Mercado Pago podem expor dados sensíveis em logs

- Severidade/categoria/status: ALTO, LGPD/observabilidade, hipótese fundamentada.
- Local: src/main/java/com/eduardo/ecomerce/service/PaymentService.java:115.
- Evidência: o conteúdo integral de MPApiResponse é registrado.
- Impacto: dependendo da resposta, logs podem conter dados do pagador, identificadores ou detalhes de pagamento.
- Recomendação: registrar código, request ID e categoria sanitizada; aplicar redaction.
- Testes: respostas simuladas contendo email/token e verificação de ausência no appender.
- Regressão: baixa.
- Prioridade: P1.

## Médios

### AUD-011 — Método de frete desconhecido é convertido silenciosamente em PAC

- Local: src/main/java/com/eduardo/ecomerce/service/ShippingService.java:42.
- Status: confirmado.
- Evidência: qualquer valor diferente de SEDEX seleciona PAC.
- Impacto: divergência entre escolha do cliente e pedido.
- Correção: enum/whitelist e rejeição de método desconhecido.
- Testes: PAC, SEDEX, vazio, espaços e valor arbitrário.
- Regressão: baixa. Prioridade P1.

### AUD-012 — Frete usa dimensões e peso fixos

- Local: src/main/java/com/eduardo/ecomerce/service/ShippingService.java:25.
- Status: confirmado.
- Evidência: todo carrinho usa 500 g e dimensões fixas, independentemente de itens/quantidades.
- Impacto: cobrança de frete incorreta e margem negativa.
- Correção: armazenar dados logísticos por produto/variante e calcular pacotes.
- Testes: múltiplas quantidades, produtos volumosos e limites dos Correios.
- Regressão: alta. Prioridade P1/P2.

### AUD-013 — Chamadas externas sem timeouts explícitos

- Locais: src/main/java/com/eduardo/ecomerce/infra/shipping/CorreiosClient.java:40, src/main/java/com/eduardo/ecomerce/infra/bling/BlingClient.java:37.
- Status: confirmado na configuração local; defaults internos das bibliotecas não foram validados.
- Impacto: threads HTTP presas, lock de banco prolongado e indisponibilidade em cascata.
- Correção: connect/read/request timeouts, circuit breaker e métricas.
- Testes: servidor lento, conexão recusada e resposta parcial.
- Regressão: média. Prioridade P1.

### AUD-014 — Constraints insuficientes no banco

- Local: migrations V4, V6, V7 e V8.
- Status: confirmado.
- Evidência: não há CHECK para preço/total positivos, estoque/quantidade não negativos ou estados permitidos.
- Impacto: corrupção lógica por bug, integração ou operação administrativa.
- Correção: constraints após auditoria dos dados e validação da migration.
- Testes: inserts negativos e transações concorrentes.
- Regressão: média/alta. Prioridade P2.

### AUD-015 — Único endereço padrão não é garantido pelo banco

- Local: src/main/java/com/eduardo/ecomerce/service/AddressService.java:77, migration V16.
- Status: confirmado.
- Evidência: a regra “um padrão por usuário” depende de leitura e escrita sem lock; não existe índice único parcial.
- Impacto: dois endereços padrão sob concorrência.
- Correção: índice único parcial e operação transacional protegida.
- Testes: duas promoções concorrentes.
- Regressão: média. Prioridade P2.

### AUD-016 — Listagens sem paginação e provável N+1

- Locais: src/main/java/com/eduardo/ecomerce/domain/product/ProductRepository.java:11, src/main/java/com/eduardo/ecomerce/domain/order/OrderRepository.java:15, src/main/java/com/eduardo/ecomerce/service/ProductService.java:43,
  src/main/java/com/eduardo/ecomerce/service/OrderService.java:120.

- Status: confirmado quanto à ausência de paginação; N+1 é hipótese fortemente sustentada pelos relacionamentos lazy e mapeamento.
- Impacto: memória excessiva e consultas crescentes com catálogo/histórico.
- Correção: Pageable, projeções e fetch plans específicos.
- Testes: contagem de queries e volumes representativos.
- Regressão: média. Prioridade P2.

### AUD-017 — Operações de storage não são consistentes com o banco

- Locais: src/main/java/com/eduardo/ecomerce/service/ProductService.java:78, src/main/java/com/eduardo/ecomerce/service/StorageService.java:100.
- Status: confirmado.
- Evidência: upload remoto, gravação no banco e exclusão anterior não são coordenados; falhas na exclusão são silenciosamente ignoradas.
- Impacto: objetos órfãos ou referência a imagem nova sem limpeza da anterior.
- Correção: workflow compensatório/outbox e observabilidade de falhas.
- Testes: falha no save e falha no delete após upload.
- Regressão: média. Prioridade P2.

### AUD-018 — Falhas assíncronas de email são silenciosas

- Local: src/main/java/com/eduardo/ecomerce/email/EmailService.java:24.
- Status: confirmado.
- Evidência: todos os métodos capturam qualquer exception, apenas registram e retornam; não há retry, fila ou estado.
- Impacto: cadastro pode responder sucesso sem que o cliente consiga verificar a conta; reset pode nunca chegar.
- Correção: outbox/fila, retries limitados, estado de entrega e reenvio seguro.
- Testes: SMTP indisponível, retry e envio duplicado.
- Regressão: média. Prioridade P2.

## Baixos

### AUD-019 — Logs registram emails completos

- Locais: src/main/java/com/eduardo/ecomerce/service/AuthService.java:62, src/main/java/com/eduardo/ecomerce/email/EmailService.java:43.
- Categoria/status: LGPD, confirmado.
- Impacto: ampliação desnecessária do tratamento de dados pessoais.
- Recomendação: identificador interno ou email mascarado; política de retenção.
- Prioridade: P2.

### AUD-020 — Datas locais sem timezone explícito

- Evidência: entidades e tokens usam LocalDateTime.now().
- Categoria/status: consistência temporal, confirmado.
- Impacto: expiração ou auditoria ambígua entre servidores/fusos.
- Recomendação: Instant/UTC e clock injetável.
- Testes: mudança de timezone e horário de verão.
- Prioridade: P3.

### AUD-021 — SecurityConfig duplicada e difícil de revisar

- Local: src/main/java/com/eduardo/ecomerce/infra/security/SecurityConfig.java:63.
- Status: confirmado.
- Evidência: matchers duplicados e múltiplas regras na mesma linha.
- Impacto: risco de erro futuro de precedência/autorização.
- Recomendação: uma regra por endpoint/grupo e testes da matriz de acesso.
- Prioridade: P3.

### AUD-022 — Documentação e nomenclatura divergentes

- Evidências:
    - README informa 17 migrations, mas existem 21;
    - nomes GabiKids, MiniModa e ecomerce coexistem;
    - README diz aplicação “em produção”, mas a integração Bling contém contratos explicitamente não confirmados;
    - Compose publica 8081, Dockerfile expõe 8080, exigindo atenção operacional.

- Impacto: operação e onboarding confusos.
- Recomendação: consolidar nome, estado de prontidão e inventário real.
- Prioridade: P3.

———

# 6. Segurança

## Controles positivos

- Senhas armazenadas com BCrypt.
- Registro força CLIENT; não há mass assignment de role.
- Consultas de endereço, carrinho e pedidos usam usuário autenticado.
- Comparação HMAC usa MessageDigest.isEqual.
- Upload confere tamanho, MIME declarado e magic bytes.
- .env está ignorado e não foi encontrado secret óbvio nos arquivos rastreados.

## Vulnerabilidades confirmadas

- OAuth Bling aceita callback sem state.
- Rate limit confia em headers falsificáveis.
- Refresh tokens não podem ser revogados.
- Tokens Bling persistidos sem proteção.
- Conteúdo completo de erro do pagamento pode alcançar logs.
- Emails pessoais são registrados.
- Webhook não verifica frescor do timestamp ts; ele participa da assinatura, mas não existe limite de idade. Isso amplia a janela de replay, embora a consulta posterior ao provedor reduza parte do impacto.
- Swagger/OpenAPI está público em todos os ambientes.
- CSRF está desativado, aceitável enquanto autenticação permanecer exclusivamente por bearer token e nenhuma credencial for automaticamente anexada pelo navegador.

Não foram encontradas queries SQL montadas por concatenação, desserialização Java arbitrária ou path traversal direto.

Não foi realizada auditoria online de CVEs. As versões devem ser verificadas futuramente em fonte de vulnerabilidades confiável e com SBOM, sem atualização automática durante a auditoria.

———

# 7. Banco de dados

- PostgreSQL e Flyway estão corretamente selecionados; ddl-auto=none reduz divergências acidentais.
- Entidades e schema estão majoritariamente alinhados, inclusive version e nullable de size.
- Não há CHECK para valores monetários, quantidades e estoque.
- Faltam índices explícitos nos FKs e consultas frequentes:
    - orders(user_id);
    - addresses(user_id, created_at);
    - password_tokens(expires_at, used);
    - cart_items(cart_id);
    - order_items(order_id);
    - potencialmente products(active).

- Listagens de pedidos, produtos e categorias não são paginadas.
- findByUserIdAndIsDefaultTrue pressupõe unicidade não garantida.
- BlingTokenRepository.findFirstByOrderByUpdatedAtDesc() não garante semanticamente uma única integração; a tabela permite múltiplas linhas.
- A reserva de estoque depende de @Version, mas não existem testes reais de concorrência contra PostgreSQL.
- Deleção de variantes referenciadas por carrinhos/pedidos tende a gerar conflito de integridade; o comportamento funcional não é modelado.
- Migrations V16 e V17 deixam campos do endereço do pedido nullable, embora o fluxo de criação sempre os preencha.

———

# 8. Qualidade e arquitetura

A separação controller/service/repository é compreensível, mas os services acumulam orquestração externa, transação, regras de domínio e mapeamento.

Pontos principais:

- PaymentService mistura comunicação externa e mutação transacional.
- OrderService concentra reserva, snapshot de preço/endereço, cálculo e limpeza do carrinho.
- BlingService possui aproximadamente 500 linhas e múltiplas responsabilidades.
- O domínio usa setters públicos, permitindo transições sem invariantes centralizados.
- OrderStatus.canTransition() existe, mas o webhook não o utiliza.
- Status do provedor é armazenado como String, sem enum/conversor.
- Catch genérico aparece nos fluxos mais sensíveis.
- Imports e configuração duplicados indicam ausência de checks estáticos.
- Não foram encontrados módulos de cupom, promoção, estorno, devolução, emissão fiscal ou eventos de domínio.

———

# 9. Testes e observabilidade

## Testes

A fonte contém 116 métodos @Test, predominantemente Mockito/unitários. As principais lacunas são:

- nenhum teste de integração completo com PostgreSQL/Flyway;
- nenhum teste de controllers e matriz real de autorização;
- nenhum teste de duplo clique ou concorrência de estoque;
- nenhum teste da janela “pagamento remoto confirmado, commit local falhou”;
- nenhum teste de webhook versus cancelamento concorrente;
- nenhum teste de expiração/restituição de reserva;
- nenhum teste visível do OAuth Bling;
- nenhum teste de JwtFilter com JWT malformado/expirado;
- nenhuma validação do Compose;
- relatórios existentes estão desatualizados e não incluem diversas classes atuais.

Os relatórios antigos indicam zero falhas, mas não constituem evidência de que a revisão atual compila ou passa.

## Observabilidade

- Há logs em services, mas sem request/correlation ID.
- Não existe configuração explícita de logging estruturado, apesar da afirmação no README.
- Não foram encontrados Micrometer, Actuator, métricas, tracing ou alertas.
- Não existem health checks de aplicação/integrações.
- Logs de email e pagamento podem conter PII.
- Falha de exclusão no storage não gera log ou métrica.
- Falhas de email não geram estado consultável.
- O job de tokens não possui coordenação para execução em múltiplas réplicas.

———

# 10. Recomendações priorizadas

## Ações imediatas

1. Suspender processamento real de pagamentos até estabelecer idempotência e reconciliação.
2. Rejeitar callback Bling sem state válido.
3. Definir política de reserva, expiração e restituição de estoque.
4. Impedir transições de webhook fora da máquina de estados.
5. Sanitizar logs de Mercado Pago.
6. Validar o Compose em ambiente isolado com todas as variáveis obrigatórias.

## Curto prazo

1. Tornar webhook transacional e concorrente-safe.
2. Adotar revogação/rotação de refresh tokens.
3. Corrigir rate limiting atrás de proxy confiável.
4. Configurar timeouts e circuit breaker.
5. Adicionar constraints e índices.
6. Implementar testes de integração e concorrência.
7. Criptografar tokens do ERP.

## Médio prazo

1. Extrair reserva de estoque e pagamento para componentes de domínio explícitos.
2. Usar outbox para email, storage e eventos.
3. Paginar catálogo e pedidos.
4. Adicionar métricas, health checks e correlation ID.
5. Padronizar tempo em UTC.
6. Formalizar políticas LGPD e retenção de logs.

## Futuro

- estorno, devolução e emissão fiscal;
- reconciliação periódica com Mercado Pago;
- sincronização incremental/event-driven com ERP;
- SBOM e análise contínua de dependências;
- testes de carga e chaos testing das integrações.

———

# 11. Plano sugerido de correção

Sem implementar alterações:

1. Estabilizar pagamentos
    - Arquivos prováveis: PaymentService, PaymentController, Order, OrderRepository, novas migrations.
    - Definir idempotency key, estados intermediários e reconciliação.
    - Testar falhas em cada fronteira entre provedor e commit.

2. Corrigir OAuth Bling
    - Arquivos: BlingController, BlingService, SecurityConfig.
    - Tornar state obrigatório, persistente/compartilhado e vinculado à autorização administrativa.

3. Modelar reserva de estoque
    - Arquivos: OrderService, PaymentService, ProductVariant, OrderStatus, migrations e job.
    - Definir reserva, confirmação, expiração e liberação idempotente.

4. Fortalecer webhook
    - Arquivos: WebhookSignatureValidator, PaymentController, PaymentService.
    - Validar timestamp, payload, identificação do pagamento e transições concorrentes.

5. Fortalecer autenticação
    - Arquivos: JwtService, JwtFilter, AuthService, User ou entidade de sessão.
    - Adicionar rotação, revogação e respostas 401 padronizadas.

6. Reforçar banco
    - Novas migrations para constraints, índices e unicidades.
    - Validar dados atuais antes de cada constraint.

7. Endurecer integrações
    - Arquivos: CorreiosClient, BlingClient, configurações do Mercado Pago/R2.
    - Timeouts, retries seletivos, circuit breaker e erros tipados.

8. Cobertura e observabilidade
    - Testcontainers/PostgreSQL, testes de SecurityFilterChain e concorrência.
    - Actuator/Micrometer, correlation ID e redaction.

Cada etapa depende de testes de regressão antes da seguinte; pagamento e estoque devem ser tratados juntos porque suas invariantes são acopladas.

———

# 12. Limitações e pontos não confirmados

- Ambiente com problema e problema principal observado não foram informados.
- Não havia logs de desenvolvimento, homologação ou produção.
- Nenhum stack trace real estava disponível.
- Não foi possível determinar frequência dos erros.
- Não executei build/testes por eles alterarem target e possivelmente resolverem dependências.
- Não validei migrations contra um PostgreSQL real.
- Não acessei serviços externos nem confirmei contratos atuais das APIs.
- Não auditei o frontend, conforme solicitado.
- Não confirmei CVEs das dependências por consulta externa.
- O conteúdo de .env não foi exposto nem utilizado.
- Fluxos de cupom, promoção, devolução, estorno, fiscal e ERP de pedidos não puderam ser auditados porque não estão implementados.
- A avaliação de “produção” se baseia somente no README; não houve acesso a infraestrutura ou configuração implantada.

Nenhum arquivo, configuração, dependência, teste, banco ou serviço foi modificado.