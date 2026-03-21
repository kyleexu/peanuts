#!/usr/bin/env python3
"""
WebSocket Client Test Script
用于测试 market WebSocket 推送功能
"""

import asyncio
import websockets
import json
import sys
from datetime import datetime


class MarketWebSocketClient:
    def __init__(self, uri="ws://localhost:8080/ws/market"):
        self.uri = uri
        self.ws = None
        self.message_count = 0
        self.ticker_count = 0
        self.candle_count = 0

    async def connect(self):
        """建立 WebSocket 连接"""
        try:
            self.ws = await websockets.connect(self.uri)
            print(f"✓ Connected to {self.uri}")
            return True
        except Exception as e:
            print(f"✗ Connection failed: {e}")
            return False

    async def disconnect(self):
        """断开 WebSocket 连接"""
        if self.ws:
            await self.ws.close()
            print("✓ Disconnected")

    async def listen(self, timeout=60):
        """监听 WebSocket 消息"""
        try:
            start_time = datetime.now()
            print(f"\nListening for messages (timeout: {timeout}s)...")
            print("-" * 80)

            async for message in self.ws:
                self.message_count += 1
                
                try:
                    data = json.loads(message)
                    msg_type = data.get("type", "unknown")
                    timestamp = data.get("timestamp", 0)
                    
                    if msg_type == "ticker":
                        self.ticker_count += 1
                        ticker = data.get("ticker", {})
                        contract = ticker.get("contract", "N/A")
                        last_price = ticker.get("lastPrice", 0)
                        volume = ticker.get("volume", 0)
                        print(f"[TICKER #{self.ticker_count}] {contract}: Price={last_price}, Volume={volume}")
                        
                    elif msg_type == "candle":
                        self.candle_count += 1
                        candle = data.get("candle", {})
                        contract = candle.get("contract", "N/A")
                        interval = candle.get("interval", "N/A")
                        close = candle.get("close", 0)
                        high = candle.get("high", 0)
                        low = candle.get("low", 0)
                        print(f"[CANDLE #{self.candle_count}] {contract} {interval}: O={candle.get('open', 0):.2f}, H={high:.2f}, L={low:.2f}, C={close:.2f}")
                    
                except json.JSONDecodeError as e:
                    print(f"[ERROR] Failed to parse message: {e}")

                # 检查超时
                elapsed = (datetime.now() - start_time).total_seconds()
                if elapsed > timeout:
                    print(f"\nTimeout after {elapsed:.1f}s")
                    break

        except Exception as e:
            print(f"✗ Error listening: {e}")

    def print_stats(self):
        """打印统计信息"""
        print("-" * 80)
        print(f"\nStatistics:")
        print(f"  Total Messages: {self.message_count}")
        print(f"  Ticker Messages: {self.ticker_count}")
        print(f"  Candle Messages: {self.candle_count}")
        print()

    async def run(self, timeout=60):
        """运行客户端"""
        if await self.connect():
            await self.listen(timeout)
            await self.disconnect()
            self.print_stats()
        else:
            sys.exit(1)


async def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Market WebSocket Client Test")
    parser.add_argument("--uri", default="ws://localhost:8080/ws/market", help="WebSocket URI")
    parser.add_argument("--timeout", type=int, default=60, help="Listen timeout in seconds")
    
    args = parser.parse_args()
    
    client = MarketWebSocketClient(args.uri)
    await client.run(args.timeout)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
        sys.exit(0)
