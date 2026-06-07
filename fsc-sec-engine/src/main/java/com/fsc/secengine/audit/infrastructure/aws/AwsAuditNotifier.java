package com.fsc.secengine.audit.infrastructure.aws;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AwsAuditNotifier {

    private static final Logger log = LoggerFactory.getLogger(AwsAuditNotifier.class);
    private final SqsTemplate sqsTemplate;

    public AwsAuditNotifier(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    public void notifyFraud(String transactionId, String reason) {
        log.info("Enviando alerta de fraude para SQS (LocalStack) para transação: {}", transactionId);
        // Em um ambiente real usaríamos o nome da fila configurado
        sqsTemplate.send("fsc-fraud-alerts-queue", "{\"transactionId\":\"" + transactionId + "\", \"reason\":\"" + reason + "\"}");
    }
}
