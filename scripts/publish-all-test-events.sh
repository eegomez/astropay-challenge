#!/bin/bash

# Publish all types of test transactions to SQS for comprehensive testing
# Usage: ./publish-all-test-events.sh

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🚀 Publishing diverse test events to SQS for comprehensive testing..."
echo "This will create transactions for different users, products, statuses, and currencies."
echo ""

# USER: user123 - Multiple transaction types
echo "📦 user123 transactions:"
echo ""

# 1. Credit Card Payment (user123, USD, COMPLETED)
echo "1️⃣  CARD payment - user123 (USD, COMPLETED)"
"$SCRIPT_DIR/publish-card-payment.sh" 89.99 "Starbucks"
sleep 1

# 2. Crypto Deposit (user123, USD, COMPLETED)
echo ""
echo "2️⃣  CRYPTO deposit - user123 (USD, COMPLETED)"
"$SCRIPT_DIR/publish-crypto-transaction.sh" 0.5 "BTC"
sleep 1

# 3. Bank Transfer (user123, USD, COMPLETED)
echo ""
echo "3️⃣  BANK transfer - user123 (USD, COMPLETED)"
"$SCRIPT_DIR/publish-bank-transfer.sh" 1500.00
sleep 1

# 4. P2P Transfer (user123, USD, COMPLETED)
echo ""
echo "4️⃣  P2P transfer - user123 (USD, COMPLETED)"
"$SCRIPT_DIR/publish-p2p-transfer.sh" 75.00 "Jane Smith"
sleep 1

# USER: user456 - Different currency and status
echo ""
echo "📦 user456 transactions:"
echo ""

# 5. EUR Card Payment (user456, EUR, COMPLETED)
echo "5️⃣  CARD payment - user456 (EUR, COMPLETED)"
"$SCRIPT_DIR/publish-eur-payment.sh" 45.50 "Carrefour"
sleep 1

# 6. Pending Crypto (user456, USD, PENDING)
echo ""
echo "6️⃣  CRYPTO withdrawal - user456 (USD, PENDING)"
"$SCRIPT_DIR/publish-pending-crypto.sh" 0.25 "ETH"
sleep 1

# USER: user789 - Failed transaction
echo ""
echo "📦 user789 transactions:"
echo ""

# 7. Failed Payment (user789, USD, FAILED)
echo "7️⃣  CARD payment - user789 (USD, FAILED)"
"$SCRIPT_DIR/publish-failed-payment.sh" 250.00 "Amazon"
sleep 1

echo ""
echo "✅ All test events published successfully!"
echo ""
echo "Summary of published transactions:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "👤 user123 (4 transactions):"
echo "   - CARD payment at Starbucks (\$89.99 USD, COMPLETED)"
echo "   - CRYPTO BTC deposit (0.5 BTC, COMPLETED)"
echo "   - BANK transfer (\$1,500.00 USD, COMPLETED)"
echo "   - P2P transfer to Jane Smith (\$75.00 USD, COMPLETED)"
echo ""
echo "👤 user456 (2 transactions):"
echo "   - CARD payment at Carrefour (€45.50 EUR, COMPLETED)"
echo "   - CRYPTO ETH withdrawal (0.25 ETH, PENDING)"
echo ""
echo "👤 user789 (1 transaction):"
echo "   - CARD payment at Amazon (\$250.00 USD, FAILED)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Now you can test various filters in api-examples.http!"
echo ""
