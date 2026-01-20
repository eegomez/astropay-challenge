#!/bin/bash

# Publish a failed card payment transaction
# This script creates FAILED transactions for testing status filters

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-250.00}
MERCHANT=${2:-"Amazon"}

TRANSACTION_ID="tx-card-failed-$(uuidgen | tr '[:upper:]' '[:lower:]')"
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
    "userId": "user789",
    "product": "CARD",
    "type": "PAYMENT",
    "status": "FAILED",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "Failed payment at $MERCHANT - Insufficient funds",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "merchantName": "$MERCHANT",
      "merchantCategory": "5999",
      "merchantCategoryDescription": "Online Shopping",
      "cardLast4": "1234",
      "cardBrand": "Mastercard",
      "failureReason": "INSUFFICIENT_FUNDS",
      "location": "Online"
    }
  }
}
EOF
)

echo "📢 Publishing FAILED CARD PAYMENT transaction to SQS..."
echo "User: user789, Product: CARD, Status: FAILED, Amount: \$$AMOUNT"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ Failed payment transaction published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
