// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionOffsetSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.RangeSubject;
import com.android.tools.r8.utils.codeinspector.TryCatchSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringInMonitorTestMain {
  static final Object lock = new Object();

  private static int foo = 0;

  @NeverInline
  private static synchronized void sync() throws OutOfMemoryError {
    String bar = foo == 0 ? "bar" : "";
    if (bar == "") {
      System.out.println("bar");
      throw new OutOfMemoryError();
    }
  }

  @NeverInline
  private static void oom() throws OutOfMemoryError {
    System.out.println("oom");
    if (System.currentTimeMillis() > 0) {
      throw new OutOfMemoryError();
    }
    System.out.println("oom");
    System.out.println("this-string-will-not-be-loaded.");
    System.out.println("this-string-will-not-be-loaded.");
  }

  public static void main(String[] args) {
    try {
      synchronized (lock) {
        System.out.println("1st sync");
        sync();
        oom();
        System.out.println("1st sync");
      }
    } catch (OutOfMemoryError oom) {
      // Pretend to recover from OOM
      synchronized (lock) {
        System.out.println("2nd sync");
      }
    }
  }
}

@RunWith(Parameterized.class)
public class StringInMonitorTest extends TestBase {
  private final Backend backend;
  private static final Class<?> MAIN = StringInMonitorTestMain.class;
  private static final List<Class<?>> CLASSES = ImmutableList.of(NeverInline.class, MAIN);
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "1st sync",
      "oom",
      "2nd sync"
  );

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringInMonitorTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(
      TestRunResult result,
      int expectedConstStringCount1,
      int expectedConstStringCount2,
      int expectedConstStringCount3) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());

    long count = Streams.stream(mainMethod.iterateInstructions(
        i -> i.isConstString("1st sync", JumboStringMode.ALLOW))).count();
    assertEquals(expectedConstStringCount1, count);

    // TODO(b/122302789): CfInstruction#getOffset()
    if (backend == Backend.DEX) {
      Iterator<InstructionSubject> constStringIterator =
          mainMethod.iterateInstructions(i -> i.isConstString(JumboStringMode.ALLOW));
      // All const-string's in main(...) should be covered by try (or synthetic catch-all) region.
      while (constStringIterator.hasNext()) {
        InstructionSubject constString = constStringIterator.next();
        InstructionOffsetSubject offsetSubject = constString.getOffset(mainMethod);
        // const-string of interest is indirectly covered. See b/122285813 for reference.
        Iterator<TryCatchSubject> catchAllIterator =
            mainMethod.iterateTryCatches(TryCatchSubject::hasCatchAll);
        boolean covered = false;
        while (catchAllIterator.hasNext()) {
          RangeSubject tryRange = catchAllIterator.next().getRange();
          covered |= tryRange.includes(offsetSubject);
          if (covered) {
            break;
          }
        }
        assertTrue(covered);
      }
    }

    MethodSubject sync = mainClass.uniqueMethodWithName("sync");
    assertThat(sync, isPresent());
    count = Streams.stream(sync.iterateInstructions(
        i -> i.isConstString("", JumboStringMode.ALLOW))).count();
    assertEquals(expectedConstStringCount2, count);

    // In CF, we don't explicitly add monitor-{enter|exit} and catch-all for synchronized methods.
    if (backend == Backend.DEX) {
      Iterator<InstructionSubject> constStringIterator =
          sync.iterateInstructions(i -> i.isConstString(JumboStringMode.ALLOW));
      // All const-string's in sync() should be covered by the synthetic catch-all regions.
      while (constStringIterator.hasNext()) {
        InstructionSubject constString = constStringIterator.next();
        InstructionOffsetSubject offsetSubject = constString.getOffset(mainMethod);
        Iterator<TryCatchSubject> catchAllIterator =
            sync.iterateTryCatches(TryCatchSubject::hasCatchAll);
        boolean covered = false;
        while (catchAllIterator.hasNext()) {
          RangeSubject tryRange = catchAllIterator.next().getRange();
          covered |= tryRange.includes(offsetSubject);
          if (covered) {
            break;
          }
        }
        assertTrue(covered);
      }
    }

    MethodSubject oom = mainClass.uniqueMethodWithName("oom");
    assertThat(oom, isPresent());
    count = Streams.stream(oom.iterateInstructions(
        i -> i.isConstString("this-string-will-not-be-loaded.", JumboStringMode.ALLOW))).count();
    assertEquals(expectedConstStringCount3, count);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", backend == Backend.DEX);
    TestRunResult result = testForD8()
        .debug()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2, 2, 1);

    result = testForD8()
        .release()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2, 2, 1);
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    // Due to the different behavior regarding constant canonicalization.
    int expectedConstStringCount3 = backend == Backend.CF ? 2 : 1;
    test(result, 2, 2, expectedConstStringCount3);
  }

}
