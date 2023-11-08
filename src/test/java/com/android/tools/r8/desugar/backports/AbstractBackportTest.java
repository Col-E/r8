// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

abstract class AbstractBackportTest extends TestBase {
  protected final TestParameters parameters;
  private final ClassInfo targetClass;
  private final ClassInfo testClass;
  private final Path testJar;
  private final String testClassName;
  private final Int2IntSortedMap invokeStaticCounts = new Int2IntAVLTreeMap();
  private final Set<String> ignoredInvokes = new HashSet<>();

  private static class ClassInfo {
    private final String name;
    private final Class<?> clazz;
    private final List<byte[]> classFileData;

    private ClassInfo(String name) {
      this.name = name;
      this.clazz = null;
      this.classFileData = null;
    }

    private ClassInfo(Class<?> clazz) {
      this.name = clazz.getName();
      this.clazz = clazz;
      this.classFileData = null;
    }

    private ClassInfo(byte[] classFileData) {
      this(ImmutableList.of(classFileData));
    }

    private ClassInfo(List<byte[]> classFileData) {
      this.name = extractClassName(classFileData.get(0));
      this.clazz = null;
      this.classFileData = classFileData;
    }

    String getName() {
      return name;
    }

    TestBuilder<?, ?> addAsProgramClass(TestBuilder<?, ?> builder) throws IOException {
      if (clazz != null) {
        return builder.addProgramClassesAndInnerClasses(clazz);
      } else {
        return builder.addProgramClassFileData(classFileData);
      }
    }
  }

  AbstractBackportTest(TestParameters parameters, Class<?> targetClass,
      Class<?> testClass) {
    this(parameters, new ClassInfo(targetClass), new ClassInfo(testClass), null, null);
  }

  AbstractBackportTest(
      TestParameters parameters, String className, List<byte[]> testClassFileData) {
    this(parameters, new ClassInfo(className), new ClassInfo(testClassFileData), null, null);
  }

  AbstractBackportTest(
      TestParameters parameters, byte[] targetClassFileData, List<byte[]> testClassFileData) {
    this(
        parameters,
        new ClassInfo(targetClassFileData),
        new ClassInfo(testClassFileData),
        null,
        null);
  }

  AbstractBackportTest(TestParameters parameters, Class<?> targetClass,
      Path testJar, String testClassName) {
    this(parameters, new ClassInfo(targetClass), null, testJar, testClassName);
  }

  private AbstractBackportTest(
      TestParameters parameters,
      ClassInfo targetClass,
      ClassInfo testClass,
      Path testJar,
      String testClassName) {
    this.parameters = parameters;
    this.targetClass = targetClass;
    this.testClass = testClass;
    this.testJar = testJar;

    if (testClass != null) {
      assert testJar == null;
      assert testClassName == null;
      this.testClassName = testClass.getName();
    } else {
      assert testJar != null;
      assert testClassName != null;
      this.testClassName = testClassName;
    }

    // Assume all method calls will be rewritten on the lowest API level.
    invokeStaticCounts.put(AndroidApiLevel.B.getLevel(), 0);
  }

  void registerTarget(AndroidApiLevel apiLevel, int invokeStaticCount) {
    invokeStaticCounts.put(apiLevel.getLevel(), invokeStaticCount);
  }

  private int getTargetInvokesCount(AndroidApiLevel apiLevel) {
    int key = invokeStaticCounts.headMap(apiLevel.getLevel() + 1).lastIntKey();
    return invokeStaticCounts.get(key);
  }

  void ignoreInvokes(String methodName) {
    ignoredInvokes.add(methodName);
  }

  protected void configureProgram(TestBuilder<?, ?> builder) throws IOException {
    builder.addProgramClasses(MiniAssert.class, IgnoreInvokes.class);
    if (testClass != null) {
      testClass.addAsProgramClass(builder);
    } else {
      builder.addProgramFiles(testJar);
    }
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::configureProgram)
        .run(parameters.getRuntime(), testClassName)
        .assertSuccess();
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    if (diagnostics.getWarnings().isEmpty()) {
      diagnostics.assertNoMessages();
      return;
    }
    // When compiling with an old android.jar some tests refer to non-present types.
    // Check only java.util types are missing and that none of them are about the target
    // type that is being backported.
    diagnostics
        .assertOnlyWarnings()
        .assertAllWarningsMatch(diagnosticType(InterfaceDesugarMissingTypeDiagnostic.class))
        .assertAllWarningsMatch(diagnosticMessage(containsString("java.util")))
        .assertNoWarningsMatch(diagnosticMessage(containsString(targetClass.getName())));
  }

  @Test
  public void testD8() throws Exception {
    testD8(D8TestRunResult::assertSuccess);
  }

  public void testD8(ThrowingConsumer<D8TestRunResult, RuntimeException> runResultConsumer)
      throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .setMinApi(parameters)
        .apply(this::configureProgram)
        .setIncludeClassesChecksum(true)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .inspect(this::assertDesugaring)
        .run(parameters.getRuntime(), testClassName)
        .apply(runResultConsumer);
  }

  @Test
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(Backend.CF)
        .setMinApi(parameters)
        .apply(this::configureProgram)
        .setIncludeClassesChecksum(true)
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), testClassName)
        .assertSuccess()
        .inspect(this::assertDesugaring);
  }

  private void assertDesugaring(CodeInspector inspector) {
    ClassSubject testSubject = inspector.clazz(testClassName);
    assertThat(testSubject, isPresent());

    List<InstructionSubject> javaInvokeStatics = testSubject.allMethods()
        .stream()
        // Do not count @IgnoreInvokes-annotated methods.
        .filter(i -> !i.annotation(IgnoreInvokes.class.getName()).isPresent())
        .flatMap(MethodSubject::streamInstructions)
        .filter(InstructionSubject::isInvoke)
        .filter(is -> is.getMethod().holder.toSourceString().equals(targetClass.getName()))
        // Do not count invokes if explicitly ignored.
        .filter(is -> !ignoredInvokes.contains(is.getMethod().name.toString()))
        .collect(toList());

    AndroidApiLevel apiLevel = parameters.getApiLevel();
    long expectedTargetInvokes = getTargetInvokesCount(apiLevel);
    long actualTargetInvokes = javaInvokeStatics.size();
    assertEquals("Expected "
        + expectedTargetInvokes
        + " invokes on "
        + targetClass.getName()
        + " but found "
        + actualTargetInvokes
        + ": "
        + javaInvokeStatics, expectedTargetInvokes, actualTargetInvokes);
  }

  public String getTestClassName() {
    return testClassName;
  }

  /** JUnit {@link Assert} isn't available in the VM runtime. This is a mini mirror of its API. */
  static abstract class MiniAssert {
    static void assertTrue(boolean value) {
      assertEquals(true, value);
    }

    static void assertFalse(boolean value) {
      assertEquals(false, value);
    }

    static void assertEquals(boolean expected, boolean actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(int expected, int actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(long expected, long actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(float expected, float actual) {
      if (Float.compare(expected, actual) != 0) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(double expected, double actual) {
      if (Double.compare(expected, actual) != 0) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(Object expected, Object actual) {
      if (expected != actual && (expected == null || !expected.equals(actual))) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertSame(Object expected, Object actual) {
      if (expected != actual) {
        throw new AssertionError(
            "Expected <" + expected + "> to be same instance as <" + actual + '>');
      }
    }

    static void fail(String message) {
      throw new AssertionError("Failed: " + message);
    }
  }
}
