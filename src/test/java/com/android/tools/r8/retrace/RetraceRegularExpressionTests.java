// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.Retrace.DEFAULT_REGULAR_EXPRESSION;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.retrace.stacktraces.InlineFileNameStackTrace;
import com.android.tools.r8.retrace.stacktraces.RetraceAssertionErrorStackTrace;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceRegularExpressionTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceRegularExpressionTests(TestParameters parameters) {}

  @Test
  public void ensureNotMatchingOnLiteral() {
    runRetraceTest(
        "foo\\%c",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("foocom.android.tools.r8.a");
          }

          @Override
          public String mapping() {
            return "com.android.tools.r8.R8 -> com.android.tools.r8.a:";
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("foocom.android.tools.r8.a");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchMultipleTypeNamesOnLine() {
    // TODO(b/171691292): We are not supporting having multiple class matches for.
    runRetraceTest(
        "%c\\s%c\\s%c",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.a.a b.b.b c.c.c");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "AA.AA.AA -> a.a.a:", "BB.BB.BB -> b.b.b:", "CC.CC.CC -> c.c.c:");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("AA.AA.AA b.b.b c.c.c");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchMultipleSlashNamesOnLine() {
    // TODO(b/171691292): We are not supporting having multiple class matches for.
    runRetraceTest(
        "%C\\s%C\\s%C",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a/a/a b/b/b c/c/c");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("AA.AA -> a.a.a:", "BB.BB -> b.b.b:", "CC.CC -> c.c.c:");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("AA/AA b/b/b c/c/c");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchMethodNameInNoContext() {
    runRetraceTest(
        "a.b.c.%m",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  void foo() -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchMethodNameInContext() {
    runRetraceTest(
        "%c.%m",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  void foo() -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchUnknownMethodNameInContext() {
    runRetraceTest(
        "%c.%m",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  void foo() -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.a");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchQualifiedMethodInTrace() {
    runRetraceTest(DEFAULT_REGULAR_EXPRESSION, new InlineFileNameStackTrace());
  }

  @Test
  public void matchFieldNameInNoContext() {
    runRetraceTest(
        "a.b.c.%f",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  int foo -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchFieldNameInContext() {
    runRetraceTest(
        "%c.%f",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  int foo -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchUnknownFieldNameInContext() {
    runRetraceTest(
        "%c.%f",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  boolean foo -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.a");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchQualifiedFieldInTrace() {
    runRetraceTest(
        "%c\\.%f\\(%s\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return Collections.singletonList("a.b.c.d(Main.dummy)");
          }

          @Override
          public List<String> retracedStackTrace() {
            return Collections.singletonList("foo.Bar$Baz.baz(Bar.dummy)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.naming.retrace.Main -> a.b.c:",
                "    int foo.Bar$Baz.baz -> d");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchSourceFileInContext() {
    runRetraceTest(
        "%c\\(%s\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c(SourceFile)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  boolean foo -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8(R8.java)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchSourceFileInNoContext() {
    runRetraceTest(
        "%c\\(%s\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.d(SourceFile)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  boolean foo -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("a.b.d(d.java)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testLineNumberInMethodContext() {
    runRetraceTest(
        "%c\\.%m\\(%l\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(3)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.R8 -> a.b.c:", "  3:3:boolean foo():7 -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo(7)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testNoLineNumberInMethodContext() {
    runRetraceTest(
        "%c\\.%m\\(%l\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a()");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.R8 -> a.b.c:", "  3:3:boolean foo():7 -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo()");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testNotFoundLineNumberInMethodContext() {
    runRetraceTest(
        "%c\\.%m\\(%l\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(42)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.R8 -> a.b.c:", "  3:3:boolean foo():7 -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo(42)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testNotFoundLineNumberInNoLineNumberMappingMethodContext() {
    runRetraceTest(
        "%c\\.%m\\(%l\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(4)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines("com.android.tools.r8.R8 -> a.b.c:", "  boolean foo() -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo(4)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testPruningByLineNumber() {
    runRetraceTest(
        "%c\\.%m\\(%l\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(3)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.R8 -> a.b.c:",
                "  3:3:boolean foo():7 -> a",
                "  4:4:boolean bar(int):8 -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo(7)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchOnType() {
    runRetraceTest(
        "%t",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("void", "a.a.a[]", "a.a.a[][][]");
          }

          @Override
          public String mapping() {
            return "";
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("void", "a.a.a[]", "a.a.a[][][]");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchOnFieldOrReturnType() {
    runRetraceTest(
        "%t %c.%m",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("void a.b.c.a", "a.a.a[] a.b.c.b");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo() -> a",
                "  com.android.tools.r8.D8[] bar() -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "void com.android.tools.r8.R8.foo",
                "com.android.tools.r8.D8[] com.android.tools.r8.R8.bar");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  @Ignore("b/165782924")
  public void useReturnTypeToNarrowMethodMatches() {
    runRetraceTest(
        "%t %c.%m",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("void a.b.c.a", "a.a.a[] a.b.c.b");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo() -> a",
                "  int foo(int) -> a",
                "  com.android.tools.r8.D8[] bar() -> b",
                "  com.android.tools.r8.D8 bar(int) -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "void com.android.tools.r8.R8.foo",
                "com.android.tools.r8.D8[] com.android.tools.r8.R8.bar");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void returnTypeCanMatchVoid() {
    runRetraceTest(
        "%t",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("void");
          }

          @Override
          public String mapping() {
            return "";
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("void");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testArguments() {
    runRetraceTest(
        "%c.%m\\(%a\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(int,a.a.a[],boolean)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo(int,com.android.tools.r8.D8[],boolean) -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8.foo(int,com.android.tools.r8.D8[],boolean)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testNoArguments() {
    runRetraceTest(
        "%c.%m\\(%a\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a()");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo() -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.foo()");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  @Ignore("b/165782924")
  public void testPruningOfMethodsByFormals() {
    runRetraceTest(
        "%c.%m\\(%a\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(a.a.a)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo() -> a",
                "  void bar(com.android.tools.r8.D8) -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.bar(com.android.tools.r8.D8)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void matchOnSimpleStackTrace() {
    runRetraceTest(
        DEFAULT_REGULAR_EXPRESSION,
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.a: foo bar baz",
                "  at com.android.tools.r8.b.a(SourceFile)",
                "  at a.c.a.b(SourceFile)");
          }

          @Override
          public String mapping() {
            return StringUtils.joinLines(
                "com.android.tools.r8.R8 -> com.android.tools.r8.a:",
                "com.android.tools.r8.Bar -> com.android.tools.r8.b:",
                "  void foo() -> a",
                "com.android.tools.r8.Baz -> a.c.a:",
                "  void bar() -> b");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8: foo bar baz",
                "  at com.android.tools.r8.Bar.foo(Bar.java)",
                "  at com.android.tools.r8.Baz.bar(Baz.java)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testSourceFileLineNumber() {
    runRetraceTest(
        DEFAULT_REGULAR_EXPRESSION.replace("%s(?::%l)?", "%S"),
        new RetraceAssertionErrorStackTrace());
  }

  @Test
  public void testEscaping() {
    runRetraceTest(
        "\\%c\\\\%c\\\\\\%c.%m\\(\\\\%S\\)\\\\\\%S",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("%c\\com.android.tools.r8.Foo\\%c.a(\\SourceFile:1)\\%S");
          }

          @Override
          public String mapping() {
            return "com.android.tools.r8.Bar -> com.android.tools.r8.Foo:\n"
                + "  1:1:void m():13:13 -> a";
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of("%c\\com.android.tools.r8.Bar\\%c.m(\\Bar.java:13)\\%S");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  private TestDiagnosticMessagesImpl runRetraceTest(
      String regularExpression, StackTraceForTest stackTraceForTest) {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(stackTraceForTest::mapping)
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setRetracedStackTraceConsumer(
                retraced -> {
                  assertEquals(stackTraceForTest.retracedStackTrace(), retraced);
                })
            .setRegularExpression(regularExpression)
            .build();
    Retrace.run(retraceCommand);
    return diagnosticsHandler;
  }
}
