package dev.ohneb.clientstacker.client;

import dev.ohneb.clientstacker.core.Conversion;
import dev.ohneb.clientstacker.core.TradeLimits;
import dev.ohneb.clientstacker.mixin.MerchantScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TradeOperation implements Operation {
    private enum Stage { PREPARE, BEFORE_CRAFT, CRAFT, BEFORE_OPEN, OPEN, SELECTED }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Entity merchant;
    private final Object level;
    private final ItemStack baseA;
    private final ItemStack costB;
    private final ItemStack result;
    private final int maxPrice;
    private final int maxUses;
    private final boolean bulk;
    private final int scroll;
    private final TradeLimits limits;
    private final Map<Conversion, Integer> retainedValues;
    private MerchantMenu menu;
    private MerchantOffer offer;
    private int index;
    private Stage stage = Stage.PREPARE;
    private CraftOperation craft;
    private long deadline;
    private long spent;
    private int completed;
    private int expectedUses = -1;
    private Map<Integer, ItemStack> expectedInventory;
    private int contentsId = -1;
    private int offersId = -1;
    private List<ItemStack> beforeSelect;
    private InventoryCheckpoint checkpoint;
    private Conversion neededConversion;
    private int neededCrafts;

    TradeOperation(MerchantMenu menu, int index, boolean bulk, Entity merchant) {
        if (merchant == null) throw new Stopped("merchant");
        this.merchant = merchant;
        this.level = minecraft.level;
        this.menu = menu;
        this.index = index;
        this.offer = menu.getOffers().get(index);
        this.baseA = offer.getBaseCostA().copy();
        this.costB = offer.getCostB().copy();
        this.result = offer.getResult().copy();
        this.maxPrice = offer.getCostA().getCount();
        this.maxUses = offer.getMaxUses();
        this.bulk = bulk;
        this.scroll = ClientStacker.screen() instanceof MerchantScreenAccess screen ? screen.clientStacker$scroll() : 0;
        this.limits = ClientStacker.config().tradeLimits();
        this.retainedValues = new HashMap<>();
        for (var material : Materials.ALL) retainedValues.put(material, ClientStacker.config().retainedValue(material));
        if (!Materials.plain(baseA) || !Materials.plain(costB)) throw new Stopped("components");
        if (!menu.getCarried().isEmpty()) throw new Stopped("cursor");
        if (!ClientStacker.config().protectedSlots.isEmpty()
            && (!menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty())) {
            throw new Stopped("input_provenance");
        }
        resetDeadline();
    }

    @Override
    public boolean tick() {
        checkMerchant();
        if (System.nanoTime() > deadline) throw new Stopped("timeout");
        switch (stage) {
            case PREPARE -> {
                Operation.requireCurrent(menu);
                if (!menu.getCarried().isEmpty()) throw new Stopped("cursor");
                MenuInventory.clearInputs(menu, List.of(0, 1));
                findOffer();
                if (offer.isOutOfStock()) return true;
                if (atLimit()) return true;
                interactionHand();
                Conversion needed = null;
                int crafts = 0;
                for (ItemStack cost : Materials.costs(offer)) {
                    if (cost.isEmpty()) continue;
                    long count = MenuInventory.count(menu, cost, true);
                    if (count >= cost.getCount()) continue;
                    Conversion conversion = Materials.forTrade(cost);
                    if (conversion == null) return noResources();
                    crafts = Math.toIntExact(conversion.blocksNeeded(cost.getCount(), count));
                    if (MenuInventory.count(menu, new ItemStack(Materials.item(conversion.compact())), true)
                        - ClientStacker.config().blockReserve(conversion) < crafts) {
                        return noResources();
                    }
                    needed = conversion;
                    break;
                }
                if (needed != null) {
                    neededConversion = needed;
                    neededCrafts = crafts;
                    checkpoint = new InventoryCheckpoint(menu, 2);
                    stage = Stage.BEFORE_CRAFT;
                } else {
                    // Selecting an offer fills inputs on the server. Reject sources the server
                    // could choose but this request is not allowed to touch.
                    for (var slot : MenuInventory.playerSlots(menu)) {
                        if (slot.getItem().isEmpty()) continue;
                        boolean matches = offer.getItemCostA().test(slot.getItem())
                            || offer.getItemCostB().map(cost -> cost.test(slot.getItem())).orElse(false);
                        if (matches && (MenuInventory.protectedSlot(slot) || !Materials.plain(slot.getItem()))) {
                            throw new Stopped("protected_trade");
                        }
                    }
                    beforeSelect = MenuInventory.copySlots(menu);
                    stage = Stage.SELECTED;
                    // Selection may rearrange inputs; verify its authoritative contents before buying.
                    menu.setSelectionHint(index);
                    minecraft.getConnection().send(new ServerboundSelectTradePacket(index));
                    checkpoint = new InventoryCheckpoint(menu, 2, false);
                }
                resetDeadline();
            }
            case BEFORE_CRAFT -> {
                if (!checkpoint.ready()) return false;
                interactionHand();
                minecraft.player.closeContainer();
                ClientStacker.showScreen(new InventoryScreen(minecraft.player));
                craft = new CraftOperation(minecraft.player.inventoryMenu, neededConversion, false, neededCrafts);
                checkpoint = null;
                stage = Stage.CRAFT;
                resetDeadline();
            }
            case CRAFT -> {
                if (craft.tick()) {
                    craft = null;
                    expectedInventory = inventory();
                    reopen();
                } else {
                    // CraftOperation owns its per-step timeout.
                    resetDeadline();
                }
            }
            case OPEN -> {
                var active = minecraft.player.containerMenu;
                if (!(active instanceof MerchantMenu opened)) {
                    if (active != minecraft.player.inventoryMenu) throw new Stopped("closed");
                    return false;
                }
                if (opened == menu || contentsId != opened.containerId || offersId != opened.containerId) return false;
                menu = opened;
                ClientStacker.associate(menu, merchant);
                findOffer();
                if (ClientStacker.screen() instanceof MerchantScreenAccess screen) {
                    screen.clientStacker$selection(index);
                    screen.clientStacker$scroll(Math.max(0, Math.min(scroll, menu.getOffers().size() - 7)));
                }
                if (!matchesInventory(expectedInventory) || !menu.getCarried().isEmpty()) throw new Stopped("uncertain");
                if (expectedUses >= 0) {
                    if (offer.getUses() != expectedUses) throw new Stopped("uncertain");
                    completed++;
                    expectedUses = -1;
                    if (!bulk) return true;
                }
                stage = Stage.PREPARE;
                resetDeadline();
            }
            case BEFORE_OPEN -> {
                if (!checkpoint.ready()) return false;
                checkpoint = null;
                reopen();
            }
            case SELECTED -> {
                Operation.requireCurrent(menu);
                if (!checkpoint.ready()) return false;
                findOffer();
                if (!MenuInventory.sameContents(beforeSelect, menu, 2)) throw new Stopped("changed");
                if (splitIdenticalCosts()) {
                    checkpoint = new InventoryCheckpoint(menu, 2);
                    resetDeadline();
                    return false;
                }
                if (offer.isOutOfStock() || !ItemStack.matches(menu.getSlot(2).getItem(), result)
                    || !offer.satisfiedBy(menu.getSlot(0).getItem(), menu.getSlot(1).getItem())) throw new Stopped("changed");
                if (atLimit()) {
                    MenuInventory.clearInputs(menu, List.of(0, 1));
                    return true;
                }
                for (var slot : MenuInventory.playerSlots(menu)) {
                    if (MenuInventory.protectedSlot(slot) && !ItemStack.matches(beforeSelect.get(slot.index), slot.getItem())) {
                        throw new Stopped("changed");
                    }
                }
                ItemStack leftA = menu.getSlot(0).getItem().copy();
                ItemStack leftB = menu.getSlot(1).getItem().copy();
                leftA.shrink(offer.getCostA().getCount());
                leftB.shrink(costB.getCount());
                var placement = MenuInventory.placement(menu, result, leftA, leftB);
                expectedUses = offer.getUses() + 1;
                long price = price();
                ClientStacker.click(menu, 2, 0);
                if (!ItemStack.matches(menu.getCarried(), result)) throw new Stopped("uncertain");
                MenuInventory.putCarried(menu, placement.get(0));
                if (!ItemStack.matches(menu.getSlot(0).getItem(), leftA)
                    || !ItemStack.matches(menu.getSlot(1).getItem(), leftB)) throw new Stopped("uncertain");
                MenuInventory.clearInputs(menu, List.of(0, 1));
                spent += price;
                expectedInventory = inventory();
                checkpoint = new InventoryCheckpoint(menu, 2);
                stage = Stage.BEFORE_OPEN;
                resetDeadline();
            }
        }
        return false;
    }

    private boolean noResources() {
        if (completed == 0) ClientStacker.message("resources");
        return true;
    }

    private boolean splitIdenticalCosts() {
        ItemStack a = offer.getCostA();
        ItemStack first = menu.getSlot(0).getItem();
        ItemStack second = menu.getSlot(1).getItem();
        if (costB.isEmpty() || !ItemStack.isSameItemSameComponents(a, costB)
            || !ItemStack.isSameItemSameComponents(first, a)
            || (!second.isEmpty() && !ItemStack.isSameItemSameComponents(second, costB))) return false;
        int missing = costB.getCount() - second.getCount();
        if (missing <= 0 || first.getCount() - missing < a.getCount()) return false;
        ClientStacker.click(menu, 0, 0);
        for (int i = 0; i < missing; i++) ClientStacker.click(menu, 1, 1);
        ClientStacker.click(menu, 0, 0);
        return true;
    }

    private boolean atLimit() {
        TradeLimits.Stop limit = limits.check(spent, price(), ClientStacker.balance(menu, true),
            MenuInventory.count(menu, result, false), result.getCount());
        if (limit == TradeLimits.Stop.NONE) {
            for (var material : Materials.ALL) {
                long expense = 0;
                long compactCost = 0;
                for (ItemStack cost : Materials.costs(offer)) {
                    expense += material.itemValue(Materials.id(cost), cost.getCount());
                    if (material.compact().equals(Materials.id(cost))) compactCost += cost.getCount();
                }
                if (expense == 0) continue;
                long blocks = MenuInventory.count(menu, new ItemStack(Materials.item(material.compact())), true);
                long loose = MenuInventory.count(menu, new ItemStack(Materials.item(material.loose())), true);
                for (int slot : new int[]{0, 1}) {
                    ItemStack input = menu.getSlot(slot).getItem();
                    if (!Materials.plain(input)) continue;
                    if (material.compact().equals(Materials.id(input))) blocks += input.getCount();
                    if (material.loose().equals(Materials.id(input))) loose += input.getCount();
                }
                int retained = retainedValues.get(material);
                int blockReserve = ClientStacker.config().blockReserve(material);
                long value = material.value(blocks, loose);
                if ((retained > 0 && (value < retained || expense > value - retained))
                    || (blockReserve > 0 && compactCost > 0 && compactCost > blocks - blockReserve)) {
                    ClientStacker.message("material_reserve");
                    return true;
                }
            }
            return false;
        }
        ClientStacker.message("limit." + limit.name().toLowerCase(java.util.Locale.ROOT));
        return true;
    }

    private long price() {
        return Materials.costs(offer).stream()
            .mapToLong(cost -> Materials.EMERALDS.itemValue(Materials.id(cost), cost.getCount())).sum();
    }

    private InteractionHand interactionHand() {
        for (InteractionHand hand : InteractionHand.values()) {
            if (minecraft.player.getItemInHand(hand).isEmpty()) return hand;
        }
        throw new Stopped("interaction_hand");
    }

    private void findOffer() {
        int found = -1;
        for (int i = 0; i < menu.getOffers().size(); i++) {
            MerchantOffer candidate = menu.getOffers().get(i);
            if (ItemStack.matches(candidate.getBaseCostA(), baseA)
                && ItemStack.matches(candidate.getCostB(), costB)
                && ItemStack.matches(candidate.getResult(), result) && candidate.getMaxUses() == maxUses) {
                if (found != -1) throw new Stopped("offer");
                found = i;
            }
        }
        if (found == -1) throw new Stopped("offer");
        index = found;
        offer = menu.getOffers().get(index);
        if (offer.getCostA().getCount() > maxPrice) throw new Stopped("price");
    }

    private void checkMerchant() {
        if (minecraft.player == null || minecraft.level != level || !minecraft.player.isAlive()
            || merchant.isRemoved() || !merchant.isAlive()
            || merchant.getBoundingBox().distanceToSqr(minecraft.player.getEyePosition())
                >= minecraft.player.entityInteractionRange() * minecraft.player.entityInteractionRange()
            || !minecraft.player.hasLineOfSight(merchant)) throw new Stopped("merchant");
    }

    private void reopen() {
        checkMerchant();
        if (!minecraft.player.containerMenu.getCarried().isEmpty()) throw new Stopped("cursor");
        InteractionHand hand = interactionHand();
        minecraft.player.closeContainer();
        contentsId = -1;
        offersId = -1;
        stage = Stage.OPEN;
        ClientStacker.reopen(merchant, hand);
        resetDeadline();
    }

    private Map<Integer, ItemStack> inventory() {
        var result = new HashMap<Integer, ItemStack>();
        for (var slot : MenuInventory.playerSlots(minecraft.player.containerMenu)) {
            result.put(slot.getContainerSlot(), slot.getItem().copy());
        }
        return result;
    }

    private boolean matchesInventory(Map<Integer, ItemStack> expected) {
        if (expected == null) return false;
        return MenuInventory.playerSlots(menu).stream()
            .allMatch(slot -> ItemStack.matches(expected.get(slot.getContainerSlot()), slot.getItem()));
    }

    @Override
    public void slot(int container, int slot, ItemStack item) {
        if (craft != null) craft.slot(container, slot, item);
    }

    @Override
    public void contents(int container) {
        if (craft != null) craft.contents(container);
        if (checkpoint != null) checkpoint.contents(container);
        if (stage == Stage.OPEN) contentsId = container;
    }

    @Override
    public void barrier(int id) {
        if (craft != null) craft.barrier(id);
        if (checkpoint != null) checkpoint.barrier(id);
    }

    @Override
    public void offers(int container) {
        if (stage == Stage.OPEN) offersId = container;
    }

    @Override
    public void cancel() {
        if (craft != null) craft.cancel();
        // A purchase awaiting a fresh snapshot is never repeated or reversed.
    }

    @Override public int completed() { return completed; }
    @Override public String unit() { return "trades"; }

    private void resetDeadline() {
        deadline = System.nanoTime() + ClientStacker.config().timeoutSeconds * 1_000_000_000L;
    }
}
