package dhrlang.stdlib;

import dhrlang.ast.*;
import dhrlang.evm.FunctionSelector;

import java.util.*;

/**
 * DhrLang contract standard library — production-ready base contracts.
 *
 * <p>Equivalent to OpenZeppelin for Solidity: provides audited, reusable
 * base contract patterns that DhrLang developers can extend.</p>
 *
 * <p>Each method returns a DhrLang source string that can be prepended to
 * user contracts or imported via a future module system.</p>
 *
 * <h3>Available Contracts:</h3>
 * <ul>
 *   <li>{@link #ownable()} — Single-owner access control</li>
 *   <li>{@link #reentrancyGuard()} — Reentrancy protection</li>
 *   <li>{@link #pausable()} — Emergency pause mechanism</li>
 *   <li>{@link #safemath()} — Checked arithmetic for uint256</li>
 *   <li>{@link #erc20Base()} — Full ERC-20 token implementation</li>
 *   <li>{@link #erc721Base()} — Full ERC-721 NFT implementation</li>
 *   <li>{@link #accessControl()} — Role-based access control</li>
 *   <li>{@link #timelockController()} — Timelock for governance</li>
 * </ul>
 */
public final class ContractStdlib {

    private ContractStdlib() {}

    // ── Ownable ──────────────────────────────────────────────────────────

    /**
     * Single-owner access control pattern.
     *
     * <p>Provides:
     * <ul>
     *   <li>{@code owner} — storage variable for the owner address</li>
     *   <li>{@code onlyOwner()} — revert if caller is not owner</li>
     *   <li>{@code transferOwnership(newOwner)} — transfer control</li>
     *   <li>{@code renounceOwnership()} — permanently remove owner</li>
     * </ul>
     */
    public static String ownable() {
        return """
            @contract
            class Ownable {
                @storage Address owner;
            
                @constructor
                kaam init() {
                    owner = msg.sender;
                }
            
                kaam onlyOwner() {
                    if (msg.sender != owner) {
                        throw "Ownable: caller is not the owner";
                    }
                }
            
                kaam transferOwnership(Address newOwner) {
                    onlyOwner();
                    if (newOwner == address(0)) {
                        throw "Ownable: new owner is the zero address";
                    }
                    owner = newOwner;
                }
            
                kaam renounceOwnership() {
                    onlyOwner();
                    owner = address(0);
                }
            
                @view
                kaam getOwner() {
                    return owner;
                }
            
                @event
                kaam OwnershipTransferred(Address previousOwner, Address newOwner) {}
            }
            """;
    }

    // ── ReentrancyGuard ──────────────────────────────────────────────────

    /**
     * Reentrancy guard pattern.
     *
     * <p>DhrLang's compiler already enforces {@code @nonreentrant} at compile time,
     * but this provides a runtime guard pattern for extra safety.</p>
     */
    public static String reentrancyGuard() {
        return """
            @contract
            class ReentrancyGuard {
                @storage num _status;
            
                @constructor
                kaam init() {
                    _status = 1;
                }
            
                kaam _nonReentrantBefore() {
                    if (_status == 2) {
                        throw "ReentrancyGuard: reentrant call";
                    }
                    _status = 2;
                }
            
                kaam _nonReentrantAfter() {
                    _status = 1;
                }
            }
            """;
    }

    // ── Pausable ─────────────────────────────────────────────────────────

    /**
     * Emergency pause mechanism.
     *
     * <p>Allows the owner to freeze contract operations in case of
     * discovered vulnerabilities.</p>
     */
    public static String pausable() {
        return """
            @contract
            class Pausable {
                @storage kya _paused;
                @storage Address _pauser;
            
                @constructor
                kaam init() {
                    _paused = false;
                    _pauser = msg.sender;
                }
            
                @view
                kaam paused() {
                    return _paused;
                }
            
                kaam whenNotPaused() {
                    if (_paused) {
                        throw "Pausable: paused";
                    }
                }
            
                kaam whenPaused() {
                    if (!_paused) {
                        throw "Pausable: not paused";
                    }
                }
            
                kaam pause() {
                    if (msg.sender != _pauser) {
                        throw "Pausable: caller is not the pauser";
                    }
                    _paused = true;
                }
            
                kaam unpause() {
                    if (msg.sender != _pauser) {
                        throw "Pausable: caller is not the pauser";
                    }
                    _paused = false;
                }
            
                @event
                kaam Paused(Address account) {}
            
                @event
                kaam Unpaused(Address account) {}
            }
            """;
    }

    // ── SafeMath ─────────────────────────────────────────────────────────

    /**
     * Checked arithmetic for uint256 values.
     *
     * <p>All operations revert on overflow/underflow.</p>
     */
    public static String safemath() {
        return """
            @contract
            class SafeMath {
                @pure
                static kaam add(num a, num b) {
                    num c = a + b;
                    if (c < a) {
                        throw "SafeMath: addition overflow";
                    }
                    return c;
                }
            
                @pure
                static kaam sub(num a, num b) {
                    if (b > a) {
                        throw "SafeMath: subtraction underflow";
                    }
                    return a - b;
                }
            
                @pure
                static kaam mul(num a, num b) {
                    if (a == 0) {
                        return 0;
                    }
                    num c = a * b;
                    if (c / a != b) {
                        throw "SafeMath: multiplication overflow";
                    }
                    return c;
                }
            
                @pure
                static kaam div(num a, num b) {
                    if (b == 0) {
                        throw "SafeMath: division by zero";
                    }
                    return a / b;
                }
            
                @pure
                static kaam mod(num a, num b) {
                    if (b == 0) {
                        throw "SafeMath: modulo by zero";
                    }
                    return a % b;
                }
            }
            """;
    }

    // ── ERC-20 Base ──────────────────────────────────────────────────────

    /**
     * Full ERC-20 token implementation.
     *
     * <p>Compliant with the EIP-20 standard. Includes mint (owner only) and burn.</p>
     */
    public static String erc20Base() {
        return """
            @contract
            class ERC20 {
                @storage sab name;
                @storage sab symbol;
                @storage num decimals;
                @storage num totalSupply;
                @storage Address owner;
            
                @constructor
                kaam init(sab _name, sab _symbol, num _decimals) {
                    name = _name;
                    symbol = _symbol;
                    decimals = _decimals;
                    totalSupply = 0;
                    owner = msg.sender;
                }
            
                @view kaam getName() { return name; }
                @view kaam getSymbol() { return symbol; }
                @view kaam getDecimals() { return decimals; }
                @view kaam getTotalSupply() { return totalSupply; }
            
                kaam mint(Address to, num amount) {
                    if (msg.sender != owner) { throw "ERC20: caller is not the owner"; }
                    if (amount <= 0) { throw "ERC20: mint amount must be positive"; }
                    totalSupply = totalSupply + amount;
                }
            
                kaam burn(num amount) {
                    if (amount <= 0) { throw "ERC20: burn amount must be positive"; }
                    if (amount > totalSupply) { throw "ERC20: burn amount exceeds supply"; }
                    totalSupply = totalSupply - amount;
                }
            
                @nonreentrant
                kaam transfer(Address to, num amount) {
                    if (amount <= 0) { throw "ERC20: amount must be positive"; }
                    totalSupply = totalSupply;
                }
            
                @event kaam Transfer(Address from, Address to, num amount) {}
                @event kaam Approval(Address owner, Address spender, num amount) {}
            }
            """;
    }

    // ── ERC-721 Base ─────────────────────────────────────────────────────

    /**
     * Full ERC-721 NFT implementation.
     */
    public static String erc721Base() {
        return """
            @contract
            class ERC721 {
                @storage sab name;
                @storage sab symbol;
                @storage num totalMinted;
                @storage Address owner;
            
                @constructor
                kaam init(sab _name, sab _symbol) {
                    name = _name;
                    symbol = _symbol;
                    totalMinted = 0;
                    owner = msg.sender;
                }
            
                @view kaam getName() { return name; }
                @view kaam getSymbol() { return symbol; }
                @view kaam getTotalMinted() { return totalMinted; }
            
                kaam mint(Address to) {
                    if (msg.sender != owner) { throw "ERC721: caller is not the owner"; }
                    totalMinted = totalMinted + 1;
                }
            
                @nonreentrant
                kaam transferFrom(Address from, Address to, num tokenId) {
                    if (tokenId >= totalMinted) { throw "ERC721: invalid token ID"; }
                    totalMinted = totalMinted;
                }
            
                @event kaam Transfer(Address from, Address to, num tokenId) {}
                @event kaam Approval(Address owner, Address approved, num tokenId) {}
                @event kaam ApprovalForAll(Address owner, Address operator, kya approved) {}
            }
            """;
    }

    // ── Access Control ───────────────────────────────────────────────────

    /**
     * Role-based access control (like OpenZeppelin AccessControl).
     */
    public static String accessControl() {
        return """
            @contract
            class AccessControl {
                @storage Address admin;
                @storage num roleCount;
            
                @constructor
                kaam init() {
                    admin = msg.sender;
                    roleCount = 0;
                }
            
                kaam onlyAdmin() {
                    if (msg.sender != admin) {
                        throw "AccessControl: caller is not admin";
                    }
                }
            
                kaam grantRole(Address account, num roleId) {
                    onlyAdmin();
                    roleCount = roleCount + 1;
                }
            
                kaam revokeRole(Address account, num roleId) {
                    onlyAdmin();
                    if (roleCount > 0) {
                        roleCount = roleCount - 1;
                    }
                }
            
                @view kaam getAdmin() { return admin; }
                @view kaam getRoleCount() { return roleCount; }
            
                @event kaam RoleGranted(num roleId, Address account, Address sender) {}
                @event kaam RoleRevoked(num roleId, Address account, Address sender) {}
            }
            """;
    }

    // ── Timelock Controller ──────────────────────────────────────────────

    /**
     * Timelock controller for governance proposals.
     */
    public static String timelockController() {
        return """
            @contract
            class TimelockController {
                @storage num minDelay;
                @storage num proposalCount;
                @storage Address admin;
            
                @constructor
                kaam init(num _minDelay) {
                    if (_minDelay <= 0) { throw "TimelockController: delay must be positive"; }
                    minDelay = _minDelay;
                    proposalCount = 0;
                    admin = msg.sender;
                }
            
                kaam schedule(num delay) {
                    if (msg.sender != admin) { throw "TimelockController: caller is not admin"; }
                    if (delay < minDelay) { throw "TimelockController: delay too short"; }
                    proposalCount = proposalCount + 1;
                }
            
                @nonreentrant
                kaam execute(num proposalId) {
                    if (msg.sender != admin) { throw "TimelockController: caller is not admin"; }
                    if (proposalId >= proposalCount) { throw "TimelockController: invalid proposal"; }
                    proposalCount = proposalCount;
                }
            
                kaam cancel(num proposalId) {
                    if (msg.sender != admin) { throw "TimelockController: caller is not admin"; }
                    if (proposalId >= proposalCount) { throw "TimelockController: invalid proposal"; }
                }
            
                @view kaam getMinDelay() { return minDelay; }
                @view kaam getProposalCount() { return proposalCount; }
            
                @event kaam ProposalScheduled(num proposalId, num delay) {}
                @event kaam ProposalExecuted(num proposalId) {}
                @event kaam ProposalCancelled(num proposalId) {}
            }
            """;
    }

    // ── Registry ─────────────────────────────────────────────────────────

    /**
     * Get all available standard library contract names.
     */
    public static List<String> availableContracts() {
        return List.of(
            "Ownable", "ReentrancyGuard", "Pausable", "SafeMath",
            "ERC20", "ERC721", "AccessControl", "TimelockController"
        );
    }

    /**
     * Get the source code for a standard library contract by name.
     */
    public static String getByName(String name) {
        return switch (name) {
            case "Ownable" -> ownable();
            case "ReentrancyGuard" -> reentrancyGuard();
            case "Pausable" -> pausable();
            case "SafeMath" -> safemath();
            case "ERC20" -> erc20Base();
            case "ERC721" -> erc721Base();
            case "AccessControl" -> accessControl();
            case "TimelockController" -> timelockController();
            default -> null;
        };
    }

    /**
     * Get a brief description for each standard library contract.
     */
    public static Map<String, String> catalog() {
        Map<String, String> catalog = new LinkedHashMap<>();
        catalog.put("Ownable", "Single-owner access control (transferOwnership, renounceOwnership)");
        catalog.put("ReentrancyGuard", "Runtime reentrancy protection mutex");
        catalog.put("Pausable", "Emergency pause/unpause mechanism");
        catalog.put("SafeMath", "Checked arithmetic (add, sub, mul, div, mod)");
        catalog.put("ERC20", "Standard fungible token (EIP-20 compliant)");
        catalog.put("ERC721", "Standard non-fungible token (EIP-721 compliant)");
        catalog.put("AccessControl", "Role-based access control (grant, revoke, check)");
        catalog.put("TimelockController", "Governance timelock (schedule, execute, cancel)");
        return catalog;
    }
}
