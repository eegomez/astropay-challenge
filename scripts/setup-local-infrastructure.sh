#!/bin/bash

# Setup script for local development infrastructure
# Requires: Docker, AWS CLI, curl

set -e

# Set dummy credentials for LocalStack
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

echo "🚀 Setting up Activity Feed local infrastructure..."

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Start services using docker-compose
echo -e "${YELLOW}Starting LocalStack and OpenSearch with docker-compose...${NC}"
if ! curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1 || ! curl -s -u admin:G9!vQ2#rT7@pL3xZ http://localhost:9200 > /dev/null 2>&1; then
    echo "Starting services..."
    docker-compose -f ../infra/docker-compose.yml up -d
    echo "Waiting for services to be ready..."
    echo "  - Waiting for LocalStack..."
    sleep 10
    echo "  - Waiting for OpenSearch..."
    sleep 30
else
    echo -e "${GREEN}✓ Services are already running${NC}"
fi

# Verify services are healthy
echo -e "${YELLOW}Verifying services...${NC}"
if curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; then
    echo -e "${GREEN}✓ LocalStack is running${NC}"
else
    echo "ERROR: LocalStack is not responding"
    exit 1
fi

if curl -s -u admin:G9!vQ2#rT7@pL3xZ http://localhost:9200 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ OpenSearch is running${NC}"
else
    echo "ERROR: OpenSearch is not responding"
    exit 1
fi

# Create DynamoDB table with userId (PK), sk (SK), and id GSI for fast lookups
echo -e "${YELLOW}Creating DynamoDB table...${NC}"
aws dynamodb create-table \
    --table-name transactions \
    --attribute-definitions \
        AttributeName=user_id,AttributeType=S \
        AttributeName=sk,AttributeType=S \
        AttributeName=id,AttributeType=S \
    --key-schema \
        AttributeName=user_id,KeyType=HASH \
        AttributeName=sk,KeyType=RANGE \
    --global-secondary-indexes \
        "[{\"IndexName\":\"id-index\",\"KeySchema\":[{\"AttributeName\":\"id\",\"KeyType\":\"HASH\"}],\"Projection\":{\"ProjectionType\":\"ALL\"},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":5,\"WriteCapacityUnits\":5}}]" \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:4566 \
    --region us-east-1 \
    2>/dev/null || echo "Table 'transactions' may already exist"

echo -e "${GREEN}✓ DynamoDB table ready${NC}"

# Create SQS queue
echo -e "${YELLOW}Creating SQS queue...${NC}"
aws sqs create-queue \
    --queue-name activity-events \
    --endpoint-url http://localhost:4566 \
    --region us-east-1 \
    2>/dev/null || echo "Queue 'activity-events' may already exist"

echo -e "${GREEN}✓ SQS queue ready${NC}"

# Create OpenSearch index
echo -e "${YELLOW}Creating OpenSearch index...${NC}"
curl -X PUT "http://localhost:9200/activity_items" \
    -u admin:G9!vQ2#rT7@pL3xZ \
    -H 'Content-Type: application/json' \
    -d '{
      "mappings": {
        "properties": {
          "userId": {"type": "keyword"},
          "sk": {"type": "keyword"},
          "id": {"type": "keyword"},
          "product": {"type": "keyword"},
          "type": {"type": "keyword"},
          "status": {"type": "keyword"},
          "currency": {"type": "keyword"},
          "amount": {"type": "double"},
          "description": {"type": "text"},
          "occurredAt": {"type": "date"},
          "createdAt": {"type": "date"},
          "sourceService": {"type": "keyword"},
          "eventId": {"type": "keyword"},
          "transactionId": {"type": "keyword"},
          "metadata": {
            "type": "object",
            "enabled": true
          }
        }
      }
    }' \
    2>/dev/null || echo "Index 'activity_items' may already exist"

echo -e "${GREEN}✓ OpenSearch index ready${NC}"

echo ""
echo -e "${GREEN}✅ Local infrastructure setup complete!${NC}"
echo ""
echo "Services available at:"
echo "  - LocalStack (DynamoDB, SQS): http://localhost:4566"
echo "  - OpenSearch: http://localhost:9200"
echo ""
echo "You can now run the application with:"
echo "  ./gradlew bootRun"
