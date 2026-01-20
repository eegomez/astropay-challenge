#!/bin/bash

# Publish a P2P transfer transaction
# This script creates P2P transactions for testing product filters

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-75.00}
RECIPIENT_NAME=${2:-"Jane Smith"}

TRANSACTION_ID="tx-p2p-$(uuidgen | tr '[:upper:]' '[:lower:]')"
EVENT_ID="evt-$(uuidgen | tr '[:upper:]' '[:lower:]')"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

EVENT_JSON=$(cat <<EOF
{
  "eventId": "$EVENT_ID",
  "eventType": "TRANSACTION_CREATED",
  "sourceService": "p2p-service",
  "eventTimestamp": "$TIMESTAMP",
  "payload": {
    "transactionId": "$TRANSACTION_ID",
    "userId": "user123",
    "product": "P2P",
    "type": "TRANSFER",
    "status": "COMPLETED",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "P2P transfer to $RECIPIENT_NAME",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "recipientId": "user999",
      "recipientName": "$RECIPIENT_NAME",
      "recipientEmail": "jane.smith@example.com",
      "message": "Thanks for lunch!",
      "transferType": "INSTANT",
      "fee": 0.50
    }
  }
}
EOF
)

echo "📢 Publishing P2P TRANSFER transaction to SQS..."
echo "User: user123, Product: P2P, Type: TRANSFER, Amount: \$$AMOUNT"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ P2P transfer transaction published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
