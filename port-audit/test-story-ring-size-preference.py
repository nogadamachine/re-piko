#!/usr/bin/env python3
"""Compile and exercise the production story-ring preference getter locally."""
from pathlib import Path
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
pref_source = root / "extensions/instagram/src/main/java/app/morphe/extension/instagram/utils/Pref.java"
widget_source = root / "extensions/instagram/src/main/java/app/morphe/extension/instagram/settings/preference/widgets/EditTextPref.java"


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


production_method = extract_block(
    pref_source.read_text(),
    "public static float customiseStoryRingSize()",
)
widget_listener = extract_block(
    widget_source.read_text(),
    "public boolean onPreferenceChange(Preference preference, Object newValue)",
)
assert "helper.setValue(preference,newValue);" in widget_listener
assert "return true;" in widget_listener
assert all(token not in widget_listener for token in (
    "parseInt", "parseFloat", "isFinite", "> 0", ">= 1", "Math.max",
)), "generic EditTextPref unexpectedly gained positive-value validation"


with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)

    def write(relative, source):
        path = folder / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        return path

    sources = [
        write(
            "app/morphe/extension/crimera/settings/StringSetting.java",
            "package app.morphe.extension.crimera.settings; public final class StringSetting {}",
        ),
        write(
            "app/morphe/extension/instagram/settings/Settings.java",
            """
package app.morphe.extension.instagram.settings;
import app.morphe.extension.crimera.settings.StringSetting;
public final class Settings {
 public static final StringSetting CUSTOMISE_STORY_RING_SIZE = new StringSetting();
}
""",
        ),
        write(
            "app/morphe/extension/crimera/sharedPreference/SharedPref.java",
            """
package app.morphe.extension.crimera.sharedPreference;
import app.morphe.extension.crimera.settings.StringSetting;
public final class SharedPref {
 public static String value;
 public static String getStringPref(StringSetting ignored){return value;}
}
""",
        ),
        write(
            "app/morphe/extension/instagram/utils/Pref.java",
            """
package app.morphe.extension.instagram.utils;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.crimera.sharedPreference.SharedPref;
public final class Pref {
""" + production_method + "\n}\n",
        ),
        write(
            "StoryRingSizePreferenceTest.java",
            """
import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.utils.Pref;
public final class StoryRingSizePreferenceTest {
 static int checks;
 static void expect(String input,float expected){
  SharedPref.value=input;
  float actual=Pref.customiseStoryRingSize();
  checks++;
  if(Float.floatToIntBits(actual)!=Float.floatToIntBits(expected))
   throw new AssertionError("input="+input+" expected="+expected+" actual="+actual);
 }
 public static void main(String[] args){
  expect("0",100.0f);
  expect("-1",100.0f);
  expect("NaN",100.0f);
  expect("+Infinity",100.0f);
  expect("-Infinity",100.0f);
  expect(null,100.0f);
  expect("malformed",100.0f);
  expect("1",1.0f);
  expect("50",50.0f);
  expect("100",100.0f);
  expect("3.4028235E38",Float.MAX_VALUE);
  System.out.println(checks+" production story tray size preference checks passed; runtime UI is separate");
 }
}
""",
        ),
    ]
    subprocess.run(["javac", "--release", "17", "-d", str(folder), *map(str, sources)], check=True)
    subprocess.run(["java", "-cp", str(folder), "StoryRingSizePreferenceTest"], check=True)

print("generic EditTextPref stores numeric input without a positive-value check")
