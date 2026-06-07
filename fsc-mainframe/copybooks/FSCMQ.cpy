      *================================================================*
      * COPYBOOK: FSCMQ                                               *
      * DESCRICAO: Estrutura da mensagem IBM MQ para liquidacao       *
      *            (usada pelo programa FSCSET - MQ Triggered)        *
      * VERSAO: 1.0.0                                                 *
      *================================================================*
       01 FSC-MQ-SETTLEMENT-MSG.
          05 FSC-MQ-MSG-VERSION      PIC 9(4) COMP VALUE 1.
          05 FSC-MQ-TRANS-ID         PIC X(36).
          05 FSC-MQ-ORDER-ID         PIC X(36).
          05 FSC-MQ-DEBIT-ACCT       PIC X(20).
          05 FSC-MQ-CREDIT-ACCT      PIC X(20).
          05 FSC-MQ-AMOUNT           PIC S9(13)V99 COMP-3.
          05 FSC-MQ-FEE              PIC S9(9)V99  COMP-3.
          05 FSC-MQ-CURRENCY         PIC X(3).
          05 FSC-MQ-STATUS           PIC X(10).
          05 FSC-MQ-TIMESTAMP        PIC X(26).
          05 FSC-MQ-AUDIT-HASH       PIC X(64).
