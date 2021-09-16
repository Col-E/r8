// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import java.util.Arrays;
import java.util.List;

public class ActualIdentityStackTrace extends ActualBotStackTraceBase {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.AssertionError: Unexpected failure: 'I( 8207) Could not find method"
            + " privateinterfacemethods.I$-CC.$default$iFoo, referenced from method"
            + " privateinterfacemethods.I$-CC.$default$dFoo  (dalvikvm)",
        "    W( 8207) VFY: unable to resolve static method 11:"
            + " Lprivateinterfacemethods/I$-CC;.$default$iFoo"
            + " (Lprivateinterfacemethods/I;Z)Ljava/lang/String;  (dalvikvm)",
        "    I( 8207) Could not find method privateinterfacemethods.I$-CC.$default$iFoo,"
            + " referenced from method privateinterfacemethods.I$-CC.lambda$lFoo$0  (dalvikvm)",
        "    W( 8207) VFY: unable to resolve static method 11:"
            + " Lprivateinterfacemethods/I$-CC;.$default$iFoo"
            + " (Lprivateinterfacemethods/I;Z)Ljava/lang/String;  (dalvikvm)",
        "    I( 8207) Could not find method privateinterfacemethods.I$-CC.$default$iFoo,"
            + " referenced from method privateinterfacemethods.I$-CC.sFoo  (dalvikvm)",
        "    W( 8207) VFY: unable to resolve static method 11:"
            + " Lprivateinterfacemethods/I$-CC;.$default$iFoo"
            + " (Lprivateinterfacemethods/I;Z)Ljava/lang/String;  (dalvikvm)",
        "    W( 8207) Exception Ljava/lang/NoSuchMethodError; thrown while initializing"
            + " Lprivateinterfacemethods/I;  (dalvikvm)",
        "    W( 8207) threadid=1: thread exiting with uncaught exception (group=0xf653e180) "
            + " (dalvikvm)",
        "    java.lang.ExceptionInInitializerError",
        "    \tat"
            + " privateinterfacemethods.PrivateInterfaceMethods.main(PrivateInterfaceMethods.java:9)",
        "    \tat dalvik.system.NativeStart.main(Native Method)",
        "    Caused by: java.lang.NoSuchMethodError: privateinterfacemethods.I$-CC.$default$iFoo",
        "    \tat privateinterfacemethods.I$-CC.sFoo(I.java:40)",
        "    \tat privateinterfacemethods.I$-CC.access$000(I.java:28)",
        "    \tat privateinterfacemethods.I$1.<init>(I.java:31)",
        "    \tat privateinterfacemethods.I.<clinit>(I.java:30)",
        "    \t... 2 more");
  }

  @Override
  public String mapping() {
    return r8MappingFromGitSha("82710798b61fd70910d76d23a71e436356becb66");
  }

  @Override
  public List<String> retracedStackTrace() {
    return obfuscatedStackTrace();
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return obfuscatedStackTrace();
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
