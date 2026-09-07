# ==============================================================================
# IMSA 遠端 VPS (Linux / Ubuntu) 一鍵部署腳本 (deploy.sh)
#
# 使用方式：
#   chmod +x deploy.sh
#   ./deploy.sh
# ==============================================================================
set -e

echo "🚀 [IMSA Deploy] 開始執行遠端 VPS 自動化部署流程..."

# 1. 檢查 Docker 與 Docker Compose
if ! command -v docker &> /dev/null; then
    echo "❌ 錯誤：未偵測到 Docker，請先在伺服器安裝 Docker！"
    exit 1
fi

# 2. 檢查 .env 檔案是否存在，若不存在則提示建立
if [ ! -f .env ]; then
    echo "⚠️ 尚未發現 .env 設定檔，自動從 .env.example 複製範本..."
    cp .env.example .env
    echo "💡 請編輯 .env 填入您的正式環境變數（如 Vonage 金鑰），完成後重新執行此腳本。"
fi

# 3. 建置並以背景模式啟動容器
echo "📦 正在編譯最新映像檔並啟動容器服務 (PostgreSQL & Spring Boot Backend)..."
docker compose down --remove-orphans || true
docker compose build backend
docker compose up -d

# 4. 輪詢健康檢查狀態
echo "⏳ 等待服務就緒與資料庫對接 (Healthcheck)..."
MAX_ATTEMPTS=20
ATTEMPT=0
SUCCESS=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    STATUS=$(docker inspect --format='{{json .State.Health.Status}}' imsa-backend 2>/dev/null || echo "null")
    if [ "$STATUS" = "\"healthy\"" ]; then
        SUCCESS=true
        break
    fi
    ATTEMPT=$((ATTEMPT+1))
    echo "   ...等待服務啟動中 (第 $ATTEMPT 次嘗試，狀態: $STATUS)..."
    sleep 3
done

if [ "$SUCCESS" = true ]; then
    echo "✅ [IMSA Deploy] 部署成功！後端服務已 healthy 正常上線運作！"
    echo "🌐 API 端點: http://<您的VPS_IP>:8080/api/users/health"
    docker compose ps
else
    echo "⚠️ 警告：後端容器啟動逾時或尚未進入 healthy 狀態，請查看日誌："
    docker compose logs --tail=40 backend
    exit 1
fi
