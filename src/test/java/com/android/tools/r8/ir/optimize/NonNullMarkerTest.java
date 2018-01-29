// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.NonNull;
import com.android.tools.r8.ir.nonnull.NonNullAfterArrayAccess;
import com.android.tools.r8.ir.nonnull.NonNullAfterFieldAccess;
import com.android.tools.r8.ir.nonnull.NonNullAfterInvoke;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import org.junit.Test;

public class NonNullMarkerTest extends AsmTestBase {
  private static final InternalOptions TEST_OPTIONS = new InternalOptions();

  private void buildAndTest(
      Class<?> testClass,
      MethodSignature signature,
      int expectedNumberOfNonNull)
      throws Exception {
    AndroidApp app = buildAndroidApp(asBytes(testClass));
    DexApplication dexApplication =
        new ApplicationReader(app, TEST_OPTIONS, new Timing("NonNullMarkerTest.appReader"))
            .read().toDirect();
    AppInfo appInfo = new AppInfo(dexApplication);
    DexInspector dexInspector = new DexInspector(appInfo.app);
    DexEncodedMethod foo = dexInspector.clazz(testClass.getName()).method(signature).getMethod();
    IRCode irCode = foo.buildIR(TEST_OPTIONS);
    checkCountOfNonNull(irCode, 0);

    NonNullMarker nonNullMarker = new NonNullMarker();

    nonNullMarker.addNonNull(irCode);
    assertTrue(irCode.isConsistentSSA());
    checkCountOfNonNull(irCode, expectedNumberOfNonNull);

    nonNullMarker.cleanupNonNull(irCode);
    assertTrue(irCode.isConsistentSSA());
    checkCountOfNonNull(irCode, 0);
  }

  private static void checkCountOfNonNull(IRCode code, int expectedOccurrences) {
    int count = 0;
    Instruction prev = null, curr = null;
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      prev = curr != null && !curr.isGoto() ? curr : prev;
      curr = it.next();
      if (curr instanceof NonNull) {
        // Make sure non-null is added to the right place.
        assertTrue(prev == null || NonNullMarker.throwsOnNullInput(prev));
        count++;
      }
    }
    assertEquals(count, expectedOccurrences);
  }

  @Test
  public void nonNullAfterSafeInvokes() throws Exception {
    MethodSignature signature =
        new MethodSignature("foo", "int", new String[]{"java.lang.String"});
    buildAndTest(NonNullAfterInvoke.class, signature, 1);
  }

  @Test
  public void nonNullAfterSafeArrayAccess() throws Exception {
    MethodSignature signature =
        new MethodSignature("foo", "int", new String[]{"java.lang.String[]"});
    buildAndTest(NonNullAfterArrayAccess.class, signature, 1);
  }

  @Test
  public void nonNullAfterSafeFieldAccess() throws Exception {
    MethodSignature signature = new MethodSignature("foo", "int",
        new String[]{"com.android.tools.r8.ir.nonnull.FieldAccessTest"});
    buildAndTest(NonNullAfterFieldAccess.class, signature, 1);
  }

  @Test
  public void avoidRedundantNonNull() throws Exception {
    MethodSignature signature = new MethodSignature("foo2", "int",
        new String[]{"com.android.tools.r8.ir.nonnull.FieldAccessTest"});
    buildAndTest(NonNullAfterFieldAccess.class, signature, 1);
  }

}
