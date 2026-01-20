#!/bin/bash

# Script to publish a test transaction event to SQS
# Usage: ./publish-test-event.sh [product] [type] [amount]
# Examples:
#   ./publish-test-event.sh CARD PAYMENT 89.99
#   ./publish-test-event.sh CRYPTO DEPOSIT 0.5
#   ./publish-test-event.sh BANK TRANSFER 1500.00

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

PRODUCT=${1:-CARD}
TYPE=${2:-PAYMENT}
AMOUNT=${3:-50.00}

TRANSACTION_ID="tx-$(uuidgen | tr '[:upper:]' '[:lower:]')"
EVENT_ID="evt-$(uuidgen | tr '[:upper:]' '[:lower:]')"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

EVENT_JSON=$(cat <<EOF
{
  "eventId": "$EVENT_ID",
  "eventType": "TRANSACTION_CREATED",
  "sourceService": "test-service",
  "eventTimestamp": "$TIMESTAMP",
  "payload": {
    "transactionId": "$TRANSACTION_ID",
    "userId": "user123",
    "product": "$PRODUCT",
    "type": "$TYPE",
    "status": "COMPLETED",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "$PRODUCT $TYPE transaction",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "source": "test-script",
      "testData": true
    }
  }
}
EOF
)

echo "📢 Publishing test event to SQS..."
echo "Product: $PRODUCT, Type: $TYPE, Amount: \$$AMOUNT"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ Event published successfully!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
echo "The event will be processed within 5 seconds by the consumer."
echo "Check application logs to verify processing."
