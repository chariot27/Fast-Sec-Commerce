      *================================================================*
      * PROGRAMA: FSCSET                                              *
      * DESCRICAO: Programa MQ-Triggered. Le transacao aprovada da   *
      *            fila IBM MQ (via Kafka Bridge), debita o cliente   *
      *            e credita o lojista no DB2. Grava log de auditoria.*
      * REGRAS:                                                        *
      *   - Usa EXEC CICS SYNCPOINT apos gravar no DB2               *
      *   - Trata SQLCODE -803 (chave duplicada = idempotente)       *
      *   - Usa COMP-3 para todos os valores monetarios              *
      * VERSAO: 1.0.0                                                 *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. FSCSET.
       AUTHOR. FSC-MAINFRAME-TEAM.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-Z.
       OBJECT-COMPUTER. IBM-Z.

       DATA DIVISION.

       WORKING-STORAGE SECTION.

           COPY FSCMQ.

       01  WS-DEBIT-BALANCE        PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-NEW-DEBIT-BAL        PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-CREDIT-BALANCE       PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-NEW-CREDIT-BAL       PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-TODAY-DATE           PIC X(10).
       01  WS-PROCESS-TIME         PIC X(26).

           EXEC SQL BEGIN DECLARE SECTION END-EXEC.
       01  HV-TRANS-ID             PIC X(36).
       01  HV-DEBIT-ACCT           PIC X(20).
       01  HV-CREDIT-ACCT          PIC X(20).
       01  HV-AMOUNT               PIC S9(13)V99 COMP-3.
       01  HV-FEE                  PIC S9(9)V99  COMP-3.
       01  HV-DB-BALANCE           PIC S9(13)V99 COMP-3.
       01  HV-STATUS               PIC X(10).
           EXEC SQL END DECLARE SECTION END-EXEC.

           EXEC SQL INCLUDE SQLCA END-EXEC.

       PROCEDURE DIVISION.

      *================================================================*
       0000-MAIN-CONTROL.
      *================================================================*
           PERFORM 1000-INIT
           PERFORM 2000-DEBIT-SENDER
           PERFORM 3000-CREDIT-MERCHANT
           PERFORM 4000-WRITE-AUDIT-LOG
           PERFORM 5000-SYNCPOINT
           PERFORM 9000-RETURN
           STOP RUN.

      *================================================================*
       1000-INIT.
      *================================================================*
           MOVE FSC-MQ-TRANS-ID   TO HV-TRANS-ID
           MOVE FSC-MQ-DEBIT-ACCT TO HV-DEBIT-ACCT
           MOVE FSC-MQ-CREDIT-ACCT TO HV-CREDIT-ACCT
           MOVE FSC-MQ-AMOUNT     TO HV-AMOUNT
           MOVE FSC-MQ-FEE        TO HV-FEE
           EXEC CICS ASKTIME ABSTIME(WS-PROCESS-TIME) END-EXEC.

      *================================================================*
       2000-DEBIT-SENDER.
      *  Debita o valor + taxa da conta do comprador.                 *
      *================================================================*
           EXEC SQL
               UPDATE FSC.ACCOUNTS
               SET    BAL_AMOUNT = BAL_AMOUNT - (:HV-AMOUNT + :HV-FEE)
               WHERE  ACCOUNT_ID = :HV-DEBIT-ACCT
               AND    BAL_AMOUNT >= (:HV-AMOUNT + :HV-FEE)
           END-EXEC

           EVALUATE SQLCODE
               WHEN 0
                   CONTINUE
               WHEN +100
                   MOVE 'INSUFFICIENT FUNDS OR ACCT NOT FOUND'
                       TO SQLERRM
                   PERFORM 8000-ROLLBACK
               WHEN OTHER
                   PERFORM 8000-ROLLBACK
           END-EVALUATE.

      *================================================================*
       3000-CREDIT-MERCHANT.
      *  Credita o valor liquido (sem taxa) na conta do lojista.     *
      *================================================================*
           EXEC SQL
               UPDATE FSC.ACCOUNTS
               SET    BAL_AMOUNT = BAL_AMOUNT + :HV-AMOUNT
               WHERE  ACCOUNT_ID = :HV-CREDIT-ACCT
           END-EXEC

           EVALUATE SQLCODE
               WHEN 0
                   CONTINUE
               WHEN OTHER
                   PERFORM 8000-ROLLBACK
           END-EVALUATE.

      *================================================================*
       4000-WRITE-AUDIT-LOG.
      *  Grava a transacao liquidada na tabela de auditoria do z/OS. *
      *================================================================*
           EXEC SQL
               INSERT INTO FSC.LEDGER_AUDIT
                   (TRANS_ID, DEBIT_ACCT, CREDIT_ACCT,
                    AMOUNT, FEE, STATUS, PROC_TIMESTAMP,
                    AUDIT_HASH)
               VALUES
                   (:HV-TRANS-ID, :HV-DEBIT-ACCT, :HV-CREDIT-ACCT,
                    :HV-AMOUNT, :HV-FEE, 'SETTLED', :WS-PROCESS-TIME,
                    :FSC-MQ-AUDIT-HASH)
           END-EXEC

           EVALUATE SQLCODE
               WHEN 0
                   CONTINUE
               WHEN -803
      *           Idempotencia: ja liquidada, ignorar silenciosamente
                  CONTINUE
               WHEN OTHER
                   PERFORM 8000-ROLLBACK
           END-EVALUATE.

      *================================================================*
       5000-SYNCPOINT.
      *  Confirma atomicamente debito + credito + log no DB2.        *
      *================================================================*
           EXEC CICS SYNCPOINT END-EXEC.

      *================================================================*
       8000-ROLLBACK.
      *================================================================*
           EXEC CICS SYNCPOINT ROLLBACK END-EXEC
           EXEC CICS ABEND ABCODE('FSET') NODUMP END-EXEC.

      *================================================================*
       9000-RETURN.
      *================================================================*
           EXEC CICS RETURN END-EXEC.
