#!/bin/bash

# Publish a EUR card payment transaction
# This script creates EUR transactions for testing currency filters

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-45.50}
MERCHANT=${2:-"Carrefour"}

TRANSACTION_ID="tx-card-eur-$(uuidgen | tr '[:upper:]' '[:lower:]')"
EVENT_ID="evt-$(uuidgen | tr '[:upper:]' '[:lower:]')"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

EVENT_JSON=$(cat <<EOF
{
  "eventId": "$EVENT_ID",
  "eventType": "TRANSACTION_CREATED",
  "sourceService": "card-service",
  "eventTimestamp": "$TIMESTAMP",
  "payload": {
    "transactionId": "$TRANSACTION_ID",
    "userId": "user456",
    "product": "CARD",
    "type": "PAYMENT",
    "status": "COMPLETED",
    "amount": $AMOUNT,
    "currency": "EUR",
    "description": "Card payment at $MERCHANT",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "merchantName": "$MERCHANT",
      "merchantCategory": "5411",
      "merchantCategoryDescription": "Grocery Store",
      "cardLast4": "5678",
      "cardBrand": "Visa",
      "paymentMethod": "chip",
      "location": "Paris, France"
    }
  }
}
EOF
)

echo "📢 Publishing EUR CARD PAYMENT transaction to SQS..."
echo "User: user456, Product: CARD, Currency: EUR, Amount: €$AMOUNT"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ EUR payment transaction published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
