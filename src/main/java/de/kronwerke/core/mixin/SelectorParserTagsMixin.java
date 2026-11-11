package de.kronwerke.core.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;

import de.kronwerke.core.perf.TagIndex;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;

/** Notes the positive tag= options of a selector, so the selector can use the tag index. */
@Mixin(EntitySelectorParser.class)
public abstract class SelectorParserTagsMixin implements TagIndex.Required {

    @Unique
    private List<String> kronwerke$tags = new ArrayList<>();

    @WrapOperation(method = "parseOptions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/arguments/selector/options/EntitySelectorOptions;get(Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;Ljava/lang/String;I)Lnet/minecraft/commands/arguments/selector/options/EntitySelectorOptions$Modifier;"))
    private EntitySelectorOptions.Modifier kronwerke$noteTag(EntitySelectorParser parser, String name, int cursor, Operation<EntitySelectorOptions.Modifier> original) {
        EntitySelectorOptions.Modifier modifier = original.call(parser, name, cursor);
        if (!"tag".equals(name)) return modifier;
        return p -> {
            StringReader reader = p.getReader();
            int start = reader.getCursor();
            modifier.handle(p);
            String raw = reader.getString().substring(start, reader.getCursor()).trim();
            if (!raw.isEmpty() && raw.charAt(0) != '!') kronwerke$tags.add(raw);
        };
    }

    @Inject(method = "getSelector", at = @At("RETURN"))
    private void kronwerke$passTags(CallbackInfoReturnable<EntitySelector> cir) {
        if (!kronwerke$tags.isEmpty()) ((TagIndex.Required) cir.getReturnValue()).kronwerke$requiredTags(List.copyOf(kronwerke$tags));
    }

    @Override
    public List<String> kronwerke$requiredTags() {
        return kronwerke$tags;
    }

    @Override
    public void kronwerke$requiredTags(List<String> tags) {
        kronwerke$tags = new ArrayList<>(tags);
    }
}
