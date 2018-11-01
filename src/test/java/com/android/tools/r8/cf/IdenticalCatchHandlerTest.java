// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Set;
import org.junit.Test;

public class IdenticalCatchHandlerTest extends TestBase {

  private static class TestClass {
    public void foo(Object a, Object b, Object c) {
      if (a == null) {
        try {
          System.out.println(b.toString());
        } catch (RuntimeException e) {
        }
      } else {
        try {
          System.out.println(c.toString());
        } catch (RuntimeException e) {
        }
      }
      System.out.println("Hello");
    }
  }

  @Test
  public void test() throws Exception {
    byte[] inputBytes = ToolHelper.getClassAsBytes(TestClass.class);
    AndroidApp inputApp =
        AndroidApp.builder()
            .addClassProgramData(inputBytes, Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .build();
    assertEquals(2, countCatchHandlers(inputApp));
    AndroidApp outputDexApp = ToolHelper.runR8(inputApp);
    assertEquals(1, countCatchHandlers(outputDexApp));
    AndroidApp outputCfApp =
        ToolHelper.runR8WithProgramConsumer(inputApp, ClassFileConsumer.emptyConsumer());
    assertEquals(1, countCatchHandlers(outputCfApp));
  }

  private int countCatchHandlers(AndroidApp inputApp) throws Exception {
    CodeInspector inspector = new CodeInspector(inputApp);
    DexClass dexClass = inspector.clazz(TestClass.class).getDexClass();
    Code code = dexClass.virtualMethods()[0].getCode();
    if (code.isCfCode()) {
      CfCode cfCode = code.asCfCode();
      Set<CfLabel> targets = Sets.newIdentityHashSet();
      for (CfTryCatch tryCatch : cfCode.getTryCatchRanges()) {
        targets.addAll(tryCatch.targets);
      }
      return targets.size();
    } else if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      IntSet targets = new IntOpenHashSet();
      for (Try aTry : dexCode.tries) {
        targets.add(aTry.handlerOffset);
      }
      return targets.size();
    } else {
      throw new Unimplemented(code.getClass().getName());
    }
  }
}
