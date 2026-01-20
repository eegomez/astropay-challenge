#!/bin/bash

# Publish a pending crypto transaction for user456
# This script creates PENDING transactions for testing status filters

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AMOUNT=${1:-0.25}
CRYPTO_SYMBOL=${2:-ETH}

TRANSACTION_ID="tx-crypto-pending-$(uuidgen | tr '[:upper:]' '[:lower:]')"
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
    "userId": "user456",
    "product": "CRYPTO",
    "type": "WITHDRAWAL",
    "status": "PENDING",
    "amount": $AMOUNT,
    "currency": "USD",
    "description": "Crypto $CRYPTO_SYMBOL withdrawal - pending confirmation",
    "occurredAt": "$TIMESTAMP",
    "metadata": {
      "cryptoSymbol": "$CRYPTO_SYMBOL",
      "cryptoAmount": "$AMOUNT",
      "network": "Ethereum",
      "walletAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
      "confirmations": 1,
      "requiredConfirmations": 6
    }
  }
}
EOF
)

echo "📢 Publishing PENDING CRYPTO transaction to SQS..."
echo "User: user456, Product: CRYPTO, Type: WITHDRAWAL, Status: PENDING"
echo ""

aws sqs send-message \
    --queue-url http://localhost:4566/000000000000/activity-events \
    --message-body "$EVENT_JSON" \
    --endpoint-url http://localhost:4566 \
    --region us-east-1

echo ""
echo "✅ Pending crypto transaction published!"
echo "Transaction ID: $TRANSACTION_ID"
echo "Event ID: $EVENT_ID"
echo ""
