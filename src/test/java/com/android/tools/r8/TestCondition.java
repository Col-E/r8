// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.R8RunArtTestsTest.DexTool;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class TestCondition {

  enum Runtime {
    ART_V4_0_4,
    ART_V4_4_4,
    ART_V5_1_1,
    ART_V6_0_1,
    ART_V7_0_0,
    ART_DEFAULT,
    JAVA;

    static final Runtime LOWEST_ART_VERSION = ART_V4_0_4;
    static final Runtime HIGHEST_ART_VERSION = ART_DEFAULT;

    static Runtime fromDexVmVersion(DexVm.Version version) {
      switch (version) {
        case V4_0_4:
          return ART_V4_0_4;
        case V4_4_4:
          return ART_V4_4_4;
        case V5_1_1:
          return ART_V5_1_1;
        case V6_0_1:
          return ART_V6_0_1;
        case V7_0_0:
          return ART_V7_0_0;
        case DEFAULT:
          return ART_DEFAULT;
        default:
          throw new Unreachable();
      }
    }

    static boolean isArt(Runtime runtime) {
      return EnumSet.range(LOWEST_ART_VERSION, HIGHEST_ART_VERSION).contains(runtime);
    }
  }

  static class ToolSet {

    final EnumSet<DexTool> set;

    public ToolSet(EnumSet<DexTool> set) {
      this.set = set;
    }
  }

  static class CompilerSet {

    final EnumSet<CompilerUnderTest> set;

    public CompilerSet(EnumSet<CompilerUnderTest> set) {
      this.set = set;
    }
  }

  static class RuntimeSet {

    private EnumSet<Runtime> set;

    public RuntimeSet(EnumSet<Runtime> set) {
      this.set = set;
    }

    public static RuntimeSet fromDexVmVersionSet(EnumSet<DexVm.Version> dexVmSet) {
      List<Runtime> list = new ArrayList<>(dexVmSet.size());
      for (DexVm.Version version : dexVmSet) {
        list.add(Runtime.fromDexVmVersion(version));
      }
      return new RuntimeSet(EnumSet.copyOf(list));
    }

    boolean contains(Runtime runtime) {
      return set.contains(runtime);
    }
  }

  static class CompilationModeSet {

    final EnumSet<CompilationMode> set;

    public CompilationModeSet(EnumSet<CompilationMode> set) {
      this.set = set;
    }
  }

  public static final CompilerSet D8_COMPILER =
      compilers(CompilerUnderTest.D8, CompilerUnderTest.D8_AFTER_R8CF);
  public static final CompilerSet D8_NOT_AFTER_R8CF_COMPILER = compilers(CompilerUnderTest.D8);
  public static final CompilerSet D8_AFTER_R8CF_COMPILER =
      compilers(CompilerUnderTest.D8_AFTER_R8CF);
  // R8_COMPILER refers to R8 both in the standalone setting and after D8
  // R8_NOT_AFTER_D8_COMPILER and R8_AFTER_D8_COMPILER refers to the standalone and the combined
  // settings, respectively
  public static final CompilerSet R8_COMPILER =
      compilers(
          CompilerUnderTest.R8,
          CompilerUnderTest.R8_AFTER_D8,
          CompilerUnderTest.D8_AFTER_R8CF,
          CompilerUnderTest.R8CF);
  public static final CompilerSet R8DEX_COMPILER =
      compilers(CompilerUnderTest.R8, CompilerUnderTest.R8_AFTER_D8);
  public static final CompilerSet R8_AFTER_D8_COMPILER = compilers(CompilerUnderTest.R8_AFTER_D8);
  public static final CompilerSet R8_NOT_AFTER_D8_COMPILER =
      compilers(CompilerUnderTest.R8, CompilerUnderTest.D8_AFTER_R8CF, CompilerUnderTest.R8CF);
  public static final CompilerSet R8DEX_NOT_AFTER_D8_COMPILER = compilers(CompilerUnderTest.R8);

  public static final CompilationModeSet DEBUG_MODE =
      new CompilationModeSet(EnumSet.of(CompilationMode.DEBUG));
  public static final CompilationModeSet RELEASE_MODE =
      new CompilationModeSet(EnumSet.of(CompilationMode.RELEASE));

  private static final ToolSet ANY_TOOL = new ToolSet(EnumSet.allOf(DexTool.class));
  private static final CompilerSet ANY_COMPILER =
      new CompilerSet(EnumSet.allOf(CompilerUnderTest.class));
  private static final RuntimeSet ANY_RUNTIME = new RuntimeSet(EnumSet.allOf(Runtime.class));
  private static final RuntimeSet ANY_DEX_VM_RUNTIME =
      RuntimeSet.fromDexVmVersionSet(EnumSet.allOf(ToolHelper.DexVm.Version.class));
  private static final CompilationModeSet ANY_MODE =
      new CompilationModeSet(EnumSet.allOf(CompilationMode.class));

  private final EnumSet<DexTool> dexTools;
  private final EnumSet<CompilerUnderTest> compilers;
  private final EnumSet<Runtime> runtimes;
  private final EnumSet<CompilationMode> compilationModes;

  public TestCondition(
      EnumSet<DexTool> dexTools,
      EnumSet<CompilerUnderTest> compilers,
      EnumSet<Runtime> runtimes,
      EnumSet<CompilationMode> compilationModes) {
    this.dexTools = dexTools;
    this.compilers = compilers;
    this.runtimes = runtimes;
    this.compilationModes = compilationModes;
  }

  public static ToolSet tools(DexTool... tools) {
    assert tools.length > 0;
    return new ToolSet(EnumSet.copyOf(Arrays.asList(tools)));
  }

  public static CompilerSet compilers(CompilerUnderTest... compilers) {
    assert compilers.length > 0;
    return new CompilerSet(EnumSet.copyOf(Arrays.asList(compilers)));
  }

  public static RuntimeSet runtimes(DexVm.Version... runtimes) {
    assert runtimes.length > 0;
    return RuntimeSet.fromDexVmVersionSet(EnumSet.copyOf(Arrays.asList(runtimes)));
  }

  public static RuntimeSet runtimes(Runtime... runtimes) {
    assert runtimes.length > 0;
    return new RuntimeSet(EnumSet.copyOf(Arrays.asList(runtimes)));
  }

  public static RuntimeSet runtimesUpTo(DexVm.Version upto) {
    return RuntimeSet.fromDexVmVersionSet(EnumSet.range(DexVm.Version.first(), upto));
  }

  public static RuntimeSet artRuntimesUpTo(Runtime upto) {
    assert Runtime.isArt(upto);
    return new RuntimeSet(EnumSet.range(Runtime.LOWEST_ART_VERSION, upto));
  }

  public static RuntimeSet artRuntimesUpToAndJava(Runtime upto) {
    return runtimes(
        Sets.union(artRuntimesUpTo(upto).set, runtimes(Runtime.JAVA).set).toArray(new Runtime[0]));
  }

  public static RuntimeSet runtimesFrom(DexVm.Version start) {
    return RuntimeSet.fromDexVmVersionSet(EnumSet.range(start, DexVm.Version.last()));
  }

  public static RuntimeSet artRuntimesFrom(Runtime start) {
    assert Runtime.isArt(start);
    return new RuntimeSet(EnumSet.range(start, Runtime.HIGHEST_ART_VERSION));
  }

  public static RuntimeSet artRuntimesFromAndJava(Runtime start) {
    return runtimes(
        Sets.union(artRuntimesFrom(start).set, runtimes(Runtime.JAVA).set).toArray(new Runtime[0]));
  }

  public static RuntimeSet and(RuntimeSet... sets) {
    return new RuntimeSet(
        EnumSet.copyOf(
            Arrays.stream(sets)
                .flatMap(runtimeSet -> runtimeSet.set.stream())
                .collect(Collectors.toSet())));
  }

  public static TestCondition match(
      ToolSet tools,
      CompilerSet compilers,
      RuntimeSet runtimes,
      CompilationModeSet compilationModes) {
    return new TestCondition(tools.set, compilers.set, runtimes.set, compilationModes.set);
  }

  public static TestCondition match(ToolSet tools, CompilerSet compilers, RuntimeSet runtimes) {
    return match(tools, compilers, runtimes, TestCondition.ANY_MODE);
  }

  public static TestCondition any() {
    return match(TestCondition.ANY_TOOL, TestCondition.ANY_COMPILER, TestCondition.ANY_RUNTIME);
  }

  public static TestCondition anyDexVm() {
    return match(
        TestCondition.ANY_TOOL, TestCondition.ANY_COMPILER, TestCondition.ANY_DEX_VM_RUNTIME);
  }

  public static TestCondition match(ToolSet tools) {
    return match(tools, TestCondition.ANY_COMPILER, TestCondition.ANY_RUNTIME);
  }

  public static TestCondition match(ToolSet tools, CompilerSet compilers) {
    return match(tools, compilers, TestCondition.ANY_RUNTIME);
  }

  public static TestCondition match(ToolSet tools, RuntimeSet runtimes) {
    return match(tools, TestCondition.ANY_COMPILER, runtimes);
  }

  public static TestCondition match(CompilerSet compilers) {
    return match(TestCondition.ANY_TOOL, compilers, TestCondition.ANY_RUNTIME);
  }

  public static TestCondition match(CompilerSet compilers, CompilationModeSet compilationModes) {
    return match(TestCondition.ANY_TOOL, compilers, TestCondition.ANY_RUNTIME, compilationModes);
  }

  public static TestCondition match(CompilerSet compilers, RuntimeSet runtimes) {
    return match(TestCondition.ANY_TOOL, compilers, runtimes);
  }

  public static TestCondition match(RuntimeSet runtimes) {
    return match(TestCondition.ANY_TOOL, TestCondition.ANY_COMPILER, runtimes);
  }

  public boolean test(
      DexTool dexTool,
      CompilerUnderTest compilerUnderTest,
      Runtime runtime,
      CompilationMode compilationMode) {
    return dexTools.contains(dexTool)
        && compilers.contains(compilerUnderTest)
        && runtimes.contains(runtime)
        && compilationModes.contains(compilationMode);
  }

  public boolean test(
      DexTool dexTool,
      CompilerUnderTest compilerUnderTest,
      DexVm.Version version,
      CompilationMode compilationMode) {
    return test(dexTool, compilerUnderTest, Runtime.fromDexVmVersion(version), compilationMode);
  }
}
