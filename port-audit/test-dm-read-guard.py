#!/usr/bin/env python3
"""Exercise the production DM guards with minimal local Android and Instagram stubs."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
dm = root / 'extensions/instagram/src/main/java/app/morphe/extension/instagram/patches/dm'

with tempfile.TemporaryDirectory() as temporary:
    folder = Path(temporary)

    def write(relative, source):
        path = folder / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        return path

    stubs = [
        write('android/content/Context.java', 'package android.content; public class Context {}'),
        write('android/os/SystemClock.java', 'package android.os; public class SystemClock { public static long elapsedRealtime(){return 1;} }'),
        write('android/util/Log.java', 'package android.util; public class Log { public static int d(String tag,String text){return 0;} }'),
        write('com/instagram/model/direct/DirectThreadKey.java', 'package com.instagram.model.direct; public class DirectThreadKey { public String A00; }'),
        write('com/instagram/common/session/UserSession.java', 'package com.instagram.common.session; public class UserSession {}'),
        write('app/morphe/extension/crimera/PikoUtils.java', 'package app.morphe.extension.crimera; public class PikoUtils { public static void logger(Object value){} }'),
        write('app/morphe/extension/crimera/ObjectBrowser.java', 'package app.morphe.extension.crimera; public class ObjectBrowser { public static void browseObject(android.content.Context context,Object value){} }'),
        write('app/morphe/extension/instagram/utils/Pref.java', '''
package app.morphe.extension.instagram.utils;
public class Pref {
 public static boolean anonymous, enabled=true;
 public static boolean viewDmAnonymously(){return anonymous;}
 public static boolean enableMarkChatAsReadOption(){return enabled;}
 public static boolean pikoDebug(){return false;}
}
'''),
        write('app/morphe/extension/instagram/utils/IgStr.java', 'package app.morphe.extension.instagram.utils; public class IgStr { public static String str(String value){return value;} }'),
        write('app/morphe/extension/instagram/entity/Entity.java', '''
package app.morphe.extension.instagram.entity;
public class Entity {
 public static int fieldReads;
 public Object getField(Object value,String name) throws Exception {fieldReads++;return null;}
 public Object getMethod(Object value,String name,Class<?>[] types,Object... args) throws Exception {return null;}
}
'''),
        write('app/morphe/extension/shared/MarkChatAsReadScope.java', '''
package app.morphe.extension.shared;
public class MarkChatAsReadScope {
 public interface Operation {void run() throws Exception;}
 public static boolean isActive(){return false;}
 public static void run(Operation operation) throws Exception {operation.run();}
}
'''),
        write('app/morphe/extension/shared/Utils.java', 'package app.morphe.extension.shared; public class Utils { public static void showToastShort(String value){} }'),
    ]
    test = write('app/morphe/extension/instagram/patches/dm/DmReadGuardTest.java', '''
package app.morphe.extension.instagram.patches.dm;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.utils.Pref;
public class DmReadGuardTest {
 static int checks;
 static void require(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  Object value=new Object();
  Pref.enabled=true; Pref.anonymous=false; Entity.fieldReads=0;
  require(!MarkChatAsRead.shouldBlockNativeRead(value,value,value,value,false));
  require(Entity.fieldReads==0);
  require(!MarkChatAsRead.shouldBlockNativeVisualRead(value,value));
  require(Entity.fieldReads==0);
  Pref.anonymous=true; Entity.fieldReads=0;
  require(MarkChatAsRead.shouldBlockNativeRead(value,value,value,value,false));
  require(Entity.fieldReads>0);
  Pref.enabled=false; Entity.fieldReads=0;
  require(MarkChatAsRead.shouldBlockNativeRead(value,value,value,value,false));
  require(Entity.fieldReads==0);
  System.out.println(checks+" production DM guard checks passed; device and server behavior is separate");
 }
}
''')
    sources = stubs + [test, dm / 'MarkChatAsRead.java', dm / 'PendingReadCache.java', dm / 'PendingVisualReads.java']
    subprocess.run(['javac', '-d', str(folder), *map(str, sources)], check=True)
    subprocess.run(['java', '-cp', str(folder),
                    'app.morphe.extension.instagram.patches.dm.DmReadGuardTest'], check=True)
