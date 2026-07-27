package dhrlang.validation;

import dhrlang.ast.*;
import dhrlang.lexer.Lexer;
import dhrlang.lexer.Token;
import dhrlang.parser.Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StorageLayouter} — deterministic slot assignment.
 */
@DisplayName("StorageLayouter Tests")
class StorageLayouterTest {

    private StorageLayouter layouter;

    @BeforeEach
    void setUp() {
        layouter = new StorageLayouter();
    }

    private Program parse(String code) {
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.scanTokens();
        Parser parser = new Parser(tokens);
        return parser.parse();
    }

    // ── Basic layout ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Basic slot assignment")
    class BasicLayoutTests {

        @Test
        @DisplayName("single @storage field gets slot 0")
        void singleStorageField() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("Token");
            assertNotNull(layout, "Layout should exist for Token");
            assertEquals(1, layout.getTotalSlots());

            StorageLayouter.SlotInfo slot = layout.getSlotFor("balance");
            assertNotNull(slot);
            assertEquals(0, slot.getSlotIndex(), "First field gets slot 0");
            assertEquals("num", slot.getFieldType());
        }

        @Test
        @DisplayName("multiple @storage fields get sequential slots")
        void multipleStorageFields() {
            String code = """
                @contract
                class Token {
                    @storage num totalSupply;
                    @storage num owner;
                    @storage num decimals;
                    @constructor
                    kaam init() {
                        totalSupply = 0;
                        owner = 0;
                        decimals = 18;
                    }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("Token");
            assertNotNull(layout);
            assertEquals(3, layout.getTotalSlots());

            assertEquals(0, layout.getSlotFor("totalSupply").getSlotIndex());
            assertEquals(1, layout.getSlotFor("owner").getSlotIndex());
            assertEquals(2, layout.getSlotFor("decimals").getSlotIndex());
        }

        @Test
        @DisplayName("non-storage fields are not assigned slots")
        void nonStorageFieldsSkipped() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    num localVar;
                    @constructor
                    kaam init() { balance = 0; }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("Token");
            assertNotNull(layout);
            assertEquals(1, layout.getTotalSlots(), "Only @storage fields get slots");
            assertFalse(layout.hasSlot("localVar"));
        }
    }

    // ── Non-contract classes ──────────────────────────────────────────────

    @Nested
    @DisplayName("Non-contract classes")
    class NonContractTests {

        @Test
        @DisplayName("non-contract classes produce no layout")
        void nonContractNoLayout() {
            String code = """
                class Regular {
                    num x;
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            assertNull(layouter.getLayout("Regular"));
            assertTrue(layouter.getAllLayouts().isEmpty());
        }
    }

    // ── Multiple contracts ────────────────────────────────────────────────

    @Nested
    @DisplayName("Multiple contracts")
    class MultiContractTests {

        @Test
        @DisplayName("each contract gets its own independent layout")
        void independentLayouts() {
            String code = """
                @contract
                class Token {
                    @storage num supply;
                    @constructor
                    kaam init() { supply = 0; }
                }
                @contract
                class Vault {
                    @storage num locked;
                    @storage num admin;
                    @constructor
                    kaam init() {
                        locked = 0;
                        admin = 0;
                    }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            assertEquals(2, layouter.getAllLayouts().size());

            StorageLayouter.ContractLayout tokenLayout = layouter.getLayout("Token");
            assertNotNull(tokenLayout);
            assertEquals(1, tokenLayout.getTotalSlots());

            StorageLayouter.ContractLayout vaultLayout = layouter.getLayout("Vault");
            assertNotNull(vaultLayout);
            assertEquals(2, vaultLayout.getTotalSlots());
            assertEquals(0, vaultLayout.getSlotFor("locked").getSlotIndex());
            assertEquals(1, vaultLayout.getSlotFor("admin").getSlotIndex());
        }
    }

    // ── Contract with no @storage ─────────────────────────────────────────

    @Nested
    @DisplayName("Contract with no @storage fields")
    class EmptyStorageTests {

        @Test
        @DisplayName("contract with no @storage fields gets empty layout")
        void emptyLayout() {
            String code = """
                @contract
                class PureMath {
                    @constructor
                    kaam init() { }
                    @pure
                    num add(num a, num b) { return a + b; }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("PureMath");
            assertNotNull(layout);
            assertEquals(0, layout.getTotalSlots());
            assertTrue(layout.getSlots().isEmpty());
        }
    }

    // ── Overflow detection ────────────────────────────────────────────────

    @Nested
    @DisplayName("Overflow detection")
    class OverflowTests {

        @Test
        @DisplayName("no overflow for small contracts")
        void noOverflow() {
            String code = """
                @contract
                class Small {
                    @storage num a;
                    @storage num b;
                    @constructor
                    kaam init() { a = 0; b = 0; }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            assertFalse(layouter.hasOverflow());
            assertTrue(layouter.getOverflowContracts().isEmpty());
        }
    }

    // ── SlotInfo ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SlotInfo details")
    class SlotInfoTests {

        @Test
        @DisplayName("SlotInfo contains correct field name, type, and slot index")
        void slotInfoDetails() {
            String code = """
                @contract
                class Token {
                    @storage num owner;
                    @storage num balance;
                    @constructor
                    kaam init() {
                        owner = 0;
                        balance = 0;
                    }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("Token");
            StorageLayouter.SlotInfo ownerSlot = layout.getSlotFor("owner");

            assertEquals("owner", ownerSlot.getFieldName());
            assertEquals("num", ownerSlot.getFieldType());
            assertEquals(0, ownerSlot.getSlotIndex());
            assertTrue(ownerSlot.getSizeInBytes() > 0);
        }

        @Test
        @DisplayName("toString produces readable output")
        void slotInfoToString() {
            StorageLayouter.SlotInfo info = new StorageLayouter.SlotInfo("balance", "uint256", 0, 32);
            String str = info.toString();
            assertTrue(str.contains("balance"));
            assertTrue(str.contains("uint256"));
            assertTrue(str.contains("0"));
        }
    }

    // ── ContractLayout ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ContractLayout API")
    class ContractLayoutTests {

        @Test
        @DisplayName("hasSlot returns false for non-existent field")
        void hasSlotFalse() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("Token");
            assertTrue(layout.hasSlot("balance"));
            assertFalse(layout.hasSlot("nonExistent"));
        }

        @Test
        @DisplayName("getSlotFor returns null for non-existent field")
        void getSlotForNull() {
            String code = """
                @contract
                class Token {
                    @storage num balance;
                    @constructor
                    kaam init() { balance = 0; }
                }
                """;
            Program program = parse(code);
            layouter.layoutAll(program);

            StorageLayouter.ContractLayout layout = layouter.getLayout("Token");
            assertNull(layout.getSlotFor("nonExistent"));
        }
    }
}
