package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/prishvindt/sector/backend/internal/config"
	httpapi "github.com/prishvindt/sector/backend/internal/http"
	"github.com/prishvindt/sector/backend/internal/reporter"
	"github.com/prishvindt/sector/backend/internal/store"
)

func main() {
	logger := log.New(os.Stdout, "sector-telemetry: ", log.LstdFlags|log.LUTC)

	cfg, err := config.Load()
	if err != nil {
		logger.Fatalf("load config: %v", err)
	}
	if cfg.AppToken == "" {
		if cfg.AllowEmptyAppToken {
			logger.Printf("warning: APP_TOKEN is empty and ALLOW_EMPTY_APP_TOKEN=true; events endpoint runs in dev mode")
		} else {
			logger.Printf("warning: APP_TOKEN is empty; events endpoint will reject requests")
		}
	}
	if cfg.AdminToken == "" {
		logger.Printf("warning: ADMIN_TOKEN is empty; admin api is disabled")
	}
	if cfg.TelegramReportEnabled && (cfg.TelegramBotToken == "" || cfg.TelegramChatID == "") {
		logger.Printf("warning: telegram report enabled but TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is empty")
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	st, err := store.Open(ctx, cfg.DatabasePath, cfg.MigrationsDir)
	if err != nil {
		logger.Fatalf("open store: %v", err)
	}
	defer st.Close()

	st.StartRetention(ctx, cfg.EventRetentionDays, 24*time.Hour, logger)
	reporter.StartDailyScheduler(ctx, cfg, st, logger)

	api := httpapi.NewServer(cfg, st, logger)
	server := &http.Server{
		Addr:              cfg.HTTPAddr,
		Handler:           api.Router(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	go func() {
		logger.Printf("listening on %s", cfg.HTTPAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Fatalf("http server failed: %v", err)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Printf("http shutdown failed: %v", err)
	}
}
