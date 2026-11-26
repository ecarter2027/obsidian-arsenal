package com.obsidianarsenal.client;

import com.obsidianarsenal.ObsidianArsenal;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Iron's scroll tooltips list a spell's level, rarity, mana, cooldown and stats but not what it does (that text,
 * spell.<id>.guide, only shows in the spellbook screen). For this mod's spells, the description is added to the
 * scroll's tooltip as well, under the stats, wrapped to tooltip width in grey italics.
 */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, value = Dist.CLIENT)
public final class SpellTooltips {
    private static final int WRAP = 38;   // characters per tooltip line

    private SpellTooltips() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void tooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        if (stack.getItem() != ItemRegistry.SCROLL.get() || !ISpellContainer.isSpellContainer(stack)) return;
        ISpellContainer container = ISpellContainer.get(stack);
        if (container == null || container.isEmpty()) return;
        AbstractSpell spell = container.getSpellAtIndex(0).getSpell();
        if (spell == null || !ObsidianArsenal.MODID.equals(spell.getSpellResource().getNamespace())) return;
        String key = "spell." + spell.getSpellResource().getNamespace() + "." + spell.getSpellResource().getPath() + ".guide";
        if (!I18n.exists(key)) return;
        List<Component> lines = e.getToolTip();
        lines.add(Component.empty());
        for (String line : wrap(I18n.get(key)))
            lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > WRAP) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
