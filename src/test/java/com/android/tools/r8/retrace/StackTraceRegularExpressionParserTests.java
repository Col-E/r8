// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser.DEFAULT_REGULAR_EXPRESSION;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.stacktraces.InlineFileNameStackTrace;
import com.android.tools.r8.retrace.stacktraces.LongLineStackTrace;
import com.android.tools.r8.retrace.stacktraces.RetraceAssertionErrorStackTrace;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.utils.BooleanUtils;
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
public class StackTraceRegularExpressionParserTests extends TestBase {

  @Parameters(name = "{0}, verbose: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  private final boolean verbose;

  public StackTraceRegularExpressionParserTests(TestParameters parameters, boolean verbose) {
    parameters.assertNoneRuntime();
    this.verbose = verbose;
  }

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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.void foo()");
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.int foo");
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
          public List<String> retraceVerboseStackTrace() {
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
            return Collections.singletonList("foo.Bar$Baz.baz(Bar.java)");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return Collections.singletonList("foo.Bar$Baz.int baz(Bar.java)");
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
          public List<String> retraceVerboseStackTrace() {
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
            return ImmutableList.of("a.b.d(SourceFile)");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("a.b.d(SourceFile)");
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.boolean foo()(7)");
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.boolean foo()()");
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
            return ImmutableList.of("com.android.tools.r8.R8.a(42)");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.a(42)");
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.boolean foo()(4)");
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.boolean foo()(7)");
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
            // TODO(b/199919195): Consider not writing full method description.
            return ImmutableList.of(
                "void com.android.tools.r8.R8.void foo()",
                "com.android.tools.r8.D8[] com.android.tools.r8.R8.com.android.tools.r8.D8[]"
                    + " bar()");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test()
  public void testSourceFileBeforeClassStackTrace() {
    runRetraceTest(
        "\\S* \\(%s: 70\\) %c.%m",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return Collections.singletonList(
                "0xffffffffffffffff (some-bundle.aab-canary-42: 70) ii2.p");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.naming.retrace.Main -> ii2:", "  void test() -> p");
          }

          @Override
          public List<String> retracedStackTrace() {
            return Collections.singletonList(
                "0xffffffffffffffff (Main.java: 70)"
                    + " com.android.tools.r8.naming.retrace.Main.test");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return Collections.singletonList(
                "0xffffffffffffffff (Main.java: 70)"
                    + " com.android.tools.r8.naming.retrace.Main.void test()");
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8.void foo(int,com.android.tools.r8.D8[],boolean)"
                    + "(int,com.android.tools.r8.D8[],boolean)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testArgumentsWithWhitespace() {
    runRetraceTest(
        "%c.%m\\(%a\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(int, a.a.a[], a.b.c)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo(int,original.signature) -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8.foo"
                    + "(int, com.android.tools.r8.D8[], com.android.tools.r8.R8)");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8.void foo"
                    + "(int,original.signature)"
                    + "(int, com.android.tools.r8.D8[], com.android.tools.r8.R8)");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testArgumentsWithDifferentWhitespace() {
    runRetraceTest(
        "%c.%m\\(%a\\)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of("a.b.c.a(int,a.a.a[], a.a.a,  a.b.c)");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "com.android.tools.r8.D8 -> a.a.a:",
                "com.android.tools.r8.R8 -> a.b.c:",
                "  void foo(int,original.signature) -> a");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8.foo(int,com.android.tools.r8.D8[],"
                    + " com.android.tools.r8.D8,  com.android.tools.r8.R8)");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8.void foo"
                    + "(int,original.signature)"
                    + "(int,com.android.tools.r8.D8[],"
                    + " com.android.tools.r8.D8,  com.android.tools.r8.R8)");
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("com.android.tools.r8.R8.void foo()()");
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
          public List<String> retraceVerboseStackTrace() {
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "com.android.tools.r8.R8: foo bar baz",
                "  at com.android.tools.r8.Bar.void foo()(Bar.java)",
                "  at com.android.tools.r8.Baz.void bar()(Baz.java)");
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
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of("%c\\com.android.tools.r8.Bar\\%c.void m()(\\Bar.java:13)\\%S");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  /* This is a regression test for b/216359244 */
  @Test
  public void testMissingSourceFile() {
    runRetraceTest(
        ".*FOO\\s+:\\s+\\[\\d+\\]\\s+%c\\.%m\\(%l\\):.*",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of(
                "12-24 19:53:19.052 10197 30302 30302 I FOO : [2] huk.g(1): getDownloads()");
          }

          @Override
          public String mapping() {
            return "foo.Bar -> huk:\n" + "  void baz():13:13 -> g\n" + "  void qux():12:12 -> g\n";
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "12-24 19:53:19.052 10197 30302 30302 I FOO : [2] foo.Bar.baz(13): getDownloads()",
                "<OR> 12-24 19:53:19.052 10197 30302 30302 I FOO : [2] foo.Bar.qux(12):"
                    + " getDownloads()");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "12-24 19:53:19.052 10197 30302 30302 I FOO : [2] foo.Bar.void baz()(13):"
                    + " getDownloads()",
                "<OR> 12-24 19:53:19.052 10197 30302 30302 I FOO : [2] foo.Bar.void qux()(12):"
                    + " getDownloads()");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  @Test
  public void testBigLine() {
    runRetraceTest(
        RetraceOptions.defaultRegularExpression(),
        new StackTraceForTest() {

          private final String LINE =
              "12-16 19:29:11.111 media 453 490 D WVCdm : [for_bar.cpp(334):AddKey] key_data ="
                  + " (1401)"
                  + " CAISvgYKmgIKIEIzMkU1QkU3RjlGMThCNzYzQjAwMDAwMDAwMDAwMDAwEuMBeyJ2ZXJzaW9uIjoiM"
                  + "S4wIiwiZXNuIjoiTkZBTkRST0lEMi1QUlYtQk9SRUFMLUdPT0dMQ0hST01FQ0FTVD1IRC0yMTc5My0"
                  + "1Nzk0RDMyRTQxRUI5QTc2QzZFQzIyNTdEQzVGNTQ2MTNDNzdBQTdCNzAwNzcyMkREQjVCRDYzRUFER"
                  + "TZBRUUyIiwic2FsdCI6IjQ5OTI3MDU0ODMyNzI0NjIxMDc0MDM4Nzg3MDkxMjAyNCIsImlzc3VlVGl"
                  + "tZSI6MTYzOTY2MzE1MDAwMCwibW92aWVJZCI6IjgxMTg0NDE3In0gASgAOMDRAkDA0QJIroztjQYSF"
                  + "AgBEAAYACDA0QIowNECWABgAXgBGmYSEBnHXFI_ttc_1lja-bpjqHkaUN_81aKusLLERz_xX0vPIR1"
                  + "I8yu9u9zQndpy1aEFJYtQiB0sed6wAC3c6aeH4oLsGFPiuVgGweP1MGc3yxunqusDpoGe03JGD1VSE"
                  + "9aB0jSKIAEahgEKEAAAAAAGmcHCAAAAAAAAAAASEN4b9_2XH75TnxdBkpJ4YhoaIOdhQxec7DpUs0D"
                  + "ixSQXTlvjbXqX3PIOg9DiQLCa4vbzIAIoBToECAEQKkI0CiDuiCOCIxvTlCqkYTKBWzMOfeh9BpKc5"
                  + "RDdE2omMbOfaRIQRWVqtbR8Qg4JEY0u0CAFRBqGAQoQAAAAAAaZwcMAAAAAAAAAABIQ7iP9449yr2F"
                  + "_97v4QHerRxognKGBtw2cwGpxKltxU7A_1asVTS0aPaw8dr5e2rP2SW4gAigFOgQIARAqQjQKIB-50"
                  + "Ou_RGn8rzRDr76FeWpBLYSt99zHVSZK0xnd_anREhC4eACukLOUwL5TQ8w1oOAVGoYBChAAAAAABpn"
                  + "BwQAAAAAAAAAAEhDsGE2bR6VgRM85PY3YU9qIGiAhD3h7I8Cv5Irr315yecWo0YA1t9_sr0g2z0zsa"
                  + "6Dq0yACKAE6BAgBECpCNAogQoIDcKUGMhCA-HetsARASXxXJUazkIIaSwCKE9asAEwSEMW4TIMZZkc"
                  + "r3LYIITelMlIgroztjQY4ABogzw-SGqQ1AYy8EopI2zSvXX_hkFpOoWxCvFQuQEj5oQwigAIRzNhvB"
                  + "9-YMOy2Muentb574WJKnVqXfEor5mFDIQ3vDELdjbkibaS4THyK2DlUQyp0M3C8n5bQ1JApslsfh5w"
                  + "SEMvA8Y0sXE9H6xnAKnMJTioZJOTElqPrbbRM5APhI3ohL8rp7u8ydBm9aWqkprDngrU3b1KdHd6J9"
                  + "YiJRJ4Y55M4qqCdwbCqr57xYAu_IIH_p8erfnhfNgUE2svtZBiLMP37vOMSQAP08_zkMf87VRHBxuI"
                  + "2rlnGcdgLoJyPgLp2ZBbdLw-b4Nq6uIZQfNABQ1SuM3OZ6uD6eq-NH9FawWvN0EmzWZ88DGiHtgM7h"
                  + "zqvIb8JNmLInt28us-vw6KQOggKBjE2LjUuMEABSoACAAAAAgAAAQAABAAQZrvQTwAAABMAAAE3AAA"
                  + "AEAAAAUkAAABQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAQAAAAAAAAAAAAAAAAAAAAAAAKjAA"
                  + "AAAAAAAqMAAAAAAAAAAAAAAAAMAAAGgAAAAEAAAAbIAAAAQAAABxAAAABAAAAIUAAAAEAAAAfIAAAA"
                  + "gAAACKQAAABAAAAI7AAAAEAAAAk0AAAAQAAACnQAAABAAAAJ7AAAAIAAAArIAAAAQAAACxAAAABAAA"
                  + "ALWAAAAEAAAAyYAAAAQAAADBAAAACC8nuR7W9i8AuG9xF3K__n1kk9F_G1i25QRKR9YXyssdsadWT";

          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of(LINE);
          }

          @Override
          public String mapping() {
            return "foo.Bar -> huk:\n" + "  void baz():13:13 -> g\n" + "  void qux():12:12 -> g\n";
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(LINE);
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(LINE);
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  /* This is a regression test for b/222749348 */
  @Test
  public void testGroups() {
    runRetraceTest(
        "(?:(?:.*?%c %m\\(%s\\s*:\\s*%l\\s*\\)))",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of(
                "FOO bar(PG: 1)",
                "FOO bar(PG : 1)",
                "FOO bar(PG:1)",
                "FOO bar(PG:1 )",
                "FOO bar(PG: 1 )");
          }

          @Override
          public String mapping() {
            return StringUtils.lines(
                "this.was.Deobfuscated -> FOO:",
                "    int[] mFontFamily -> a",
                "    1:3:void someMethod(int):65:67 -> bar");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "this.was.Deobfuscated someMethod(Deobfuscated.java: 65)",
                "this.was.Deobfuscated someMethod(Deobfuscated.java: 65)",
                "this.was.Deobfuscated someMethod(Deobfuscated.java:65)",
                "this.was.Deobfuscated someMethod(Deobfuscated.java:65 )",
                "this.was.Deobfuscated someMethod(Deobfuscated.java: 65 )");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "this.was.Deobfuscated void someMethod(int)(Deobfuscated.java: 65)",
                "this.was.Deobfuscated void someMethod(int)(Deobfuscated.java: 65)",
                "this.was.Deobfuscated void someMethod(int)(Deobfuscated.java:65)",
                "this.was.Deobfuscated void someMethod(int)(Deobfuscated.java:65 )",
                "this.was.Deobfuscated void someMethod(int)(Deobfuscated.java: 65 )");
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
  }

  /** This is a regression test for b/234758957 */
  @Test()
  public void testLongLine() {
    runRetraceTest("(?:.*?\\(\\s*%s(?:\\s*:\\s*%l\\s*)?\\)\\s*%c\\.%m)|", new LongLineStackTrace());
  }

  /** This is a regression test for b/300416467. */
  @Test
  public void testGraphsModule() {
    runRetraceTest(
        "(?:.*, %c,.*)",
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            return ImmutableList.of(
                "TaskGraph@4b0b0f5(\"SomePipeline\") [created at 08-19 18:02:01.531]",
                "                                     method                 future  (Note: all"
                    + " times are in ms. relative to TaskGraph creation)",
                "    requested,   queued,  started, finished (+  latency), finished, task",
                "        0.057,    1.290,    2.690,    3.408 (+    0.718),    66.376,"
                    + " foo.bar.baz.c.b.a.c.b.a.a.b, 0, 6, 0",
                "");
          }

          @Override
          public String mapping() {
            return StringUtils.unixLines("some.original.factory -> foo.bar.baz.c.b.a.c.b.a.a.b:");
          }

          @Override
          public List<String> retracedStackTrace() {
            return ImmutableList.of(
                "TaskGraph@4b0b0f5(\"SomePipeline\") [created at 08-19 18:02:01.531]",
                "                                     method                 future  (Note: all"
                    + " times are in ms. relative to TaskGraph creation)",
                "    requested,   queued,  started, finished (+  latency), finished, task",
                "        0.057,    1.290,    2.690,    3.408 (+    0.718),    66.376,"
                    + " some.original.factory, 0, 6, 0",
                "");
          }

          @Override
          public List<String> retraceVerboseStackTrace() {
            return ImmutableList.of(
                "TaskGraph@4b0b0f5(\"SomePipeline\") [created at 08-19 18:02:01.531]",
                "                                     method                 future  (Note: all"
                    + " times are in ms. relative to TaskGraph creation)",
                "    requested,   queued,  started, finished (+  latency), finished, task",
                "        0.057,    1.290,    2.690,    3.408 (+    0.718),    66.376,"
                    + " some.original.factory, 0, 6, 0",
                "");
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
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(
                        ProguardMapProducer.fromString(stackTraceForTest.mapping()))
                    .build())
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setRetracedStackTraceConsumer(
                retraced -> {
                  assertEquals(
                      verbose
                          ? stackTraceForTest.retraceVerboseStackTrace()
                          : stackTraceForTest.retracedStackTrace(),
                      retraced);
                })
            .setRegularExpression(regularExpression)
            .setVerbose(verbose)
            .build();
    Retrace.run(retraceCommand);
    return diagnosticsHandler;
  }
}
