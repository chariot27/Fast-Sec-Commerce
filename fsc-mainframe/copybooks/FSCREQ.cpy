      *================================================================*
      * COPYBOOK: FSCREQ                                              *
      * DESCRICAO: Estrutura de Request/Response para o programa      *
      *            FSCCHK (verificacao de saldo) via z/OS Connect EE  *
      * VERSAO: 1.0.0                                                 *
      *================================================================*
       01 FSC-CHK-REQUEST.
          05 FSC-REQ-TRANS-ID        PIC X(36).
          05 FSC-REQ-ACCOUNT-ID      PIC X(20).
          05 FSC-REQ-AMOUNT          PIC S9(13)V99 COMP-3.
          05 FSC-REQ-CURRENCY        PIC X(3).
          05 FSC-REQ-TIMESTAMP       PIC X(26).

       01 FSC-CHK-RESPONSE.
          05 FSC-RES-RETURN-CODE     PIC S9(4) COMP.
          05 FSC-RES-REASON-CODE     PIC X(4).
          05 FSC-RES-BALANCE         PIC S9(13)V99 COMP-3.
          05 FSC-RES-AVAILABLE       PIC S9(13)V99 COMP-3.
          05 FSC-RES-STATUS          PIC X(20).
          05 FSC-RES-MSG             PIC X(100).
