# MW-Planner

---

## Product Handover

- Detailed handover documentation: [PRODUCT_HANDOVER.md](PRODUCT_HANDOVER.md)
# Local Setup Guide

This directory provides Docker Compose scripts and configuration files to set up all required services for local development.

## Services Overview

| Service            | Ports           | Credentials / Access Details                         | Purpose / Description                                                                                       |
|--------------------|-----------------|------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| **Redis**          | 6379            | No authentication by default                         | In-memory cache and message broker                                                                          |
| **MinIO (AWS S3)** | 9000, 9001      | User: `admin`<br>Password: `admin123`                | AWS S3-compatible object storage for file uploads and creatives. Can be also used as cloud agnostic tool ** |
| **Prometheus**     | 9090            | No authentication                                    | Monitoring and metrics collection                                                                           |
| **Grafana**        | 13000           | User: `admin`<br>Password: `admin`                   | Visualization and dashboard for metrics                                                                     |
| **MongoDB**        | 27017           | DB: `mw-planner`                                     | NoSQL database for storing unstructured for the application data                                            |
| **Mongo Express**  | 8081            | User: `admin`<br>Password: `admin`                   | Web-based UI for managing MongoDB                                                                           |


> Environment variables are set in your shell or `.env` file.

## Setup Instructions

### Note: Ensure Docker and Docker Compose are installed on your machine.

1. **Start Services**
   ```sh
   cd .\local-setup
   docker compose up -d
   ```

2. **Access Services**
    - Redis: `localhost:6379`
    - MinIO Console: `http://localhost:9001`
    - Prometheus: `http://localhost:9090`
    - Grafana: `http://localhost:13000`
    - MongoDB: `localhost:27017`
    - Mongo Express: `http://localhost:8081`

3. **Grafana Setup**
    - Login to Grafana at `http://localhost:13000` (default: `admin` / `admin`)
    - Add Prometheus data source:
        - Go to **Configuration > Data Sources**
        - Click **Add data source**
        - Select **Prometheus**
        - Set URL to `http://prometheus:9090`
        - Click **Save & Test**
    - Import Dashboard:
        - Go to **Create > Import**
        - Upload `./local-setup/grafana-boards/Non functional Dashboard - Spring Boot-MongoDb.json`
        - Assign Prometheus as the data source

## Notes

- Database initialization scripts and configuration files are located in `local-setup` directory.
- For custom environment variables, reuse the `.env` file or set them in your shell before running Docker Compose.

---

Refer to individual config files for advanced settings.