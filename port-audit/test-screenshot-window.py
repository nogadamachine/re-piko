#!/usr/bin/env python3
"""Exercise the production flag adapter against Android window test doubles."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/privacy/ScreenshotPatch.java'
with tempfile.TemporaryDirectory() as tmp:
    d = Path(tmp)
    (d / 'WindowManager.java').write_text('package android.view; public interface WindowManager { class LayoutParams { public static final int FLAG_SECURE=8192; } }')
    (d / 'Window.java').write_text('package android.view; public class Window { public int flags; public void setFlags(int value,int mask) { flags=(flags & ~mask) | (value & mask); } }')
    (d / 'Pref.java').write_text('package app.morphe.extension.instagram.utils; public class Pref { public static boolean enabled; public static boolean disableScreenshotDetection(){return enabled;} }')
    (d / 'ScreenshotWindowTest.java').write_text('''
import android.view.Window;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.privacy.ScreenshotPatch;
public class ScreenshotWindowTest {
 static int checks;
 static void eq(int expected,int actual) {checks++;if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
 public static void main(String[] args) {
  Window w=new Window(); w.flags=128;
  Pref.enabled=false; ScreenshotPatch.setWindowFlags(w,8192,8192); eq(8320,w.flags);
  Pref.enabled=true; ScreenshotPatch.setWindowFlags(w,8192,8192); eq(128,w.flags);
  Pref.enabled=false; ScreenshotPatch.setWindowFlags(w,8192,8192); eq(8320,w.flags);
  Pref.enabled=true; ScreenshotPatch.setWindowFlags(w,8192|1024,8192|1024); eq(128|1024,w.flags);
  ScreenshotPatch.setWindowFlags(w,0,1024); eq(128,w.flags);
  System.out.println(checks+" production window flag checks passed; device capture is tested separately");
 }
}
''')
    subprocess.run(['javac','-d',str(d),str(source),*[str(d / name) for name in ['WindowManager.java','Window.java','Pref.java','ScreenshotWindowTest.java']]],check=True)
    subprocess.run(['java','-cp',str(d),'ScreenshotWindowTest'],check=True)
