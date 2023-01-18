package org.localmc.tools.prp.mixin;

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.localmc.tools.prp.PatchouliResPatchMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.patchouli.client.book.BookContentClasspathLoader;
import vazkii.patchouli.client.book.BookContentLoader;
import vazkii.patchouli.client.book.BookContentsBuilder;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;


@Mixin(value = BookContentClasspathLoader.class, remap = false)
public class MixinBookContentClasspathLoader {

    @Inject(at = @At("HEAD"), method = "findFiles")
    private void findFiles(Book book, String dir, List<Identifier> list, CallbackInfo ci) {
        String prefix = String.format("%s/%s/%s/%s", BookRegistry.BOOKS_LOCATION, book.id.getPath(), BookContentsBuilder.DEFAULT_LANG, dir);
        Map<Identifier, Resource> files = MinecraftClient.getInstance().getResourceManager().findResources(prefix, p -> p.getNamespace().endsWith(".json"));

        files.keySet().stream()
                .distinct()
                .filter(file -> file.getNamespace().equals(book.id.getNamespace()))
                .map(file -> {
                    // caller expects list to contain logical id's, not file paths.
                    // we end up going from path -> id -> back to path, but it's okay as a transitional measure
                    Preconditions.checkArgument(file.getPath().startsWith(prefix));
                    Preconditions.checkArgument(file.getPath().endsWith(".json"));
                    String newPath = file.getPath().substring(prefix.length(), file.getPath().length() - ".json".length());
                    // Vanilla expects `prefix` above to not have a trailing slash, so we
                    // have to remove it ourselves from the path
                    if (newPath.startsWith("/")) {
                        newPath = newPath.substring(1);
                    }
                    return new Identifier(file.getNamespace(), newPath);
                })
                .forEach(list::add);

    }

    @Inject(at = @At("HEAD"), method = "loadJson", cancellable = true, remap = false)
    private void loadJson(Book book, Identifier resloc, @Nullable Identifier fallback, CallbackInfoReturnable<JsonElement> callback) {
        PatchouliResPatchMod.LOGGER.debug("Loading {}", resloc);
        ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
        try {
            if (manager.getResource(resloc).isPresent()) {
                callback.setReturnValue(BookContentLoader.streamToJson(manager.getResource(resloc).get().getInputStream()));
            } else if (fallback != null && manager.getResource(fallback).isPresent()) {
                callback.setReturnValue(BookContentLoader.streamToJson(manager.getResource(fallback).get().getInputStream()));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
