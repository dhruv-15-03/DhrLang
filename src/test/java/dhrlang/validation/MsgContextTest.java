package dhrlang.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MsgContext} contract globals.
 */
@DisplayName("MsgContext Tests")
class MsgContextTest {

    @Nested
    @DisplayName("MSG type properties")
    class MsgTypeTests {

        @Test
        @DisplayName("msg.sender returns Address type")
        void msgSenderIsAddress() {
            assertEquals("Address", MsgContext.getMsgPropertyType("sender"));
        }

        @Test
        @DisplayName("msg.value returns uint256 type")
        void msgValueIsUint256() {
            assertEquals("uint256", MsgContext.getMsgPropertyType("value"));
        }

        @Test
        @DisplayName("msg.data returns the calldata synthetic type")
        void msgDataIsCallData() {
            assertEquals(MsgContext.CALLDATA_TYPE, MsgContext.getMsgPropertyType("data"));
        }

        @Test
        @DisplayName("msg.sig returns uint256 type")
        void msgSigIsUint256() {
            assertEquals("uint256", MsgContext.getMsgPropertyType("sig"));
        }

        @Test
        @DisplayName("msg.data.length returns uint256 type")
        void callDataLengthIsUint256() {
            assertEquals("uint256", MsgContext.getCallDataPropertyType("length"));
        }

        @Test
        @DisplayName("unknown msg.data property returns null")
        void unknownCallDataPropertyReturnsNull() {
            assertNull(MsgContext.getCallDataPropertyType("size"));
        }

        @Test
        @DisplayName("unknown msg property returns null")
        void unknownMsgPropertyReturnsNull() {
            assertNull(MsgContext.getMsgPropertyType("unknown"));
        }

        @Test
        @DisplayName("isMsgProperty returns true for valid properties")
        void isMsgPropertyValid() {
            assertTrue(MsgContext.isMsgProperty(MsgContext.MSG_TYPE, "sender"));
            assertTrue(MsgContext.isMsgProperty(MsgContext.MSG_TYPE, "value"));
            assertTrue(MsgContext.isMsgProperty(MsgContext.MSG_TYPE, "data"));
            assertTrue(MsgContext.isMsgProperty(MsgContext.MSG_TYPE, "sig"));
        }

        @Test
        @DisplayName("isMsgProperty returns false for invalid property")
        void isMsgPropertyInvalid() {
            assertFalse(MsgContext.isMsgProperty(MsgContext.MSG_TYPE, "gas"));
            assertFalse(MsgContext.isMsgProperty(MsgContext.MSG_TYPE, ""));
        }
    }

    @Nested
    @DisplayName("BLOCK type properties")
    class BlockTypeTests {

        @Test
        @DisplayName("block.timestamp returns uint256 type")
        void blockTimestampIsUint256() {
            assertEquals("uint256", MsgContext.getBlockPropertyType("timestamp"));
        }

        @Test
        @DisplayName("block.number returns uint256 type")
        void blockNumberIsUint256() {
            assertEquals("uint256", MsgContext.getBlockPropertyType("number"));
        }

        @Test
        @DisplayName("unknown block property returns null")
        void unknownBlockPropertyReturnsNull() {
            assertNull(MsgContext.getBlockPropertyType("unknown"));
        }

        @Test
        @DisplayName("isBlockProperty returns true for valid properties")
        void isBlockPropertyValid() {
            assertTrue(MsgContext.isBlockProperty(MsgContext.BLOCK_TYPE, "timestamp"));
            assertTrue(MsgContext.isBlockProperty(MsgContext.BLOCK_TYPE, "number"));
        }

        @Test
        @DisplayName("isBlockProperty returns false for invalid property")
        void isBlockPropertyInvalid() {
            assertFalse(MsgContext.isBlockProperty(MsgContext.BLOCK_TYPE, "difficulty"));
        }
    }

    @Nested
    @DisplayName("Contract global detection")
    class ContractGlobalTests {

        @Test
        @DisplayName("isContractGlobal recognizes msg")
        void msgIsContractGlobal() {
            assertTrue(MsgContext.isContractGlobal("msg"));
        }

        @Test
        @DisplayName("isContractGlobal recognizes block")
        void blockIsContractGlobal() {
            assertTrue(MsgContext.isContractGlobal("block"));
        }

        @Test
        @DisplayName("isContractGlobal rejects unknown names")
        void unknownIsNotContractGlobal() {
            assertFalse(MsgContext.isContractGlobal("tx"));
            assertFalse(MsgContext.isContractGlobal("this"));
            assertFalse(MsgContext.isContractGlobal(""));
        }
    }

    @Nested
    @DisplayName("Type constants")
    class TypeConstantTests {

        @Test
        @DisplayName("MSG_TYPE is a synthetic type name")
        void msgTypeIsSynthetic() {
            assertEquals("$MsgContext", MsgContext.MSG_TYPE);
        }

        @Test
        @DisplayName("BLOCK_TYPE is a synthetic type name")
        void blockTypeIsSynthetic() {
            assertEquals("$BlockContext", MsgContext.BLOCK_TYPE);
        }
    }

    @Nested
    @DisplayName("Property name collections")
    class PropertyNameTests {

        @Test
        @DisplayName("getMsgPropertyNames returns sender, value, data and sig")
        void msgPropertyNames() {
            var names = MsgContext.getMsgPropertyNames();
            assertTrue(names.contains("sender"));
            assertTrue(names.contains("value"));
            assertTrue(names.contains("data"));
            assertTrue(names.contains("sig"));
            assertEquals(4, names.size());
        }

        @Test
        @DisplayName("getBlockPropertyNames returns timestamp and number")
        void blockPropertyNames() {
            var names = MsgContext.getBlockPropertyNames();
            assertTrue(names.contains("timestamp"));
            assertTrue(names.contains("number"));
            assertEquals(2, names.size());
        }
    }
}
