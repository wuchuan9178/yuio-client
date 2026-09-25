package dev.sherii.yuio.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

public final class StringUtils {
   public static final StringUtils INSTANCE = new StringUtils();
   private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6};");

   private StringUtils() {
   }

   // 把 "#rrggbb;" 这种色标转成带颜色的 Text
   public final Text formatHex(String text) {
      MutableText result = Text.empty();
      List<String> parts = new ArrayList<>();
      Matcher matcher = HEX.matcher(text);
      int start = 0;

      while (matcher.find()) {
         if (matcher.start() > start) parts.add(text.substring(start, matcher.start()));
         parts.add(matcher.group());
         start = matcher.end();
      }

      if (start < text.length()) parts.add(text.substring(start));

      String hex = "#ffffff";
      for (String part : parts) {
         if (HEX.matcher(part).matches()) {
            hex = part.substring(0, part.length() - 1);
         } else {
            TextColor color = TextColor.parse(hex).result().orElse(null);
            result.append(Text.literal(part).styled(style -> style.withColor(color)));
         }
      }

      return result;
   }
}
