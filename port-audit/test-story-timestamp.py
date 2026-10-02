"""Check live option changes and Korean output using the production Java formatter."""
import json
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
resources = ET.parse(root / 'patches/src/main/resources/addresources/values-ko-rKR/instagram/strings.xml')
strings = {e.get('name'): e.text for e in resources.getroot() if e.get('name', '').startswith('piko_story_timestamp_')}
with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)
    (folder / 'Pref.java').write_text('package app.morphe.extension.instagram.utils; public class Pref { public static String mode="default"; public static String customiseStoryTimestamp(){return mode;} }')
    cases = ''.join('case ' + json.dumps(k) + ': return ' + json.dumps(v) + ';' for k, v in strings.items())
    (folder / 'IgStr.java').write_text('package app.morphe.extension.instagram.utils; public class IgStr { public static String str(String key){switch(key){' + cases + 'default:throw new AssertionError(key);}}}')
    (folder / 'Logger.java').write_text('package app.morphe.extension.shared; public class Logger { public static void printException(java.util.function.Supplier<String> text, Exception e){throw new AssertionError(text.get(),e);} }')
    (folder / 'TimestampTest.java').write_text('''
import java.util.*;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.story.StoryTimestamp;
public class TimestampTest {
 static int checks;
 static void require(boolean v){checks++;if(!v)throw new AssertionError("check " + checks);}
 public static void main(String[] args){
  Locale.setDefault(Locale.KOREAN);TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
  long now = System.currentTimeMillis()/1000;
  require(StoryTimestamp.customiseStoryTimestamp(now)==null);
  Pref.mode="detailed";String detailed=StoryTimestamp.customiseStoryTimestamp(now);
  require(detailed.matches("[0-9]+월 [0-9]+일 .+요일 [0-9]{2}:[0-9]{2}:[0-9]{2}"));
  Pref.mode="posttime";require(StoryTimestamp.customiseStoryTimestamp(now).matches("[0-9]{2}:[0-9]{2}:[0-9]{2}"));
  Pref.mode="timeleft";require(StoryTimestamp.customiseStoryTimestamp(now).matches("[0-9]+시간 [0-9]+분 [0-9]+초 남음"));
  require(StoryTimestamp.customiseStoryTimestamp(now-86401).equals("만료됨"));
  Pref.mode="default";require(StoryTimestamp.customiseStoryTimestamp(now)==null);
  Pref.mode=null;require(StoryTimestamp.customiseStoryTimestamp(now)==null);
  System.out.println(checks+" timestamp checks passed; actual story UI is separate");
 }
}
''')
    source = root / 'extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/story/StoryTimestamp.java'
    subprocess.run(['javac', '-d', str(folder), str(source), *map(str, folder.glob('*.java'))], check=True)
    subprocess.run(['java', '-cp', str(folder), 'TimestampTest'], check=True)
