package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

/**
 * @author IzumiiKonata
 * Date: 2026/5/10 21:46
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("equipmentAssets")
    EquipmentAssetManager getEquipmentAssetManager();

    @Accessor("blockModelResolver") BlockModelResolver getBlockModelResolver();
    @Accessor("itemModelResolver") ItemModelResolver getItemModelResolver();
    @Accessor("mapRenderer") MapRenderer getMapRenderer();
    @Accessor("font") Font getFont();
    @Accessor("entityModels") Supplier<EntityModelSet> getEntityModels();
    @Accessor("atlasManager") AtlasManager getAtlasManager();
    @Accessor("playerSkinRenderCache") PlayerSkinRenderCache getPlayerSkinRenderCache();

}
