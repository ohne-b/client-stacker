package dev.ohneb.clientstacker.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class OptionalMixins implements IMixinConfigPlugin {
    @Override
    public List<String> getMixins() {
        //? if <=1.21.8 && fabric {
        /*String target = "fabric/cc/cassian/trading/BetterGuiMerchant.class";
        *///?} else if <=1.21.8 && neoforge {
        /*String target = "neoforge/cc/cassian/trading/BetterGuiMerchant.class";
        *///?} else {
        String target = "cc/cassian/trading/BetterGuiMerchant.class";
        //?}
        return getClass().getClassLoader().getResource(target) == null ? List.of() : List.of("VtpTradeMixin");
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
