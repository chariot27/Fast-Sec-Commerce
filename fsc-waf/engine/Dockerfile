FROM rust:1.78-slim AS builder
RUN apt-get update && apt-get install -y pkg-config libssl-dev && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY Cargo.toml .
RUN mkdir src && echo "fn main(){}" > src/main.rs && cargo build --release 2>/dev/null; rm -f src/main.rs
COPY src ./src
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y libssl3 ca-certificates curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/fsc-waf-engine /usr/local/bin/fsc-waf-engine
EXPOSE 9100
HEALTHCHECK --interval=5s --timeout=3s CMD curl -f http://localhost:9100/health || exit 1
CMD ["fsc-waf-engine"]
