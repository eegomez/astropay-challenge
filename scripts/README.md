# Test Scripts

Scripts para facilitar el testing local del Activity Feed service.

---

## 🚀 Setup

### 1. Setup Infrastructure

Crea la infraestructura local (DynamoDB, SQS, OpenSearch):

```bash
./scripts/setup-local-infrastructure.sh
```

Este script:
- ✅ Inicia LocalStack y OpenSearch usando docker-compose
- ✅ Verifica que los servicios estén saludables
- ✅ Crea tabla DynamoDB con schema: `user_id` (PK), `sk` (SK)
- ✅ Crea cola SQS: `activity-events`
- ✅ Crea índice OpenSearch: `activity_items`

### 2. Teardown Infrastructure

Para detener y eliminar los servicios:

```bash
./scripts/teardown-local-infrastructure.sh
```

Este script:
- 🛑 Detiene los containers de docker-compose
- 🗑️ Elimina los containers detenidos
- 🧹 Limpia recursos locales

---

## 📨 Publishing Test Events

### Opción 1: Script General (Flexible)

```bash
./scripts/publish-test-event.sh [PRODUCT] [TYPE] [AMOUNT]
```

**Ejemplos:**
```bash
# Card payment
./scripts/publish-test-event.sh CARD PAYMENT 89.99

# Crypto deposit
./scripts/publish-test-event.sh CRYPTO DEPOSIT 0.5

# Bank transfer
./scripts/publish-test-event.sh BANK TRANSFER 1500.00

# P2P transfer
./scripts/publish-test-event.sh P2P TRANSFER 25.00
```

### Opción 2: Scripts Específicos por Tipo

#### 💳 Credit Card Payment
```bash
./scripts/publish-card-payment.sh [amount] [merchant]
```

**Ejemplo:**
```bash
./scripts/publish-card-payment.sh 89.99 "Starbucks"
```

**Genera:**
- Product: `CARD`
- Type: `PAYMENT`
- Metadata: cardLast4, cardBrand, merchantName, location

---

#### ₿ Crypto Transaction
```bash
./scripts/publish-crypto-transaction.sh [amount] [crypto_symbol]
```

**Ejemplo:**
```bash
./scripts/publish-crypto-transaction.sh 0.5 "BTC"
```

**Genera:**
- Product: `CRYPTO`
- Type: `DEPOSIT`
- Metadata: cryptoSymbol, cryptoAmount, exchangeRate, walletAddress

---

#### 🏦 Bank Transfer
```bash
./scripts/publish-bank-transfer.sh [amount] [destination_account]
```

**Ejemplo:**
```bash
./scripts/publish-bank-transfer.sh 1500.00 "**** **** **** 5678"
```

**Genera:**
- Product: `BANK`
- Type: `TRANSFER`
- Metadata: destinationAccount, bankName, routingNumber, transferType

---

#### 🔄 P2P Transfer
```bash
./scripts/publish-p2p-transfer.sh [amount] [recipient_name]
```

**Ejemplo:**
```bash
./scripts/publish-p2p-transfer.sh 75.00 "Jane Smith"
```

**Genera:**
- Product: `P2P`
- Type: `TRANSFER`
- Metadata: recipientId, recipientName, recipientEmail, message, transferType

---

#### 💶 EUR Payment
```bash
./scripts/publish-eur-payment.sh [amount] [merchant]
```

**Ejemplo:**
```bash
./scripts/publish-eur-payment.sh 45.50 "Carrefour"
```

**Genera:**
- Product: `CARD`
- Type: `PAYMENT`
- Currency: `EUR`
- Status: `COMPLETED`

---

#### ⏳ Pending Crypto Transaction
```bash
./scripts/publish-pending-crypto.sh [amount] [crypto_symbol]
```

**Ejemplo:**
```bash
./scripts/publish-pending-crypto.sh 0.25 "ETH"
```

**Genera:**
- Product: `CRYPTO`
- Type: `WITHDRAWAL`
- Status: `PENDING`
- User: `user456`

---

#### ❌ Failed Payment
```bash
./scripts/publish-failed-payment.sh [amount] [merchant]
```

**Ejemplo:**
```bash
./scripts/publish-failed-payment.sh 250.00 "Amazon"
```

**Genera:**
- Product: `CARD`
- Type: `PAYMENT`
- Status: `FAILED`
- User: `user789`
- Metadata: failureReason (INSUFFICIENT_FUNDS)

---

### Opción 3: Publicar Todos los Tipos (Testing Completo)

```bash
./scripts/publish-all-test-events.sh
```

Este script publica 7 transacciones diversas para testing completo:

**user123 (4 transacciones):**
1. CARD payment at Starbucks ($89.99 USD, COMPLETED)
2. CRYPTO BTC deposit (0.5 BTC, COMPLETED)
3. BANK transfer ($1,500.00 USD, COMPLETED)
4. P2P transfer to Jane Smith ($75.00 USD, COMPLETED)

**user456 (2 transacciones):**
5. CARD payment at Carrefour (€45.50 EUR, COMPLETED)
6. CRYPTO ETH withdrawal (0.25 ETH, PENDING)

**user789 (1 transacción):**
7. CARD payment at Amazon ($250.00 USD, FAILED)

---

## 📋 Enums Válidos

### Product
```
CARD          - Credit/Debit card transactions
P2P           - Peer-to-peer transfers
EARNINGS      - Income/earnings
CRYPTO        - Cryptocurrency transactions
WALLET_TOPUP  - Wallet balance top-ups
BILL_PAYMENT  - Bill payments
WITHDRAWAL    - ATM/cash withdrawals
BANK          - Bank transfers
```

### TransactionType
```
PAYMENT       - Purchase/payment
TRANSFER      - Money transfer
DEPOSIT       - Money deposit
WITHDRAWAL    - Money withdrawal
REFUND        - Refund
FEE           - Fee charge
```

### TransactionStatus
```
PENDING
COMPLETED
FAILED
CANCELLED
```

### Currency
```
USD
EUR
GBP
```

---

## 🧪 Testing Workflow

### Step 1: Setup Infrastructure
```bash
./scripts/setup-local-infrastructure.sh
```

### Step 2: Start Application
```bash
./gradlew bootRun
```

### Step 3: Publish Test Events
```bash
# Opción A: Publicar todos los tipos
./scripts/publish-all-test-events.sh

# Opción B: Publicar uno por uno
./scripts/publish-card-payment.sh 25.50 "Amazon"
./scripts/publish-crypto-transaction.sh 1.0 "ETH"
./scripts/publish-bank-transfer.sh 5000.00
```

### Step 4: Verify Processing
Revisa los logs de la aplicación:
```
Processing transaction event: eventId=evt-xxx, transactionId=tx-xxx
Building transaction - userId: user123, sk: 2025-01-19T..., id: tx-xxx
Saved transaction to primary data store: tx-xxx
Indexed transaction in search repository: tx-xxx
Successfully processed transaction event: tx-xxx
```

### Step 5: Query via API
```bash
# Get by userId (simple query - usa DynamoDB)
curl "http://localhost:8080/api/v1/transactions?userId=user123"

# Complex query (usa OpenSearch)
curl "http://localhost:8080/api/v1/transactions?userId=user123&product=CARD&type=PAYMENT"
```

### Step 6: Cleanup (Optional)
```bash
# Detener servicios cuando termines
./scripts/teardown-local-infrastructure.sh
```

---

## 🔧 Troubleshooting

### Error: "The specified queue does not exist"
```bash
# Recrear la cola
aws sqs create-queue \
    --queue-name activity-events \
    --endpoint-url http://localhost:4566 \
    --region us-east-1
```

### Error: "One of the required keys was not given a value"
El `userId` está null en el evento. Verifica que el JSON tenga:
```json
{
  "payload": {
    "userId": "user123",  // ← REQUIRED
    ...
  }
}
```

### Recrear tabla DynamoDB
```bash
# Eliminar tabla vieja
aws dynamodb delete-table \
    --table-name transactions \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

# Recrear con el script
./scripts/setup-local-infrastructure.sh
```

### Recrear índice OpenSearch
```bash
# Eliminar índice viejo
curl -X DELETE "http://localhost:9200/activity_items" \
    -u admin:G9!vQ2#rT7@pL3xZ

# Recrear con el script
./scripts/setup-local-infrastructure.sh
```

---

## 📊 Monitoring

### Ver mensajes en SQS
```bash
aws sqs receive-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --max-number-of-messages 10 \
    --endpoint-url http://localhost:4566 \
    --region us-east-1
```

### Ver datos en DynamoDB
```bash
aws dynamodb scan \
    --table-name transactions \
    --endpoint-url http://localhost:4566 \
    --region us-east-1
```

### Query OpenSearch
```bash
curl -X GET "http://localhost:9200/activity_items/_search?pretty" \
    -u admin:G9!vQ2#rT7@pL3xZ
```

---

## 🎯 Tips

1. **userId fijo**: Todos los scripts usan `user123` por defecto para facilitar queries
2. **Timestamps**: Todos los eventos usan timestamp UTC actual
3. **IDs únicos**: Cada evento genera IDs únicos con `uuidgen`
4. **Metadata rica**: Cada tipo de transacción incluye metadata específica para testing

---

**Enjoy testing! 🚀**
