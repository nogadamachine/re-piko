#!/usr/bin/env python3
"""Exercise the production Java hook with mutable preference and logger test doubles."""
from pathlib import Path
import subprocess,tempfile,sys
root=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]) if len(sys.argv)>1 else root/'extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/dm/EphemeralMediaPatch.java'
with tempfile.TemporaryDirectory() as tmp:
 d=Path(tmp)
 (d/'Entity.java').write_text('package app.morphe.extension.instagram.entity; public class Entity {}')
 (d/'Pref.java').write_text('package app.morphe.extension.instagram.utils; public class Pref { public static boolean enabled=true; public static boolean makeEphemeralMediaPermanent(){return enabled;} }')
 (d/'Logger.java').write_text('package app.morphe.extension.shared; public class Logger { public static void printException(java.util.function.Supplier<String> message){ throw new AssertionError(message.get()); } }')
 (d/'EphemeralMediaTest.java').write_text('''
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.patches.dm.EphemeralMediaPatch;
public class EphemeralMediaTest {
 static int checks=0;
 static void eq(String expected, String actual) { checks++; if(!java.util.Objects.equals(expected,actual))throw new AssertionError("expected="+expected+" actual="+actual); }
 public static void main(String[] args) {
  long future=System.currentTimeMillis()/1000+3600;
  Pref.enabled=true; eq("permanent",EphemeralMediaPatch.makeEphemeralMediaPermanent(future,"once"));
  Pref.enabled=false; eq("once",EphemeralMediaPatch.makeEphemeralMediaPermanent(future,"once"));
  Pref.enabled=true; eq("permanent",EphemeralMediaPatch.makeEphemeralMediaPermanent(future,"twice"));
  eq("once",EphemeralMediaPatch.makeEphemeralMediaPermanent(1L,"once"));
  eq("once",EphemeralMediaPatch.makeEphemeralMediaPermanent(null,"once"));
  eq(null,EphemeralMediaPatch.makeEphemeralMediaPermanent(future,null));
  eq("permanent",EphemeralMediaPatch.makeEphemeralMediaPermanent(future,"permanent"));
  eq("permanent",EphemeralMediaPatch.makeEphemeralMediaPermanent(Long.MAX_VALUE,"once"));
  System.out.println(checks+" Java hook checks passed; device/server behavior is separate");
 }
}
''')
 subprocess.run(['javac','-d',str(d),str(source),str(d/'Entity.java'),str(d/'Pref.java'),str(d/'Logger.java'),str(d/'EphemeralMediaTest.java')],check=True)
 subprocess.run(['java','-cp',str(d),'EphemeralMediaTest'],check=True)
