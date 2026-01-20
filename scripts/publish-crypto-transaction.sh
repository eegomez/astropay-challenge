#!/bin/bash

# Publish a crypto purchase/deposit event to SQS
# Usage: ./publish-crypto-transaction.sh [amount] [crypto_symbol]

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-0.5}
CRYPTO_SYMBOL=${2:-BTC}

TRANSACTION_ID="tx-crypto-$(uuidgen | tr '[:upper:]' '[:lower:]')"
EVENT_ID="evt-$(uuidgen | tr '[:upper:]' '[:lower:]')"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

EVENT_JSON=$(cat <<EOF
{
  "eventId": "$EVENT_ID",
  "eventType": "TRANSACTION_CREATED",
  "sourceService": "crypto-service",
  "eventTimestamp": "$TIMESTAMP",
  "payload": {
    "transactionId": "$TRANSACTION_ID",
    "userId": "user123",
    "product": "CRYPTO",
    "type": "DEPOSIT",
    "status": "COMPLETED",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "Crypto $CRYPTO_SYMBOL purchase",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "cryptoSymbol": "$CRYPTO_SYMBOL",
      "cryptoAmount": "$AMOUNT",
      "exchangeRate": "45000.00",
      "network": "Bitcoin",
      "walletAddress": "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"
    }
  }
}
EOF
)

echo "📢 Publishing CRYPTO transaction to SQS..."
echo "Product: CRYPTO, Type: DEPOSIT, Amount: $AMOUNT $CRYPTO_SYMBOL"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ Crypto transaction event published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
