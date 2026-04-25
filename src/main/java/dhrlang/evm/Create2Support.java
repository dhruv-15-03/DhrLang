package dhrlang.evm;

import java.math.BigInteger;

/**
 * CREATE2 deterministic contract deployment support.
 *
 * <p>CREATE2 (EIP-1014) enables deploying contracts to predictable addresses
 * computed as: {@code keccak256(0xFF ‖ sender ‖ salt ‖ keccak256(initCode))}</p>
 *
 * <p>Use cases:
 * <ul>
 *   <li>Factory contracts that deploy child contracts</li>
 *   <li>Counterfactual deployment (predict address before deploying)</li>
 *   <li>Cross-chain deterministic addresses (same salt → same address)</li>
 *   <li>Minimal proxy (EIP-1167) clone deployment</li>
 * </ul>
 */
public final class Create2Support {

    private Create2Support() {}

    /**
     * Compute the CREATE2 address for a contract deployment.
     *
     * @param deployer   the factory contract address (20 bytes, hex with 0x)
     * @param salt       the salt (32 bytes)
     * @param initCode   the contract creation bytecode
     * @return the predicted contract address (0x-prefixed hex)
     */
    public static String computeAddress(String deployer, byte[] salt, byte[] initCode) {
        byte[] deployerBytes = parseAddress(deployer);
        byte[] initCodeHash = FunctionSelector.keccak256(initCode);

        // keccak256(0xFF ‖ deployer[20] ‖ salt[32] ‖ keccak256(initCode)[32])
        byte[] preimage = new byte[1 + 20 + 32 + 32]; // 85 bytes
        preimage[0] = (byte) 0xFF;
        System.arraycopy(deployerBytes, 0, preimage, 1, 20);
        System.arraycopy(salt, 0, preimage, 21, 32);
        System.arraycopy(initCodeHash, 0, preimage, 53, 32);

        byte[] hash = FunctionSelector.keccak256(preimage);

        // Address = last 20 bytes
        byte[] address = new byte[20];
        System.arraycopy(hash, 12, address, 0, 20);
        return "0x" + FunctionSelector.bytesToHex(address);
    }

    /**
     * Compute a deterministic salt from a string label.
     *
     * @param label  a human-readable label (e.g., "ERC20Token-v1")
     * @return 32-byte salt
     */
    public static byte[] saltFromLabel(String label) {
        return FunctionSelector.keccak256(label.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Compute a salt from a nonce (for sequential deployments).
     *
     * @param nonce  a counter value
     * @return 32-byte salt
     */
    public static byte[] saltFromNonce(long nonce) {
        byte[] salt = new byte[32];
        for (int i = 7; i >= 0; i--) {
            salt[31 - i] = (byte) (nonce >>> (i * 8));
        }
        return salt;
    }

    /**
     * Generate EVM bytecode for a CREATE2 call inside a factory contract.
     *
     * <p>Emits opcodes that:
     * <ol>
     *   <li>Store init code in memory</li>
     *   <li>Call CREATE2(value, offset, size, salt)</li>
     *   <li>Check return ≠ 0 (deployment succeeded)</li>
     * </ol>
     *
     * @param buf          the code buffer to emit into
     * @param initCode     the child contract creation bytecode
     * @param saltOffset   memory offset where salt is stored, or -1 for stack
     */
    public static void emitCreate2(EvmCodeBuffer buf, byte[] initCode, int saltOffset) {
        int memBase = 0x100; // use memory above the scratch area

        // Store init code in memory starting at memBase
        int wordsNeeded = (initCode.length + 31) / 32;
        for (int w = 0; w < wordsNeeded; w++) {
            byte[] word = new byte[32];
            int srcOff = w * 32;
            int copyLen = Math.min(32, initCode.length - srcOff);
            if (copyLen > 0) {
                System.arraycopy(initCode, srcOff, word, 0, copyLen);
            }
            buf.push32(new BigInteger(1, word));
            buf.mstoreAt(memBase + w * 32);
        }

        // Stack for CREATE2: [value, offset, size, salt] (right-to-left push)
        // Salt (from stack or memory)
        if (saltOffset >= 0) {
            buf.mloadAt(saltOffset);
        }
        // else: expect salt already on stack

        // Size of init code
        buf.pushInt(initCode.length);

        // Memory offset of init code
        buf.pushInt(memBase);

        // Value (ETH to send) — 0 for non-payable
        buf.pushInt(0);

        // CREATE2 opcode = 0xF5
        buf.emit(EvmOpcode.CREATE2);

        // Result: new contract address (or 0 if failed)
        // Check for failure
        buf.emit(EvmOpcode.DUP1);
        buf.emit(EvmOpcode.ISZERO);
        String failLabel = buf.newLabel();
        String okLabel = buf.newLabel();
        buf.jumpIf(failLabel);
        buf.jumpTo(okLabel);

        buf.placeLabel(failLabel);
        buf.emit(EvmOpcode.POP);
        buf.revertWithMessage("CREATE2 failed");

        buf.placeLabel(okLabel);
        // Address of deployed contract is on stack
    }

    /**
     * Generate EIP-1167 minimal proxy bytecode for a given implementation address.
     *
     * <p>The minimal proxy (clone) delegates all calls to the implementation
     * via DELEGATECALL. Size: 45 bytes.</p>
     *
     * @param implementation the implementation contract address (0x-prefixed hex)
     * @return the minimal proxy creation bytecode
     */
    public static byte[] minimalProxyBytecode(String implementation) {
        byte[] addr = parseAddress(implementation);

        // EIP-1167 minimal proxy runtime code (45 bytes):
        // 363d3d373d3d3d363d73<address>5af43d82803e903d91602b57fd5bf3
        byte[] runtime = new byte[45];
        byte[] prefix = hexToBytes("363d3d373d3d3d363d73");
        byte[] suffix = hexToBytes("5af43d82803e903d91602b57fd5bf3");

        System.arraycopy(prefix, 0, runtime, 0, prefix.length);
        System.arraycopy(addr, 0, runtime, prefix.length, 20);
        System.arraycopy(suffix, 0, runtime, prefix.length + 20, suffix.length);

        // Creation code: deploy the runtime code
        // PUSH1 <len> PUSH1 0x0b DUP1 PUSH1 <runtime-offset> PUSH1 0x00 CODECOPY PUSH1 0x00 RETURN
        // Simplified: wrap runtime in a simple deployer
        byte[] creation = new byte[runtime.length + 10];
        creation[0] = 0x60; // PUSH1
        creation[1] = (byte) runtime.length; // runtime length
        creation[2] = (byte) 0x80; // DUP1
        creation[3] = 0x60; // PUSH1
        creation[4] = 0x0a; // offset to runtime code in creation
        creation[5] = 0x60; // PUSH1
        creation[6] = 0x00; // memory dest
        creation[7] = 0x39; // CODECOPY
        creation[8] = 0x60; // PUSH1
        creation[9] = 0x00; // memory offset
        // RETURN doesn't need explicit opcode here since we need to fix offset
        // Actually, a full minimal proxy deployer is:
        // 3d602d80600a3d3981f3 <runtime>
        byte[] deployer = hexToBytes("3d602d80600a3d3981f3");
        byte[] fullCreation = new byte[deployer.length + runtime.length];
        System.arraycopy(deployer, 0, fullCreation, 0, deployer.length);
        System.arraycopy(runtime, 0, fullCreation, deployer.length, runtime.length);
        return fullCreation;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static byte[] parseAddress(String address) {
        String hex = address.startsWith("0x") ? address.substring(2) : address;
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20 && i * 2 + 1 < hex.length(); i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
