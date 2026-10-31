# GabiKids · API de e-commerce

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![CI](https://github.com/eduardo-Ciudad/ecommerce/actions/workflows/ci.yml/badge.svg)

API REST da loja de roupas infantis GabiKids, em produção em [gabikidstore.com](https://gabikidstore.com). Cobre a loja virtual de ponta a ponta (catálogo, carrinho, frete, cupons e pagamento), a integração com o ERP Bling e as vendas feitas no balcão da loja física.

Projeto desenvolvido por mim pela [CiudadLab](https://ciudadlab.com.br). O frontend está em [ecommerce-vanilla-frontend](https://github.com/eduardo-Ciudad/ecommerce-vanilla-frontend).

## O problema

Antes do site, a loja vendia só pelo Instagram e pelo WhatsApp. Cada cliente precisava perguntar foto, tamanho e cor, e a dona respondia tudo à mão. Vendas se perdiam na demora. O estoque já ficava no Bling, então o site precisava conversar com ele em vez de criar um segundo controle.

## O que o sistema faz

**Loja virtual**
* Catálogo sincronizado com o Bling, com categorias em dois níveis, várias imagens por produto e variações com tamanho, cor, SKU e GTIN
* Carrinho persistente, endereços e frete em tempo real pelos Correios (PAC e SEDEX)
* Cupons de desconto aplicados sobre o valor dos produtos
* Checkout com Mercado Pago (Pix e cartão parcelado), com nova tentativa de pagamento por até 30 minutos depois de uma recusa

**Integração com o Bling**
* OAuth2 completo, com tokens criptografados no banco
* Sincronização de categorias, produtos e imagens (redimensionadas antes de ir para o storage)
* Envio automático de cada pedido pago ao Bling, com baixa de estoque, nova tentativa automática e reenvio manual pelo admin

**Loja física**
* Venda no balcão registrada pelo admin, com dinheiro, Pix, maquininha, link de pagamento ou fiado
* Site e loja física usam o mesmo estoque, e o dashboard separa o faturamento por canal

**Administração e Google**
* Dashboard com contadores, faturamento do mês e vendas dos últimos 30 dias
* Gestão de pedidos, cupons e produtos, com busca exata por SKU e GTIN
* Feed XML de produtos para o Google Merchant Center

**Contas**
* Cadastro com verificação de e-mail, login com access e refresh token, recuperação e troca de senha
* Papéis ADMIN e CLIENT

## Decisões técnicas

**Pagamento não espera o Bling.** Quando um pagamento é aprovado, o pedido publica um `OrderPaidEvent`. O envio ao Bling roda num `@TransactionalEventListener(AFTER_COMMIT)` assíncrono: se a transação do pagamento falhar, o evento é descartado, e se o Bling estiver fora, o cliente não sente. Um scheduler tenta de novo a cada 10 minutos, até 5 vezes, e o `numeroLoja` do pedido garante que o mesmo pedido nunca entra duas vezes.

**Estoque sem venda duplicada.** A variação de produto usa lock otimista (`@Version`), e as operações críticas do pedido usam `PESSIMISTIC_WRITE`. Um pedido pendente reserva o estoque por 30 minutos, e um scheduler libera os pedidos expirados a cada 5 minutos.

**Status do pedido com regras.** As transições permitidas (`PENDING → PAID → SHIPPED → DELIVERED`, com cancelamento só antes do envio) ficam no próprio enum, e qualquer outra mudança é recusada.

**Webhook de pagamento confiável.** O webhook do Mercado Pago valida a assinatura HMAC SHA256, confere o valor pago com o valor do pedido e é idempotente.

**Segredos e abuso.** Os tokens OAuth do Bling são criptografados com AES GCM num `AttributeConverter` do JPA. O rate limit é por IP (Bucket4j e Caffeine), e o cabeçalho de IP só é aceito quando vem de um proxy confiável.

## Arquitetura

```
Frontend (Vercel) ──HTTPS──► Nginx ──► API Spring Boot (Docker, VPS) ──► PostgreSQL
                                              │
                    Bling · Mercado Pago · Correios · Cloudflare R2 · SMTP
```

O código é organizado em camadas: controllers recebem a requisição, services concentram as regras de negócio, `domain` guarda as entidades e repositories, e `infra` isola cada integração externa.

```
src/main/java/com/eduardo/ecomerce
├── controller        # Endpoints REST
├── domain            # Entidades JPA e repositories (pedido, produto, variação, cupom...)
├── dto               # Entrada e saída da API
├── email             # E-mails transacionais assíncronos
├── infra
│   ├── bling         # Cliente da API v3 do Bling e controle de requisições
│   ├── config        # Mercado Pago, storage, OpenAPI, relógio
│   ├── exception     # Tratamento global de erros
│   ├── payment       # Validação de assinatura do webhook
│   ├── persistence   # Criptografia AES GCM
│   ├── security      # JWT, Spring Security, rate limit
│   ├── shipping      # Cliente dos Correios
│   └── validation    # Validação de CPF e CNPJ
└── service
    ├── bling         # Envio de pedidos ao Bling (listener, retry, mapper)
    └── feed          # Feed do Google Merchant
```

## Stack

* Java 17, Spring Boot 3.5, Spring Data JPA, Spring Security
* PostgreSQL 16 e Flyway (36 migrations)
* JWT com JJWT, Bucket4j e Caffeine
* Mercado Pago SDK, API v3 do Bling, API dos Correios
* AWS SDK S3 apontando para o Cloudflare R2, Thumbnailator para as imagens
* Springdoc OpenAPI
* JUnit 5, Mockito, MockWebServer
* Docker e GitHub Actions

## Como rodar

### Com Docker

```bash
git clone https://github.com/eduardo-Ciudad/ecommerce.git
cd ecommerce
cp .env.example .env   # preencha as variáveis
docker compose up --build
```

A API sobe em `http://localhost:8081`, e as migrations rodam sozinhas.

### Sem Docker

Pré-requisitos: Java 17 e PostgreSQL com um banco `ecommerce_db`.

```bash
# exporte as variáveis do .env.example
./mvnw spring-boot:run
```

Nesse caso a API sobe em `http://localhost:8080`.

### Testes

```bash
./mvnw test
```

### Variáveis de ambiente

Todas estão no [`.env.example`](.env.example), agrupadas por integração.

| Grupo | Variáveis |
|---|---|
| Banco | `DB_USERNAME`, `DB_PASSWORD` |
| Segurança | `JWT_SECRET`, `APP_ENCRYPTION_KEY`, `TRUSTED_PROXIES`, `CORS_ORIGINS`, `FRONTEND_URL` |
| Mercado Pago | `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`, `MP_NOTIFICATION_URL` |
| Storage (R2) | `STORAGE_ENDPOINT`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`, `STORAGE_BUCKET`, `STORAGE_PUBLIC_URL` |
| E-mail | `MAIL_USERNAME`, `MAIL_PASSWORD`, `ADMIN_NOTIFICATION_EMAIL` |
| Correios | `CORREIOS_*` |
| Bling | `BLING_*` (OAuth) e `BLING_ORDERS_*` (envio de pedidos e venda no balcão) |

O envio de pedidos ao Bling vem desligado por padrão (`BLING_ORDERS_ENABLED=false`), para o projeto rodar localmente sem uma conta Bling.

## Endpoints principais

A documentação completa fica em `/swagger-ui.html` com a aplicação rodando.

| Área | Rotas |
|---|---|
| Autenticação | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/change-password` · `GET /auth/verify-email` |
| Catálogo | `GET /categories`, `GET /products`, `GET /products/{id}` · CRUD de produtos, variações e imagens (ADMIN) |
| Carrinho e endereços | `/cart`, `/cart/items`, `/addresses` |
| Frete | `GET /shipping/calculate` |
| Cupons | `GET /coupons/validate` · CRUD em `/coupons` (ADMIN) |
| Pedidos | `POST /orders`, `GET /orders`, `PUT /orders/{id}/cancel` |
| Pagamento | `POST /payments/process`, `POST /payments/webhook` |
| Admin | `GET /admin/stats`, `GET /orders/admin`, `POST /orders/admin/local-sales`, `POST /orders/admin/{id}/bling/resend` |
| Bling | `GET /bling/authorize`, `GET /bling/callback`, `POST /bling/sync/categories`, `POST /bling/sync/products` |
| Google | `GET /feeds/google.xml` |

## Testes

São mais de 470 testes em 45 classes, cobrindo as regras de pedido, estoque, pagamento, cupons, venda no balcão, envio ao Bling, frete, autenticação e as regras de autorização de cada rota. As chamadas HTTP aos serviços externos são testadas com MockWebServer.

O CI no GitHub Actions roda `mvn verify` contra um PostgreSQL 16 a cada push e pull request na `main`.

## Autor

Eduardo · [CiudadLab](https://ciudadlab.com.br) · [LinkedIn](https://www.linkedin.com/in/eduardociudadf) · [GitHub](https://github.com/eduardo-Ciudad)