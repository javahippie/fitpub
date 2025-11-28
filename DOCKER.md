# Docker Deployment Guide

This guide explains how to deploy FitPub using Docker and Docker Compose.

## Prerequisites

- Docker Engine 20.10 or later
- Docker Compose 2.0 or later

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd feditrack
```

### 2. Create Environment File

Copy the example environment file and customize it:

```bash
cp .env.example .env
```

### 3. Configure Environment Variables

Edit `.env` and update the following critical values:

**Security (REQUIRED):**
```bash
# Generate a secure JWT secret
JWT_SECRET=$(openssl rand -base64 64)

# Use a strong database password
POSTGRES_PASSWORD=$(openssl rand -base64 32)
```

**Domain Configuration (REQUIRED):**
```bash
APP_DOMAIN=your-domain.com
APP_BASE_URL=https://your-domain.com
```

### 4. Start the Application

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Check service status
docker-compose ps
```

### 5. Verify Deployment

The application should be available at:
- Application: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health

## Environment Variables

See `.env.example` for all available configuration options:

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_DB` | Database name | fitpub |
| `POSTGRES_USER` | Database user | fitpub |
| `POSTGRES_PASSWORD` | Database password | **MUST CHANGE** |
| `POSTGRES_PORT` | Database port | 5432 |
| `APP_PORT` | Application port | 8080 |
| `APP_DOMAIN` | Your domain name | example.com |
| `APP_BASE_URL` | Full application URL | https://example.com |
| `JWT_SECRET` | JWT signing secret | **MUST CHANGE** |
| `JWT_EXPIRATION_MS` | JWT expiration time | 86400000 (24h) |

## Docker Compose Services

### postgres
- **Image:** postgis/postgis:16-3.4
- **Port:** 5432 (configurable via POSTGRES_PORT)
- **Volume:** `postgres_data` - Persistent database storage
- **Health Check:** PostgreSQL readiness check

### app
- **Build:** From Dockerfile
- **Port:** 8080 (configurable via APP_PORT)
- **Volumes:**
  - `app_uploads` - User uploaded files
  - `app_logs` - Application logs
- **Health Check:** Spring Boot Actuator health endpoint
- **Depends On:** postgres (waits for healthy state)

## Volumes

Three named volumes are created for data persistence:

```bash
# List volumes
docker volume ls | grep fitpub

# Inspect volume
docker volume inspect feditrack_postgres_data

# Backup database volume
docker run --rm -v feditrack_postgres_data:/data -v $(pwd):/backup \
  alpine tar czf /backup/postgres-backup-$(date +%Y%m%d).tar.gz -C /data .

# Restore database volume
docker run --rm -v feditrack_postgres_data:/data -v $(pwd):/backup \
  alpine tar xzf /backup/postgres-backup-YYYYMMDD.tar.gz -C /data
```

## Common Operations

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f app
docker-compose logs -f postgres
```

### Restart Services

```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart app
```

### Stop Services

```bash
# Stop services (keeps containers)
docker-compose stop

# Stop and remove containers (keeps volumes)
docker-compose down

# Stop and remove everything including volumes (DANGER: data loss)
docker-compose down -v
```

### Execute Commands in Container

```bash
# Access app container shell
docker-compose exec app bash

# Access PostgreSQL CLI
docker-compose exec postgres psql -U fitpub -d fitpub

# Run SQL query
docker-compose exec postgres psql -U fitpub -d fitpub -c "SELECT version();"
```

### Database Operations

```bash
# Create database backup
docker-compose exec postgres pg_dump -U fitpub fitpub > backup.sql

# Restore database backup
docker-compose exec -T postgres psql -U fitpub fitpub < backup.sql

# Check Flyway migration status
docker-compose exec postgres psql -U fitpub -d fitpub -c \
  "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"
```

### Rebuild Application

```bash
# Rebuild and restart app
docker-compose up -d --build app

# Force rebuild without cache
docker-compose build --no-cache app
docker-compose up -d app
```

## Production Deployment

### Security Checklist

- [ ] Change `POSTGRES_PASSWORD` to a strong random password
- [ ] Generate secure `JWT_SECRET` using `openssl rand -base64 64`
- [ ] Set correct `APP_DOMAIN` and `APP_BASE_URL`
- [ ] Configure HTTPS/TLS (use reverse proxy like nginx or Traefik)
- [ ] Disable `JPA_SHOW_SQL` and `JPA_FORMAT_SQL`
- [ ] Set appropriate log levels (INFO or WARN for production)
- [ ] Configure firewall rules (only expose necessary ports)
- [ ] Set up regular database backups
- [ ] Configure volume backup strategy
- [ ] Review and restrict network access

### Reverse Proxy Example (nginx)

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Monitoring

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Database health
docker-compose exec postgres pg_isready -U fitpub
```

### Resource Usage

```bash
# Container stats
docker stats

# Specific container stats
docker stats fitpub-app fitpub-postgres
```

## Troubleshooting

### Application Won't Start

```bash
# Check logs
docker-compose logs app

# Check if database is ready
docker-compose ps postgres
docker-compose exec postgres pg_isready -U fitpub

# Verify environment variables
docker-compose config
```

### Database Connection Issues

```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Test database connection
docker-compose exec postgres psql -U fitpub -d fitpub -c "SELECT 1;"

# Check network connectivity
docker-compose exec app ping postgres
```

### Migration Failures

```bash
# Check Flyway schema history
docker-compose exec postgres psql -U fitpub -d fitpub -c \
  "SELECT * FROM flyway_schema_history;"

# Reset database (DANGER: data loss)
docker-compose down -v
docker-compose up -d
```

### Out of Disk Space

```bash
# Check Docker disk usage
docker system df

# Clean up unused resources
docker system prune -a --volumes

# Remove specific volume
docker volume rm feditrack_postgres_data
```

## Development Mode

For local development with live reload:

```bash
# Use development profile
echo "SPRING_PROFILES_ACTIVE=dev" >> .env

# Enable SQL logging
echo "JPA_SHOW_SQL=true" >> .env
echo "JPA_FORMAT_SQL=true" >> .env

# Mount source code for live reload (modify docker-compose.yml)
# Add under app.volumes:
#   - ./src:/app/src
```

## Scaling

To run multiple app instances behind a load balancer:

```bash
# Scale app service
docker-compose up -d --scale app=3

# Note: You'll need to configure a load balancer and remove
# the container_name directive from docker-compose.yml
```

## Updating

```bash
# Pull latest code
git pull

# Rebuild and restart
docker-compose down
docker-compose up -d --build

# Check migration status
docker-compose logs app | grep -i flyway
```
