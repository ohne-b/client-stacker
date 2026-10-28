package dev.ohneb.clientstacker.testmixin;

import dev.ohneb.clientstacker.client.ClientStacker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(ClientStacker.class)
public abstract class TestConfigMixin {
    @ModifyVariable(method = "initialize", at = @At("HEAD"), argsOnly = true)
    private static Path isolatedSettings(Path original) throws IOException {
        return Files.createTempDirectory(Path.of(System.getProperty("clientstacker.test.artifacts")), "gametest-config-");
    }
}
