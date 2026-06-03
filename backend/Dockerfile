# syntax=docker/dockerfile:1

FROM golang:1.24-bookworm AS build
WORKDIR /src

COPY go.mod ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=1 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/sector-telemetry ./cmd/server

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd -r -g 10001 sector \
    && useradd -r -u 10001 -g sector sector \
    && mkdir -p /data /app/migrations \
    && chown -R sector:sector /data /app

COPY --from=build /out/sector-telemetry /app/sector-telemetry
COPY migrations /app/migrations

USER sector
EXPOSE 8080

ENV HTTP_ADDR=:8080
ENV DATABASE_PATH=/data/telemetry.db
ENV MIGRATIONS_DIR=/app/migrations

ENTRYPOINT ["/app/sector-telemetry"]
