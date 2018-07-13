// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.bridgestokeep;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

public class KeepNonVisibilityBridgeMethodsTest extends TestBase {

  private String keepMainAllowAccessModification(Class clazz, boolean obfuscate) {
    return "-keep public class " + clazz.getCanonicalName() + " {\n"
        + "  public static void main(java.lang.String[]);\n"
        + "}\n"
        + "-allowaccessmodification\n"
        + (obfuscate ? "-printmapping" : "-dontobfuscate\n");
  }

  private void run(boolean obfuscate) throws Exception {
    List<Class> classes = ImmutableList.of(
        DataAdapter.class,
        SimpleDataAdapter.class,
        ObservableList.class,
        SimpleObservableList.class,
        Main.class);
    String proguardConfig = keepMainAllowAccessModification(Main.class, obfuscate);
    DexInspector inspector = new DexInspector(compileWithR8(classes, proguardConfig));

    // The bridge for registerObserver cannot be removed.
    Method registerObserver =
        SimpleDataAdapter.class.getMethod("registerObserver", DataAdapter.Observer.class);
    MethodSubject subject = inspector.method(registerObserver);
    assertTrue(subject.isPresent());
    // The method is there, but it might be unmarked as a bridge if
    // another method is inlined into it.
    // assertTrue(subject.isBridge());
  }

  @Test
  public void testWithObfuscation() throws Exception {
    run(true);
  }

  @Test
  public void testWithoutObfuscation() throws Exception {
    run(false);
  }
}
