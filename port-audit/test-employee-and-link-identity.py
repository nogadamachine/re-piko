#!/usr/bin/env python3
"""Exercise the production employee flag and external identity method bodies."""
from pathlib import Path
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
hook_source = root / "extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/devFlags/HookFlags.java"
links_source = root / "extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/Links.java"


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


hook_text = hook_source.read_text()
links_text = links_source.read_text()
employee_method = extract_block(hook_text, "private static void employeeOptionsFlags()")
signature_method = extract_block(links_text, "public static boolean signatureCheck(Object appIdentityObject)")
assert "BOOL_FLAGS.remove(\"28538::0\")" in employee_method
assert "META_PACKAGES" not in links_text


with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)

    def write(relative, source):
        path = folder / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        return path

    sources = [
        write(
            "app/morphe/extension/instagram/utils/Pref.java",
            """
package app.morphe.extension.instagram.utils;
public final class Pref {
 public static boolean employee;
 public static boolean enableEmployeeOptions(){return employee;}
}
""",
        ),
        write(
            "app/morphe/extension/instagram/patches/devFlags/HookFlags.java",
            """
package app.morphe.extension.instagram.patches.devFlags;
import java.util.HashMap;
import java.util.Map;
import app.morphe.extension.instagram.utils.Pref;
public final class HookFlags {
 private static final Map<String,Boolean> BOOL_FLAGS=new HashMap<>();
""" + employee_method + """
 public static void applyEmployee(){employeeOptionsFlags();}
 public static void applyRecommended(Boolean value){if(value!=null)BOOL_FLAGS.put("28538::0",value);}
 public static Boolean employeeOverride(){return BOOL_FLAGS.get("28538::0");}
 public static boolean resolve(boolean nativeValue){Boolean value=employeeOverride();return value==null?nativeValue:value;}
 public static void putUnrelated(){BOOL_FLAGS.put("other",false);}
 public static boolean hasUnrelated(){return BOOL_FLAGS.containsKey("other");}
}
""",
        ),
        write(
            "android/content/Context.java",
            """
package android.content;
public class Context {
 private final String packageName;
 public Context(String packageName){this.packageName=packageName;}
 public String getPackageName(){return packageName;}
}
""",
        ),
        write(
            "app/morphe/extension/shared/Utils.java",
            """
package app.morphe.extension.shared;
public final class Utils {
 public static android.content.Context context=new android.content.Context("com.instagram.android.exampleclone");
 public static android.content.Context getContext(){return context;}
}
""",
        ),
        write(
            "app/morphe/extension/shared/Logger.java",
            """
package app.morphe.extension.shared;
public final class Logger {
 public interface LogMessage {String get();}
 public static int caught;
 public static void printException(LogMessage text,Exception error){caught++;}
}
""",
        ),
        write(
            "app/morphe/extension/instagram/entity/Entity.java",
            """
package app.morphe.extension.instagram.entity;
public final class Entity {
 public static final Object MALFORMED=new Object();
 private final Object value;
 public Entity(Object value){this.value=value;}
 public Object getField(String name) throws Exception {
  if(value==null || value==MALFORMED)throw new Exception("malformed");
  return value;
 }
}
""",
        ),
        write(
            "app/morphe/extension/instagram/patches/Links.java",
            """
package app.morphe.extension.instagram.patches;
import java.util.List;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
public final class Links {
""" + signature_method + "\n}\n",
        ),
        write(
            "EmployeeAndIdentityTest.java",
            """
import java.util.*;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.patches.Links;
import app.morphe.extension.instagram.patches.devFlags.HookFlags;
import app.morphe.extension.instagram.utils.Pref;
public final class EmployeeAndIdentityTest {
 static int checks;
 static void require(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 static void identity(Object value,boolean expected){
  boolean actual;
  try{actual=Links.signatureCheck(value);}catch(Throwable error){throw new AssertionError("exception escaped",error);}
  require(actual==expected);
 }
 public static void main(String[] args){
  Pref.employee=true;HookFlags.applyEmployee();require(Boolean.TRUE.equals(HookFlags.employeeOverride()));
  require(HookFlags.resolve(false));
  Pref.employee=false;HookFlags.applyEmployee();require(HookFlags.employeeOverride()==null);
  require(HookFlags.resolve(true));require(!HookFlags.resolve(false));
  Pref.employee=true;HookFlags.applyEmployee();Pref.employee=false;HookFlags.applyEmployee();
  require(HookFlags.employeeOverride()==null);require(HookFlags.resolve(true));
  HookFlags.putUnrelated();HookFlags.applyEmployee();require(HookFlags.hasUnrelated());
  HookFlags.applyRecommended(Boolean.FALSE);require(Boolean.FALSE.equals(HookFlags.employeeOverride()));
  Pref.employee=false;HookFlags.applyEmployee();HookFlags.applyRecommended(Boolean.TRUE);
  require(Boolean.TRUE.equals(HookFlags.employeeOverride()));

  identity(List.of("com.instagram.android.exampleclone"),true);
  identity(List.of("com.instagram.android"),false);
  identity(List.of("com.whatsapp"),false);
  identity(List.of("example.foreign"),false);
  identity(null,false);
  identity(Collections.emptyList(),false);
  identity(Arrays.asList("com.instagram.android.exampleclone","example.foreign"),false);
  identity(Arrays.asList((String)null),false);
  identity(Arrays.asList((Object)Integer.valueOf(7)),false);
  identity("not a package list",false);
  identity(Entity.MALFORMED,false);
  System.out.println(checks+" employee and external identity checks passed");
 }
}
""",
        ),
    ]
    subprocess.run(["javac", "--release", "17", "-d", str(folder), *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", str(folder), "EmployeeAndIdentityTest"], check=True)
