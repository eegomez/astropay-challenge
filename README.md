# Activity Feed Service - Local Development Guide

> **Senior Backend Engineer Challenge - AstroPay**

A scalable, real-time system to consolidate all financial transactions into a unified Activity Feed with instant search, filtering, and efficient pagination.

---

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Detailed Setup](#detailed-setup)
4. [Testing the API](#testing-the-api)
5. [Troubleshooting](#troubleshooting)
6. [Project Structure](#project-structure)
7. [Additional Resources](#additional-resources)

---

## Prerequisites

Before running the application, ensure you have the following installed:

### Required
- **Java 21** - [Download](https://adoptium.net/)
- **Docker** - [Download](https://www.docker.com/get-started)
- **AWS CLI** - [Installation Guide](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)

### Verify Installation
```bash
java -version    # Should show Java 21
docker --version # Should show Docker 20+
aws --version    # Should show AWS CLI 2+
```

### Optional (Recommended)
- **IntelliJ IDEA** or **VS Code** with REST Client extension
- **curl** (for API testing from terminal)

---

## Quick Start

```bash
# 1. Navigate to project directory
cd /Users/{YOUR_USER}/repositories/activity-feed-service

# 2. Setup infrastructure (Docker containers + AWS resources)
cd scripts
chmod +x setup-local-infrastructure.sh
./setup-local-infrastructure.sh
cd ..

# 3. Start the application
./gradlew bootRun

# 4. In a new terminal, publish test data
cd scripts
chmod +x publish-all-test-events.sh
./publish-all-test-events.sh
cd ..

# 5. Test the API
# Open api-examples.http in your IDE or use curl:
curl http://localhost:8080/api/v1/health
```

---

## Detailed Setup

### Step 1: Infrastructure Setup

The setup script will:
- Start LocalStack (AWS emulation) and OpenSearch containers
- Create DynamoDB table `transactions` with GSI `id-index`
- Create SQS queue `activity-events`
- Create OpenSearch index `activity_items`

```bash
cd scripts
chmod +x setup-local-infrastructure.sh
./setup-local-infrastructure.sh
```

**Expected Output:**
```
🚀 Setting up Activity Feed local infrastructure...
Starting LocalStack and OpenSearch with docker-compose...
✓ LocalStack is running
✓ OpenSearch is running
✓ DynamoDB table ready
✓ SQS queue ready
✓ OpenSearch index ready

✅ Local infrastructure setup complete!

Services available at:
  - LocalStack (DynamoDB, SQS): http://localhost:4566
  - OpenSearch: http://localhost:9200
```

**Troubleshooting:**
- If ports 4566 or 9200 are already in use, stop conflicting services
- If Docker is not running, start Docker Desktop first
- On Mac with Colima, make sure Colima is running: `colima start`

### Step 2: Start the Application

```bash
# From project root
./gradlew bootRun
```

**Expected Output:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.1)

...
Started ActivityFeedServiceApplication in 5.123 seconds
```

The application runs on **http://localhost:8080**

**Verify it's running:**
```bash
curl http://localhost:8080/api/v1/health
```

Expected response:
```json
{
  "service": "activity-feed",
  "timestamp": "2026-01-20T10:30:00Z",
  "status": "UP",
  "dependencies": {
    "dynamodb": "UP",
    "sqs": "UP",
    "opensearch": "UP"
  }
}
```

### Step 3: Publish Test Events

This script publishes 7 test transactions to SQS that will be consumed by the service:

```bash
cd scripts
chmod +x publish-all-test-events.sh
./publish-all-test-events.sh
```

**Test Data Created:**
- **user123** (4 transactions):
  - CARD payment at Starbucks ($89.99 USD, COMPLETED)
  - CRYPTO BTC deposit (0.5 BTC, COMPLETED)
  - BANK transfer ($1,500.00 USD, COMPLETED)
  - P2P transfer to Jane Smith ($75.00 USD, COMPLETED)

- **user456** (2 transactions):
  - CARD payment at Carrefour (€45.50 EUR, COMPLETED)
  - CRYPTO ETH withdrawal (0.25 ETH, PENDING)

- **user789** (1 transaction):
  - CARD payment at Amazon ($250.00 USD, FAILED)

**Wait 5-10 seconds** for the SQS consumer to process all events.

**Verify events were processed:**
Check application logs for:
```
Successfully processed and deleted message: <message-id>
```

---

## Testing the API

### Option 1: Using api-examples.http (Recommended)

Open `api-examples.http` in IntelliJ IDEA or VS Code with REST Client extension.

**Features:**
- Pre-configured requests for all endpoints
- Step-by-step pagination examples
- Filter combinations (product, status, currency, date range)
- Full-text search examples
- Metadata queries

**Click the green "Run" arrow next to any request** to execute it.

### Option 2: Using curl

#### Health Check
```bash
curl http://localhost:8080/api/v1/health
```

#### Get All Transactions for user123
```bash
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions"
```

#### Get Transactions with Filters
```bash
# Filter by product
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions?product=CARD"

# Filter by status
curl "http://localhost:8080/api/v1/activity-feed/users/user456/transactions?status=PENDING"

# Filter by date range
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions?startDate=2026-01-01T00:00:00Z&endDate=2026-12-31T23:59:59Z"

# Full-text search
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions?searchText=Starbucks"

# Multiple filters
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions?product=CARD&status=COMPLETED&currency=USD"
```

#### Get Transaction by ID
```bash
# First, get a transaction ID from the list
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions" | jq '.data.content[0].id'

# Then fetch by ID (replace with actual ID)
curl "http://localhost:8080/api/v1/activity-feed/transactions/tx-card-abc123"
```

#### Pagination Example
```bash
# Page 1: Get first 2 transactions
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions?limit=2" | jq '.data.nextCursor'

# Page 2: Use the cursor from page 1 (replace <CURSOR> with actual value)
curl "http://localhost:8080/api/v1/activity-feed/users/user123/transactions?limit=2&cursor=<CURSOR>"
```

### Option 3: Swagger UI

Navigate to: **http://localhost:8080/swagger-ui.html**

Interactive API documentation with:
- All endpoints documented
- Request/response schemas
- "Try it out" functionality
- Example values

---

## Troubleshooting

### Application won't start

**Problem:** Port 8080 already in use
```bash
# Find process using port 8080
lsof -i :8080

# Kill the process (replace PID with actual process ID)
kill -9 <PID>
```

**Problem:** Cannot connect to LocalStack
```bash
# Check if LocalStack is running
curl http://localhost:4566/_localstack/health

# If not running, restart infrastructure
cd scripts
./setup-local-infrastructure.sh
```

### No transactions returned

**Problem:** Events not published or not processed

```bash
# 1. Check SQS queue has messages
aws sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/activity-events \
  --attribute-names ApproximateNumberOfMessages \
  --endpoint-url http://localhost:4566 \
  --region us-east-1

# 2. Check DynamoDB has data
aws dynamodb scan \
  --table-name transactions \
  --endpoint-url http://localhost:4566 \
  --region us-east-1

# 3. Check OpenSearch has data
curl -u admin:G9!vQ2#rT7@pL3xZ "http://localhost:9200/activity_items/_search?pretty"

# 4. Re-publish test events
cd scripts
./publish-all-test-events.sh
```

### OpenSearch connection refused

**Problem:** OpenSearch container not healthy

```bash
# Check OpenSearch logs
docker logs -f <opensearch-container-id>

# Restart OpenSearch
docker-compose -f infra/docker-compose.yml restart opensearch

# Wait 30 seconds for OpenSearch to be ready
sleep 30
```

### Clear all data and start fresh

```bash
# 1. Stop the application (Ctrl+C)

# 2. Teardown infrastructure
cd scripts
chmod +x teardown-local-infrastructure.sh
./teardown-local-infrastructure.sh

# 3. Setup infrastructure again
./setup-local-infrastructure.sh

# 4. Start application
cd ..
./gradlew bootRun

# 5. Publish test data
cd scripts
./publish-all-test-events.sh
```

---

## Project Structure

```
activity-feed-service/
├── src/
│   ├── main/
│   │   ├── java/com/wallet/activityfeed/
│   │   │   ├── config/          # AWS, OpenSearch, CORS configuration
│   │   │   ├── consumer/        # SQS event consumer
│   │   │   ├── controller/      # REST API endpoints
│   │   │   ├── domain/          # DynamoDB entities
│   │   │   ├── dto/             # Request/Response DTOs
│   │   │   ├── event/           # Event models
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── mapper/          # Entity ↔ DTO mappers
│   │   │   ├── repository/      # DynamoDB + OpenSearch repos
│   │   │   └── usecase/         # Business logic services
│   │   └── resources/
│   │       └── application.yml  # Configuration
│   └── test/                    # Unit tests
├── scripts/
│   ├── setup-local-infrastructure.sh    # Setup script
│   ├── teardown-local-infrastructure.sh # Cleanup script
│   ├── publish-all-test-events.sh       # Publish test data
│   └── publish-*.sh                     # Individual event publishers
├── infra/
│   └── docker-compose.yml       # LocalStack + OpenSearch
├── api-examples.http            # API test examples
├── openapi.yaml                 # OpenAPI 3.0 specification
├── ARCHITECTURE.md              # Architecture documentation
└── README.md                    # This file
```

---

## Additional Resources

### Documentation
- **Architecture Deep Dive**: See [ARCHITECTURE.md](ARCHITECTURE.md)
- **API Specification**: See [openapi.yaml](openapi.yaml)
- **Scripts Documentation**: See [scripts/README.md](scripts/README.md)

### Key Endpoints
- **Application**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Docs**: http://localhost:8080/api-docs
- **Health Check**: http://localhost:8080/api/v1/health
- **LocalStack**: http://localhost:4566
- **OpenSearch**: http://localhost:9200

### Default Credentials
- **LocalStack AWS**:
  - Access Key: `test`
  - Secret Key: `test`
  - Region: `us-east-1`
- **OpenSearch**:
  - Username: `admin`
  - Password: `G9!vQ2#rT7@pL3xZ`

### Development Tips
1. **Hot Reload**: Use `./gradlew bootRun --continuous` for auto-restart on code changes
2. **Debug Mode**: Run with `./gradlew bootRun --debug-jvm` and attach debugger on port 5005
3. **Build**: `./gradlew build` - Compiles code and runs tests
4. **Tests Only**: `./gradlew test` - Run unit tests
5. **Clean Build**: `./gradlew clean build` - Fresh build

---

## Next Steps

1. ✅ Run the application locally
2. ✅ Explore API examples in `api-examples.http`
3. ✅ Read architecture documentation in `ARCHITECTURE.md`
4. 📖 Review the code structure
5. 🧪 Write additional tests
6. 🚀 Deploy to AWS (replace LocalStack with real AWS services)

---

**Questions or Issues?**

Check the logs:
```bash
# Application logs (while running)
# Shown in terminal where you ran ./gradlew bootRun

# Docker logs
docker-compose -f infra/docker-compose.yml logs -f
```

**Happy Coding! 🚀**
