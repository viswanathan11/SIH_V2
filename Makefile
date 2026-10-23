.PHONY: all build test dev clean up down smoke reset

all: build test

build:
	cd backend && mvn clean package -DskipTests
	cd frontend && npm run build

test:
	cd backend && mvn test
	cd frontend && npm test

dev-backend:
	cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local-embedded

dev-frontend:
	cd frontend && npm run dev

up:
	docker compose up -d --build

down:
	docker compose down -v

smoke:
	bash scripts/smoke.sh http://localhost:8080

reset:
	bash scripts/reset-demo.sh http://localhost:8080
