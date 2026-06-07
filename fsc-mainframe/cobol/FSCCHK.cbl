      *================================================================*
      * PROGRAMA: FSCCHK                                              *
      * DESCRICAO: Programa CICS exposto via z/OS Connect EE (REST)   *
      *            Verifica saldo disponivel de uma conta no DB2.     *
      *            Chamado sincronamente pelo FSC-Gateway (Spring).   *
      * REGRAS:                                                        *
      *   - Recebe dados via DFHCOMMAREA (CHANNELS/CONTAINERS)        *
      *   - Nao usa SEND/RECEIVE MAP (nao e um programa de terminal)  *
      *   - Verifica SQLCODE apos todo EXEC SQL                       *
      *   - Usa COMP-3 para valores monetarios                        *
      * VERSAO: 1.0.0                                                 *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. FSCCHK.
       AUTHOR. FSC-MAINFRAME-TEAM.

       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SOURCE-COMPUTER. IBM-Z.
       OBJECT-COMPUTER. IBM-Z.

       DATA DIVISION.

       WORKING-STORAGE SECTION.

      *-- Copybooks de Request/Response --*
           COPY FSCREQ.

      *-- Variaveis de Controle DB2 --*
       01  WS-SQLCA.
           05 SQLCODE              PIC S9(9) COMP.
           05 SQLERRM              PIC X(70).

      *-- Dados Lidos do DB2 --*
       01  WS-DB2-BALANCE          PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-DB2-RESERVED         PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-AVAILABLE-BALANCE    PIC S9(13)V99 COMP-3 VALUE ZEROS.

      *-- Variaveis de Trabalho --*
       01  WS-RETURN-CODE          PIC S9(4) COMP VALUE ZEROS.
       01  WS-LENGTH               PIC S9(8) COMP VALUE ZEROS.

      *-- Declaracao SQL (Host Variables) --*
           EXEC SQL BEGIN DECLARE SECTION END-EXEC.
       01  HV-ACCOUNT-ID           PIC X(20).
       01  HV-BALANCE              PIC S9(13)V99 COMP-3.
       01  HV-RESERVED             PIC S9(13)V99 COMP-3.
       01  HV-ACCT-STATUS          PIC X(10).
           EXEC SQL END DECLARE SECTION END-EXEC.

      *-- Include SQLCA Padrao DB2 --*
           EXEC SQL INCLUDE SQLCA END-EXEC.

       LINKAGE SECTION.
       01  DFHCOMMAREA.
           05 CA-REQUEST           PIC X(200).
           05 CA-RESPONSE          PIC X(300).

       PROCEDURE DIVISION.

      *================================================================*
       0000-MAIN-CONTROL.
      *================================================================*
           PERFORM 1000-INIT
           PERFORM 2000-CHECK-BALANCE
           PERFORM 9000-RETURN-TO-CICS
           STOP RUN.

      *================================================================*
       1000-INIT.
      *================================================================*
      *-- Mapear COMMAREA para estrutura de Request --*
           MOVE CA-REQUEST         TO FSC-CHK-REQUEST
           MOVE FSC-REQ-ACCOUNT-ID TO HV-ACCOUNT-ID
           INITIALIZE FSC-CHK-RESPONSE.

      *================================================================*
       2000-CHECK-BALANCE.
      *================================================================*
           EXEC SQL
               SELECT BAL_AMOUNT,
                      BAL_RESERVED,
                      ACCT_STATUS
               INTO   :HV-BALANCE,
                      :HV-RESERVED,
                      :HV-ACCT-STATUS
               FROM   FSC.ACCOUNTS
               WHERE  ACCOUNT_ID = :HV-ACCOUNT-ID
               WITH   UR
           END-EXEC

           EVALUATE SQLCODE
               WHEN 0
                   PERFORM 2100-PROCESS-BALANCE
               WHEN +100
                   MOVE -100             TO FSC-RES-RETURN-CODE
                   MOVE 'NFND'           TO FSC-RES-REASON-CODE
                   MOVE 'ACCOUNT NOT FOUND IN LEDGER'
                                         TO FSC-RES-MSG
               WHEN OTHER
                   MOVE SQLCODE          TO WS-RETURN-CODE
                   PERFORM 8000-DB2-ERROR
           END-EVALUATE.

      *================================================================*
       2100-PROCESS-BALANCE.
      *================================================================*
           MOVE HV-BALANCE               TO WS-DB2-BALANCE
           MOVE HV-RESERVED              TO WS-DB2-RESERVED
           COMPUTE WS-AVAILABLE-BALANCE  =
               WS-DB2-BALANCE - WS-DB2-RESERVED

           MOVE WS-DB2-BALANCE           TO FSC-RES-BALANCE
           MOVE WS-AVAILABLE-BALANCE     TO FSC-RES-AVAILABLE
           MOVE HV-ACCT-STATUS           TO FSC-RES-STATUS
           MOVE ZEROS                    TO FSC-RES-RETURN-CODE
           MOVE 'OK  '                   TO FSC-RES-REASON-CODE
           MOVE 'BALANCE CHECK SUCCESSFUL'
                                         TO FSC-RES-MSG

           IF WS-AVAILABLE-BALANCE < FSC-REQ-AMOUNT
               MOVE 'INSUFFICIENT_FUNDS' TO FSC-RES-STATUS
               MOVE 'INSF'               TO FSC-RES-REASON-CODE
               MOVE 'INSUFFICIENT FUNDS FOR THIS TRANSACTION'
                                         TO FSC-RES-MSG
           END-IF.

      *================================================================*
       8000-DB2-ERROR.
      *================================================================*
           MOVE -999                     TO FSC-RES-RETURN-CODE
           MOVE 'DB2E'                   TO FSC-RES-REASON-CODE
           MOVE 'DB2 ERROR - SEE CICS LOG'
                                         TO FSC-RES-MSG
           EXEC CICS ABEND ABCODE('FCHK') NODUMP END-EXEC.

      *================================================================*
       9000-RETURN-TO-CICS.
      *================================================================*
           MOVE FSC-CHK-RESPONSE         TO CA-RESPONSE
           EXEC CICS RETURN END-EXEC.
