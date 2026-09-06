# IMSA (I Am Still Alive) - 單身人士健在監測系統

## 1. 專案簡介
IMSA 是一套專為單身/獨居人士設計的健在監測與安全回報系統。透過 Android 系統級無障礙守護進程，於日常解鎖手機時無感回報心跳，並於超時未活動時自動通知緊急聯絡人。

---

## 2. 雲端伺服器與連線資訊 (Oracle Cloud VPS)

* **公用 IP (Public IP)**: `64.181.242.49`
* **SSH 登入帳號**: `ubuntu`
* **SSH 金鑰本機存放位置 (重要 ⚠️)**:
  ```text
  C:\Users\r1244\.ssh\oracle-vps.key
  ```
* **一鍵連線指令 (PowerShell / CMD)**:
  ```powershell
  ssh -i "C:\Users\r1244\.ssh\oracle-vps.key" ubuntu@64.181.242.49
  ```

---

## 3. 伺服器規格與架構

* **主機型號**: Oracle Cloud Always Free (`VM.Standard.E2.1.Micro`)
* **記憶體配置**: 1 GB 實體 RAM + 4 GB Swapfile 虛擬記憶體 (共 5 GB 可用)
* **磁碟空間**: 75 GB (10 VPU)
* **作業系統**: Ubuntu 24.04 LTS
* **容器環境**: Docker 29.8.0 + Docker Compose v5.5.1
* **對外開放 Port**:
  * `22`: SSH 管理連線
  * `80` / `443`: Web HTTP / HTTPS
  * `8080`: Spring Boot 後端 API 服務 (`http://64.181.242.49:8080`)
  * `8000`: 管理平台 (備用)
