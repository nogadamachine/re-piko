#!/usr/bin/env python3
"""Exercise the production image-selection preference body and modeled native scorer."""
from pathlib import Path
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
pref_source = root / "extensions/instagram/src/main/java/app/morphe/extension/instagram/utils/Pref.java"
patch_source = root / "patches/src/main/kotlin/app/crimera/patches/instagram/misc/improveImageViewing/ImproveImageViewingPatch.kt"


def extract_block(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError(f"unterminated block: {signature}")


pref_text = pref_source.read_text()
patch_text = patch_source.read_text()
selection_method = extract_block(
    pref_text, "public static int improveImageSelectionTarget(int defaultSize)"
)
primitive_metrics_method = extract_block(
    pref_text, "public static int improveImageViewing(int defaultSize)"
)
boxed_metrics_method = extract_block(
    pref_text, "public static Integer improveImageViewing(Integer defaultSize)"
)

assert "MAX_IMAGE_SELECTION_TARGET = 2_045_222_521" in pref_text
assert "improveImageSelectionTarget(I)I" in patch_text
assert "addInstructions(0, PREF_CALL)" in patch_text
assert "improveImageViewing(I)I" not in patch_text
assert "improveImageViewing(Ljava/lang/Integer;)Ljava/lang/Integer;" in patch_text


with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)

    def write(relative, source):
        path = folder / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        return path

    sources = [
        write(
            "app/morphe/extension/crimera/sharedPreference/SharedPref.java",
            """
package app.morphe.extension.crimera.sharedPreference;
public final class SharedPref {
 public static boolean enabled;
 public static Boolean getBooleanPref(Object ignored){return enabled;}
}
""",
        ),
        write(
            "app/morphe/extension/instagram/settings/Settings.java",
            """
package app.morphe.extension.instagram.settings;
public final class Settings {public static final Object IMPROVE_IMAGE_VIEWING=new Object();}
""",
        ),
        write(
            "app/morphe/extension/instagram/utils/Pref.java",
            """
package app.morphe.extension.instagram.utils;
import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.Settings;
public final class Pref {
 private static final int MAX_IMAGE_SIZE=4096;
 private static final int MAX_IMAGE_SELECTION_TARGET=2_045_222_521;
""" + primitive_metrics_method + "\n" + selection_method + "\n" + boxed_metrics_method + "\n}\n",
        ),
        write(
            "ImageSelectionTest.java",
            """
import java.util.*;
import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.utils.Pref;
public final class ImageSelectionTest {
 static int checks;
 static void require(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 static final class Candidate {
  final String name;final int height;final int width;
  Candidate(String name,int height,int width){this.name=name;this.height=height;this.width=width;}
 }
 static Candidate select(boolean squareOnly,List<Candidate> candidates,int originalTarget){
  int target=Pref.improveImageSelectionTarget(originalTarget);
  int bestScore=Integer.MAX_VALUE;Candidate best=null;
  for(Candidate candidate:candidates){
   boolean square=candidate.height==candidate.width;
   if(squareOnly && !square)continue;
   int score=Math.abs(target+target/20-candidate.width);
   if(score<bestScore || (best!=null && score==bestScore && candidate.width<best.width)){
    best=candidate;bestScore=score;
   }
  }
  return best;
 }
 static void selected(Candidate expected,boolean squareOnly,List<Candidate> values,int target){
  require(select(squareOnly,values,target)==expected);
 }
 public static void main(String[] args){
  Candidate large=new Candidate("large3000",3000,3000);
  Candidate small=new Candidate("small500",500,500);
  Candidate wide=new Candidate("wide1000x500",500,1000);
  Candidate medium5000=new Candidate("medium5000",5000,5000);
  Candidate large8192=new Candidate("large8192",8192,8192);

  SharedPref.enabled=false;
  require(Pref.improveImageSelectionTarget(500)==500);
  require(Pref.improveImageViewing(270)==270);
  require(Pref.improveImageViewing(Integer.valueOf(720)).intValue()==720);
  selected(small,false,Arrays.asList(large,small),500);
  selected(small,false,Arrays.asList(small,large),500);
  selected(large,true,Arrays.asList(wide,large),500);

  SharedPref.enabled=true;
  int selectionTarget=Pref.improveImageSelectionTarget(500);
  require(selectionTarget==2_045_222_521);
  require(selectionTarget+selectionTarget/20==Integer.MAX_VALUE);
  require(Pref.improveImageViewing(270)==4096);
  require(Pref.improveImageViewing(Integer.valueOf(720)).intValue()==4096);
  selected(large,false,Arrays.asList(large,small),500);
  selected(large,false,Arrays.asList(small,large),500);
  selected(large,true,Arrays.asList(wide,large),500);
  selected(large8192,false,Arrays.asList(medium5000,large8192),500);
  selected(large8192,false,Arrays.asList(large8192,medium5000),500);
  selected(large,false,Collections.singletonList(large),500);
  selected(null,false,Collections.emptyList(),500);
  require(wide.height==500 && wide.width==1000);
  require(large8192.height==8192 && large8192.width==8192);
  System.out.println(checks+" image selection checks passed");
 }
}
""",
        ),
    ]
    subprocess.run(["javac", "--release", "17", "-d", str(folder), *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", str(folder), "ImageSelectionTest"], check=True)
