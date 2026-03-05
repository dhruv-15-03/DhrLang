package dhrlang.production;

import dhrlang.ast.*;

import java.util.*;

/**
 * Provides standard smart contract templates (ERC20, ERC721, MultiSig, Vault).
 *
 * <p>Each template is a complete, ready-to-compile DhrLang program with
 * proper annotations, storage layout, and standard-compliant function
 * signatures.</p>
 *
 * <p><b>User story:</b> SC-505 — As a developer, I want example contracts
 * (ERC20, ERC721).</p>
 */
public final class ExampleContractTemplates {

    private ExampleContractTemplates() {}

    // ── Template Enum ────────────────────────────────────────────────────

    /**
     * Available contract template types.
     */
    public enum TemplateType {
        ERC20_TOKEN("ERC20 Token", "Standard fungible token"),
        ERC721_NFT("ERC721 NFT", "Non-fungible token"),
        MULTI_SIG_WALLET("Multi-Sig Wallet", "Multi-signature wallet"),
        STAKING_VAULT("Staking Vault", "Token staking with rewards");

        private final String displayName;
        private final String description;

        TemplateType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // ── TemplateInfo ─────────────────────────────────────────────────────

    /**
     * Metadata about a contract template.
     */
    public static final class TemplateInfo {
        private final TemplateType type;
        private final String name;
        private final String description;
        private final String sourceCode;
        private final List<String> features;
        private final int functionCount;
        private final int storageSlotCount;
        private final int eventCount;

        TemplateInfo(TemplateType type, String name, String description, String sourceCode,
                     List<String> features, int functionCount, int storageSlotCount,
                     int eventCount) {
            this.type = type;
            this.name = name;
            this.description = description;
            this.sourceCode = sourceCode;
            this.features = Collections.unmodifiableList(features);
            this.functionCount = functionCount;
            this.storageSlotCount = storageSlotCount;
            this.eventCount = eventCount;
        }

        public TemplateType getType() { return type; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getSourceCode() { return sourceCode; }
        public List<String> getFeatures() { return features; }
        public int getFunctionCount() { return functionCount; }
        public int getStorageSlotCount() { return storageSlotCount; }
        public int getEventCount() { return eventCount; }
    }

    // ── Template Registry ────────────────────────────────────────────────

    private static final Map<TemplateType, TemplateInfo> TEMPLATES = new EnumMap<>(TemplateType.class);
    static {
        TEMPLATES.put(TemplateType.ERC20_TOKEN, buildErc20Template());
        TEMPLATES.put(TemplateType.ERC721_NFT, buildErc721Template());
        TEMPLATES.put(TemplateType.MULTI_SIG_WALLET, buildMultiSigTemplate());
        TEMPLATES.put(TemplateType.STAKING_VAULT, buildStakingVaultTemplate());
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Get a template by type.
     */
    public static TemplateInfo getTemplate(TemplateType type) {
        return TEMPLATES.get(type);
    }

    /**
     * Get all available templates.
     */
    public static Collection<TemplateInfo> allTemplates() {
        return Collections.unmodifiableCollection(TEMPLATES.values());
    }

    /**
     * Get template types available.
     */
    public static Set<TemplateType> availableTypes() {
        return Collections.unmodifiableSet(TEMPLATES.keySet());
    }

    /**
     * Build a DhrLang AST Program from a template type.
     *
     * @param type the template type
     * @return a {@link Program} with the template contracts
     */
    public static Program buildProgram(TemplateType type) {
        switch (type) {
            case ERC20_TOKEN: return buildErc20Ast();
            case ERC721_NFT: return buildErc721Ast();
            case MULTI_SIG_WALLET: return buildMultiSigAst();
            case STAKING_VAULT: return buildStakingVaultAst();
            default: throw new IllegalArgumentException("Unknown template: " + type);
        }
    }

    /**
     * Format a brief summary of all available templates.
     */
    public static String formatCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║          DhrLang Contract Templates                         ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");
        for (TemplateInfo t : TEMPLATES.values()) {
            sb.append("  ").append(t.getType().getDisplayName()).append('\n');
            sb.append("    ").append(t.getDescription()).append('\n');
            sb.append("    Functions: ").append(t.getFunctionCount())
                    .append("  Storage slots: ").append(t.getStorageSlotCount())
                    .append("  Events: ").append(t.getEventCount()).append('\n');
            sb.append("    Features: ").append(String.join(", ", t.getFeatures())).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    // ── ERC20 Template ───────────────────────────────────────────────────

    private static TemplateInfo buildErc20Template() {
        String source =
                "@contract\n" +
                "class DhrToken {\n" +
                "    @storage Address owner;\n" +
                "    @storage uint256 totalSupply;\n" +
                "    @storage mapping(Address → uint256) balances;\n" +
                "    @storage mapping(Address → mapping(Address → uint256)) allowances;\n" +
                "    @immutable kya name;\n" +
                "    @immutable kya symbol;\n" +
                "\n" +
                "    @constructor\n" +
                "    kaam init(kya _name, kya _symbol, uint256 _initialSupply) {\n" +
                "        owner = msg.sender;\n" +
                "        name = _name;\n" +
                "        symbol = _symbol;\n" +
                "        totalSupply = _initialSupply;\n" +
                "        balances[msg.sender] = _initialSupply;\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam balanceOf(Address account) -> uint256 {\n" +
                "        return balances[account];\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam transfer(Address to, uint256 amount) -> sab {\n" +
                "        require(balances[msg.sender] >= amount, \"Insufficient balance\");\n" +
                "        balances[msg.sender] -= amount;\n" +
                "        balances[to] += amount;\n" +
                "        emit Transfer(msg.sender, to, amount);\n" +
                "        return sahi;\n" +
                "    }\n" +
                "\n" +
                "    kaam approve(Address spender, uint256 amount) -> sab {\n" +
                "        allowances[msg.sender][spender] = amount;\n" +
                "        emit Approval(msg.sender, spender, amount);\n" +
                "        return sahi;\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam transferFrom(Address from, Address to, uint256 amount) -> sab {\n" +
                "        require(balances[from] >= amount, \"Insufficient balance\");\n" +
                "        require(allowances[from][msg.sender] >= amount, \"Insufficient allowance\");\n" +
                "        balances[from] -= amount;\n" +
                "        balances[to] += amount;\n" +
                "        allowances[from][msg.sender] -= amount;\n" +
                "        emit Transfer(from, to, amount);\n" +
                "        return sahi;\n" +
                "    }\n" +
                "\n" +
                "    @event\n" +
                "    kaam Transfer(Address from, Address to, uint256 amount) {}\n" +
                "\n" +
                "    @event\n" +
                "    kaam Approval(Address owner, Address spender, uint256 amount) {}\n" +
                "}\n";

        return new TemplateInfo(
                TemplateType.ERC20_TOKEN, "DhrToken",
                "ERC20-compatible fungible token with transfer, approve, and transferFrom.",
                source,
                List.of("ERC20", "@nonreentrant transfers", "allowance system", "events"),
                7, 4, 2
        );
    }

    private static Program buildErc20Ast() {
        return buildSimpleContractProgram("DhrToken",
                List.of(
                        storageVar("Address", "owner"),
                        storageVar("uint256", "totalSupply"),
                        storageVar("mapping(Address → uint256)", "balances"),
                        storageVar("mapping(Address → mapping(Address → uint256))", "allowances")
                ),
                List.of(
                        annotatedFn("init", ContractAnnotation.CONSTRUCTOR, "kya", "kya", "uint256"),
                        annotatedFn("balanceOf", ContractAnnotation.VIEW, "Address"),
                        annotatedFn("transfer", ContractAnnotation.NONREENTRANT, "Address", "uint256"),
                        fn("approve", "Address", "uint256"),
                        annotatedFn("transferFrom", ContractAnnotation.NONREENTRANT,
                                "Address", "Address", "uint256"),
                        annotatedFn("Transfer", ContractAnnotation.EVENT,
                                "Address", "Address", "uint256"),
                        annotatedFn("Approval", ContractAnnotation.EVENT,
                                "Address", "Address", "uint256")
                )
        );
    }

    // ── ERC721 Template ──────────────────────────────────────────────────

    private static TemplateInfo buildErc721Template() {
        String source =
                "@contract\n" +
                "class DhrNFT {\n" +
                "    @storage Address owner;\n" +
                "    @storage uint256 nextTokenId;\n" +
                "    @storage mapping(uint256 → Address) owners;\n" +
                "    @storage mapping(Address → uint256) balances;\n" +
                "    @storage mapping(uint256 → Address) tokenApprovals;\n" +
                "    @storage mapping(uint256 → kya) tokenURIs;\n" +
                "    @immutable kya name;\n" +
                "    @immutable kya symbol;\n" +
                "\n" +
                "    @constructor\n" +
                "    kaam init(kya _name, kya _symbol) {\n" +
                "        owner = msg.sender;\n" +
                "        name = _name;\n" +
                "        symbol = _symbol;\n" +
                "        nextTokenId = 1;\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam mint(Address to, kya uri) -> uint256 {\n" +
                "        require(msg.sender == owner, \"Only owner can mint\");\n" +
                "        num tokenId = nextTokenId;\n" +
                "        nextTokenId += 1;\n" +
                "        owners[tokenId] = to;\n" +
                "        balances[to] += 1;\n" +
                "        tokenURIs[tokenId] = uri;\n" +
                "        emit Transfer(address(0), to, tokenId);\n" +
                "        return tokenId;\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam ownerOf(uint256 tokenId) -> Address {\n" +
                "        return owners[tokenId];\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam balanceOf(Address account) -> uint256 {\n" +
                "        return balances[account];\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam transferFrom(Address from, Address to, uint256 tokenId) {\n" +
                "        require(owners[tokenId] == from, \"Not the owner\");\n" +
                "        require(msg.sender == from || tokenApprovals[tokenId] == msg.sender, \"Not authorized\");\n" +
                "        owners[tokenId] = to;\n" +
                "        balances[from] -= 1;\n" +
                "        balances[to] += 1;\n" +
                "        tokenApprovals[tokenId] = address(0);\n" +
                "        emit Transfer(from, to, tokenId);\n" +
                "    }\n" +
                "\n" +
                "    kaam approve(Address to, uint256 tokenId) {\n" +
                "        require(owners[tokenId] == msg.sender, \"Not the owner\");\n" +
                "        tokenApprovals[tokenId] = to;\n" +
                "        emit Approval(msg.sender, to, tokenId);\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam tokenURI(uint256 tokenId) -> kya {\n" +
                "        return tokenURIs[tokenId];\n" +
                "    }\n" +
                "\n" +
                "    @event\n" +
                "    kaam Transfer(Address from, Address to, uint256 tokenId) {}\n" +
                "\n" +
                "    @event\n" +
                "    kaam Approval(Address owner, Address approved, uint256 tokenId) {}\n" +
                "}\n";

        return new TemplateInfo(
                TemplateType.ERC721_NFT, "DhrNFT",
                "ERC721-compatible non-fungible token with mint, transfer, and approval.",
                source,
                List.of("ERC721", "mint with URI", "@nonreentrant transfers", "approval"),
                9, 6, 2
        );
    }

    private static Program buildErc721Ast() {
        return buildSimpleContractProgram("DhrNFT",
                List.of(
                        storageVar("Address", "owner"),
                        storageVar("uint256", "nextTokenId"),
                        storageVar("mapping(uint256 → Address)", "owners"),
                        storageVar("mapping(Address → uint256)", "balances"),
                        storageVar("mapping(uint256 → Address)", "tokenApprovals"),
                        storageVar("mapping(uint256 → kya)", "tokenURIs")
                ),
                List.of(
                        annotatedFn("init", ContractAnnotation.CONSTRUCTOR, "kya", "kya"),
                        annotatedFn("mint", ContractAnnotation.NONREENTRANT, "Address", "kya"),
                        annotatedFn("ownerOf", ContractAnnotation.VIEW, "uint256"),
                        annotatedFn("balanceOf", ContractAnnotation.VIEW, "Address"),
                        annotatedFn("transferFrom", ContractAnnotation.NONREENTRANT,
                                "Address", "Address", "uint256"),
                        fn("approve", "Address", "uint256"),
                        annotatedFn("tokenURI", ContractAnnotation.VIEW, "uint256"),
                        annotatedFn("Transfer", ContractAnnotation.EVENT,
                                "Address", "Address", "uint256"),
                        annotatedFn("Approval", ContractAnnotation.EVENT,
                                "Address", "Address", "uint256")
                )
        );
    }

    // ── Multi-Sig Wallet Template ────────────────────────────────────────

    private static TemplateInfo buildMultiSigTemplate() {
        String source =
                "@contract\n" +
                "class MultiSigWallet {\n" +
                "    @storage uint256 required;\n" +
                "    @storage uint256 txCount;\n" +
                "    @storage mapping(Address → sab) isOwner;\n" +
                "    @storage mapping(uint256 → Address) txDestination;\n" +
                "    @storage mapping(uint256 → uint256) txValue;\n" +
                "    @storage mapping(uint256 → sab) txExecuted;\n" +
                "    @storage mapping(uint256 → uint256) txConfirmCount;\n" +
                "\n" +
                "    @constructor\n" +
                "    kaam init(uint256 _required) {\n" +
                "        required = _required;\n" +
                "        isOwner[msg.sender] = sahi;\n" +
                "        txCount = 0;\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam submitTransaction(Address destination, uint256 value) -> uint256 {\n" +
                "        require(isOwner[msg.sender], \"Not an owner\");\n" +
                "        num txId = txCount;\n" +
                "        txDestination[txId] = destination;\n" +
                "        txValue[txId] = value;\n" +
                "        txExecuted[txId] = galat;\n" +
                "        txConfirmCount[txId] = 0;\n" +
                "        txCount += 1;\n" +
                "        emit Submission(txId);\n" +
                "        return txId;\n" +
                "    }\n" +
                "\n" +
                "    kaam confirmTransaction(uint256 txId) {\n" +
                "        require(isOwner[msg.sender], \"Not an owner\");\n" +
                "        require(!txExecuted[txId], \"Already executed\");\n" +
                "        txConfirmCount[txId] += 1;\n" +
                "        emit Confirmation(msg.sender, txId);\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam executeTransaction(uint256 txId) {\n" +
                "        require(txConfirmCount[txId] >= required, \"Not enough confirmations\");\n" +
                "        require(!txExecuted[txId], \"Already executed\");\n" +
                "        txExecuted[txId] = sahi;\n" +
                "        emit Execution(txId);\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam getConfirmationCount(uint256 txId) -> uint256 {\n" +
                "        return txConfirmCount[txId];\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam isConfirmed(uint256 txId) -> sab {\n" +
                "        return txConfirmCount[txId] >= required;\n" +
                "    }\n" +
                "\n" +
                "    @event\n" +
                "    kaam Submission(uint256 txId) {}\n" +
                "\n" +
                "    @event\n" +
                "    kaam Confirmation(Address sender, uint256 txId) {}\n" +
                "\n" +
                "    @event\n" +
                "    kaam Execution(uint256 txId) {}\n" +
                "}\n";

        return new TemplateInfo(
                TemplateType.MULTI_SIG_WALLET, "MultiSigWallet",
                "Multi-signature wallet requiring M-of-N confirmations for transactions.",
                source,
                List.of("M-of-N signatures", "@nonreentrant execution", "transaction queue", "events"),
                8, 7, 3
        );
    }

    private static Program buildMultiSigAst() {
        return buildSimpleContractProgram("MultiSigWallet",
                List.of(
                        storageVar("uint256", "required"),
                        storageVar("uint256", "txCount"),
                        storageVar("mapping(Address → sab)", "isOwner"),
                        storageVar("mapping(uint256 → Address)", "txDestination"),
                        storageVar("mapping(uint256 → uint256)", "txValue"),
                        storageVar("mapping(uint256 → sab)", "txExecuted"),
                        storageVar("mapping(uint256 → uint256)", "txConfirmCount")
                ),
                List.of(
                        annotatedFn("init", ContractAnnotation.CONSTRUCTOR, "uint256"),
                        annotatedFn("submitTransaction", ContractAnnotation.NONREENTRANT,
                                "Address", "uint256"),
                        fn("confirmTransaction", "uint256"),
                        annotatedFn("executeTransaction", ContractAnnotation.NONREENTRANT, "uint256"),
                        annotatedFn("getConfirmationCount", ContractAnnotation.VIEW, "uint256"),
                        annotatedFn("isConfirmed", ContractAnnotation.VIEW, "uint256"),
                        annotatedFn("Submission", ContractAnnotation.EVENT, "uint256"),
                        annotatedFn("Confirmation", ContractAnnotation.EVENT, "Address", "uint256"),
                        annotatedFn("Execution", ContractAnnotation.EVENT, "uint256")
                )
        );
    }

    // ── Staking Vault Template ───────────────────────────────────────────

    private static TemplateInfo buildStakingVaultTemplate() {
        String source =
                "@contract\n" +
                "class StakingVault {\n" +
                "    @storage Address owner;\n" +
                "    @storage uint256 rewardRate;\n" +
                "    @storage uint256 totalStaked;\n" +
                "    @storage mapping(Address → uint256) stakes;\n" +
                "    @storage mapping(Address → uint256) lastStakeTime;\n" +
                "    @storage mapping(Address → uint256) rewards;\n" +
                "\n" +
                "    @constructor\n" +
                "    kaam init(uint256 _rewardRate) {\n" +
                "        owner = msg.sender;\n" +
                "        rewardRate = _rewardRate;\n" +
                "        totalStaked = 0;\n" +
                "    }\n" +
                "\n" +
                "    @payable\n" +
                "    @nonreentrant\n" +
                "    kaam stake() {\n" +
                "        updateReward(msg.sender);\n" +
                "        stakes[msg.sender] += msg.value;\n" +
                "        totalStaked += msg.value;\n" +
                "        lastStakeTime[msg.sender] = block.timestamp;\n" +
                "        emit Staked(msg.sender, msg.value);\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam unstake(uint256 amount) {\n" +
                "        require(stakes[msg.sender] >= amount, \"Insufficient stake\");\n" +
                "        updateReward(msg.sender);\n" +
                "        stakes[msg.sender] -= amount;\n" +
                "        totalStaked -= amount;\n" +
                "        emit Unstaked(msg.sender, amount);\n" +
                "    }\n" +
                "\n" +
                "    @nonreentrant\n" +
                "    kaam claimRewards() -> uint256 {\n" +
                "        updateReward(msg.sender);\n" +
                "        num reward = rewards[msg.sender];\n" +
                "        rewards[msg.sender] = 0;\n" +
                "        emit RewardClaimed(msg.sender, reward);\n" +
                "        return reward;\n" +
                "    }\n" +
                "\n" +
                "    kaam updateReward(Address account) {\n" +
                "        num timeElapsed = block.timestamp - lastStakeTime[account];\n" +
                "        num pending = stakes[account] * rewardRate * timeElapsed;\n" +
                "        rewards[account] += pending;\n" +
                "        lastStakeTime[account] = block.timestamp;\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam getStake(Address account) -> uint256 {\n" +
                "        return stakes[account];\n" +
                "    }\n" +
                "\n" +
                "    @view\n" +
                "    kaam getPendingRewards(Address account) -> uint256 {\n" +
                "        return rewards[account];\n" +
                "    }\n" +
                "\n" +
                "    @event\n" +
                "    kaam Staked(Address user, uint256 amount) {}\n" +
                "\n" +
                "    @event\n" +
                "    kaam Unstaked(Address user, uint256 amount) {}\n" +
                "\n" +
                "    @event\n" +
                "    kaam RewardClaimed(Address user, uint256 amount) {}\n" +
                "}\n";

        return new TemplateInfo(
                TemplateType.STAKING_VAULT, "StakingVault",
                "Staking vault with time-based reward distribution.",
                source,
                List.of("staking", "time-based rewards", "@payable", "@nonreentrant", "events"),
                9, 6, 3
        );
    }

    private static Program buildStakingVaultAst() {
        return buildSimpleContractProgram("StakingVault",
                List.of(
                        storageVar("Address", "owner"),
                        storageVar("uint256", "rewardRate"),
                        storageVar("uint256", "totalStaked"),
                        storageVar("mapping(Address → uint256)", "stakes"),
                        storageVar("mapping(Address → uint256)", "lastStakeTime"),
                        storageVar("mapping(Address → uint256)", "rewards")
                ),
                List.of(
                        annotatedFn("init", ContractAnnotation.CONSTRUCTOR, "uint256"),
                        annotatedMultiFn("stake", Set.of(ContractAnnotation.PAYABLE, ContractAnnotation.NONREENTRANT)),
                        annotatedFn("unstake", ContractAnnotation.NONREENTRANT, "uint256"),
                        annotatedFn("claimRewards", ContractAnnotation.NONREENTRANT),
                        fn("updateReward", "Address"),
                        annotatedFn("getStake", ContractAnnotation.VIEW, "Address"),
                        annotatedFn("getPendingRewards", ContractAnnotation.VIEW, "Address"),
                        annotatedFn("Staked", ContractAnnotation.EVENT, "Address", "uint256"),
                        annotatedFn("Unstaked", ContractAnnotation.EVENT, "Address", "uint256"),
                        annotatedFn("RewardClaimed", ContractAnnotation.EVENT, "Address", "uint256")
                )
        );
    }

    // ── AST Helpers ──────────────────────────────────────────────────────

    /**
     * Build a VarDecl with @storage annotation.
     */
    static VarDecl storageVar(String type, String name) {
        return new VarDecl(type, name, null, Set.of(),
                EnumSet.of(ContractAnnotation.STORAGE));
    }

    /**
     * Build a FunctionDecl with no annotations.
     */
    static FunctionDecl fn(String name, String... paramTypes) {
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        return new FunctionDecl("void", name, params, new Block(List.of()));
    }

    /**
     * Build a FunctionDecl with one annotation.
     */
    static FunctionDecl annotatedFn(String name, ContractAnnotation ann, String... paramTypes) {
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        return new FunctionDecl("void", name, params, new Block(List.of()),
                Set.of(), EnumSet.of(ann));
    }

    /**
     * Build a FunctionDecl with multiple annotations.
     */
    static FunctionDecl annotatedMultiFn(String name, Set<ContractAnnotation> anns, String... paramTypes) {
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        EnumSet<ContractAnnotation> annSet = EnumSet.noneOf(ContractAnnotation.class);
        annSet.addAll(anns);
        return new FunctionDecl("void", name, params, new Block(List.of()),
                Set.of(), annSet);
    }

    /**
     * Build a @contract Program with storage vars and functions.
     */
    static Program buildSimpleContractProgram(String name,
                                              List<VarDecl> vars,
                                              List<FunctionDecl> fns) {
        ClassDecl cls = new ClassDecl(name, null, new ArrayList<>(),
                fns, vars, Set.of(),
                EnumSet.of(ContractAnnotation.CONTRACT));
        return new Program(List.of(cls));
    }
}
