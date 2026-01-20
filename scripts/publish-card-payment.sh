#!/bin/bash

# Publish a credit card payment event to SQS
# Usage: ./publish-card-payment.sh [amount] [merchant]

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-89.99}
MERCHANT=${2:-"Starbucks"}

TRANSACTION_ID="tx-card-$(uuidgen | tr '[:upper:]' '[:lower:]')"
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
    "userId": "user123",
    "product": "CARD",
    "type": "PAYMENT",
    "status": "COMPLETED",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "Card payment at $MERCHANT",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "merchantName": "$MERCHANT",
      "merchantCategory": "5812",
      "merchantCategoryDescription": "Restaurants",
      "cardLast4": "4242",
      "cardBrand": "Visa",
      "paymentMethod": "contactless",
      "location": "New York, NY"
    }
  }
}
EOF
)

echo "📢 Publishing CARD PAYMENT transaction to SQS..."
echo "Product: CARD, Type: PAYMENT, Amount: \$$AMOUNT at $MERCHANT"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ Card payment event published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
