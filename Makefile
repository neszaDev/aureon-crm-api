# Command catalog. Every target is a thin alias for ./mvnw or docker compose;
# CI calls the same underlying commands. Run `make` to list targets.
.DEFAULT_GOAL := help
PROJECT := $(notdir $(CURDIR))
# Compose files live in docker/, local variables in env/.env; the project directory stays the
# repo root so the build context and volume names don't change.
COMPOSE := docker compose --project-directory . --env-file env/.env -f docker/compose.yml -f docker/compose.override.yml
COMPOSE_ALL := $(COMPOSE) --profile local --profile dev

.PHONY: help setup fmt lint test verify smoke watch up down db-reset

help: ## List targets
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  %-10s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

setup: env/.env ## One-time: enable git hooks, create env/.env from env/.env.example
	git config core.hooksPath .githooks

# Created on first use by any compose target; never overwritten.
env/.env:
	cp env/.env.example env/.env
	@echo "Created env/.env from env/.env.example"

fmt: ## Format all sources (Spotless)
	./mvnw -q spotless:apply

lint: ## Formatting check + compile with -Xlint -Werror
	./mvnw -q spotless:check compile

test: ## Unit tests only (*Test, no Docker needed)
	./mvnw test

verify: ## Everything CI runs: format, compile, unit + integration tests (needs Docker)
	./mvnw verify

smoke: ## Build the runtime image and smoke-test it with the prod profile (as CI does)
	docker build -f docker/Dockerfile --target runtime -t $(PROJECT):smoke .
	scripts/smoke-test.sh $(PROJECT):smoke

watch: env/.env ## Run locally with hot reload (compose local profile)
	$(COMPOSE) --profile local build app-local
	$(COMPOSE) --profile local watch

up: env/.env ## Run the packaged image with a local Postgres (compose dev profile)
	$(COMPOSE) --profile dev up --build

down: env/.env ## Stop local/dev containers (keeps data)
	$(COMPOSE_ALL) down

db-reset: env/.env ## Stop containers and delete the local Postgres volume
	$(COMPOSE_ALL) down
	docker volume rm -f $(PROJECT)_postgres-data
