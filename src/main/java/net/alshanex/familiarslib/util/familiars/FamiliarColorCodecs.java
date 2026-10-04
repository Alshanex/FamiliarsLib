package net.alshanex.familiarslib.util.familiars;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.item.DyeColor;

import java.util.Locale;

public final class FamiliarColorCodecs {
    /**
     * A color in a data file. Accepts:
     * <ul>
     *     <li>a hex string: {@code "#5A8F3C"} or {@code "5A8F3C"}</li>
     *     <li>a dye name: {@code "lime"}, {@code "light_blue"}...</li>
     *     <li>a plain integer: {@code 5935932}</li>
     * </ul>
     * Decodes to a 0xRRGGBB int.
     */
    public static final Codec<Integer> COLOR = Codec.either(Codec.INT, Codec.STRING).comapFlatMap(
            either -> either.map(i -> DataResult.success(i & 0xFFFFFF), FamiliarColorCodecs::parse),
            rgb -> Either.right(String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF))
    );

    private static DataResult<Integer> parse(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);

        DyeColor dye = DyeColor.byName(value, null);
        if (dye != null) {
            return DataResult.success(dye.getTextureDiffuseColor() & 0xFFFFFF);
        }

        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() == 6) {
            try {
                return DataResult.success(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
            }
        }
        return DataResult.error(() -> "Invalid color '" + raw + "': expected \"#RRGGBB\" or a dye name");
    }

    private FamiliarColorCodecs() {}
}