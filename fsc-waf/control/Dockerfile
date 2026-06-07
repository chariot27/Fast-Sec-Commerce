FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY *.go .
RUN go build -o fsc-waf-control .

FROM alpine:3.19
RUN apk add --no-cache curl
COPY --from=builder /app/fsc-waf-control /usr/local/bin/fsc-waf-control
EXPOSE 9200
HEALTHCHECK --interval=5s --timeout=3s CMD curl -f http://localhost:9200/health || exit 1
CMD ["fsc-waf-control"]
