#!/usr/bin/env bash

# Market Service WebSocket 快速开始指南
# Quick Start Guide for WebSocket Real-Time Market Data Push

echo "=========================================="
echo "Market Service WebSocket Quick Start"
echo "=========================================="
echo ""

# 定义颜色
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: 编译项目
echo -e "${BLUE}Step 1: Compiling market...${NC}"
cd /Users/ganten/workspace/github/peanuts/market
mvn -q -DskipTests clean compile

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Compilation successful${NC}"
else
    echo -e "${YELLOW}✗ Compilation failed${NC}"
    exit 1
fi

echo ""

# Step 2: 打包项目
echo -e "${BLUE}Step 2: Building JAR package...${NC}"
mvn -q -DskipTests clean package -DskipTests

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Package build successful${NC}"
    JAR_FILE=$(find target -name "market-*.jar" | grep -v sources)
    echo -e "  JAR file: ${GREEN}$JAR_FILE${NC}"
else
    echo -e "${YELLOW}✗ Package build failed${NC}"
    exit 1
fi

echo ""

# Step 3: 信息提示
echo -e "${BLUE}Step 3: Ready to start service${NC}"
echo -e "${GREEN}✓ Build complete!${NC}"
echo ""
echo "To start the market with WebSocket support:"
echo ""
echo "  ${YELLOW}cd /Users/ganten/workspace/github/peanuts/market${NC}"
echo "  ${YELLOW}java -jar target/market-1.0.0-SNAPSHOT.jar${NC}"
echo ""
echo "Or use Spring Boot Maven plugin:"
echo ""
echo "  ${YELLOW}mvn spring-boot:run${NC}"
echo ""

echo "=========================================="
echo "Access Options:"
echo "=========================================="
echo ""
echo "1. WebSocket Test Client (Browser UI):"
echo "   ${BLUE}http://localhost:8082/websocket-client.html${NC}"
echo ""
echo "2. WebSocket Endpoint:"
echo "   ${BLUE}ws://localhost:8082/ws/market${NC}"
echo ""
echo "3. Python Test Script:"
echo "   ${YELLOW}python3 /Users/ganten/workspace/github/peanuts/market/test-websocket.py${NC}"
echo ""

echo "=========================================="
echo "Key WebSocket Push Messages:"
echo "=========================================="
echo ""
echo "When a trade occurs (via Aeron trade stream), the service pushes:"
echo ""
echo "  • Ticker data (market quote with OHLC, volume, turnover)"
echo "  • Candle data x 5 intervals (1m, 5m, 15m, 1h, 1d)"
echo ""
echo "Message format is JSON with 'type' and 'timestamp' fields"
echo ""

echo "=========================================="
echo "Documentation:"
echo "=========================================="
echo ""
echo "Full documentation:"
echo "  ${BLUE}/Users/ganten/workspace/github/peanuts/market/README-WEBSOCKET.md${NC}"
echo ""

echo -e "${GREEN}Ready to go! 🚀${NC}"
echo ""
