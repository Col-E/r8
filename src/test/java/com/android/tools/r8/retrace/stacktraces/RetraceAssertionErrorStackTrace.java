// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class RetraceAssertionErrorStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.AssertionError",
        "        at com.android.tools.r8.retrace.h.<init>(:4)",
        "        at com.android.tools.r8.retrace.f.a(:48)",
        "        at com.android.tools.r8.retrace.f.a(:2)",
        "        at com.android.tools.r8.retrace.Retrace.run(:5)",
        "        at"
            + " com.android.tools.r8.retrace.RetraceTests.testNullLineTrace(RetraceTests.java:73)");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.AssertionError",
        "        at"
            + " com.android.tools.r8.retrace.RetraceCore$StackTraceNode.<init>(RetraceCore.java:31)",
        "        at com.android.tools.r8.retrace.RetraceCore.retraceLine(RetraceCore.java:117)",
        "        at com.android.tools.r8.retrace.RetraceCore.retrace(RetraceCore.java:107)",
        "        at com.android.tools.r8.retrace.Retrace.run(Retrace.java:116)",
        "        at"
            + " com.android.tools.r8.retrace.RetraceTests.testNullLineTrace(RetraceTests.java:73)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.lang.AssertionError",
        "        at com.android.tools.r8.retrace.RetraceCore$StackTraceNode."
            + "void <init>(java.util.List)(RetraceCore.java:31)",
        "        at com.android.tools.r8.retrace.RetraceCore."
            + "void retraceLine(java.util.List,int,java.util.List)(RetraceCore.java:117)",
        "        at com.android.tools.r8.retrace.RetraceCore."
            + "com.android.tools.r8.retrace."
            + "RetraceCore$RetraceResult retrace()(RetraceCore.java:107)",
        "        at com.android.tools.r8.retrace.Retrace."
            + "void run(com.android.tools.r8.retrace.RetraceCommand)(Retrace.java:116)",
        "        at com.android.tools.r8.retrace.RetraceTests."
            + "testNullLineTrace(RetraceTests.java:73)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.retrace.Retrace -> com.android.tools.r8.retrace.Retrace:",
        "    boolean $assertionsDisabled -> a",
        "    1:5:void <clinit>():34:38 -> <clinit>",
        "    1:1:void <init>():35:35 -> <init>",
        "    1:7:com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer"
            + " getMappingSupplier(java.lang.String,com.android.tools.r8.DiagnosticsHandler):86:92"
            + " -> a",
        "    8:9:com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer"
            + " getMappingSupplier(java.lang.String,com.android.tools.r8.DiagnosticsHandler):88:89"
            + " -> a",
        "    10:12:com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer"
            + " getMappingSupplier(java.lang.String,com.android.tools.r8.DiagnosticsHandler):88:90"
            + " -> a",
        "    13:13:java.lang.String lambda$getMappingSupplier$0(java.nio.file.Path):92:92 -> a",
        "    14:14:void lambda$run$1(java.util.List):135:135 -> a",
        "    15:15:void lambda$main$2(java.lang.String[]):144:144 -> a",
        "    1:1:void run(java.lang.String[]):126:126 -> b",
        "    2:22:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):43:63"
            + " -> b",
        "    2:22:void run(java.lang.String[]):127 -> b",
        "    23:23:java.util.List"
            + " getStackTraceFromFile(java.lang.String,com.android.tools.r8.DiagnosticsHandler):98:98"
            + " -> b",
        "    23:23:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):63 ->"
            + " b",
        "    23:23:void run(java.lang.String[]):127 -> b",
        "    24:25:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):63:64"
            + " -> b",
        "    24:25:void run(java.lang.String[]):127 -> b",
        "    26:27:java.util.List"
            + " getStackTraceFromFile(java.lang.String,com.android.tools.r8.DiagnosticsHandler):100:101"
            + " -> b",
        "    26:27:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):63 ->"
            + " b",
        "    26:27:void run(java.lang.String[]):127 -> b",
        "    28:30:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):67:69"
            + " -> b",
        "    28:30:void run(java.lang.String[]):127 -> b",
        "    31:35:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):67:71"
            + " -> b",
        "    31:35:void run(java.lang.String[]):127 -> b",
        "    36:39:java.util.List getStackTraceFromStandardInput():148:151 -> b",
        "    36:39:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):79 ->"
            + " b",
        "    36:39:void run(java.lang.String[]):127 -> b",
        "    40:40:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):79:79"
            + " -> b",
        "    40:40:void run(java.lang.String[]):127 -> b",
        "    41:47:void run(java.lang.String[]):130:136 -> b",
        "    48:49:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " parseArguments(java.lang.String[],com.android.tools.r8.DiagnosticsHandler):75:76"
            + " -> b",
        "    48:49:void run(java.lang.String[]):127 -> b",
        "    1:1:void main(java.lang.String[]):144:144 -> main",
        "    2:10:void"
            + " withMainProgramHandler(com.android.tools.r8.retrace.Retrace$MainAction):164:172 ->"
            + " main",
        "    2:10:void main(java.lang.String[]):144 -> main",
        "    11:11:void"
            + " withMainProgramHandler(com.android.tools.r8.retrace.Retrace$MainAction):167:167 ->"
            + " main",
        "    11:11:void main(java.lang.String[]):144 -> main",
        "    1:9:void run(com.android.tools.r8.retrace.RetraceCommand):112:120 -> run",
        "    10:12:void run(com.android.tools.r8.retrace.RetraceCommand):119:121 -> run",
        "com.android.tools.r8.retrace.Retrace$1 -> com.android.tools.r8.retrace.Retrace$a:",
        "    1:1:void <init>():126:126 -> <init>",
        "com.android.tools.r8.retrace.Retrace$MainAction ->"
            + " com.android.tools.r8.retrace.Retrace$b:",
        "com.android.tools.r8.retrace.Retrace$RetraceAbortException ->"
            + " com.android.tools.r8.retrace.Retrace$RetraceAbortException:",
        "    1:1:void <init>():156:156 -> <init>",
        "com.android.tools.r8.retrace.RetraceCommand ->"
            + " com.android.tools.r8.retrace.RetraceCommand:",
        "    com.android.tools.r8.DiagnosticsHandler diagnosticsHandler -> a",
        "    java.util.function.Consumer retracedStackTraceConsumer -> d",
        "    com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer proguardMapProducer"
            + " -> b",
        "    java.util.List stackTrace -> c",
        "    boolean $assertionsDisabled -> e",
        "    1:1:void <clinit>():13:13 -> <clinit>",
        "    1:1:void"
            + " <init>(boolean,com.android.tools.r8.DiagnosticsHandler,com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer,java.util.List,java.util.function.Consumer,com.android.tools.r8.retrace.RetraceCommand$1):13:13"
            + " -> <init>",
        "    2:12:void"
            + " <init>(boolean,com.android.tools.r8.DiagnosticsHandler,com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer,java.util.List,java.util.function.Consumer):26:36"
            + " -> <init>",
        "    1:1:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " builder(com.android.tools.r8.DiagnosticsHandler):45:45 -> a",
        "com.android.tools.r8.retrace.RetraceCommand$1 -> com.android.tools.r8.retrace.a:",
        "com.android.tools.r8.retrace.RetraceCommand$Builder ->"
            + " com.android.tools.r8.retrace.RetraceCommand$a:",
        "    com.android.tools.r8.DiagnosticsHandler diagnosticsHandler -> a",
        "    java.util.function.Consumer retracedStackTraceConsumer -> d",
        "    com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer proguardMapProducer"
            + " -> b",
        "    java.util.List stackTrace -> c",
        "    1:1:void"
            + " <init>(com.android.tools.r8.DiagnosticsHandler,com.android.tools.r8.retrace.RetraceCommand$1):53:53"
            + " -> <init>",
        "    2:3:void <init>(com.android.tools.r8.DiagnosticsHandler):61:62 -> <init>",
        "    1:1:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " setProguardMapProducer(com.android.tools.r8.retrace.RetraceCommand$ProguardMapProducer):77:77"
            + " -> a",
        "    2:2:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " setStackTrace(java.util.List):88:88 -> a",
        "    3:3:com.android.tools.r8.retrace.RetraceCommand$Builder"
            + " setRetracedStackTraceConsumer(java.util.function.Consumer):98:98 -> a",
        "    4:16:com.android.tools.r8.retrace.RetraceCommand build():103:115 -> a",
        "    17:17:com.android.tools.r8.retrace.RetraceCommand build():113:113 -> a",
        "    18:18:com.android.tools.r8.retrace.RetraceCommand build():110:110 -> a",
        "    19:19:com.android.tools.r8.retrace.RetraceCommand build():107:107 -> a",
        "    20:20:com.android.tools.r8.retrace.RetraceCommand build():104:104 -> a",
        "com.android.tools.r8.retrace.RetraceCore -> com.android.tools.r8.retrace.f:",
        "    java.util.List stackTrace -> b",
        "    com.android.tools.r8.naming.ClassNameMapper classNameMapper -> a",
        "    com.android.tools.r8.DiagnosticsHandler diagnosticsHandler -> c",
        "    1:4:void"
            + " <init>(com.android.tools.r8.naming.ClassNameMapper,java.util.List,com.android.tools.r8.DiagnosticsHandler):99:102"
            + " -> <init>",
        "    1:3:com.android.tools.r8.retrace.RetraceCore$RetraceResult retrace():106:108 -> a",
        "    4:7:void retraceLine(java.util.List,int,java.util.List):112:115 -> a",
        "    8:8:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " parseLine(int,java.lang.String):503:503 -> a",
        "    8:8:void retraceLine(java.util.List,int,java.util.List):115 -> a",
        "    9:29:com.android.tools.r8.retrace.RetraceCore$ExceptionLine"
            + " com.android.tools.r8.retrace.RetraceCore$ExceptionLine.tryParse(java.lang.String):191:211"
            + " -> a",
        "    9:29:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " parseLine(int,java.lang.String):507 -> a",
        "    9:29:void retraceLine(java.util.List,int,java.util.List):115 -> a",
        "    30:40:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " com.android.tools.r8.retrace.RetraceCore$MoreLine.tryParse(java.lang.String):452:462"
            + " -> a",
        "    30:40:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " parseLine(int,java.lang.String):511 -> a",
        "    30:40:void retraceLine(java.util.List,int,java.util.List):115 -> a",
        "    41:42:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " parseLine(int,java.lang.String):513:514 -> a",
        "    41:42:void retraceLine(java.util.List,int,java.util.List):115 -> a",
        "    43:46:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " parseLine(int,java.lang.String):513:516 -> a",
        "    43:46:void retraceLine(java.util.List,int,java.util.List):115 -> a",
        "    47:50:void retraceLine(java.util.List,int,java.util.List):116:119 -> a",
        "    51:52:com.android.tools.r8.retrace.RetraceCore$StackTraceLine"
            + " parseLine(int,java.lang.String):499:500 -> a",
        "    51:52:void retraceLine(java.util.List,int,java.util.List):115 -> a",
        "com.android.tools.r8.retrace.RetraceCore$AtLine -> com.android.tools.r8.retrace.b:",
        "    int linePosition -> f",
        "    boolean $assertionsDisabled -> g",
        "    java.lang.String startingWhitespace -> a",
        "    java.lang.String clazz -> c",
        "    java.lang.String at -> b",
        "    java.lang.String fileName -> e",
        "    java.lang.String method -> d",
        "    1:1:void <clinit>():243:243 -> <clinit>",
        "    1:7:void"
            + " <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int):261:267"
            + " -> <init>",
        "    boolean isAtLine() -> a",
        "    1:1:java.lang.String"
            + " access$000(com.android.tools.r8.retrace.RetraceCore$AtLine):243:243 -> a",
        "    2:7:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):328:333 -> a",
        "    8:22:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):331:345 -> a",
        "    23:57:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):339:373 ->"
            + " a",
        "    58:67:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):367:376 ->"
            + " a",
        "    68:74:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):351:357 ->"
            + " a",
        "    75:75:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):351:351 ->"
            + " a",
        "    76:80:java.lang.String getClassSimpleName(java.lang.String):398:402 -> a",
        "    1:1:java.lang.String"
            + " access$100(com.android.tools.r8.retrace.RetraceCore$AtLine):243:243 -> b",
        "    2:5:java.lang.String retracedFileName(java.lang.String):384:387 -> b",
        "    6:11:java.lang.String retracedFileName(java.lang.String):385:390 -> b",
        "    12:14:java.lang.String"
            + " com.android.tools.r8.com.google.common.io.Files.getFileExtension(java.lang.String):792:794"
            + " -> b",
        "    12:14:java.lang.String retracedFileName(java.lang.String):390 -> b",
        "    15:18:java.lang.String retracedFileName(java.lang.String):391:394 -> b",
        "    19:19:java.lang.Object"
            + " com.android.tools.r8.com.google.common.base.Preconditions.checkNotNull(java.lang.Object):787:787"
            + " -> b",
        "    19:19:java.lang.String"
            + " com.android.tools.r8.com.google.common.io.Files.getFileExtension(java.lang.String):791"
            + " -> b",
        "    19:19:java.lang.String retracedFileName(java.lang.String):390 -> b",
        "    1:1:java.lang.String"
            + " access$200(com.android.tools.r8.retrace.RetraceCore$AtLine):243:243 -> c",
        "    2:51:com.android.tools.r8.retrace.RetraceCore$AtLine"
            + " tryParse(java.lang.String):272:321 -> c",
        "    1:1:int access$300(com.android.tools.r8.retrace.RetraceCore$AtLine):243:243 -> d",
        "    1:15:java.lang.String toString():410:424 -> toString",
        "com.android.tools.r8.retrace.RetraceCore$AtStackTraceLineComparator ->"
            + " com.android.tools.r8.retrace.c:",
        "    1:1:void <init>():53:53 -> <init>",
        "    1:1:int compare(java.lang.Object,java.lang.Object):53:53 -> compare",
        "    2:16:int"
            + " compare(com.android.tools.r8.retrace.RetraceCore$StackTraceLine,com.android.tools.r8.retrace.RetraceCore$StackTraceLine):57:71"
            + " -> compare",
        "    2:16:int compare(java.lang.Object,java.lang.Object):53 -> compare",
        "com.android.tools.r8.retrace.RetraceCore$ExceptionLine -> com.android.tools.r8.retrace.d:",
        "    java.lang.String initialWhiteSpace -> a",
        "    java.lang.String exceptionClass -> c",
        "    java.lang.String description -> b",
        "    java.lang.String message -> d",
        "    1:5:void"
            + " <init>(java.lang.String,java.lang.String,java.lang.String,java.lang.String):183:187"
            + " -> <init>",
        "    1:6:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):216:221 -> a",
        "    1:1:java.lang.String toString():227:227 -> toString",
        "com.android.tools.r8.retrace.RetraceCore$MoreLine -> com.android.tools.r8.retrace.e:",
        "    java.lang.String line -> a",
        "    1:2:void <init>(java.lang.String):445:446 -> <init>",
        "    1:1:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):470:470 -> a",
        "    1:1:java.lang.String toString():475:475 -> toString",
        "com.android.tools.r8.retrace.RetraceCore$RetraceResult ->"
            + " com.android.tools.r8.retrace.f$a:",
        "    java.util.List nodes -> a",
        "    1:2:void <init>(java.util.List):79:80 -> <init>",
        "    1:3:java.util.List toListOfStrings():84:86 -> a",
        "com.android.tools.r8.retrace.RetraceCore$StackTraceLine ->"
            + " com.android.tools.r8.retrace.g:",
        "    1:1:void <init>():122:122 -> <init>",
        "    boolean isAtLine() -> a",
        "    java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper) -> a",
        "    1:1:int firstNonWhiteSpaceCharacterFromIndex(java.lang.String,int):126:126 -> a",
        "    2:2:int firstCharFromIndex(java.lang.String,int,char):130:130 -> a",
        "    3:3:boolean lambda$firstCharFromIndex$0(char,java.lang.Character):130:130 -> a",
        "    4:9:int firstFromIndex(java.lang.String,int,java.util.function.Predicate):134:139 ->"
            + " a",
        "com.android.tools.r8.retrace.RetraceCore$StackTraceNode ->"
            + " com.android.tools.r8.retrace.h:",
        "    java.util.List lines -> a",
        "    boolean $assertionsDisabled -> b",
        "    1:1:void <clinit>():24:24 -> <clinit>",
        "    1:4:void <init>(java.util.List):28:31 -> <init>",
        "    1:14:java.lang.String toString():36:49 -> toString",
        "com.android.tools.r8.retrace.RetraceCore$UnknownLine -> com.android.tools.r8.retrace.i:",
        "    java.lang.String line -> a",
        "    1:2:void <init>(java.lang.String):482:483 -> <init>",
        "    1:1:java.util.List retrace(com.android.tools.r8.naming.ClassNameMapper):488:488 -> a",
        "    1:1:java.lang.String toString():493:493 -> toString",
        "com.android.tools.r8.retrace.RetraceInvalidStackTraceLineDiagnostics ->"
            + " com.android.tools.r8.retrace.RetraceInvalidStackTraceLineDiagnostics:",
        "    java.lang.String message -> b",
        "    int lineNumber -> a",
        "    1:3:void <init>(int,java.lang.String):23:25 -> <init>",
        "    1:1:com.android.tools.r8.retrace.RetraceInvalidStackTraceLineDiagnostics"
            + " createNull(int):44:44 -> createNull",
        "    1:2:com.android.tools.r8.retrace.RetraceInvalidStackTraceLineDiagnostics"
            + " createParse(int,java.lang.String):48:49 -> createParse",
        "    1:1:java.lang.String getDiagnosticMessage():40:40 -> getDiagnosticMessage",
        "    1:1:com.android.tools.r8.origin.Origin getOrigin():30:30 -> getOrigin",
        "    1:1:com.android.tools.r8.position.Position getPosition():35:35 -> getPosition");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
