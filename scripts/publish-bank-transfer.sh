#!/bin/bash

# Publish a bank transfer event to SQS
# Usage: ./publish-bank-transfer.sh [amount] [destination_account]

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-1500.00}
DESTINATION_ACCOUNT=${2:-"**** **** **** 5678"}

TRANSACTION_ID="tx-bank-$(uuidgen | tr '[:upper:]' '[:lower:]')"
EVENT_ID="evt-$(uuidgen | tr '[:upper:]' '[:lower:]')"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

EVENT_JSON=$(cat <<EOF
{
  "eventId": "$EVENT_ID",
  "eventType": "TRANSACTION_CREATED",
  "sourceService": "bank-service",
  "eventTimestamp": "$TIMESTAMP",
  "payload": {
    "transactionId": "$TRANSACTION_ID",
    "userId": "user123",
    "product": "BANK",
    "type": "TRANSFER",
    "status": "COMPLETED",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "Bank transfer to $DESTINATION_ACCOUNT",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "destinationAccount": "$DESTINATION_ACCOUNT",
      "bankName": "Chase Bank",
      "routingNumber": "021000021",
      "transferType": "ACH",
      "estimatedArrival": "2-3 business days"
    }
  }
}
EOF
)

echo "📢 Publishing BANK TRANSFER transaction to SQS..."
echo "Product: BANK, Type: TRANSFER, Amount: \$$AMOUNT"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ Bank transfer event published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
