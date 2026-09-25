package dev.sherii.yuio.manager;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import org.meteordev.starscript.Section;
import org.meteordev.starscript.StandardLib;
import org.meteordev.starscript.Starscript;
import org.meteordev.starscript.compiler.Compiler;
import org.meteordev.starscript.compiler.Parser;
import org.meteordev.starscript.compiler.Parser.Result;
import org.meteordev.starscript.utils.StarscriptError;

public final class StarscriptManager extends StarscriptTextBoxRenderer {
   private final Starscript ss = new Starscript();

   public StarscriptManager() {
      StandardLib.init(this.ss);
   }

   public final Starscript getSs() {
      return this.ss;
   }

   public final String parse(String text) {
      Section section = null;
      Result result = Parser.parse(text);

      if (result.hasErrors()) {
         section = new Section(0, result.errors.get(0).toString());
      } else {
         try {
            section = this.ss.run(Compiler.compile(result));
         } catch (StarscriptError e) {
            section = new Section(0, e.getMessage());
         }
      }

      return section != null && section.text != null ? section.text : text;
   }

   public List<String> getCompletions(String text, int position) {
      List<String> completions = new ArrayList<>();
      this.ss.getCompletions(text, position, (completion, function) ->
         completions.add(function ? completion + "(" : completion));
      completions.sort(String.CASE_INSENSITIVE_ORDER);
      return completions;
   }
}
