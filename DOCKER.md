# Docker Setup for Auto-Assessor

This project includes Docker configuration for containerized deployment.

## Quick Start

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### Build and Run

1. **Copy environment template:**
   ```bash
   cp .env.example .env
   ```

2. **Add your secrets to `.env`:**
   ```bash
   OPENAI_API_KEY=your-actual-key
   AWS_ACCESS_KEY_ID=your-key
   AWS_SECRET_ACCESS_KEY=your-secret
   ```

3. **Start services:**
   ```bash
   docker-compose up --build
   ```

   The application will be available at `http://localhost:8080`

### Development Workflow

Run the app with MongoDB:
```bash
docker-compose up
```

Rebuild after code changes:
```bash
docker-compose up --build
```

View logs:
```bash
docker-compose logs -f auto-assessor
```

Stop services:
```bash
docker-compose down
```

## Building Standalone Docker Image

Build for local use:
```bash
docker build -t auto-assessor:latest .
```

Build for specific platform (e.g., ARM64 Mac):
```bash
docker buildx build --platform=linux/amd64 -t auto-assessor:latest .
```

Run standalone (requires external MongoDB):
```bash
docker run -p 8080:8080 \
  -e MONGODB_URI="mongodb://..." \
  -e OPENAI_API_KEY="sk-..." \
  auto-assessor:latest
```

## Multi-Stage Build Details

The `Dockerfile` uses a multi-stage build pattern:
1. **Builder stage**: Compiles with JDK 25 (eclipse-temurin:25-jdk-alpine)
2. **Runtime stage**: Runs with JRE 25 (eclipse-temurin:25-jre-alpine)

Benefits:
- Final image ~70-90% smaller than including build tools
- Non-root user (`appuser:1001`) for security
- Health check endpoint `/actuator/health`

## Environment Variables

Required for runtime:
- `MONGODB_URI` - MongoDB connection string with credentials
- `OPENAI_API_KEY` - OpenAI API key
- `AWS_ACCESS_KEY_ID` - AWS access key
- `AWS_SECRET_ACCESS_KEY` - AWS secret key
- `OPENAI_ORGANIZATION` (optional) - OpenAI organization ID

## Security Notes

- Never commit `.env` or actual secrets to git
- Docker Compose file mounts `.env` for local development only
- Use Docker secrets or external secret management (e.g., AWS Secrets Manager) in production
- Images run with non-root user (UID 1001) for enhanced security
- MongoDB credentials in docker-compose are for development; rotate in production

## Production Deployment

For production, use a container orchestration platform (Kubernetes, Azure Container Instances, AWS ECS, etc.) with:
- Managed secrets (e.g., AWS Secrets Manager, Azure Key Vault)
- Health checks enabled (already configured)
- Resource limits set
- Network policies enforced
- Image scanning enabled
