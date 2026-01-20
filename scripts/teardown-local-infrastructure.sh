#!/bin/bash

# Teardown script for local development infrastructure
# Stops and removes containers started by docker-compose

set -e

# Set dummy credentials for LocalStack (needed for cleanup commands)
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

echo "🛑 Tearing down Activity Feed local infrastructure..."

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Stopping services...${NC}"
docker-compose -f ../infra/docker-compose.yml down

echo -e "${YELLOW}Removing stopped containers (if any)...${NC}"
docker-compose -f ../infra/docker-compose.yml rm -f

echo ""
echo -e "${RED}✅ Local infrastructure stopped!${NC}"
echo ""
echo "To start services again, run:"
echo "  ./scripts/setup-local-infrastructure.sh"
echo ""
