package bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TransactionRecord Tests")
class TransactionRecordTest {

    private static final int FROM_ID = 1;
    private static final int TO_ID = 2;
    private static final long AMOUNT = 500L;
    private TransactionRecord successRecord;
    private TransactionRecord failedRecord;
    private TransactionRecord rollbackRecord;

    @BeforeEach
    void setUp() {
        successRecord = new TransactionRecord(FROM_ID, TO_ID, AMOUNT, TransactionRecord.Status.SUCCESS);
        failedRecord = new TransactionRecord(FROM_ID, TO_ID, AMOUNT, TransactionRecord.Status.FAILED);
        rollbackRecord = new TransactionRecord(FROM_ID, TO_ID, AMOUNT, TransactionRecord.Status.ROLLBACK);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {
        
        @Test
        @DisplayName("Should create record with all parameters")
        void shouldCreateRecordWithAllParameters() {
            assertThat(successRecord.getFromId()).isEqualTo(FROM_ID);
            assertThat(successRecord.getToId()).isEqualTo(TO_ID);
            assertThat(successRecord.getAmount()).isEqualTo(AMOUNT);
            assertThat(successRecord.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
            assertThat(successRecord.getTimestamp()).isPositive();
        }

        @Test
        @DisplayName("Should set timestamp to current time")
        void shouldSetTimestampToCurrentTime() {
            long beforeCreation = System.currentTimeMillis();
            TransactionRecord record = new TransactionRecord(FROM_ID, TO_ID, AMOUNT, TransactionRecord.Status.SUCCESS);
            long afterCreation = System.currentTimeMillis();
            
            assertThat(record.getTimestamp()).isBetween(beforeCreation, afterCreation);
        }
    }

    @Nested
    @DisplayName("Getter Tests")
    class GetterTests {
        
        @Test
        @DisplayName("Should return correct from ID")
        void shouldReturnCorrectFromId() {
            assertThat(successRecord.getFromId()).isEqualTo(FROM_ID);
        }

        @Test
        @DisplayName("Should return correct to ID")
        void shouldReturnCorrectToId() {
            assertThat(successRecord.getToId()).isEqualTo(TO_ID);
        }

        @Test
        @DisplayName("Should return correct amount")
        void shouldReturnCorrectAmount() {
            assertThat(successRecord.getAmount()).isEqualTo(AMOUNT);
        }

        @Test
        @DisplayName("Should return correct status for success")
        void shouldReturnCorrectStatusForSuccess() {
            assertThat(successRecord.getStatus()).isEqualTo(TransactionRecord.Status.SUCCESS);
        }

        @Test
        @DisplayName("Should return correct status for failed")
        void shouldReturnCorrectStatusForFailed() {
            assertThat(failedRecord.getStatus()).isEqualTo(TransactionRecord.Status.FAILED);
        }

        @Test
        @DisplayName("Should return correct status for rollback")
        void shouldReturnCorrectStatusForRollback() {
            assertThat(rollbackRecord.getStatus()).isEqualTo(TransactionRecord.Status.ROLLBACK);
        }
    }

    @Nested
    @DisplayName("Status Enum Tests")
    class StatusEnumTests {
        
        @Test
        @DisplayName("Should contain all expected status values")
        void shouldContainAllExpectedStatusValues() {
            TransactionRecord.Status[] statuses = TransactionRecord.Status.values();
            
            assertThat(statuses).containsExactlyInAnyOrder(
                TransactionRecord.Status.SUCCESS,
                TransactionRecord.Status.FAILED,
                TransactionRecord.Status.ROLLBACK
            );
        }
    }

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {
        
        @Test
        @DisplayName("Should return formatted string with all fields")
        void shouldReturnFormattedStringWithAllFields() {
            String result = successRecord.toString();
            
            assertThat(result).contains("Transaction");
            assertThat(result).contains("from=" + FROM_ID);
            assertThat(result).contains("to=" + TO_ID);
            assertThat(result).contains("amount=" + AMOUNT);
            assertThat(result).contains("status=SUCCESS");
            assertThat(result).contains("time=");
        }

        @Test
        @DisplayName("Should format different statuses correctly")
        void shouldFormatDifferentStatusesCorrectly() {
            String failedString = failedRecord.toString();
            String rollbackString = rollbackRecord.toString();
            
            assertThat(failedString).contains("status=FAILED");
            assertThat(rollbackString).contains("status=ROLLBACK");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Should handle zero amount")
        void shouldHandleZeroAmount() {
            TransactionRecord record = new TransactionRecord(FROM_ID, TO_ID, 0L, TransactionRecord.Status.SUCCESS);
            
            assertThat(record.getAmount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should handle negative amount")
        void shouldHandleNegativeAmount() {
            TransactionRecord record = new TransactionRecord(FROM_ID, TO_ID, -100L, TransactionRecord.Status.SUCCESS);
            
            assertThat(record.getAmount()).isEqualTo(-100L);
        }

        @Test
        @DisplayName("Should handle zero IDs")
        void shouldHandleZeroIds() {
            TransactionRecord record = new TransactionRecord(0, 0, AMOUNT, TransactionRecord.Status.SUCCESS);
            
            assertThat(record.getFromId()).isEqualTo(0);
            assertThat(record.getToId()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should handle negative IDs")
        void shouldHandleNegativeIds() {
            TransactionRecord record = new TransactionRecord(-1, -2, AMOUNT, TransactionRecord.Status.SUCCESS);
            
            assertThat(record.getFromId()).isEqualTo(-1);
            assertThat(record.getToId()).isEqualTo(-2);
        }
    }
}
