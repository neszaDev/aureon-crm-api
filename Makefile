# Command catalog. Every target is a thin alias for ./mvnw or docker compose;
# CI calls the same underlying commands. Run `make` to list targets.
.DEFAULT_GOAL := help
PROJECT := $(notdir $(CURDIR))
COMPOSE_ALL := docker compose --profile local --profile dev

.PHONY: help setup fmt lint test verify watch up down db-reset

help: ## List targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  %-10s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

setup: ## One-time: enable git hooks, create .env from .env.example
	git config core.hooksPath .githooks
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")

fmt: ## Format all sources (Spotless)
	./mvnw -q spotless:apply

lint: ## Formatting check + compile with -Xlint -Werror
	./mvnw -q spotless:check compile

test: ## Unit tests only (*Test, no Docker needed)
	./mvnw test

verify: ## Everything CI runs: format, compile, unit + integration tests (needs Docker)
	./mvnw verify

watch: ## Run locally with hot reload (compose local profile)
	docker compose --profile local build app-local
	docker compose --profile local watch

up: ## Run the packaged image with a local Postgres (compose dev profile)
	docker compose --profile dev up --build

down: ## Stop local/dev containers (keeps data)
	$(COMPOSE_ALL) down

db-reset: ## Stop containers and delete the local Postgres volume
	$(COMPOSE_ALL) down
	docker volume rm -f $(PROJECT)_postgres-data
