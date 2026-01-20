# Activity Feed Service - Architecture Documentation

**Author**: Erik Gomez

> **Senior Backend Engineer Challenge - AstroPay**
>
> A scalable, real-time system to consolidate all financial transactions into a unified Activity Feed with instant search, filtering, and efficient pagination.

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Challenge Requirements](#challenge-requirements)
3. [High-Level Architecture](#high-level-architecture)
4. [Technology Stack & Justification](#technology-stack--justification)
5. [Data Flow & Processing](#data-flow--processing)
6. [API Design](#api-design)
7. [Future Improvements](#future-improvements)
8. [Next Steps](#next-steps)
9. [MVP Repository](#mvp)

---

## Executive Summary

The Activity Feed Service is a **real-time, event-driven microservice** that consolidates transactions from multiple source systems (Card, P2P, Earnings, Crypto) into a unified, searchable activity feed. The architecture leverages **polyglot persistence** (DynamoDB + OpenSearch) to optimize for both simple queries (low latency) and complex searches (rich filtering).

### Core Capabilities
✅ **Real-time ingestion** via SQS event consumption

✅ **Smart query routing** (DynamoDB for simple, OpenSearch for complex)

✅ **Cursor-based pagination** with limit+1 optimization

✅ **Custom metadata search** for product-specific fields

✅ **Idempotent processing** with deduplication

---

## Challenge Requirements

### Problem Statement
Users interact with multiple products (Card, P2P, Earnings, Crypto), but transaction history is **fragmented across microservices**, creating a confusing UX. Transactions have both standard fields and **product-specific custom metadata** (e.g., merchant details for card payments, peer info for P2P transfers).

### Requirements
1. ✅ **Unified Activity Feed**: Single API consolidating all transaction types
2. ✅ **Instant Search**: Fast queries across standard fields (product, currency, status)
3. ✅ **Custom Metadata Search**: Query product-specific fields efficiently
4. ✅ **Efficient Pagination**: Handle deep pagination without performance degradation
6. ✅ **Scalable Design**: Support growing transaction volume and user base
7. ✅ **Frontend-Friendly API**: Clean, documented REST API with examples

---

## High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Source Microservices                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │  Card    │  │   P2P    │  │ Earnings │  │  Crypto  │  │   Bank   │  │
│  │ Service  │  │ Service  │  │ Service  │  │ Service  │  │ Service  │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
│       │             │             │             │             │        │
│       └─────────────┴─────────────┴─────────────┴─────────────┘        │
│                              │                                         │
│                    TransactionEvent (JSON)                             │
└──────────────────────────────┼─────────────────────────────────────────┘
                               ▼
                    ┌──────────────────────┐
                    │    Amazon SQS        │
                    │  (Event Bus/Queue)   │
                    │                      │
                    │ • Decoupling         │
                    │ • Buffering          │
                    └──────────┬───────────┘
                               │
                               ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Activity Feed Service                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              SQS Consumer (SmartLifecycle)                      │   │
│  │  • ThreadPool (5 workers)                                       │   │
│  │  • Bounded queue (backpressure)                                 │   │
│  │  • Visibility timeout (30s)                                     │   │
│  └───────────────────────┬─────────────────────────────────────────┘   │
│                          │                                             │
│                          ▼                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │           TransactionEventProcessor                             │   │
│  │  1. Validate & map event to domain model                        │   │
│  │  2. Generate sort key (occurredAt#id)                           │   │
│  │  3. Dual-write to DynamoDB + OpenSearch                         │   │
│  └───────────────────────┬─────────┬───────────────────────────────┘   │
│                          │         │                                   │
│           ┌──────────────┘         └──────────────┐                    │
│           ▼                                       ▼                    │
│  ┌─────────────────┐                    ┌──────────────────┐           │
│  │   DynamoDB      │                    │  OpenSearch      │           │
│  │  Repository     │                    │  Repository      │           │
│  │                 │                    │                  │           │
│  │ • Primary store │                    │ • Search index   │           │
│  │ • Fast lookups  │                    │ • Complex filters│           │
│  │ • userId+sk key │                    │ • Full-text      │           │
│  └─────────────────┘                    └──────────────────┘           │
│           ▲                                      ▲                     │
│           │                                      │                     │
│           │                                      │                     │
│           │                                      │                     │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │              TransactionQueryRouter                             │   │
│  │  • Analyzes filter complexity                                   │   │
│  │  • Routes simple queries → DynamoDB                             │   │
│  │  • Routes complex queries → OpenSearch                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                          ▲                                             │
│                          │                                             │
│                          │                                             │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │           REST API Controllers                                  │   │
│  │  • GET /transactions*                                           │   │
│  │  • GET /transactions/{id}                                       │   │
│  │  • GET /health                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
                           ▲
                           │
                  ┌─────────────────┐
                  │  Frontend Apps  │
                  │  (Web/Mobile)   │
                  └─────────────────┘

* Note: GET /transactions endpoint is not actually implemented like that since there is no IAM system to obtain the user-id from a token or similar.

```

## Technology Stack & Justification

### Core Stack

| Technology | Purpose | Justification |
|------------|---------|---------------|
| **Java 21** | Application runtime | Type safety, mature ecosystem, excellent AWS SDK support |
| **Spring Boot 3.2** | Framework | Production-ready, auto-configuration, actuator for observability |
| **Amazon SQS** | Event ingestion | Managed, scalable, at-least-once delivery, dead-letter queue support |
| **Amazon DynamoDB** | Primary datastore | Single-digit millisecond latency, predictable performance, automatic scaling |
| **OpenSearch** | Search engine | Full-text search, complex filtering, aggregations, metadata queries |

### Why DynamoDB?

#### DynamoDB Advantages
✅ **Predictable Performance**: Single-digit millisecond reads at any scale

✅ **Automatic Scaling**: No capacity planning, scales to petabytes

✅ **Cost-Effective**: Pay per request, no over-provisioning

✅ **High Availability**: Multi-AZ replication, 99.99% SLA

✅ **Time-Series Optimized**: Sort key pattern (`occurredAt#id`) perfect for transaction history

✅ **Strong Consistency**: Critical for financial data accuracy

#### Schema Design
```
Table: transactions
Partition Key (PK): user_id           // User identifier
Sort Key (SK): occurredAt#id          // Timestamp + transaction ID

Global Secondary Index: id-index
GSI Partition Key: id                 // Transaction ID for fast lookups

Example SK: 2026-01-20T10:30:00Z#tx-abc123

Access Patterns Supported:
✅ Get all transactions for user (Query on PK)
✅ Get transactions in date range (Query on PK + SK between)
✅ Get single transaction by ID (Query on id-index GSI)
✅ Paginate efficiently (Query with exclusiveStartKey)
```

**Why This Schema?**

- **Hot Partition Prevention**: userId as PK distributes load evenly

- **Time-Series Optimization**: SK enables efficient date-range queries

- **Natural Ordering**: Descending sort (newest first) by default

- **Idempotency**: SK uniqueness prevents duplicates

### Why OpenSearch?

#### OpenSearch Advantages

✅ **Rich Query DSL**: Complex boolean queries, multi-match, range filters

✅ **Dynamic Mapping**: Handle custom metadata without schema changes

✅ **Full-Text Search**: Elasticsearch-quality search capabilities

✅ **Aggregations**: Built-in analytics for dashboards

✅ **Horizontal Scaling**: Add nodes as data grows

✅ **Near Real-Time**: Documents searchable within 1 second

#### Index Design
```json
{
  "index": "activity_items",
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "userId": { "type": "keyword" },
      "product": { "type": "keyword" },
      "type": { "type": "keyword" },
      "status": { "type": "keyword" },
      "amount": { "type": "double" },
      "currency": { "type": "keyword" },
      "occurredAt": { "type": "date" },
      "description": { "type": "text" },
      "metadata": {
        "type": "object",
        "dynamic": true,
        "properties": {
          "merchantName": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
          "cardBrand": { "type": "keyword" },
          "recipientName": { "type": "text", "fields": { "keyword": { "type": "keyword" } } }
        }
      }
    }
  }
}
```

### Why SQS?

#### SQS Advantages

✅ **Fully Managed**: No infrastructure to maintain

✅ **At-Least-Once Delivery**: Messages never lost

✅ **Dead-Letter Queue**: Automatic retry and poison message handling

✅ **Long Polling**: Efficient message retrieval (10s wait)

✅ **Visibility Timeout**: Prevents duplicate processing during failures

✅ **FIFO Option**: Order preservation if needed (currently using standard)

#### Message Format
```json
{
  "eventId": "evt-abc123",
  "eventType": "TRANSACTION_CREATED",
  "sourceService": "card-service",
  "eventTimestamp": "2026-01-20T10:30:00Z",
  "payload": {
    "transactionId": "tx-card-xyz",
    "userId": "user123",
    "product": "CARD",
    "type": "PAYMENT",
    "status": "COMPLETED",
    "amount": 89.99,
    "currency": "USD",
    "description": "Starbucks payment",
    "occurredAt": "2026-01-20T10:30:00Z",
    "metadata": {
      "merchantName": "Starbucks",
      "cardLast4": "4242",
      "cardBrand": "Visa"
    }
  }
}
```

### Alternatives Considered (But Rejected)

#### 1. **Relational Database (PostgreSQL/MySQL)**

❌ **Rejected**: Doesn't scale horizontally well for time-series data

❌ **Rejected**: Complex indexing needed for all filter combinations

❌ **Rejected**: Higher latency at scale (100ms+ for complex queries)

❌ **Rejected**: Expensive to scale (vertical scaling, read replicas)

*Use Case*: Good for transactional systems with complex relationships, not for high-scale read-heavy activity feeds.

#### 2. **Apache Kafka**
❌ **Rejected for Ingestion**: Over-engineered for this use case (we don't need log compaction, exactly-once semantics)
✅ **Could Work**: Better if we needed event replay, but SQS simpler and sufficient
✅ **Future Use**: Consider for event sourcing

#### 3. **Single Database (DynamoDB Only or OpenSearch Only)**
❌ **Rejected**: Polyglot persistence offers best of both worlds
- DynamoDB alone: Can't handle complex queries efficiently
- OpenSearch alone: Slower for simple lookups, more expensive

---

## Data Flow & Processing

### Write Path (Event Ingestion)

```
Source Service → SQS → Consumer → Processor → DynamoDB + OpenSearch
                  ↓
              (buffer)
                  ↓
         ┌─────────────────┐
         │ Consumer Thread │
         │   (5 workers)   │
         └────────┬────────┘
                  │
    ┌─────────────┴──────────────┐
    │  1. Receive Message (10)   │
    │  2. Parallel Processing    │
    │  3. Transform to Domain    │
    │  4. Dual Write             │
    │  5. Delete from Queue      │
    └────────────────────────────┘
```

### Read Path (Query Processing)

```
Client → API → QueryRouter → [DynamoDB OR OpenSearch] → Response
                   │
          ┌────────┴─────────┐
          │  Routing Logic   │
          │                  │
          │  Simple Query?   │
          │  • userId only   │
          │  • + date range  │
          │  • sortBy date   │
          │                  │
          │  YES → DynamoDB  │
          │  NO → OpenSearch │
          └──────────────────┘
```
---

## API Design

### Common Schemas

#### Transaction Object

```json
{
  "id": "tx-card-abc123",
  "userId": "user123",
  "product": "CARD",
  "type": "PAYMENT",
  "status": "COMPLETED",
  "amount": 89.99,
  "currency": "USD",
  "description": "Card payment at Starbucks",
  "occurredAt": "2026-01-20T10:30:00Z",
  "createdAt": "2026-01-20T10:30:00Z",
  "sourceService": "card-service",
  "eventId": "evt-xyz789",
  "transactionId": "tx-card-abc123",
  "metadata": {
    "merchantName": "Starbucks",
    "merchantCategory": "5812",
    "cardLast4": "4242",
    "cardBrand": "Visa",
    "location": "New York, NY"
  }
}
```

### Endpoint: Get User Transactions

```http
GET /api/v1/activity-feed/users/{userId}/transactions
```

#### Query Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| `userId` | path | ✅ | User identifier | `user123` |
| `limit` | query | ❌ | Page size (default 20, max 100) | `20` |
| `cursor` | query | ❌ | Pagination token (Base64) | `eyJ1c2VyX2lkIjoidXNlcjEyMyI...` |
| `sortBy` | query | ❌ | Sort field (default `occurredAt`) | `occurredAt`, `amount` |
| `sortDirection` | query | ❌ | Sort order (default `DESC`) | `ASC`, `DESC` |
| `product` | query | ❌ | Filter by product | `CARD`, `CRYPTO`, `P2P` |
| `type` | query | ❌ | Filter by type | `PAYMENT`, `DEPOSIT` |
| `status` | query | ❌ | Filter by status | `COMPLETED`, `PENDING` |
| `currency` | query | ❌ | Filter by currency | `USD`, `EUR`, `BTC` |
| `startDate` | query | ❌ | Date range start (ISO 8601) | `2026-01-01T00:00:00Z` |
| `endDate` | query | ❌ | Date range end (ISO 8601) | `2026-01-31T23:59:59Z` |
| `searchText` | query | ❌ | Full-text search | `Starbucks` |
| `metadataField` | query | ❌ | Metadata field name | `merchantName` |
| `metadataValue` | query | ❌ | Metadata value to match | `Starbucks` |

#### Response Schema

Returns an array of [Transaction](#transaction-object) objects with pagination metadata.

```json
{
  "success": true,
  "message": null,
  "data": {
    "content": [ /* Array of Transaction objects */ ],
    "nextCursor": "eyJ1c2VyX2lkIjoidXNlcjEyMyIsInNrIjoiMjAyNi0wMS0yMFQxMDozMDowMFojdHgtY2FyZC1hYmMxMjMifQ==",
    "size": 1,
    "hasMore": true
  }
}
```

#### Response Codes

| Code | Meaning | Example |
|------|---------|---------|
| `200` | Success | Transactions found and returned |
| `400` | Bad Request | Invalid parameters (e.g., invalid cursor, limit > 100) |
| `404` | Not Found | User not found |
| `500` | Server Error | Internal error (logged, alerted) |

#### Pagination Strategy

**Cursor-Based (Not Offset-Based)**

✅ **Advantages:**
- Consistent results even with concurrent writes
- O(1) performance regardless of page depth
- Prevents duplicate/missing items during pagination

❌ **Offset-Based Problems:**
- Skipped results if items deleted between pages
- Duplicate results if items added between pages
- Slow for deep pages (OFFSET 1000000 scans all rows)

### Endpoint: Get Transaction by ID

```http
GET /api/v1/activity-feed/transactions/{id}
```

**Implementation**: Uses DynamoDB Global Secondary Index (id-index) for O(1) lookup performance. Fast and efficient for production use.

#### Response Schema

Returns a single [Transaction](#transaction-object) object.

```json
{
  "success": true,
  "message": null,
  "data": { /* Transaction object */ }
}
```

#### Response Codes

| Code | Meaning | Example |
|------|---------|---------|
| `200` | Success | Transaction found |
| `404` | Not Found | Transaction ID doesn't exist |
| `400` | Bad Request | Invalid transaction ID format |
| `500` | Server Error | Internal error (logged, alerted) |

---

### Dual-Write Consistency

**Strategy**: Eventual consistency is acceptable for activity feed (non-critical reads)

**Write Order:**
1. Write to DynamoDB (source of truth)
2. Write to OpenSearch (search index)

**Failure Scenarios:**
- ❌ DynamoDB write fails: Transaction fails, SQS redelivers message
- ✅ OpenSearch write fails: Transaction succeeds, OpenSearch inconsistent

**Mitigation:**
- Background reconciliation job (scan DynamoDB, reindex missing in OpenSearch)
- Consider AWS DynamoDB Streams → Lambda → OpenSearch for guaranteed consistency
- Alert on OpenSearch indexing failures

---

## Future Improvements

#### 1. **Event Streaming with Per-Product Topics**
**Current**: All transaction events published to a single queue

**Proposed**: Each product service publishes to its own topic/stream (e.g., card.events, crypto.events, p2p.events), and the Activity Feed consumes from all of them.

**Benefits:**
- ✅ Better decoupling between producers
- ✅ Easier to add new products (new topic, no breaking changes)

#### 2. **Split into Ingestor + Reader Services (CQRS-style)**
**Current**: Single service ingests events and serves REST queries

**Proposed**: Split into:
- Ingestor: consumes events, normalizes, writes to DynamoDB + OpenSearch
- Reader API: serves REST endpoints, reads from DynamoDB/OpenSearch

**Benefits:**
- ✅ Clear separation of concerns (write vs read path)
- ✅ Independent scaling (ingestion vs query load)
- ✅ Better resilience and security isolation

#### 3. **Circuit Breakers (Resilience4j)**
**Current**: No circuit breakers (cascading failures possible)

**Proposed**: Wrap external calls with circuit breakers

#### 4. **Rate Limiting**
**Current**: No rate limiting

**Proposed**: Limit external calls by amount and by user

#### 5. **Real-Time Notifications (WebSockets)**
**Current**: Polling for new transactions

**Proposed**: WebSocket connection for push notifications

**Benefits:**
- ✅ Instant updates (no polling)
- ✅ Better UX (real-time balance updates)
- ✅ Reduced API load (no constant polling)

#### 6. **Event Sourcing**
**Current**: State-based storage (current transaction state)

**Proposed**: Event store (full transaction history)

**Benefits:**
- ✅ Audit trail (who changed what when)
- ✅ Time travel (view state at any point)
- ✅ Event replay (rebuild state from events)

---

## Next Steps

1. **Code Review**: Walk through implementation with team
2. **Logging**: Add logs for debugging
3. **Metrics**: Add metrics when necessary
4. **Alerts**: Add alerts
5. **Load Testing**: Simulate 10,000 TPS, measure latencies
6. **Security Audit**: Penetration testing, IAM policy review

---

## MVP

**Github repository:** [https://github.com/eegomez/astropay-challenge](https://github.com/eegomez/astropay-challenge)

---

**Document Version**: 0.0.1
**Last Updated**: January 20, 2026
**Author**: Erik Gomez
**Review**: Pending


