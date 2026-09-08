# TriAIge / triaige-srv-mcp-ai

> Serviço de pipeline de pré-processamento de documentos (OCR, anonimização, agrupamento e resumo) e de raciocínio de IA sobre o conteúdo anonimizado.

## Sobre o projeto

O **TriAIge** é uma plataforma de triagem jurídica assistida por IA: escritórios de advocacia enviam os documentos de um caso e recebem de volta um relatório estruturado, com o texto sensível anonimizado antes de qualquer processamento por IA.

Este repositório contém o serviço responsável por:

- Consumir o "documento pronto" publicado pelo `triaige-srv-orchestrator` e executar o pipeline de pré-processamento: OCR → anonimização → agrupamento → resumo condicional.
- Devolver o resultado do pipeline ao Orchestrator via callback.
- Expor consulta de jurisprudência (com cache) como tool via protocolo MCP.
- Raciocinar sobre o texto anonimizado com um LLM (Gemini) e devolver um relatório estruturado (classificação, criticidade, partes, prazos e resumo), usado pelo Orchestrator para montar o relatório final.

## Papel deste serviço na arquitetura

```text
triaige-srv-orchestrator
   ↓ (Q2: documento pronto)
triaige-srv-mcp-ai
   ├── T1 OCR (Textract) → T2 Anonimização → T3 Agrupamento → T4 Resumo condicional
   │      ↓ (grava S3 trusted)
   │      ↓ (callback com o resultado)
   └── POST /api/ai/v1/analyze (chamado pelo Orchestrator após o callback)
          ├── lê o texto anonimizado no S3 trusted
          ├── raciocina com o Gemini, podendo chamar T5 (jurisprudência) internamente
          └── devolve relatório estruturado ao Orchestrator
```

## Responsabilidades

Este serviço é responsável por:

- **T1 — OCR:** extração de texto dos documentos originais via AWS Textract (processamento assíncrono, com fila de retry para falhas transitórias).
- **T2 — Anonimização:** remoção/mascaramento de dados pessoais (CPF, CNPJ, telefone, e-mail, RG, endereço etc.) antes de qualquer gravação em storage "confiável" ou envio a um LLM.
- **T3 — Agrupamento:** concatenação dos textos anonimizados de um mesmo grupo de anexos, respeitando a ordem original.
- **T4 — Resumo condicional:** resumo do texto agrupado quando ultrapassa limiares configuráveis de tokens/páginas.
- **T5 — Consulta de jurisprudência:** tool exposta via protocolo MCP e também chamada internamente pelo raciocínio de IA, com cache.
- **Raciocínio de IA (`POST /api/ai/v1/analyze`):** lê o texto anonimizado já gravado por este serviço, raciocina com o Gemini (function calling de `jurisprudence_query`) e devolve um relatório estruturado versionado por schema.
- Callback síncrono ao Orchestrator com o resultado do pipeline, com fila de DLQ como fallback para nunca perder o resultado silenciosamente.

### Fora do escopo

Este serviço não é responsável por:

- Receber os documentos do escritório ou emitir URLs de upload — isso é feito pelo `triaige-srv-orchestrator`.
- Renderizar o relatório final no template canônico ou notificar o escritório — isso é feito pelo Orchestrator e pelo serviço de notificação, respectivamente.
- Provisionamento de infraestrutura — isso é feito pelo `triaige-infra`.

## Arquitetura

Mesma organização em pacotes do `triaige-srv-orchestrator`:

```text
api/            controller e DTOs do único endpoint REST de negócio: POST /api/ai/v1/analyze
application/    services de domínio (OCR, anonimização, agrupamento, resumo, ai_tool_calls,
                processing_steps, idempotency) e os orquestradores de pipeline
                (DocumentPipelineService) e de análise (AnalyzeSessionUseCase)
domain/         entidades JPA, enums, exceções de negócio (McpException para o pipeline
                SQS/MCP; AnalysisException, com HTTP status, para o endpoint REST)
infrastructure/ config (AWS, MCP, propriedades), persistence (repositórios JPA), s3
                (leitura raw, leitura/escrita trusted), sqs (consumidores de Q2 e da fila
                de retry de OCR, publisher), textract (cliente assíncrono), callback
                (cliente HTTP do callback ao Orchestrator), jurisprudence (T5: cache,
                cliente do provider externo, tool), mcp (registro da tool MCP), gemini
                (cliente HTTP com retry/circuit breaker, function calling, prompt
                versionado), circuitbreaker (componente reutilizável), security (filtro
                de autenticação por token interno de /analyze)
shared/         erros, métricas
```

### Comunicação com outros serviços

| Serviço / Recurso | Tipo | Finalidade |
|---|---|---|
| `triaige-srv-orchestrator` | HTTP / REST (callback) | Devolve o resultado do pipeline após processar um documento |
| Fila `triaige-docs-preprocessing` (Q2) | SQS (consumer) | Recebe o "documento pronto" publicado pelo Orchestrator |
| MySQL | Banco de dados | Persistência de sessões, documentos, chamadas de ferramentas de IA e cache de jurisprudência |
| S3 (buckets *raw* e *trusted*) | Storage | Leitura dos documentos originais e escrita do conteúdo anonimizado/agrupado |
| AWS Textract | API externa | OCR dos documentos originais |
| Gemini (Generative Language API) | API externa | Raciocínio de IA sobre o texto anonimizado |
| Provider de jurisprudência (mockapi.io) | API externa | Consulta de jurisprudência (T5), com cache local |

## Tecnologias utilizadas

- Java 21 + Spring Boot 3.5 (Web, Validation, Data JPA, Actuator)
- Spring AI (`spring-ai-starter-mcp-server-webmvc`) — servidor MCP stateless para a tool de jurisprudência
- MySQL (via `mysql-connector-j`)
- AWS SDK v2 — SQS, S3, Textract, STS
- Cliente HTTP direto (`RestClient`) para a API do Gemini, sem SDK/`spring-ai` de LLM
- Lombok
- Jackson (com suporte a `java.time`)
- Logback com `logstash-logback-encoder` (logs estruturados em JSON)
- Micrometer + CloudWatch (métricas)
- Docker / Docker Compose
- JUnit 5, Testcontainers (MySQL, LocalStack)

## Estrutura do projeto

```text
src/
├── main/
│   ├── java/br/com/triaige/mcpai/
│   │   ├── api/            controller e DTOs de /api/ai/v1/analyze
│   │   ├── application/    pipeline (OCR, anonimização, agrupamento, resumo) e use case de análise
│   │   ├── domain/         entidades, enums, exceções
│   │   ├── infrastructure/ s3, sqs, textract, callback, jurisprudence, mcp, gemini,
│   │   │                   circuitbreaker, security, config, persistence
│   │   └── shared/         erro, métricas
│   └── resources/
│       ├── application.yml
│       └── prompts/analysis/prompt.txt
└── test/
    └── ...
```

## Pré-requisitos

- Java 21+
- Maven (ou o wrapper `mvnw`)
- Docker e Docker Compose
- Credenciais AWS válidas (Textract exige acesso real; S3/SQS provisionados via Terraform em `triaige-infra`)
- Uma `GEMINI_API_KEY` válida para exercitar o endpoint de análise

## Configuração

### Variáveis de ambiente

Copie/ajuste o `.env` na raiz do projeto (já vem preenchido com defaults de desenvolvimento):

```env
MYSQL_DATABASE=
MYSQL_USER=
MYSQL_PASSWORD=
MYSQL_PORT=
DB_URL=
DB_USERNAME=
DB_PASSWORD=

AWS_REGION=
AWS_SQS_ENDPOINT=
AWS_S3_ENDPOINT=
AWS_TEXTRACT_ENDPOINT=
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_SESSION_TOKEN=
AWS_PROFILE=

RAW_DOCUMENTS_BUCKET=
TRUSTED_DOCUMENTS_BUCKET=
DOCS_PREPROCESSING_QUEUE_URL=
OCR_RETRY_QUEUE_URL=
MCP_CALLBACK_DLQ_QUEUE_URL=

ORCHESTRATOR_BASE_URL=
MCP_INTERNAL_TOKEN=
MCP_ANALYZE_TOKEN=

JURISPRUDENCE_API_ENDPOINT=
GEMINI_API_KEY=
GEMINI_MODEL=
GEMINI_FALLBACK_MODEL=
PROMPT_VERSION=

SRV_MCP_AI_PORT=
```

> Nunca versione credenciais, tokens, senhas ou outros secrets reais no repositório.

### Configuração local

`application.yml` reflete o cenário suportado (MySQL real via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, buckets reais via `RAW_DOCUMENTS_BUCKET`/`TRUSTED_DOCUMENTS_BUCKET`), com defaults sobrescrevíveis por variável de ambiente. Não há LocalStack: os endpoints S3/SQS/Textract apontam para recursos reais — deixe as variáveis `*_ENDPOINT` em branco para usar os endpoints reais da região. No Docker Compose, o perfil configurado em `~/.aws/credentials` é montado no container e selecionado por `AWS_PROFILE`.

> **T1 (Textract) exige credenciais AWS reais.** Sem isso, a chamada falha com `OCR_FAILED` — o restante do pipeline (S3, filas, callback, T2-T5) continua exercitável.

> **`POST /api/ai/v1/analyze` exige `GEMINI_API_KEY`.** Sem uma chave real, toda chamada falha com `502 LLM_UNAVAILABLE` — comportamento esperado, não erro de configuração.

> **`POST /api/ai/v1/analyze` também exige `X-Internal-Token`.** `MCP_ANALYZE_TOKEN` precisa bater com o mesmo valor configurado no `.env` do `triaige-srv-orchestrator` — sem o header, ou com valor errado, a chamada falha com `401 UNAUTHORIZED`.

## Executando localmente

### Executando com Docker

```bash
docker compose up --build
```

Sobe o MySQL (porta `MYSQL_PORT`, padrão `3307`) e o próprio serviço (porta `SRV_MCP_AI_PORT`, padrão `8084`).

Para encerrar:

```bash
docker compose down
```

### Executando sem Docker

```bash
mvn spring-boot:run
```

Exige MySQL acessível (ex: `docker compose up mysql` deste repositório) e credenciais AWS reais para os consumidores de fila/S3.

## Testes

```bash
mvn test
```

Cobertura: todas as categorias de PII da anonimização e falsos positivos conhecidos (`AnonymizationServiceTest`), ordem de concatenação e roteamento de agrupamento/resumo (`AttachmentGroupingServiceTest`, `SummarizationServiceTest`), cache de jurisprudência (`JurisprudenceCacheServiceTest`), o endpoint de análise ponta a ponta com Testcontainers (`AnalysisControllerIT`, Gemini mockado), validação do schema do relatório (`AnalyzeSessionUseCaseValidationTest`) e o filtro de autenticação (`InternalTokenAuthFilterTest`).

## API

Único endpoint REST de negócio deste serviço:

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/ai/v1/analyze` | Recebe as referências ao conteúdo já anonimizado/agrupado, raciocina com o Gemini e devolve o relatório estruturado |

Exige o header `X-Internal-Token` (chamada servidor-a-servidor, não exposta via API Gateway) e `Idempotency-Key` para replay seguro.

## Fluxo principal

```text
Q2: documento pronto (publicado pelo Orchestrator)
   ↓
T1 OCR (Textract, com retry em caso de falha transitória)
   ↓
T2 Anonimização (remoção de PII)
   ↓
T3 Agrupamento (concatenação por grupo de anexos)
   ↓
T4 Resumo condicional (se acima do limiar de tokens/páginas)
   ↓
Gravação no S3 trusted + callback ao Orchestrator
   ↓
(Orchestrator chama POST /analyze)
   ↓
Leitura do texto anonimizado + raciocínio com o Gemini (função T5 sob demanda)
   ↓
Relatório estruturado devolvido ao Orchestrator
```

## Mensageria

| Fila | Tipo | Uso |
|---|---|---|
| `triaige-docs-preprocessing` (Q2) | Consumer | Pipeline automático T1-T4 |
| `triaige-mcp-ocr-retry` (+ DLQ) | Consumer | Retry de OCR com delay crescente (30s/2min/10min) |
| `triaige-mcp-callback-dlq` | Producer (fallback) | Fallback do callback ao Orchestrator após esgotar retries |

### Exemplo de mensagem (Q2)

```json
{
  "sessionId": "example-session-id",
  "documentId": "example-document-id",
  "attachmentGroupId": "example-group-id",
  "schemaVersion": "1.0"
}
```

## Banco de dados

### Banco utilizado

`MySQL`

### Principais entidades

- `TriageSession` / `LegalCase` / `LegalDocument`: espelham as entidades equivalentes do Orchestrator, necessárias para o pipeline rodar de forma independente.
- `ProcessingStep`: status de cada etapa do pipeline (T1-T4) por documento/grupo.
- `AiToolCall`: auditoria de chamadas a ferramentas de IA (Textract, Gemini, jurisprudência).
- `JurisprudenceCache`: cache de resultados de consulta de jurisprudência.
- `IdempotencyRecord`: suporte a idempotência do endpoint `/analyze`.

> O schema deste serviço é o mesmo modelo de dados único do TriAIge, aplicado externamente via Ansible — permite que o serviço suba isolado em desenvolvimento local, mas em produção real ambos os serviços apontam para a mesma instância/schema já provisionado. Consulte a seed do banco disponibilizada em `triaige-infra` (Ansible) para dados de exemplo.

## Integrações externas

### AWS Textract

**Finalidade:** OCR dos documentos originais (T1).

**Tipo de comunicação:** SDK (AWS SDK v2), processamento assíncrono.

### Gemini (Generative Language API)

**Finalidade:** raciocínio de IA sobre o texto anonimizado, com function calling para consultar jurisprudência sob demanda.

**Tipo de comunicação:** HTTP direto (`RestClient`), autenticação por `GEMINI_API_KEY`.

### Provider de jurisprudência (mockapi.io)

**Finalidade:** fonte de dados de jurisprudência para a tool T5 (ainda um mock, substituível por uma fonte jurídica real).

**Tipo de comunicação:** REST.

## Docker

### Build da imagem

```bash
docker build -t triaige-srv-mcp-ai .
```

### Executando a imagem

```bash
docker run \
  -p 8084:8084 \
  --env-file .env \
  triaige-srv-mcp-ai
```

## Observabilidade

- Logs estruturados em JSON (`logstash-logback-encoder`), `service=mcp`, com `sessionId`, `attachmentGroupId`, `documentId`, `tool`, `event` via MDC — nunca texto de documento ou PII.
- Prompt completo, texto anonimizado e resposta do modelo **nunca** vão para o log estruturado principal — só para um logger de debug separado, gravado localmente.
- Métricas via Micrometer (`PipelineLatencyMs`, `OcrLatencyMs`, `OcrFailureCount`, `AnonymizationPiiCount`, `SummarizationTriggeredCount`, `JurisprudenceCacheHitRatio`, `McpCallbackFailureCount`, `AnalysisLatencyMs`, `AnalysisSuccessCount`/`AnalysisFailureCount`, `GeminiRetryCount`, `CircuitBreakerOpenCount`, `JurisprudenceToolCallsPerAnalysis`, `InvalidReportFormatCount`), namespace CloudWatch `Triaige/MCP` (`CLOUDWATCH_METRICS_ENABLED=true` para habilitar).
- Health Check: `GET /actuator/health`

## Segurança

- Anonimização de dados pessoais (CPF, CNPJ, telefone, e-mail, RG, endereço) antes de qualquer gravação em storage "confiável" ou envio a um LLM — auditada pelos próprios testes de anonimização.
- Autenticação do endpoint `/analyze` por token interno (`X-Internal-Token`), não exposto via API Gateway.
- Secrets (chave do Gemini, credenciais AWS, tokens, senha do banco) configurados via variáveis de ambiente, fora do código.

## Repositórios relacionados

Este repositório faz parte do ecossistema **TriAIge**.

| Repositório | Responsabilidade |
|---|---|
| `triaige-front-nextjs` | Site institucional e mockup de dashboard (Next.js / Tailwind) |
| `triaige-srv-orchestrator` | Ingestão de documentos e orquestração do fluxo de triagem |
| `triaige-infra` | Provisionamento de infraestrutura AWS (Terraform + Ansible) |

## Projeto acadêmico

Projeto desenvolvido como Trabalho de Conclusão de Curso em:

**Curso:** Sistemas de Informação
**Instituição:** São Paulo Tech School
**Ano:** 2026

### Objetivo

Aplicar IA generativa e engenharia de software para automatizar a triagem inicial de casos jurídicos, reduzindo o tempo de análise manual de documentos por escritórios de advocacia, com anonimização de dados sensíveis antes de qualquer processamento por IA.

## Equipe

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/GabrielNunees063">
        <img src="https://avatars.githubusercontent.com/u/125298578?v=4" width="100px;"><br>
        <sub><b>Gabriel Nunes</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Bielzinschiavo">
        <img src="https://avatars.githubusercontent.com/u/125298078?v=4" width="100px;"><br>
        <sub><b>Gabriel Schiavo</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/gyuliapiqueira">
        <img src="https://avatars.githubusercontent.com/u/125298346?v=4" width="100px;"><br>
        <sub><b>Gyulia Piqueira</b></sub>
      </a>
    </td>
  </tr>

  <tr>
    <td align="center" colspan="3">
      <table>
        <tr>
          <td align="center">
            <a href="https://github.com/Kaori2">
              <img src="https://avatars.githubusercontent.com/u/125297000?v=4" width="100px;"><br>
              <sub><b>Kaori Katayama</b></sub>
            </a>
          </td>
          <td align="center">
            <a href="https://github.com/Miguel-Araujo325">
              <img src="https://avatars.githubusercontent.com/u/125296970?v=4" width="100px;"><br>
              <sub><b>Miguel Araujo</b></sub>
            </a>
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>

## Licença

> Este projeto foi desenvolvido para fins acadêmicos.
