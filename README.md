# 🛍️ GabiKids — E-commerce API

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green?style=flat-square&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-blue?style=flat-square&logo=postgresql)
![CI](https://github.com/eduardo-Ciudad/ecommerce/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

API REST de e-commerce em produção para uma loja de roupas infantil. Cobre o fluxo completo de compra: catálogo com variações de tamanho, carrinho persistente, cálculo de frete via API dos Correios, checkout com Mercado Pago (Pix e cartão), controle de estoque, autenticação JWT com verificação de email e RBAC.

---

## 🚀 Tecnologias

- **Java 17** + **Spring Boot 3.5**
- **Spring Data JPA** + **Hibernate**
- **Spring Security** + **JWT** (JJWT 0.12.6) — access token + refresh token
- **PostgreSQL** + **Flyway** (17 migrações)
- **Mercado Pago SDK** — checkout transparente (Pix + cartão), webhooks com validação HMAC-SHA256
- **Correios API** — cálculo de frete (PAC/SEDEX) com autenticação por token e retry automático
- **AWS S3 SDK** (Cloudflare R2) — armazenamento de imagens de produtos e categorias
- **Spring Mail** — verificação de email e recuperação de senha assíncronas
- **Bucket4j** + **Caffeine** — rate limiting por IP
- **Springdoc OpenAPI** — documentação Swagger
- **Lombok** + **Bean Validation**
- **JUnit 5** + **Mockito** — 100+ testes unitários
- **GitHub Actions** — CI
- **Docker** — containerização multi-stage

---

## 📦 Funcionalidades

- Autenticação JWT (access + refresh token) com verificação de email obrigatória antes da primeira compra
- Recuperação e alteração de senha por token com expiração (confirmação por email)
- RBAC com roles ADMIN e CLIENT
- Rate limiting por IP via Bucket4j + Caffeine
- Catálogo de categorias e produtos com upload de imagem (Cloudflare R2)
- Variações de produto por tamanho com preço e estoque individuais
- Carrinho persistente por usuário, protegido contra IDOR
- Endereços de entrega por usuário (CRUD com endereço padrão)
- Cálculo de frete em tempo real via Correios (PAC/SEDEX), com cache de token e retry em caso de 401
- Criação de pedidos com decremento de estoque, lock pessimista para evitar overselling em concorrência
- Checkout com Mercado Pago: Pix (QR Code) e cartão, com parcelamento
- Webhooks de pagamento com verificação de assinatura, checagem de valor e idempotência
- Soft delete de produtos (campo `active`)
- Tratamento global de exceções com respostas padronizadas
- Documentação interativa via Swagger UI
- Logging estruturado com `@Slf4j` em todos os services

---

## 🗂️ Estrutura do projeto

```
src/main/java/com/eduardo/ecomerce
├── controller           # Endpoints REST
├── domain               # Entidades JPA e Repositories
│   ├── user
│   ├── category
│   ├── product
│   ├── productvariant
│   ├── cart
│   ├── cartitem
│   ├── order
│   ├── orderitem
│   ├── address
│   └── passwordtoken
├── dto
│   ├── input             # Payloads de entrada
│   └── output             # Payloads de saída
├── email                 # Envio de emails transacionais
├── infra
│   ├── config             # Mercado Pago, OpenAPI, Storage
│   ├── exception           # GlobalExceptionHandler
│   ├── payment             # Validação de assinatura de webhook
│   ├── security             # SecurityConfig, JwtFilter, JwtService, RateLimitFilter
│   └── shipping             # Cliente da API dos Correios
└── service                 # Regras de negócio
```

---

## ⚙️ Como rodar

### Com Docker (recomendado)

```bash
git clone https://github.com/eduardo-Ciudad/ecommerce.git
cd ecommerce
```

Crie um arquivo `.env` na raiz com as variáveis necessárias (banco, JWT, Mercado Pago, storage, SMTP e Correios — veja `.env.example`):

```bash
docker compose up --build
```

A aplicação sobe em `http://localhost:8081`. As migrações Flyway rodam automaticamente.

### Sem Docker

**Pré-requisitos:** Java 17, Maven, PostgreSQL rodando localmente.

```bash
# Crie o banco
psql -U postgres -c "CREATE DATABASE ecommerce_db;"

# Exporte as variáveis de ambiente necessárias (veja .env.example)

# Rode
./mvnw spring-boot:run
```

### Testes

```bash
./mvnw test
```

---

## 📡 Endpoints principais

### Autenticação

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/auth/register` | Registrar usuário (envia email de verificação) | Público |
| POST | `/auth/login` | Login (retorna access + refresh token) | Público |
| POST | `/auth/refresh` | Renovar access token | Público |
| GET | `/auth/verify-email` | Verificar email a partir do token | Público |
| POST | `/auth/forgot-password` | Solicitar recuperação de senha | Público |
| POST | `/auth/reset-password` | Redefinir senha via token | Público |
| POST | `/auth/change-password` | Solicitar troca de senha (usuário logado) | Autenticado |
| GET | `/auth/confirm-password-change` | Confirmar troca de senha via token | Público |

### Categorias / Produtos

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/categories` | Criar categoria | ADMIN |
| POST | `/categories/{id}/image` | Upload de imagem da categoria | ADMIN |
| GET | `/categories` | Listar categorias | Autenticado |
| POST | `/products` | Criar produto | ADMIN |
| POST | `/products/{id}/image` | Upload de imagem do produto | ADMIN |
| GET | `/products` | Listar produtos ativos | Autenticado |
| POST | `/products/{id}/variants` | Adicionar variação | ADMIN |

### Endereços / Frete

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/addresses` | Cadastrar endereço | Autenticado (próprio) |
| GET | `/addresses` | Listar endereços | Autenticado (próprio) |
| GET | `/shipping/calculate` | Calcular frete (PAC + SEDEX) por CEP | Autenticado |

### Carrinho / Pedidos / Pagamento

| Método | Rota | Descrição | Acesso |
|--------|------|-----------|--------|
| POST | `/cart/items` | Adicionar item ao carrinho | Autenticado (próprio) |
| POST | `/orders` | Criar pedido a partir do carrinho | Autenticado (próprio) |
| GET | `/orders` | Listar pedidos | Autenticado (próprio) |
| POST | `/payments/process` | Processar pagamento (Pix/cartão) | Autenticado (próprio) |
| POST | `/payments/webhook` | Webhook do Mercado Pago | Mercado Pago |

> 📄 Documentação completa disponível em `/swagger-ui.html` com a aplicação rodando.

---

## 🧪 Testes

Suíte com 100+ testes unitários cobrindo todas as regras de negócio dos services principais:

| Classe de teste | Cobertura |
|----------------|-----------|
| `AuthServiceTest` | Registro, login, refresh, verificação de email, recuperação/troca de senha |
| `OrderServiceTest` | Criação de pedido, controle de estoque, cálculo de frete no total |
| `PaymentServiceTest` | Checkout Pix/cartão, webhook com validação de valor e idempotência |
| `ShippingServiceTest` | Cálculo PAC/SEDEX, normalização de CEP, falhas dos Correios |
| `AddressServiceTest` | CRUD de endereços, lógica de endereço padrão |
| `CartServiceTest` | Carrinho persistente, proteção contra IDOR |
| `CategoryServiceTest` / `ProductServiceTest` / `ProductVariantServiceTest` | CRUD, validações e vínculos |
| `StorageServiceTest` | Upload de imagens |
| `WebhookSignatureValidatorTest` | Validação HMAC de webhooks |
| `RateLimitFilterTest` / `GlobalExceptionHandlerTest` | Infraestrutura |

---

## 🔜 Próximos passos

- [ ] Integração com Bling (importação automática de catálogo)
- [ ] Deploy contínuo (VPS Hostinger)

---

## 👨‍💻 Autor

Desenvolvido por [Eduardo](https://github.com/eduardo-Ciudad)
