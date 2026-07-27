package dhrlang.testing;

import java.math.BigInteger;
import java.util.*;

/**
 * VM cheatcodes for the DhrLang contract testing framework.
 * Cheatcodes allow test writers to manipulate the simulated blockchain
 * environment (addresses, balances, timestamps, block numbers) to set up
 * specific test scenarios.
 *
 * <p>Inspired by Foundry's cheatcode model:
 * <ul>
 *   <li>{@code prank(address)} — simulate a specific msg.sender for the next call</li>
 *   <li>{@code deal(address, amount)} — set an address's balance</li>
 *   <li>{@code warp(timestamp)} — set the block timestamp</li>
 *   <li>{@code roll(blockNumber)} — set the block number</li>
 *   <li>{@code store(address, slot, value)} — write to storage directly</li>
 *   <li>{@code load(address, slot)} — read from storage directly</li>
 *   <li>{@code expectRevert()} — declare that the next call should revert</li>
 * </ul>
 */
public class TestCheatcodes {

    // ── Blockchain state ─────────────────────────────────

    private String currentSender = "0x0000000000000000000000000000000000000000";
    private String prankedSender = null;
    private boolean prankActive = false;

    private long blockTimestamp = 1_700_000_000L;  // ~Nov 2023 default
    private long blockNumber = 1L;

    private final Map<String, BigInteger> balances = new LinkedHashMap<>();
    private final Map<String, Map<Integer, BigInteger>> storage = new LinkedHashMap<>();

    private boolean expectRevert = false;
    private String expectedRevertMessage = null;

    private final List<String> cheatcodeLog = new ArrayList<>();

    // ── Constructor ──────────────────────────────────────

    public TestCheatcodes() {
        // Set some default balances
        balances.put("0x0000000000000000000000000000000000000001",
                BigInteger.valueOf(1_000_000));
        balances.put("0x0000000000000000000000000000000000000002",
                BigInteger.valueOf(500_000));
    }

    // ── prank ────────────────────────────────────────────

    /**
     * Set the msg.sender for the next call to the given address.
     * After one call, the prank is consumed.
     */
    public void prank(String address) {
        Objects.requireNonNull(address, "prank address");
        this.prankedSender = address;
        this.prankActive = true;
        cheatcodeLog.add("prank(" + address + ")");
    }

    /**
     * Get the effective msg.sender (pranked or real).
     */
    public String getEffectiveSender() {
        if (prankActive && prankedSender != null) {
            return prankedSender;
        }
        return currentSender;
    }

    /**
     * Consume the prank after a call.
     */
    public void consumePrank() {
        if (prankActive) {
            prankActive = false;
            prankedSender = null;
        }
    }

    /**
     * Check if a prank is currently active.
     */
    public boolean isPrankActive() {
        return prankActive;
    }

    /**
     * Set the permanent msg.sender (not consumed after one call).
     */
    public void setCurrentSender(String address) {
        this.currentSender = Objects.requireNonNull(address);
        cheatcodeLog.add("setSender(" + address + ")");
    }

    // ── deal ─────────────────────────────────────────────

    /**
     * Set the balance of an address.
     */
    public void deal(String address, BigInteger amount) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Balance cannot be negative: " + amount);
        }
        balances.put(address, amount);
        cheatcodeLog.add("deal(" + address + ", " + amount + ")");
    }

    /**
     * Convenience overload: deal with a long value.
     */
    public void deal(String address, long amount) {
        deal(address, BigInteger.valueOf(amount));
    }

    /**
     * Get the balance of an address.
     */
    public BigInteger getBalance(String address) {
        return balances.getOrDefault(address, BigInteger.ZERO);
    }

    // ── warp ─────────────────────────────────────────────

    /**
     * Set the block timestamp.
     */
    public void warp(long timestamp) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative: " + timestamp);
        }
        this.blockTimestamp = timestamp;
        cheatcodeLog.add("warp(" + timestamp + ")");
    }

    /**
     * Get the current block timestamp.
     */
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    // ── roll ─────────────────────────────────────────────

    /**
     * Set the block number.
     */
    public void roll(long number) {
        if (number < 0) {
            throw new IllegalArgumentException("Block number cannot be negative: " + number);
        }
        this.blockNumber = number;
        cheatcodeLog.add("roll(" + number + ")");
    }

    /**
     * Get the current block number.
     */
    public long getBlockNumber() {
        return blockNumber;
    }

    // ── store / load ─────────────────────────────────────

    /**
     * Directly write a value to a storage slot of an address.
     */
    public void store(String address, int slot, BigInteger value) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(value, "value");
        storage.computeIfAbsent(address, k -> new LinkedHashMap<>())
               .put(slot, value);
        cheatcodeLog.add("store(" + address + ", slot=" + slot + ", " + value + ")");
    }

    /**
     * Directly read a value from a storage slot of an address.
     */
    public BigInteger load(String address, int slot) {
        Map<Integer, BigInteger> addrStorage = storage.get(address);
        if (addrStorage == null) return BigInteger.ZERO;
        return addrStorage.getOrDefault(slot, BigInteger.ZERO);
    }

    // ── expectRevert ─────────────────────────────────────

    /**
     * Declare that the next call should revert.
     */
    public void expectRevert() {
        this.expectRevert = true;
        this.expectedRevertMessage = null;
        cheatcodeLog.add("expectRevert()");
    }

    /**
     * Declare that the next call should revert with a specific message.
     */
    public void expectRevert(String message) {
        this.expectRevert = true;
        this.expectedRevertMessage = message;
        cheatcodeLog.add("expectRevert(" + message + ")");
    }

    /**
     * Check if a revert is expected.
     */
    public boolean isRevertExpected() {
        return expectRevert;
    }

    /**
     * Get the expected revert message (null if any revert is acceptable).
     */
    public String getExpectedRevertMessage() {
        return expectedRevertMessage;
    }

    /**
     * Consume the revert expectation after a call.
     */
    public void consumeExpectRevert() {
        expectRevert = false;
        expectedRevertMessage = null;
    }

    // ── Gas tracking ─────────────────────────────────────

    private long gasUsed = 0;

    /**
     * Record gas used by an operation.
     */
    public void recordGas(long gas) {
        this.gasUsed += gas;
    }

    /**
     * Get total gas used since last reset.
     */
    public long getGasUsed() {
        return gasUsed;
    }

    /**
     * Reset gas counter.
     */
    public void resetGas() {
        this.gasUsed = 0;
    }

    // ── Logging ──────────────────────────────────────────

    /**
     * Return the log of all cheatcode calls.
     */
    public List<String> getCheatcodeLog() {
        return Collections.unmodifiableList(cheatcodeLog);
    }

    // ── Reset ────────────────────────────────────────────

    /**
     * Reset all state to defaults.
     */
    public void reset() {
        currentSender = "0x0000000000000000000000000000000000000000";
        prankedSender = null;
        prankActive = false;
        blockTimestamp = 1_700_000_000L;
        blockNumber = 1L;
        balances.clear();
        storage.clear();
        expectRevert = false;
        expectedRevertMessage = null;
        gasUsed = 0;
        cheatcodeLog.clear();
    }

    /**
     * Format a summary of the current environment state.
     */
    public String formatState() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ VM Cheatcode State ═══\n");
        sb.append("  sender:    ").append(getEffectiveSender()).append('\n');
        sb.append("  timestamp: ").append(blockTimestamp).append('\n');
        sb.append("  block:     ").append(blockNumber).append('\n');
        sb.append("  gasUsed:   ").append(gasUsed).append('\n');
        sb.append("  balances:  ").append(balances.size()).append(" entries\n");
        sb.append("  storage:   ").append(storage.size()).append(" addresses\n");
        if (expectRevert) {
            sb.append("  expectRevert: true");
            if (expectedRevertMessage != null) {
                sb.append(" (\"").append(expectedRevertMessage).append("\")");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
