      *================================================================*
      * PROGRAMA: FSCBCH                                              *
      * DESCRICAO: Batch JCL/COBOL. Roda de madrugada via JCL.      *
      *            Totaliza transacoes do dia, calcula receita de    *
      *            taxas e gera o relatorio consolidado em SYSPRINT. *
      * VERSAO: 1.0.0                                                 *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. FSCBCH.
       AUTHOR. FSC-MAINFRAME-TEAM.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT REPORT-FILE ASSIGN TO SYSPRINT
               ORGANIZATION IS SEQUENTIAL
               ACCESS IS SEQUENTIAL.

       DATA DIVISION.

       FILE SECTION.
       FD  REPORT-FILE
           RECORDING MODE IS F
           BLOCK CONTAINS 0 RECORDS.
       01  REPORT-RECORD           PIC X(132).

       WORKING-STORAGE SECTION.

       01  WS-REPORT-DATE          PIC X(10).
       01  WS-TOTAL-TRANS          PIC 9(9)    COMP-3 VALUE ZEROS.
       01  WS-TOTAL-AMOUNT         PIC S9(15)V99 COMP-3 VALUE ZEROS.
       01  WS-TOTAL-FEES           PIC S9(13)V99 COMP-3 VALUE ZEROS.
       01  WS-TOTAL-FRAUD-BLOCKED  PIC 9(9)    COMP-3 VALUE ZEROS.
       01  WS-EOF-FLAG             PIC X VALUE 'N'.
           88 WS-EOF               VALUE 'Y'.

           EXEC SQL BEGIN DECLARE SECTION END-EXEC.
       01  HV-PROC-DATE            PIC X(10).
       01  HV-COUNT                PIC S9(9)   COMP-3.
       01  HV-SUM-AMOUNT           PIC S9(15)V99 COMP-3.
       01  HV-SUM-FEES             PIC S9(13)V99 COMP-3.
       01  HV-SUM-FRAUD            PIC S9(9)   COMP-3.
           EXEC SQL END DECLARE SECTION END-EXEC.

           EXEC SQL INCLUDE SQLCA END-EXEC.

      *-- Linhas do Relatorio --*
       01  WS-LINE-BLANK           PIC X(132) VALUE SPACES.
       01  WS-LINE-HEADER.
           05 FILLER               PIC X(10) VALUE '=========='.
           05 FILLER               PIC X(40) VALUE
               ' FSC CORE LEDGER - DAILY SETTLEMENT RPT'.
           05 FILLER               PIC X(10) VALUE '=========='.
       01  WS-LINE-DATE.
           05 FILLER               PIC X(15) VALUE 'PROCESS DATE:  '.
           05 WS-RPT-DATE          PIC X(10).
       01  WS-LINE-TRANS.
           05 FILLER               PIC X(30) VALUE
               'TOTAL TRANSACTIONS SETTLED:   '.
           05 WS-RPT-COUNT         PIC ZZZ,ZZZ,ZZ9.
       01  WS-LINE-AMOUNT.
           05 FILLER               PIC X(30) VALUE
               'TOTAL AMOUNT SETTLED (BRL):   '.
           05 WS-RPT-AMOUNT        PIC ZZZ,ZZZ,ZZZ,ZZ9.99.
       01  WS-LINE-FEES.
           05 FILLER               PIC X(30) VALUE
               'TOTAL FEES COLLECTED (BRL):   '.
           05 WS-RPT-FEES          PIC ZZZ,ZZZ,ZZZ,ZZ9.99.
       01  WS-LINE-FRAUD.
           05 FILLER               PIC X(30) VALUE
               'FRAUD BLOCKED (COUNT):        '.
           05 WS-RPT-FRAUD         PIC ZZZ,ZZZ,ZZ9.

       PROCEDURE DIVISION.

      *================================================================*
       0000-MAIN-CONTROL.
      *================================================================*
           PERFORM 1000-INIT
           PERFORM 2000-FETCH-SUMMARY
           PERFORM 3000-WRITE-REPORT
           PERFORM 9000-FINALIZE
           STOP RUN.

      *================================================================*
       1000-INIT.
      *================================================================*
           OPEN OUTPUT REPORT-FILE
           MOVE FUNCTION CURRENT-DATE(1:10) TO WS-REPORT-DATE
           MOVE WS-REPORT-DATE TO HV-PROC-DATE.

      *================================================================*
       2000-FETCH-SUMMARY.
      *  Consulta consolidada da tabela de auditoria do dia.         *
      *================================================================*
           EXEC SQL
               SELECT COUNT(*),
                      COALESCE(SUM(AMOUNT), 0),
                      COALESCE(SUM(FEE), 0)
               INTO  :HV-COUNT,
                     :HV-SUM-AMOUNT,
                     :HV-SUM-FEES
               FROM  FSC.LEDGER_AUDIT
               WHERE STATUS = 'SETTLED'
               AND   DATE(PROC_TIMESTAMP) = :HV-PROC-DATE
           END-EXEC

           IF SQLCODE = 0
               MOVE HV-COUNT      TO WS-TOTAL-TRANS
               MOVE HV-SUM-AMOUNT TO WS-TOTAL-AMOUNT
               MOVE HV-SUM-FEES   TO WS-TOTAL-FEES
           ELSE
               MOVE 'DB2 READ ERROR' TO REPORT-RECORD
               WRITE REPORT-RECORD
           END-IF

           EXEC SQL
               SELECT COUNT(*)
               INTO  :HV-SUM-FRAUD
               FROM  FSC.LEDGER_AUDIT
               WHERE STATUS = 'FRAUD_BLOCKED'
               AND   DATE(PROC_TIMESTAMP) = :HV-PROC-DATE
           END-EXEC

           MOVE HV-SUM-FRAUD      TO WS-TOTAL-FRAUD-BLOCKED.

      *================================================================*
       3000-WRITE-REPORT.
      *================================================================*
           WRITE REPORT-RECORD FROM WS-LINE-BLANK
           WRITE REPORT-RECORD FROM WS-LINE-HEADER
           WRITE REPORT-RECORD FROM WS-LINE-BLANK

           MOVE WS-REPORT-DATE TO WS-RPT-DATE
           WRITE REPORT-RECORD FROM WS-LINE-DATE

           WRITE REPORT-RECORD FROM WS-LINE-BLANK

           MOVE WS-TOTAL-TRANS   TO WS-RPT-COUNT
           WRITE REPORT-RECORD FROM WS-LINE-TRANS

           MOVE WS-TOTAL-AMOUNT  TO WS-RPT-AMOUNT
           WRITE REPORT-RECORD FROM WS-LINE-AMOUNT

           MOVE WS-TOTAL-FEES    TO WS-RPT-FEES
           WRITE REPORT-RECORD FROM WS-LINE-FEES

           MOVE WS-TOTAL-FRAUD-BLOCKED TO WS-RPT-FRAUD
           WRITE REPORT-RECORD FROM WS-LINE-FRAUD

           WRITE REPORT-RECORD FROM WS-LINE-BLANK
           WRITE REPORT-RECORD FROM WS-LINE-HEADER.

      *================================================================*
       9000-FINALIZE.
      *================================================================*
           CLOSE REPORT-FILE.
