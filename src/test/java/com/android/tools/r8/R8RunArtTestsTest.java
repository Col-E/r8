// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestCondition.Runtime;
import com.android.tools.r8.TestCondition.RuntimeSet;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ArtErrorParser;
import com.android.tools.r8.utils.ArtErrorParser.ArtErrorInfo;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.ObjectArrays;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

/**
 * This test class is not invoked directly. Instead, the gradle script generates one subclass per
 * actual art test. This allows us to run these in parallel.
 */
public abstract class R8RunArtTestsTest extends TestBase {

  private static final boolean DEX_COMPARE_WITH_DEX_REFERENCE_ON_FAILURE = true;

  private final String name;
  private final DexTool toolchain;

  @Rule
  public ExpectedException thrown = ExpectedException.none();
  private boolean expectedException = false;

  public enum DexTool {
    DX,
    NONE // Working directly on .class files.
  }

  public enum CompilerUnderTest {
    D8,
    R8,
    R8_AFTER_D8, // refers to the R8 (default: debug) step but implies a previous D8 step as well
    D8_AFTER_R8CF,
    R8CF
  }

  public static final String ART_TESTS_DIR = "tests/2017-10-04/art";
  private static final String ART_LEGACY_TESTS_DIR = "tests/2016-12-19/art/";
  private static final String ART_TESTS_NATIVE_LIBRARY_DIR = "tests/2017-10-04/art/lib64";
  private static final String ART_LEGACY_TESTS_NATIVE_LIBRARY_DIR = "tests/2016-12-19/art/lib64";

  private static final RuntimeSet LEGACY_RUNTIME =
      TestCondition.runtimes(
          DexVm.Version.V4_0_4,
          DexVm.Version.V4_4_4,
          DexVm.Version.V5_1_1,
          DexVm.Version.V6_0_1,
          DexVm.Version.V7_0_0,
          DexVm.Version.V8_1_0,
          DexVm.Version.V9_0_0,
          DexVm.Version.V10_0_0,
          DexVm.Version.V12_0_0,
          DexVm.Version.V13_0_0,
          DexVm.Version.V14_0_0);

  private static final String JUNIT_TEST_RUNNER = "org.junit.runner.JUnitCore";
  private static final String JUNIT_JAR = "third_party/junit/junit-4.13-beta-2.jar";
  private static final String HAMCREST_JAR =
      "third_party/gradle/gradle/lib/plugins/hamcrest-core-1.3.jar";

  // Test that required to set min-api to a specific value.
  private static Map<String, AndroidApiLevel> needMinSdkVersion =
      new ImmutableMap.Builder<String, AndroidApiLevel>()
          .put("004-JniTest", AndroidApiLevel.N)
          // Has a non-abstract class with an abstract method.
          .put("800-smali", AndroidApiLevel.L)
          // Android O
          .put("952-invoke-custom", AndroidApiLevel.O)
          .put("952-invoke-custom-kinds", AndroidApiLevel.O)
          .put("953-invoke-polymorphic-compiler", AndroidApiLevel.O)
          .put("957-methodhandle-transforms", AndroidApiLevel.O)
          .put("958-methodhandle-stackframe", AndroidApiLevel.O)
          .put("959-invoke-polymorphic-accessors", AndroidApiLevel.O)
          .put("979-const-method-handle", AndroidApiLevel.P)
          .put("990-method-handle-and-mr", AndroidApiLevel.O)
          // Test intentionally asserts presence of bridge default methods desugar removes.
          .put("044-proxy", AndroidApiLevel.N)
          // Test intentionally asserts absence of default interface method in a class.
          .put("048-reflect-v8", AndroidApiLevel.N)
          // Uses default interface methods.
          .put("162-method-resolution", AndroidApiLevel.N)
          .put("616-cha-interface-default", AndroidApiLevel.N)
          .put("1910-transform-with-default", AndroidApiLevel.N)
          .put("960-default-smali", AndroidApiLevel.N)
          // Interface initializer is not triggered after desugaring.
          .put("962-iface-static", AndroidApiLevel.N)
          // Interface initializer is not triggered after desugaring.
          .put("964-default-iface-init-gen", AndroidApiLevel.N)
          .put("966-default-conflict", AndroidApiLevel.N)
          // AbstractMethodError (for method not implemented in class) instead of
          // IncompatibleClassChangeError (for conflict of default interface methods).
          .put("968-default-partial-compile-gen", AndroidApiLevel.N)
          // NoClassDefFoundError (for companion class) instead of NoSuchMethodError.
          .put("970-iface-super-resolution-gen", AndroidApiLevel.N)
          // NoClassDefFoundError (for companion class) instead of AbstractMethodError.
          .put("971-iface-super", AndroidApiLevel.N)
          // Test for miranda methods is not relevant for desugaring scenario.
          .put("972-default-imt-collision", AndroidApiLevel.N)
          // Uses default interface methods.
          .put("972-iface-super-multidex", AndroidApiLevel.N)
          // java.util.Objects is missing and test has default methods.
          .put("973-default-multidex", AndroidApiLevel.N)
          // a.klass.that.does.not.Exist is missing and test has default methods.
          .put("974-verify-interface-super", AndroidApiLevel.N)
          // Desugaring of interface private methods is not yet supported.
          .put("975-iface-private", AndroidApiLevel.N)
          .build();

  // Tests that timeout when run with Art.
  private static final Multimap<String, TestCondition> timeoutOrSkipRunWithArt =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // Loops on art - timeout.
          .put(
              "109-suspend-check",
              TestCondition.match(TestCondition.runtimes(DexVm.Version.V5_1_1)))
          // Flaky loops on art.
          .put("129-ThreadGetId", TestCondition.match(TestCondition.runtimes(DexVm.Version.V5_1_1)))
          // Takes ages to run on art 5.1.1 and behaves the same as on 6.0.1. Running this
          // tests on 5.1.1 makes our buildbot cycles time too long.
          .put("800-smali", TestCondition.match(TestCondition.runtimes(DexVm.Version.V5_1_1)))
          // Hangs on dalvik.
          .put(
              "802-deoptimization",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // TODO(b/144975341): Triage
          .put(
              "134-reg-promotion",
              TestCondition.match(TestCondition.runtimes(DexVm.Version.V10_0_0)))
          // TODO(b/197078742): Triage - test OOMs.
          .put(
              "134-reg-promotion",
              TestCondition.match(TestCondition.runtimes(DexVm.Version.V12_0_0)))
          // TODO(b/197078746): Triage - fails with "java.lang.NoSuchMethodException:
          //  org.apache.harmony.dalvik.ddmc.DdmVmInternal.enableRecentAllocations [boolean]"
          .put(
              "098-ddmc",
              TestCondition.match(
                  TestCondition.runtimes(
                      DexVm.Version.V12_0_0, DexVm.Version.V13_0_0, DexVm.Version.V14_0_0)))
          // TODO(b/197079442): Triage - fails with "java.lang.NoSuchMethodException:
          //  org.apache.harmony.dalvik.ddmc.DdmVmInternal.enableRecentAllocations [boolean]"
          .put(
              "145-alloc-tracking-stress",
              TestCondition.match(
                  TestCondition.runtimes(
                      DexVm.Version.V12_0_0, DexVm.Version.V13_0_0, DexVm.Version.V14_0_0)))
          .build();

  // Tests that are flaky with the Art version we currently use.
  // TODO(zerny): Amend flaky tests with an expected flaky result to track issues.
  private static Multimap<String, TestCondition> flakyRunWithArt =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // Sometime output also appends "Actually slept about X msec..."
          // So far only seen on the 5.1.1 runtime.
          .put("002-sleep", TestCondition.match(TestCondition.runtimes(DexVm.Version.V5_1_1)))
          // Can crash but mostly passes
          // Crashes:
          // check_reference_map_visitor.h:44] At Main.f
          // Check failed: size() >= sizeof(T) (size()=0, sizeof(T)=1)
          // If art is passed -Xrelocate instead of -Xnorelocate the test passes.
          .put("004-ReferenceMap", TestCondition.any())
          // Also marked flaky in the art repo, sometimes hangs, sometimes fails with a segfault:
          // line 105: 23283 Segmentation fault
          .put("004-ThreadStress", TestCondition.any())
          // When it fails:
          // stack_walk_jni.cc:57] Check failed: 0xdU == GetDexPc() (0xdU=13, GetDexPc()=23)
          // The R8/D8 code does not produce code with the same instructions.
          .put("004-StackWalk", TestCondition.any())
          // Nothing ensures the weak-ref is not cleared between makeRef and
          // printWeakReference on lines Main.java:78-79. An expected flaky
          // result contains: "but was:<wimp: [null]".
          .put("036-finalizer", TestCondition.any())
          // The test waits for a maximum of 500 ms which is unreliable when running on buildbots:
          // Elapsed time was too long: elapsed=552 max=550
          .put("053-wait-some", TestCondition.any())
          // Failed on buildbot with: terminate called after throwing an instance
          // of '__gnu_cxx::recursive_init_error'
          .put("096-array-copy-concurrent-gc",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Failing with: expected "second > first =[tru]e" was "second > first =[fals]e"
          .put("098-ddmc", TestCondition.match(TestCondition.runtimes(DexVm.Version.V6_0_1)))
          // Sometimes fails with out of memory on Dalvik.
          .put("114-ParallelGC",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Seen crash: currently no more information
          .put("144-static-field-sigquit", TestCondition.any())
          // Opens a lot of file descriptors and depending on the state of the machine this
          // can crash art or not. Skip the run on art.
          .put("151-OpenFileLimit", TestCondition.any())
          // Can cause a segfault in the art vm 7.0.0
          // tools/linux/art-7.0.0/bin/art: line 105: 14395 Segmentation fault
          .put("607-daemon-stress", TestCondition.any())
          // Marked as flaky in the Art repository.
          .put("149-suspend-all-stress", TestCondition.any())
          .build();

  // Tests that are never compiled or run.
  private static List<String> skipAltogether = ImmutableList.of(
      // Those tests contains an invalid type hierarchy, which we cannot currently handle.
      "065-mismatched-implements",
      "066-mismatched-super",
      // This test contains invalid dex code that reads uninitialized registers after an
      // an instruction that would in any case throw (implicit via aget null 0).
      "706-jit-skip-compilation",
      // This test uses a user defined class loader to validate correct execution order
      // between loadclass event and the execution of a static method.
      // This does not work when performing inlining across classes.
      "496-checker-inlining-class-loader",
      // These all test OOM behavior and segfault doing GC on some machines. We just ignore them.
      "080-oom-throw",
      "080-oom-fragmentation",
      "159-app-image-fields",
      "163-app-image-methods",
      "061-out-of-memory",
      "617-clinit-oome"
  );

  // Tests that may produce different output on consecutive runs or when processed or not.
  private static Multimap<String, TestCondition> outputMayDiffer =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // Art does fail during validation of dex files if it encounters an ill-typed
          // virtual-invoke
          // but allows interface-invokes to pass. As we rewrite one kind into the other, we remove
          // a verification error and thus change output.
          .put("135-MirandaDispatch", TestCondition.any())
          // One some versions of art, this will print the address of the boot classloader, which
          // may change between runs.
          .put("506-verify-aput", TestCondition.any())
          // We resolve a conflicting definition of default methods, thus removing an ICCE.
          .put("972-iface-super-multidex", TestCondition.any())
          .build();

  // Tests that make use of agents/native code.
  // Our test setup does not handle flags/linking of these.
  private static List<String> usesNativeAgentCode = ImmutableList.of(
      "497-inlining-and-class-loader",
      "626-const-class-linking",
      "642-fp-callees",
      "660-clinit",
      "909-attach-agent",
      "914-hello-obsolescence",
      "915-obsolete-2",
      "916-obsolete-jit",
      "917-fields-transformation",
      "918-fields",
      "919-obsolete-fields",
      "920-objects",
      "921-hello-failure",
      "922-properties",
      "923-monitors",
      "924-threads",
      "925-threadgroups",
      "926-multi-obsolescence",
      "927-timers",
      "928-jni-table",
      "929-search",
      "930-hello-retransform",
      "931-agent-thread",
      "932-transform-saves",
      "933-misc-events",
      "934-load-transform",
      "935-non-retransformable",
      "936-search-onload",
      "937-hello-retransform-package",
      "938-load-transform-bcp",
      "939-hello-transformation-bcp",
      "940-recursive-obsolete",
      "941-recurive-obsolete-jit",
      "942-private-recursive",
      "943-private-recursive-jit",
      "944-transform-classloaders",
      "945-obsolete-native",
      "946-obsolete-throw",
      "947-reflect-method",
      "948-change-annotations",
      "949-in-memory-transform",
      "950-redefine-intrinsic",
      "951-threaded-obsolete",
      "980-redefine-object",
      "1900-track-alloc",
      "1901-get-bytecodes",
      "1902-suspend",
      "1903-suspend-self",
      "1904-double-suspend",
      "1906-suspend-list-me-first",
      "1907-suspend-list-self-twice",
      "1909-per-agent-tls",
      "1910-transform-with-default",
      "1911-get-local-var-table",
      "1912-get-set-local-primitive",
      "1913-get-set-local-objects",
      "1914-get-local-instance",
      "1915-get-set-local-current-thread",
      "1916-get-set-current-frame",
      "1917-get-stack-frame",
      "1919-vminit-thread-start-timing",
      "1920-suspend-native-monitor",
      "1921-suspend-native-recursive-monitor",
      "1922-owned-monitors-info",
      "1923-frame-pop",
      "1924-frame-pop-toggle",
      "1925-self-frame-pop",
      "1926-missed-frame-pop",
      "1927-exception-event",
      "1928-exception-event-exception",
      "1929-exception-catch-exception",
      "1930-monitor-info",
      "1931-monitor-events",
      "1932-monitor-events-misc",
      "1933-monitor-current-contended",
      "1934-jvmti-signal-thread",
      "1935-get-set-current-frame-jit",
      "1936-thread-end-events",
      // These tests need a library name as parameter
      "164-resolution-trampoline-dex-cache",
      "597-deopt-invoke-stub",
      "597-deopt-busy-loop",
      "661-oat-writer-layout",
      "661-classloader-allocator",
      "664-aget-verifier"
  );

  // Tests with custom run.
  private static List<String> customRun = ImmutableList.of(
      "000-nop",
      "018-stack-overflow",
      "055-enum-performance",
      "071-dexfile-map-clean",
      "091-override-package-private-method",
      "103-string-append",
      "115-native-bridge",
      "116-nodex2oat",
      "117-nopatchoat",
      "118-noimage-dex2oat",
      "119-noimage-patchoat",
      "126-miranda-multidex",
      "127-checker-secondarydex",
      "131-structural-change",
      "133-static-invoke-super",
      "134-nodex2oat-nofallback",
      "137-cfi",
      "138-duplicate-classes-check2",
      "146-bad-interface",
      "147-stripped-dex-fallback",
      "304-method-tracing",
      "529-checker-unresolved",
      "555-checker-regression-x86const",
      "569-checker-pattern-replacement",
      "570-checker-osr",
      "574-irreducible-and-constant-area",
      "577-profile-foreign-dex",
      "595-profile-saving",
      "597-deopt-new-string",
      "608-checker-unresolved-lse",
      "613-inlining-dex-cache",
      "636-wrong-static-access",
      "900-hello-plugin",
      "901-hello-ti-agent",
      "902-hello-transformation",
      "910-methods",
      "911-get-stack-trace",
      "912-classes",
      "913-heaps"
  );

  // Tests with C++ code (JNI)
  private static List<String> useJNI = ImmutableList.of(
      "004-JniTest",
      "004-NativeAllocations",
      "004-ReferenceMap",
      "004-SignalTest",
      "004-StackWalk",
      "004-ThreadStress",
      "004-UnsafeTest",
      "044-proxy",
      "051-thread",
      "115-native-bridge",
      "117-nopatchoat",
      "136-daemon-jni-shutdown",
      "137-cfi",
      "139-register-natives",
      "141-class-unload",
      "148-multithread-gc-annotations",
      "149-suspend-all-stress",
      "154-gc-loop",
      "155-java-set-resolved-type",
      "157-void-class",
      "158-app-image-class-table",
      "454-get-vreg",
      "457-regs",
      "461-get-reference-vreg",
      "466-get-live-vreg",
      "497-inlining-and-class-loader",
      "543-env-long-ref",
      "566-polymorphic-inlining",
      "570-checker-osr",
      "595-profile-saving",
      "596-app-images",
      "597-deopt-new-string",
      "616-cha",
      "616-cha-abstract",
      "616-cha-interface",
      "616-cha-interface-default",
      "616-cha-miranda",
      "616-cha-regression-proxy-method",
      "616-cha-native",
      "616-cha-proxy-method-inline",
      "626-const-class-linking",
      "626-set-resolved-string",
      "900-hello-plugin",
      "901-hello-ti-agent",
      "1337-gc-coverage"
  );

  private static List<String> expectedToFailRunWithArtNonDefault = ImmutableList.of(
      // Fails due to missing symbol, jni tests, fails on non-R8/D8 run.
      "004-JniTest",
      "004-SignalTest",
      "004-ThreadStress",
      "004-UnsafeTest",
      "044-proxy",
      "051-thread",
      "136-daemon-jni-shutdown",
      "139-register-natives",
      "148-multithread-gc-annotations",
      "149-suspend-all-stress",
      "154-gc-loop",
      "155-java-set-resolved-type",
      "157-void-class",
      "158-app-image-class-table",
      "466-get-live-vreg",
      "497-inlining-and-class-loader",
      "566-polymorphic-inlining",
      "596-app-images",
      "616-cha",
      "616-cha-abstract",
      "616-cha-regression-proxy-method",
      "616-cha-native",
      "626-set-resolved-string",
      "629-vdex-speed",
      "1337-gc-coverage",

      // Fails on non-R8/D8 run
      "031-class-attributes"
  );

  private static Map<DexVm.Version, List<String>> expectedToFailRunWithArtVersion;

  static {
    ImmutableMap.Builder<DexVm.Version, List<String>> builder = ImmutableMap.builder();
    builder
        .put(DexVm.Version.V14_0_0, ImmutableList.of("543-env-long-ref", "518-null-array-get"))
        .put(DexVm.Version.V13_0_0, ImmutableList.of("543-env-long-ref", "518-null-array-get"))
        .put(DexVm.Version.V12_0_0, ImmutableList.of("543-env-long-ref", "518-null-array-get"))
        .put(
            DexVm.Version.V10_0_0,
            ImmutableList.of(
                // TODO(b/144975341): Triage, Verif error.
                "518-null-array-get",
                // TODO(b/144975341): Triage, Linking error.
                "543-env-long-ref"))
        .put(
            DexVm.Version.V9_0_0,
            ImmutableList.of(
                // TODO(120401674): Triage.
                "543-env-long-ref",
                // TODO(120261858) Triage.
                "518-null-array-get"))
        .put(DexVm.Version.V8_1_0, ImmutableList.of())
        .put(
            DexVm.Version.V7_0_0,
            ImmutableList.of(
                // Addition of checks for super-class-initialization cause this to abort on non-ToT
                // art.
                "008-exceptions",

                // Generally fails on non-R8/D8 running.
                "156-register-dex-file-multi-loader",
                "412-new-array",
                "610-arraycopy",
                "625-checker-licm-regressions"))
        .put(
            DexVm.Version.V6_0_1,
            ImmutableList.of(
                // Addition of checks for super-class-initialization cause this to abort on non-ToT
                // art.
                "008-exceptions",

                // Generally fails on non-R8/D8 running.
                "004-checker-UnsafeTest18",
                "005-annotations",
                "008-exceptions",
                "082-inline-execute",
                "099-vmdebug",
                "156-register-dex-file-multi-loader",
                "412-new-array",
                "580-checker-round",
                "594-invoke-super",
                "625-checker-licm-regressions",
                "626-const-class-linking"))
        .put(
            DexVm.Version.V5_1_1,
            ImmutableList.of(
                // Addition of checks for super-class-initialization cause this to abort on non-ToT
                // art.
                "008-exceptions",

                // Generally fails on non R8/D8 running.
                "004-checker-UnsafeTest18",
                "004-NativeAllocations",
                "005-annotations",
                "008-exceptions",
                "082-inline-execute",
                "099-vmdebug",
                "143-string-value",
                "156-register-dex-file-multi-loader",
                "536-checker-intrinsic-optimization",
                "552-invoke-non-existent-super",
                "580-checker-round",
                "580-checker-string-fact-intrinsics",
                "594-invoke-super",
                "605-new-string-from-bytes",
                "626-const-class-linking"))
        .put(
            DexVm.Version.V4_4_4,
            ImmutableList.of(
                // Addition of checks for super-class-initialization cause this to abort on non-ToT
                // art.
                "008-exceptions",

                // Generally fails on non R8/D8 running.
                "004-checker-UnsafeTest18",
                "004-NativeAllocations",
                "005-annotations",
                "008-exceptions",
                "082-inline-execute",
                "099-vmdebug",
                "143-string-value",
                "156-register-dex-file-multi-loader",
                "536-checker-intrinsic-optimization",
                "552-invoke-non-existent-super",
                "580-checker-round",
                "580-checker-string-fact-intrinsics",
                "594-invoke-super",
                "605-new-string-from-bytes",
                "626-const-class-linking"))
        .put(
            DexVm.Version.V4_0_4,
            ImmutableList.of(
                // Addition of checks for super-class-initialization cause this to abort on non-ToT
                // art.
                "008-exceptions",

                // Generally fails on non R8/D8 running.
                "004-checker-UnsafeTest18",
                "004-NativeAllocations",
                "005-annotations",
                "008-exceptions",
                "082-inline-execute",
                "099-vmdebug",
                "143-string-value",
                "156-register-dex-file-multi-loader",
                "536-checker-intrinsic-optimization",
                "552-invoke-non-existent-super",
                "580-checker-round",
                "580-checker-string-fact-intrinsics",
                "594-invoke-super",
                "605-new-string-from-bytes",
                "626-const-class-linking"));
    expectedToFailRunWithArtVersion = builder.build();
  }

  // Tests where the R8/D8 output runs in Art but the original does not.
  private static Multimap<String, TestCondition> failingRunWithArtOriginalOnly =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          .put("095-switch-MAX_INT",
              TestCondition.match(
                  TestCondition.runtimes(DexVm.Version.V4_0_4)))
          .build();

  // Tests where the output of R8 fails when run with Art.
  private static final Multimap<String, TestCondition> failingRunWithArt =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // The growth limit test fails after processing by R8 because R8 will eliminate an
          // "unneeded" const store. The following reflective call to the VM's GC will then see the
          // large array as still live and the subsequent allocations will fail to reach the desired
          // size before an out-of-memory error occurs. See:
          // tests/art/dx/104-growth-limit/src/Main.java:40
          .put(
              "104-growth-limit",
              TestCondition.match(TestCondition.R8_COMPILER, TestCondition.RELEASE_MODE))
          .put(
              "461-get-reference-vreg",
              TestCondition.match(
                  TestCondition.D8_COMPILER,
                  TestCondition.runtimes(
                      DexVm.Version.V7_0_0, DexVm.Version.V6_0_1, DexVm.Version.V5_1_1)))
          // Dalvik fails on reading an uninitialized local.
          .put(
              "471-uninitialized-locals",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Out of memory.
          .put(
              "152-dead-large-object",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Cannot resolve exception handler. Interestingly, D8 generates different code in
          // release mode (which is also the code generated by R8) which passes.
          .put(
              "111-unresolvable-exception",
              TestCondition.match(
                  TestCondition.D8_COMPILER, TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          .put(
              "534-checker-bce-deoptimization",
              TestCondition.match(
                  TestCondition.D8_COMPILER, TestCondition.runtimes(DexVm.Version.V6_0_1)))
          // Type not present.
          .put(
              "124-missing-classes",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Failed creating vtable.
          .put(
              "587-inline-class-error",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Failed creating vtable.
          .put(
              "595-error-class",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // NoSuchFieldException: systemThreadGroup on Art 4.4.4.
          .put(
              "129-ThreadGetId",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Verifier says: can't modify final field LMain;.staticFinalField.
          .put(
              "600-verifier-fails",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // VFY: tried to get class from non-ref register.
          .put(
              "506-verify-aput",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // NoSuchMethod: startMethodTracing.
          .put(
              "545-tracing-and-jit",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // filled-new-array arg 0(1) not valid.
          .put(
              "412-new-array",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // TODO(ager): unclear what is failing here.
          .put("098-ddmc", TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Unsatisfiable link error:
          // libarttest.so: undefined symbol: _ZN3art6Thread18RunEmptyCheckpointEv
          .put(
              "543-env-long-ref",
              TestCondition.match(
                  TestCondition.D8_COMPILER,
                  TestCondition.runtimes(
                      DexVm.Version.V8_1_0,
                      DexVm.Version.V7_0_0,
                      DexVm.Version.V6_0_1,
                      DexVm.Version.V5_1_1)))
          // lib64 libarttest.so: wrong ELF class ELFCLASS64.
          .put(
              "543-env-long-ref",
              TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // Leaving two static-get triggers LSE bug on 6.0.1 (b/25735083).
          // R8, with subtyping, knows the first sget is dead, and removing it avoids the bug.
          // Due to the lack of subtype hierarchy, D8 can't guarantee <clinit> side effects.
          .put(
              "550-new-instance-clinit",
              TestCondition.match(
                  TestCondition.D8_COMPILER, TestCondition.runtimes(DexVm.Version.V6_0_1)))
          // Regression test for an issue that is not fixed on version 5.1.1. Throws an Exception
          // instance instead of the expected NullPointerException. This bug is only tickled when
          // running the R8 generated code when starting from jar or from dex code generated with
          // dx. However, the code that R8 generates is valid and there is nothing we can do for
          // this one.
          .put(
              "551-implicit-null-checks",
              TestCondition.match(
                  TestCondition.tools(DexTool.NONE, DexTool.DX),
                  TestCondition.R8DEX_COMPILER,
                  TestCondition.runtimes(DexVm.Version.V5_1_1)))
          // Contains a method (B.<init>) which pass too few arguments to invoke. Also, contains an
          // iput on a static field.
          .put(
              "600-verifier-fails",
              TestCondition.match(
                  TestCondition.D8_COMPILER,
                  TestCondition.runtimes(
                      DexVm.Version.V7_0_0, DexVm.Version.V6_0_1, DexVm.Version.V5_1_1)))
          // Dalvik 4.0.4 is missing ReflectiveOperationException class.
          .put(
              "140-field-packing",
              TestCondition.match(TestCondition.runtimes(DexVm.Version.V4_0_4)))
          // Dalvik 4.0.4 is missing theUnsafe field.
          .put("528-long-hint", TestCondition.match(TestCondition.runtimes(DexVm.Version.V4_0_4)))
          // Cannot catch exception in Dalvik 4.0.4.
          .put("084-class-init", TestCondition.match(TestCondition.runtimes(DexVm.Version.V4_0_4)))
          // Tested regression still exists in Dalvik 4.0.4.
          .put(
              "301-abstract-protected",
              TestCondition.match(TestCondition.runtimes(DexVm.Version.V4_0_4)))
          // Illegal class flags in Dalvik 4.0.4.
          .put("121-modifiers", TestCondition.match(TestCondition.runtimes(DexVm.Version.V4_0_4)))
          .build();

  // Tests where the output of R8/D8 runs in Art but produces different output than the expected.txt
  // checked into the Art repo.
  private static final Multimap<String, TestCondition> failingRunWithArtOutput =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // This test assumes that class-retention annotations are preserved by the compiler and
          // then checks for backwards compatibility with M where they could incorrectly be observed
          // by the program at runtime.
          .put("005-annotations", TestCondition.match(TestCondition.D8_COMPILER))
          // On Art 4.4.4 we have fewer refs than expected (except for d8 when compiled with dx).
          .put(
              "072-precise-gc",
              TestCondition.match(
                  TestCondition.R8_COMPILER, TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          .put(
              "072-precise-gc",
              TestCondition.match(
                  TestCondition.tools(DexTool.NONE),
                  TestCondition.D8_COMPILER,
                  TestCondition.runtimesUpTo(DexVm.Version.V4_4_4)))
          // This one is expected to have different output. It counts instances, but the list that
          // keeps the instances alive is dead and could be garbage collected. The compiler reuses
          // the register for the list and therefore there are no live instances.
          .put("099-vmdebug", TestCondition.any())
          // This test relies on output on stderr, which we currently do not collect.
          .put("143-string-value", TestCondition.any())
          .put(
              "800-smali",
              TestCondition.match(
                  TestCondition.D8_COMPILER,
                  TestCondition.runtimes(DexVm.Version.V5_1_1, DexVm.Version.V6_0_1)))
          // Triggers regression test in 6.0.1 when using R8/D8 in debug mode.
          .put(
              "474-fp-sub-neg",
              TestCondition.match(
                  TestCondition.tools(DexTool.NONE),
                  TestCondition.D8_NOT_AFTER_R8CF_COMPILER,
                  TestCondition.runtimes(DexVm.Version.V6_0_1)))
          .put(
              "536-checker-needs-access-check",
              // DEX VMs incorrectly check access before checking null-value in instanceof.
              // See IllegalAccessDeadInstanceOfTest and b/288376353.
              TestCondition.anyDexVm())
          .build();

  private static final TestCondition beforeAndroidN =
      TestCondition
          .match(TestCondition
              .runtimes(DexVm.Version.V4_0_4, DexVm.Version.V4_4_4, DexVm.Version.V5_1_1,
                  DexVm.Version.V6_0_1));
  // TODO(herhut): Change to V8_0_0 once we have a new art VM.
  private static final TestCondition beforeAndroidO =
      TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V7_0_0));
  // TODO(herhut): Change to V8_0_0 once we have a new art VM.
  private static final TestCondition beforeAndroidP =
      TestCondition.match(TestCondition.runtimesUpTo(DexVm.Version.V7_0_0));

  // TODO(ager): Could we test that these fail in the way that we expect?
  private static final Multimap<String, TestCondition> expectedToFailRunWithArt =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // Contains bad finalizer that times out.
          .put("030-bad-finalizer", TestCondition.any())
          // Contains a direct method call with a null receiver.
          .put("034-call-null", TestCondition.any())
          // Testing uncaught exceptions.
          .put("054-uncaught", TestCondition.any())
          // Contains a null pointer exception.
          .put("038-inner-null", TestCondition.any())
          // Null pointer exception.
          .put("071-dexfile", TestCondition.any())
          // Array index out of bounds exception.
          .put("088-monitor-verification", TestCondition.any())
          // Attempts to run hprof.
          .put("130-hprof", TestCondition.any())
          // Null pointer exception. Test doesn't exist for dx.
          .put("138-duplicate-classes-check", TestCondition.any())
          // Array index out of bounds exception.
          .put("150-loadlibrary", TestCondition.any())
          // Fails due to non-matching Exception messages.
          .put(
              "201-built-in-except-detail-messages",
              TestCondition.or(
                  TestCondition.match(
                      TestCondition.compilers(
                          CompilerUnderTest.D8, CompilerUnderTest.D8_AFTER_R8CF),
                      TestCondition.runtimes(
                          DexVm.Version.V4_0_4,
                          DexVm.Version.V4_4_4,
                          DexVm.Version.V5_1_1,
                          DexVm.Version.V6_0_1,
                          DexVm.Version.V7_0_0,
                          DexVm.Version.V13_0_0,
                          DexVm.Version.V14_0_0)),
                  TestCondition.match(
                      TestCondition.compilers(
                          CompilerUnderTest.R8,
                          CompilerUnderTest.R8CF,
                          CompilerUnderTest.R8_AFTER_D8))))
          // Class.forName() that fails due to verification error is removed.
          .put(
              "412-new-array",
              TestCondition.match(
                  TestCondition.compilers(CompilerUnderTest.R8, CompilerUnderTest.R8_AFTER_D8)))
          // Array index out of bounds exception.
          .put("449-checker-bce", TestCondition.any())
          // Fails: get_vreg_jni.cc:46] Check failed: value == 42u (value=314630384, 42u=42)
          // The R8/D8 code does not produce values in the same registers as the tests expects in
          // Main.testPairVReg where Main.doNativeCall is called (v1 vs v0).
          .put(
              "454-get-vreg",
              TestCondition.match(
                  TestCondition.runtimes(
                      DexVm.Version.V4_0_4,
                      DexVm.Version.V4_4_4,
                      DexVm.Version.V5_1_1,
                      DexVm.Version.V6_0_1,
                      DexVm.Version.V7_0_0,
                      DexVm.Version.V8_1_0,
                      DexVm.Version.V9_0_0,
                      DexVm.Version.V10_0_0,
                      DexVm.Version.V12_0_0,
                      DexVm.Version.V13_0_0,
                      DexVm.Version.V14_0_0)))
          .put("454-get-vreg", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Fails: regs_jni.cc:42] Check failed: GetVReg(m, 0, kIntVReg, &value)
          // The R8/D8 code does not put values in the same registers as the tests expects.
          .put(
              "457-regs",
              TestCondition.match(
                  TestCondition.runtimes(
                      DexVm.Version.V4_0_4,
                      DexVm.Version.V4_4_4,
                      DexVm.Version.V5_1_1,
                      DexVm.Version.V6_0_1,
                      DexVm.Version.V7_0_0,
                      DexVm.Version.V8_1_0,
                      DexVm.Version.V9_0_0,
                      DexVm.Version.V10_0_0,
                      DexVm.Version.V12_0_0,
                      DexVm.Version.V13_0_0,
                      DexVm.Version.V14_0_0)))
          .put("457-regs", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Class not found.
          .put("529-checker-unresolved", TestCondition.any())
          // Fails: env_long_ref.cc:44] Check failed: GetVReg(m, 1, kReferenceVReg, &value)
          // The R8/D8 code does not produce values in the same registers as the tests expects in
          // the stack frame for TestCase.testCase checked by the native Main.lookForMyRegisters
          // (v1 vs v0).
          .put("543-env-long-ref", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Array index out of bounds exception.
          .put("555-UnsafeGetLong-regression", TestCondition.any())
          // Array index out of bounds exception.
          .put("563-checker-fakestring", TestCondition.any())
          // Array index out of bounds exception.
          .put("575-checker-string-init-alias", TestCondition.any())
          // Runtime exception.
          .put("577-profile-foreign-dex", TestCondition.any())
          // Class.forName() that fails due to linkage error is removed.
          .put(
              "587-inline-class-error",
              TestCondition.match(
                  TestCondition.compilers(CompilerUnderTest.R8, CompilerUnderTest.R8_AFTER_D8)))
          // Array index out of bounds exception.
          .put("602-deoptimizeable", TestCondition.any())
          // Array index out of bounds exception.
          .put("604-hot-static-interface", TestCondition.any())
          // Array index out of bounds exception.
          .put("612-jit-dex-cache", TestCondition.any())
          // Fails: get_reference_vreg_jni.cc:43] Check failed: GetVReg(m, 1, kReferenceVReg,
          // &value)
          // The R8 code does not put values in the same registers as the tests expects in
          // Main.testThisWithInstanceCall checked by the native Main.doNativeCallRef (v0 vs. v1 and
          // only 1 register instead fof 2).
          .put("461-get-reference-vreg", TestCondition.match(TestCondition.R8_COMPILER))
          // This test uses register r1 in method that is declared to only use 1 register (r0).
          // This is in dex code which D8 does not convert and which R8/CF does not process.
          // Therefore the error is a verification error at runtime and that is expected.
          .put("142-classloader2", TestCondition.match(TestCondition.D8_COMPILER))
          // Invoke-custom is supported by D8 and R8, but it can only run on our newest version
          // of art.
          .put("952-invoke-custom", beforeAndroidO)
          .put("952-invoke-custom-kinds", beforeAndroidO)
          // Invoke-polymorphic is supported by D8 and R8, but it can only run on our newest version
          // of art.
          .put("953-invoke-polymorphic-compiler", beforeAndroidO)
          .put("957-methodhandle-transforms", beforeAndroidO)
          .put("958-methodhandle-stackframe", beforeAndroidO)
          .put("959-invoke-polymorphic-accessors", beforeAndroidO)
          .put("044-proxy", beforeAndroidN) // --min-sdk = 24
          .put("048-reflect-v8", beforeAndroidN) // --min-sdk = 24
          .put("962-iface-static", beforeAndroidN) // --min-sdk = 24
          .put("964-default-iface-init-gen", beforeAndroidN) // --min-sdk = 24
          .put("968-default-partial-compile-gen", beforeAndroidN) // --min-sdk = 24
          .put("970-iface-super-resolution-gen", beforeAndroidN) // --min-sdk = 24
          .put("971-iface-super", beforeAndroidN) // --min-sdk = 24
          .put("972-default-imt-collision", beforeAndroidN) // --min-sdk = 24
          .put("973-default-multidex", beforeAndroidN) // --min-sdk = 24
          .put("974-verify-interface-super", beforeAndroidN) // --min-sdk = 24
          .put("975-iface-private", beforeAndroidN) // --min-sdk = 24
          .put("979-const-method-handle", beforeAndroidP)
          // Missing class junit.framework.Assert (see JunitAvailabilityInHostArtTest).
          // TODO(120884788): Add this again.
          /*
          .put(
              "021-string2",
              TestCondition.or(
                  TestCondition.match(
                      TestCondition.compilers(CompilerUnderTest.D8_AFTER_R8CF),
                      TestCondition.runtimesFrom(DexVm.Version.V7_0_0)),
                  TestCondition.match(
                      TestCondition.compilers(CompilerUnderTest.D8_AFTER_R8CF),
                      TestCondition.runtimes(DexVm.Version.V4_0_4, DexVm.Version.V4_4_4))))
          */
          // Missing class junit.framework.Assert (see JunitAvailabilityInHostArtTest).
          // TODO(120884788): Add this again.
          /*
          .put(
              "082-inline-execute",
              TestCondition.or(
                  TestCondition.match(
                      TestCondition.compilers(CompilerUnderTest.D8_AFTER_R8CF),
                      TestCondition.runtimesFrom(DexVm.Version.V7_0_0)),
                  TestCondition.match(
                      TestCondition.compilers(CompilerUnderTest.D8_AFTER_R8CF),
                      TestCondition.runtimes(DexVm.Version.V4_0_4, DexVm.Version.V4_4_4))))
          */
          .build();

  // Tests where code generation fails.
  private static final Multimap<String, TestCondition> failingWithCompiler =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // Contains two methods with the same name and signature but different code.
          .put("097-duplicate-method", TestCondition.any())
          // Dex code contains a method (B.<init>) which pass too few arguments to invoke, and it
          // also contains an iput on a static field.
          .put("600-verifier-fails", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // R8 generates too large code in Goto.bigGoto(). b/74327727
          .put("003-omnibus-opcodes", TestCondition.match(TestCondition.D8_AFTER_R8CF_COMPILER))
          .build();

  // Tests that are invalid dex files and on which R8/D8 fails and that is OK.
  private static final Multimap<String, TestCondition> expectedToFailWithCompiler =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // When starting from the Jar frontend we see the A$B class both from the Java source
          // code and from the smali dex code. We reject that because there are then two definitions
          // of the same class in the application. When running from the final dex files there is
          // only one A$B class because of a custom build script that merges them.
          .put("121-modifiers", TestCondition.match(TestCondition.tools(DexTool.NONE)))
          // This test uses register r1 in method that is declared to only use 1 register (r0).
          .put("142-classloader2", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test with invalid register usage: invoke-static {v2,v2,v2} f(LIF)V
          .put("457-regs", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // This test uses an uninitialized register.
          .put("471-uninitialized-locals", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test which mixes int and float registers.
          .put("459-dead-phi", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test for verification error: contains an aput-object with an single-valued input.
          .put("506-verify-aput", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test with invalid register usage: returns a register of either long or double.
          .put("510-checker-try-catch", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test with invalid register usage: contains an int-to-byte on the result of aget-object.
          .put("518-null-array-get", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test with invalid register usage: phi of int and float.
          .put("535-regression-const-val", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test with invalid register usage: phi of int and float.
          .put("552-checker-primitive-typeprop", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Test with invalid register usage: invoke-static {v0,v0}, foo(IL)V
          .put("557-checker-ref-equivalent", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // This test is starting from invalid dex code. It splits up a double value and uses
          // the first register of a double with the second register of another double.
          .put("800-smali", TestCondition.match(TestCondition.R8DEX_COMPILER))
          // Contains a loop in the class hierarchy.
          .put("804-class-extends-itself", TestCondition.any())
          // These tests have illegal class flag combinations, so we reject them.
          .put("161-final-abstract-class", TestCondition.any())
          .build();

  // Tests that does not have dex input for some toolchains.
  private static Multimap<String, TestCondition> noInputDex =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          .put("914-hello-obsolescence", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("915-obsolete-2", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("916-obsolete-jit", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("919-obsolete-fields", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("926-multi-obsolescence", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("941-recurive-obsolete-jit", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("940-recursive-obsolete", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("942-private-recursive", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("943-private-recursive-jit", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("945-obsolete-native", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("948-change-annotations", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("952-invoke-custom", TestCondition.match(
              TestCondition.tools(DexTool.NONE, DexTool.DX),
              LEGACY_RUNTIME))
          .put("952-invoke-custom-kinds",
              TestCondition.match(TestCondition.tools(DexTool.DX)))
          .put("979-const-method-handle",
              TestCondition.match(TestCondition.tools(DexTool.DX)))
          .build();

  // Tests that does not have valid input for us to be compatible with dx running.
  private static List<String> noInputJar = ImmutableList.of(
      "097-duplicate-method", // No input class files.
      "630-safecast-array", // No input class files.
      "801-VoidCheckCast", // No input class files.
      "804-class-extends-itself", // No input class files.
      "972-iface-super-multidex", // Based on multiple smali files
      "973-default-multidex", // Based on multiple smali files.
      "974-verify-interface-super", // No input class files.
      "975-iface-private", // No input class files.
      "976-conflict-no-methods", // No input class files
      "978-virtual-interface", // No input class files
      "663-odd-dex-size", // No input class files
      "663-odd-dex-size2", // No input class files
      "663-odd-dex-size3", // No input class files
      "663-odd-dex-size4" // No input class files
  );

  // Tests to skip on some conditions
  private static final Multimap<String, TestCondition> testToSkip =
      new ImmutableListMultimap.Builder<String, TestCondition>()
          // When running R8 on dex input (D8, DX) this test non-deterministically fails
          // with a compiler exception, due to invoke-virtual on an interface, or it completes but
          // the output when run on Art is not as expected. b/65233869
          .put(
              "162-method-resolution",
              TestCondition.match(
                  TestCondition.tools(DexTool.DX, DexTool.NONE), TestCondition.R8_COMPILER))
          // Produces wrong output when compiled in release mode, which we cannot express.
          .put("015-switch", TestCondition.match(TestCondition.runtimes(DexVm.Version.V4_0_4)))
          .build();

  public static List<String> requireInliningToBeDisabled =
      ImmutableList.of(
          // Test for a specific stack trace that gets destroyed by inlining.
          "008-exceptions",
          "492-checker-inline-invoke-interface",
          "493-checker-inline-invoke-interface",
          "488-checker-inline-recursive-calls",
          "487-checker-inline-calls",
          "122-npe",
          "141-class-unload",

          // Calls some internal art methods that cannot tolerate inlining.
          "466-get-live-vreg",

          // Requires a certain call pattern to surface an Art bug.
          "534-checker-bce-deoptimization",

          // Requires something to be allocated in a method so that it goes out of scope.
          "059-finalizer-throw",

          // Has tests in submethods, which we should not inline.
          "625-checker-licm-regressions");

  private static List<String> requireClassInliningToBeDisabled = ImmutableList.of(
      // Test is registered to be failing (failingRunWithArt), it fails only on 4.0.4
      // (because of a bug in this version of dalvik) but succeeds on later versions.
      // If class inliner us enabled it actually works fine on 4.0.4 since the class
      // instantiation is properly inlined.
      "301-abstract-protected",
      // Test depends on exception produced for missing method or similar cases, but
      // after class inlining removes class instantiations and references the exception
      // is not produced.
      "435-new-instance"
  );

  private static List<String> hasMissingClasses =
      ImmutableList.of(
          "003-omnibus-opcodes",
          "004-UnsafeTest",
          "004-checker-UnsafeTest18",
          "005-annotations",
          "067-preemptive-unpark",
          "071-dexfile-map-clean",
          "091-override-package-private-method",
          "111-unresolvable-exception",
          "124-missing-classes",
          "127-checker-secondarydex",
          "138-duplicate-classes-check2",
          "140-field-packing",
          "143-string-value",
          "151-OpenFileLimit",
          "435-new-instance",
          "528-long-hint",
          "529-checker-unresolved",
          "555-UnsafeGetLong-regression",
          "585-inline-unresolved",
          "606-erroneous-class",
          "608-checker-unresolved-lse",
          "659-unpadded-array",
          "706-checker-scheduler",
          "709-checker-varhandles",
          "803-no-super",
          "912-classes",
          "952-invoke-custom-kinds",
          // Depends on java.lang.invoke.Transformers, which is hidden.
          "958-methodhandle-stackframe",
          "1338-gc-no-los");

  private static final String NO_CLASS_ACCESS_MODIFICATION_RULE =
      "-keep,allowobfuscation,allowoptimization,allowshrinking class *";
  private static final String NO_MEMBER_ACCESS_MODIFICATION_RULE =
      "-keepclassmembers,allowobfuscation,allowoptimization,allowshrinking class * { *; }";

  private static Map<String, List<String>> keepRules =
      ImmutableMap.of(
          "005-annotations",
          ImmutableList.of(NO_MEMBER_ACCESS_MODIFICATION_RULE),
          "021-string2",
          ImmutableList.of("-dontwarn junit.framework.**"),
          "042-new-instance",
          ImmutableList.of(NO_MEMBER_ACCESS_MODIFICATION_RULE),
          "046-reflect",
          ImmutableList.of(NO_MEMBER_ACCESS_MODIFICATION_RULE),
          "064-field-access",
          ImmutableList.of(NO_MEMBER_ACCESS_MODIFICATION_RULE),
          "082-inline-execute",
          ImmutableList.of("-dontwarn junit.framework.**"),
          "100-reflect2",
          ImmutableList.of(NO_CLASS_ACCESS_MODIFICATION_RULE),
          "301-abstract-protected",
          ImmutableList.of(
              "-keepclassmembers,allowobfuscation,allowoptimization,allowshrinking class Abstract {"
                  + "  protected abstract java.lang.String m();"
                  + "}"),
          "435-new-instance",
          ImmutableList.of(NO_CLASS_ACCESS_MODIFICATION_RULE),
          // Constructor MakeBoundType.<init>(int) is called using reflection.
          "476-checker-ctor-fence-redun-elim",
          ImmutableList.of(
              "-keep class TestDontOptimizeAcrossEscape$MakeBoundTypeTest$MakeBoundType {",
              "  void <init>(int);",
              "}"));

  private static Map<String, Consumer<InternalOptions>> configurations =
      ImmutableMap.of(
          // Has a new-instance instruction that attempts to instantiate an interface.
          "162-method-resolution", options -> options.testing.disableStackMapVerification = true,
          "435-new-instance", options -> options.testing.allowTypeErrors = true);

  private static List<String> failuresToTriage = ImmutableList.of(
      // Dex file input into a jar file, not yet supported by the test framework.
      "663-odd-dex-size",
      "663-odd-dex-size2",
      "663-odd-dex-size3",
      "663-odd-dex-size4",

      // This is flaky.
      "104-growth-limit",

      // Various failures.
      "138-duplicate-classes-check",
      "461-get-reference-vreg",
      "629-vdex-speed",
      "638-no-line-number",
      "647-jni-get-field-id",
      "649-vdex-duplicate-method",
      "652-deopt-intrinsic",
      "655-jit-clinit",
      "656-annotation-lookup-generic-jni",
      "656-loop-deopt",
      "707-checker-invalid-profile",
      "708-jit-cache-churn",

      // These use "native trace".
      "981-dedup-original-dex",
      "982-ok-no-retransform",
      "983-source-transform-verify",
      "984-obsolete-invoke",
      "985-re-obsolete",
      "986-native-method-bind",
      "987-agent-bind",
      "988-method-trace",
      "989-method-trace-throw",
      "990-field-trace",
      "991-field-trace-2",
      "992-source-data",
      "993-breakpoints",
      "994-breakpoint-line",
      "995-breakpoints-throw",
      "996-breakpoint-obsolete",
      "997-single-step",

      // These two fail with missing *-hostdex.jar files.
      "648-inline-caches-unresolved",
      "998-redefine-use-after-free"
  );

  private static class TestSpecification {

    // Name of the Art test
    private final String name;
    // Directory of the test files (containing prebuild dex/jar files and expected output).
    private final File directory;
    // Compiler that these expectations are for dx, or none if running on class files.
    private final DexTool dexTool;
    // Native library to use for running this test - if any.
    private final String nativeLibrary;
    // Skip running this test with Art or Java - most likely due to timeout.
    private final boolean skipRun;
    // Skip running this test altogether. For example, there might be no input in this configuration
    // (e.g. no class files).
    private final boolean skipTest;
    // Fails compilation - most likely throws an exception.
    private final boolean failsWithX8;
    // Expected to fail compilation with a CompilationError.
    private final boolean expectedToFailWithX8;
    // Fails running the output in Art or on Java with an assertion error. On Art it's typically due
    // to verification errors.
    private final boolean failsOnRun;
    // Runs in art but fails the run because it produces different results.
    private final boolean failsWithArtOutput;
    // Original fails in art but the R8/D8 version can run in art.
    private final boolean failsWithArtOriginalOnly;
    // Test might produce different outputs.
    private final boolean outputMayDiffer;
    // Whether to disable inlining
    private final boolean disableInlining;
    // Whether to disable class inlining
    private final boolean disableClassInlining;
    // Has missing classes.
    private final boolean hasMissingClasses;
    // Explicitly disable desugaring.
    private final boolean disableDesugaring;
    // Extra keep rules to use when running with R8.
    private final List<String> keepRules;
    private final Consumer<InternalOptions> configuration;

    TestSpecification(
        String name,
        DexTool dexTool,
        File directory,
        boolean skipRun,
        boolean skipTest,
        boolean failsWithX8,
        boolean failsOnRun,
        boolean failsWithArtOutput,
        boolean failsWithArtOriginalOnly,
        String nativeLibrary,
        boolean expectedToFailWithX8,
        boolean outputMayDiffer,
        boolean disableInlining,
        boolean disableClassInlining,
        boolean hasMissingClasses,
        boolean disableDesugaring,
        List<String> keepRules,
        Consumer<InternalOptions> configuration) {
      this.name = name;
      this.dexTool = dexTool;
      this.nativeLibrary = nativeLibrary;
      this.directory = directory;
      this.skipRun = skipRun;
      this.skipTest = skipTest;
      this.failsWithX8 = failsWithX8;
      this.failsOnRun = failsOnRun;
      this.failsWithArtOutput = failsWithArtOutput;
      this.failsWithArtOriginalOnly = failsWithArtOriginalOnly;
      this.expectedToFailWithX8 = expectedToFailWithX8;
      this.outputMayDiffer = outputMayDiffer;
      this.disableInlining = disableInlining;
      this.disableClassInlining = disableClassInlining;
      this.hasMissingClasses = hasMissingClasses;
      this.disableDesugaring = disableDesugaring;
      this.keepRules = keepRules;
      this.configuration = configuration;
    }

    public File resolveFile(String name) {
      return directory.toPath().resolve(name).toFile();
    }

    public String toString() {
      return name + " (" + dexTool + ")";
    }
  }

  private static class SpecificationKey {

    private final String name;
    private final DexTool toolchain;

    private SpecificationKey(String name, DexTool toolchain) {
      assert name != null;
      this.name = name;
      this.toolchain = toolchain;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SpecificationKey)) {
        return false;
      }

      SpecificationKey that = (SpecificationKey) o;
      return name.equals(that.name) && toolchain == that.toolchain;
    }

    @Override
    public int hashCode() {
      return 31 * name.hashCode() + toolchain.hashCode();
    }
  }

  private static Set<String> collectTestsMatchingConditions(
      DexTool dexTool,
      CompilerUnderTest compilerUnderTest,
      DexVm.Version dexVmVersion,
      CompilationMode mode,
      Multimap<String, TestCondition> testConditionsMap) {
    Set<String> set = Sets.newHashSet();
    for (Map.Entry<String, TestCondition> kv : testConditionsMap.entries()) {
      if (kv.getValue().test(dexTool, compilerUnderTest, dexVmVersion, mode)) {
        set.add(kv.getKey());
      }
    }
    return set;
  }

  private static Map<SpecificationKey, TestSpecification> getTestsMap(
      CompilerUnderTest compilerUnderTest, CompilationMode compilationMode, DexVm dexVm) {
    DexVm.Version version = dexVm.getVersion();
    File defaultArtTestDir = new File(ART_TESTS_DIR);
    File legacyArtTestDir = new File(ART_LEGACY_TESTS_DIR);
    if (!defaultArtTestDir.exists() || !legacyArtTestDir.exists()) {
      // Don't run any tests if the directory does not exist.
      return Collections.emptyMap();
    }
    Map<SpecificationKey, TestSpecification> data = new HashMap<>();

    // Collect tests where running Art is skipped (we still run R8/D8 on these).
    Set<String> skipArt = new HashSet<>(customRun);

    // Collect the tests requiring the native library.
    Set<String> useNativeLibrary = Sets.newHashSet(useJNI);
    for (DexTool dexTool : DexTool.values()) {
      Set<String> skipTest = Sets.newHashSet(skipAltogether);
      skipTest.addAll(usesNativeAgentCode);
      skipTest.addAll(failuresToTriage);

      File artTestDir =
          LEGACY_RUNTIME.contains(Runtime.fromDexVmVersion(version))
              ? legacyArtTestDir
              : defaultArtTestDir;
      // Collect the tests failing code generation.
      Set<String> failsWithCompiler =
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, failingWithCompiler);

      // Collect the tests that are flaky.
      skipArt.addAll(collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, flakyRunWithArt));

      // Collect tests that has no input:
      if (dexTool == DexTool.NONE) {
        skipTest.addAll(noInputJar);
      }

      // Collect the test that we should skip in this configuration because there is no dex input
      skipTest.addAll(collectTestsMatchingConditions(
          dexTool, compilerUnderTest, version, compilationMode, noInputDex));

      // Collect the test that we should skip in this configuration
      skipTest.addAll(collectTestsMatchingConditions(
          dexTool, compilerUnderTest, version, compilationMode, testToSkip));

      // Collect the test that we should skip in this configuration.
      skipArt.addAll(
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, timeoutOrSkipRunWithArt));

      // Collect the tests failing to run in Art (we still run R8/D8 on these).
      Set<String> failsWithArt =
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, failingRunWithArt);
      {
        Set<String> tmpSet =
            collectTestsMatchingConditions(
                dexTool, compilerUnderTest, version, compilationMode, expectedToFailRunWithArt);
        failsWithArt.addAll(tmpSet);
      }

      if (!ToolHelper.isDefaultDexVm(dexVm)) {
        // Generally failing when not TOT art.
        failsWithArt.addAll(expectedToFailRunWithArtNonDefault);
        // Version specific failures
        failsWithArt.addAll(expectedToFailRunWithArtVersion.get(version));
      }

      // Collect the tests failing with output differences in Art.
      Set<String> failsRunWithArtOutput =
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, failingRunWithArtOutput);
      Set<String> expectedToFailWithCompilerSet =
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, expectedToFailWithCompiler);

      // Collect the tests where the original works in Art and the R8/D8 generated output does not.
      Set<String> failsRunWithArtOriginalOnly =
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, failingRunWithArtOriginalOnly);

      Set<String> testsWhereOutputMayDiffer =
          collectTestsMatchingConditions(
              dexTool, compilerUnderTest, version, compilationMode, outputMayDiffer);

      File compilerTestDir = artTestDir.toPath().resolve(dexToolDirectory(dexTool)).toFile();
      File[] testDirs = compilerTestDir.listFiles();
      assert testDirs != null;
      for (File testDir : testDirs) {
        String name = testDir.getName();
        boolean skip = skipTest.contains(name);
        // All the native code for all Art tests is currently linked into the
        // libarttest.so file.
        data.put(
            new SpecificationKey(name, dexTool),
            new TestSpecification(
                name,
                dexTool,
                testDir,
                skipArt.contains(name),
                skip || ToolHelper.isWindows() && dexVm.getKind() == Kind.HOST,
                failsWithCompiler.contains(name),
                failsWithArt.contains(name),
                failsRunWithArtOutput.contains(name),
                failsRunWithArtOriginalOnly.contains(name),
                useNativeLibrary.contains(name) ? "arttest" : null,
                expectedToFailWithCompilerSet.contains(name),
                testsWhereOutputMayDiffer.contains(name),
                requireInliningToBeDisabled.contains(name),
                requireClassInliningToBeDisabled.contains(name),
                hasMissingClasses.contains(name),
                false,
                keepRules.getOrDefault(name, ImmutableList.of()),
                configurations.get(name)));
      }
    }
    return data;
  }

  private static CompilationMode defaultCompilationMode(CompilerUnderTest compilerUnderTest) {
    CompilationMode compilationMode = null;
    switch (compilerUnderTest) {
      case R8:
      case R8CF:
        compilationMode = CompilationMode.RELEASE;
        break;
      case D8:
      case R8_AFTER_D8:
      case D8_AFTER_R8CF:
        compilationMode = CompilationMode.DEBUG;
        break;
      default:
        throw new RuntimeException("Unreachable.");
    }
    return compilationMode;
  }

  private static String dexToolDirectory(DexTool tool) {
    // DexTool.NONE uses class files in the dx directory.
    return "dx";
  }

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  public R8RunArtTestsTest(String name, DexTool toolchain) {
    this.name = name;
    this.toolchain = toolchain;
  }

  private ArtCommandBuilder buildArtCommand(
      File dexFile, TestSpecification specification, DexVm artVersion) {
    ArtCommandBuilder builder = new ArtCommandBuilder(artVersion);
    builder.appendClasspath(dexFile.toString());
    // All Art tests have the main class Main.
    builder.setMainClass("Main");
    if (specification.nativeLibrary != null) {
      // All the native libraries for all Art tests is in the same directory.
      File artTestNativeLibraryDir = new File(ART_TESTS_NATIVE_LIBRARY_DIR);
      if (artVersion != DexVm.ART_DEFAULT) {
        artTestNativeLibraryDir = new File(ART_LEGACY_TESTS_NATIVE_LIBRARY_DIR);
      }
      builder.addToJavaLibraryPath(artTestNativeLibraryDir);
      builder.appendProgramArgument(specification.nativeLibrary);
    }
    return builder;
  }

  private static class CompilationOptions implements Consumer<InternalOptions> {

    private final boolean disableInlining;
    private final boolean disableClassInlining;
    private final boolean hasMissingClasses;
    private final boolean disableDesugaring;
    private final List<String> keepRules;
    private final Consumer<InternalOptions> configuration;

    private CompilationOptions(TestSpecification spec) {
      this.disableInlining = spec.disableInlining;
      this.disableClassInlining = spec.disableClassInlining;
      this.hasMissingClasses = spec.hasMissingClasses;
      this.disableDesugaring = spec.disableDesugaring;
      this.keepRules = spec.keepRules;
      this.configuration = spec.configuration;
    }

    @Override
    public void accept(InternalOptions options) {
      if (disableInlining) {
        options.inlinerOptions().enableInlining = false;
      }
      if (disableClassInlining) {
        options.enableClassInlining = false;
      }
      // Some tests actually rely on missing classes for what they test.
      options.ignoreMissingClasses = hasMissingClasses;
      if (configuration != null) {
        configuration.accept(options);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CompilationOptions options = (CompilationOptions) o;
      return disableInlining == options.disableInlining
          && disableClassInlining == options.disableClassInlining
          && hasMissingClasses == options.hasMissingClasses;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          disableInlining,
          disableClassInlining,
          hasMissingClasses);
    }
  }

  private void executeCompilerUnderTest(
      CompilerUnderTest compilerUnderTest,
      Collection<String> fileNames,
      String resultPath,
      CompilationMode compilationMode,
      CompilationOptions compilationOptions)
      throws CompilationFailedException {
    executeCompilerUnderTest(
        compilerUnderTest, fileNames, resultPath, compilationMode, null, compilationOptions);
  }

  private void executeCompilerUnderTest(
      CompilerUnderTest compilerUnderTest,
      Collection<String> fileNames,
      String resultPath,
      CompilationMode mode,
      String keepRulesFile,
      CompilationOptions compilationOptions)
      throws CompilationFailedException {
    assert mode != null;
    switch (compilerUnderTest) {
      case D8_AFTER_R8CF:
        {
          assert keepRulesFile == null : "Keep-rules file specified for D8.";

          List<ProgramResource> dexInputs = new ArrayList<>();
          List<ProgramResource> cfInputs = new ArrayList<>();
          for (String f : fileNames) {
            Path p = Paths.get(f);
            if (FileUtils.isDexFile(p)) {
              dexInputs.add(ProgramResource.fromFile(ProgramResource.Kind.DEX, p));
            } else if (FileUtils.isClassFile(p)) {
              cfInputs.add(ProgramResource.fromFile(ProgramResource.Kind.CF, p));
            } else {
              assert FileUtils.isArchive(p);
              ArchiveProgramResourceProvider provider =
                  ArchiveProgramResourceProvider.fromArchive(p);

              try {
                for (ProgramResource pr : provider.getProgramResources()) {
                  if (pr.getKind() == ProgramResource.Kind.DEX) {
                    dexInputs.add(pr);
                  } else {
                    assert pr.getKind() == ProgramResource.Kind.CF;
                    cfInputs.add(pr);
                  }
                }
              } catch (ResourceException e) {
                throw new CompilationError("", e);
              }
            }
          }

          D8Command.Builder builder =
              D8Command.builder()
                  .setMode(mode)
                  .addProgramResourceProvider(
                      new ProgramResourceProvider() {
                        @Override
                        public Collection<ProgramResource> getProgramResources() {
                          return dexInputs;
                        }
                      })
                  .setOutput(Paths.get(resultPath), OutputMode.DexIndexed);

          Origin cfOrigin =
              new Origin(Origin.root()) {
                @Override
                public String part() {
                  return "R8/CF";
                }
              };

          R8Command.Builder r8builder =
              R8Command.builder()
                  .setMode(mode)
                  .setDisableTreeShaking(true)
                  .setDisableMinification(true)
                  .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown())
                  .addProguardConfiguration(compilationOptions.keepRules, Origin.unknown())
                  .setProgramConsumer(
                      new ClassFileConsumer() {

                        @Override
                        public synchronized void accept(
                            ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                          builder.addClassProgramData(data.copyByteData(), cfOrigin);
                        }

                        @Override
                        public void finished(DiagnosticsHandler handler) {}
                      })
                  .addProgramResourceProvider(
                      new ProgramResourceProvider() {
                        @Override
                        public Collection<ProgramResource> getProgramResources() {
                          return cfInputs;
                        }
                      });

          AndroidApiLevel minSdkVersion = needMinSdkVersion.get(name);
          if (minSdkVersion != null) {
            builder.setMinApiLevel(minSdkVersion.getLevel());
            builder.addLibraryFiles(ToolHelper.getAndroidJar(minSdkVersion));
            r8builder.addLibraryFiles(ToolHelper.getAndroidJar(minSdkVersion));
          } else {
            builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.getDefault()));
            r8builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.getDefault()));
          }
          ToolHelper.runR8(r8builder.build(), options -> options.ignoreMissingClasses = true);
          D8.run(builder.build());
          break;
        }
      case D8: {
        assert keepRulesFile == null : "Keep-rules file specified for D8.";
          D8Command.Builder builder =
              D8Command.builder()
                  .setMode(mode)
                  .addProgramFiles(ListUtils.map(fileNames, Paths::get))
                  .setOutput(Paths.get(resultPath), OutputMode.DexIndexed)
                  .setDisableDesugaring(compilationOptions.disableDesugaring);
        AndroidApiLevel minSdkVersion = needMinSdkVersion.get(name);
        if (minSdkVersion != null) {
          builder.setMinApiLevel(minSdkVersion.getLevel());
          builder.addLibraryFiles(ToolHelper.getAndroidJar(minSdkVersion));
        } else {
          builder
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.getDefault()));
        }
          ToolHelper.runD8(builder, compilationOptions::accept);
        break;
      }
      case R8:
      case R8CF:
        {
          boolean cfBackend = compilerUnderTest == CompilerUnderTest.R8CF;
          R8Command.Builder builder =
              R8Command.builder()
                  .setMode(mode)
                  .setDisableTreeShaking(true)
                  .setDisableMinification(true)
                  .setDisableDesugaring(compilationOptions.disableDesugaring)
                  .addProguardConfiguration(ImmutableList.of("-dontoptimize"), Origin.unknown())
                  .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown())
                  .addProguardConfiguration(compilationOptions.keepRules, Origin.unknown())
                  .setOutput(
                      Paths.get(resultPath),
                      cfBackend ? OutputMode.ClassFile : OutputMode.DexIndexed);
          ToolHelper.allowTestProguardOptions(builder);
          // Add program files directly to the underlying app to avoid errors on DEX inputs.
          ToolHelper.getAppBuilder(builder).addProgramFiles(ListUtils.map(fileNames, Paths::get));
          if (cfBackend) {
            builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
          } else {
            AndroidApiLevel minSdkVersion = needMinSdkVersion.get(name);
            if (minSdkVersion != null) {
              builder.setMinApiLevel(minSdkVersion.getLevel());
              ToolHelper.addFilteredAndroidJar(builder, minSdkVersion);
            } else {
              ToolHelper.addFilteredAndroidJar(builder, AndroidApiLevel.getDefault());
            }
          }
          if (keepRulesFile != null) {
            builder.addProguardConfigurationFiles(Paths.get(keepRulesFile));
          }
          // Add internal flags for testing purposes.
          ToolHelper.runR8(
              builder.build(),
              options -> {
                compilationOptions.accept(options);
                // Make sure we don't depend on this settings.
                options.classInlinerOptions().classInliningInstructionAllowance = 10000;
                options.lineNumberOptimization = LineNumberOptimization.OFF;
              });
          break;
        }
      default:
        assert false : compilerUnderTest;
    }
  }

  private static boolean isAuxClassFile(String fileName, String auxClassFileBase) {
    return fileName.endsWith(".class")
        && (fileName.startsWith(auxClassFileBase + "$")
        || fileName.startsWith(auxClassFileBase + "_"));
  }


  private static Runtime getRuntime(TestRuntime vm) {
    if (vm.isCf()) {
      return Runtime.JAVA;
    } else if (vm.isDex()) {
      return Runtime.fromDexVmVersion(vm.asDex().getVm().getVersion());
    } else {
      throw new Unreachable();
    }
  }

  private static class VmSpec {
    final TestRuntime vm;
    final TestSpecification spec;

    private VmSpec(TestRuntime vm, TestSpecification testSpecification) {
      this.vm = vm;
      this.spec = testSpecification;
    }
  }

  private static class VmErrors {
    private final Set<TestRuntime> failedVms = new HashSet<>();
    private StringBuilder message;

    private void addShouldHaveFailedError(CompilerUnderTest compilerUnderTest, TestRuntime vm) {
      addFailure(vm);
      message.append(
          "FAILURE: Test should have failed on "
              + vm
              + " after compiling with "
              + compilerUnderTest
              + ".\n");
    }

    private void addFailedOnRunError(
        CompilerUnderTest compilerUnderTest, TestRuntime vm, AssertionError error) {
      addFailure(vm);
      message.append(
          "FAILURE: Test failed on "
              + vm
              + " after compiling with "
              + compilerUnderTest
              + ", error:\n"
              + error.getMessage()
              + "\n");
    }

    private void addFailure(TestRuntime vm) {
      if (message == null) {
        message = new StringBuilder();
      }
      failedVms.add(vm);
    }
  }

  protected void runArtTest(DexVm dexVm, CompilerUnderTest compilerUnderTest) throws Throwable {
    CompilerUnderTest firstCompilerUnderTest =
        compilerUnderTest == CompilerUnderTest.R8_AFTER_D8
            ? CompilerUnderTest.D8
            : compilerUnderTest;

    CompilationMode compilationMode = defaultCompilationMode(compilerUnderTest);

    TestSpecification specification =
        getTestsMap(firstCompilerUnderTest, compilationMode, dexVm)
            .get(new SpecificationKey(name, toolchain));

    if (specification == null) {
      if (dexVm.getVersion() == DexVm.Version.DEFAULT) {
        throw new RuntimeException("Test " + name + " has no specification for toolchain"
            + toolchain + ".");
      } else {
        // For older VMs the test might not exist, as the tests are currently generates from the
        // directories present in the art test directory for AOSP master.
        return;
      }
    }

    if (specification.skipTest) {
      return;
    }

    if (specification.nativeLibrary != null && dexVm.getKind() == Kind.TARGET) {
      // JNI tests not yet supported for devices
      return;
    }

    File[] inputFiles;
    if (toolchain == DexTool.NONE) {
      inputFiles = addFileTree(new File[0], new File(specification.directory, "classes"));
      inputFiles = addFileTree(inputFiles, new File(specification.directory, "jasmin_classes"));
      File smali = new File(specification.directory, "smali");
      if (smali.exists()) {
        File smaliDex = new File(smali, "out.dex");
        assert smaliDex.exists();
        inputFiles = ObjectArrays.concat(inputFiles, smaliDex);
      }
      inputFiles = addFileTree(inputFiles, new File(specification.directory, "classes2"));
      inputFiles = addFileTree(inputFiles, new File(specification.directory, "jasmin_classes2"));
    } else {
      inputFiles =
          specification.directory.listFiles((File file) ->
              file.getName().endsWith(".dex") && !file.getName().startsWith("jasmin"));
    }

    List<String> fileNames = new ArrayList<>();
    for (File file : inputFiles) {
      fileNames.add(file.getCanonicalPath());
    }

    File resultDir =
        temp.newFolder(firstCompilerUnderTest.toString().toLowerCase(Locale.ROOT) + "-output");

    runArtTestDoRunOnArt(
        dexVm, firstCompilerUnderTest, specification, fileNames, resultDir, compilationMode);

    if (compilerUnderTest == CompilerUnderTest.R8_AFTER_D8) {
      if (expectedException) {
        // The expected exception was not thrown while running D8.
        return;
      }

      compilationMode = CompilationMode.DEBUG;
      specification =
          getTestsMap(CompilerUnderTest.R8_AFTER_D8, compilationMode, dexVm)
              .get(new SpecificationKey(name, DexTool.DX));

      if (specification == null) {
        throw new RuntimeException(
            "Test " + name + " has no specification for toolchain" + toolchain + ".");
      }

      if (specification.skipTest) {
        return;
      }

      fileNames.clear();
      for (File file : resultDir.listFiles((File file) -> file.getName().endsWith(".dex"))) {
        fileNames.add(file.getCanonicalPath());
      }

      resultDir = temp.newFolder("r8-output");

      runArtTestDoRunOnArt(
          dexVm, CompilerUnderTest.R8, specification, fileNames, resultDir, compilationMode);
    }
  }

  private File[] addFileTree(File[] files, File directory) {
    if (!directory.exists()) {
      return files;
    }
    return ObjectArrays.concat(
        files,
        Iterables.toArray(
            StreamSupport.stream(
                    com.google.common.io.Files.fileTraverser()
                        .breadthFirst(directory)
                        .spliterator(),
                    false)
                .filter(f -> !f.isDirectory())
                .collect(Collectors.toList()),
            File.class),
        File.class);
  }

  private void runArtTestDoRunOnArt(
      DexVm version,
      CompilerUnderTest compilerUnderTest,
      TestSpecification specification,
      List<String> fileNames,
      File resultDir,
      CompilationMode compilationMode)
      throws Throwable {
    if (specification.expectedToFailWithX8) {
      expectedException = true;
      try {
        executeCompilerUnderTest(
            compilerUnderTest,
            fileNames,
            resultDir.getCanonicalPath(),
            compilationMode,
            new CompilationOptions(specification));
      } catch (CompilationFailedException | CompilationError e) {
        expectException(CompilationError.class);
        throw new CompilationError(e.getMessage(), e);
      }
      throw new RuntimeException("Should have failed R8/D8 compilation with a CompilationError.");
    } else if (specification.failsWithX8) {
      expectException(Throwable.class);
      executeCompilerUnderTest(
          compilerUnderTest,
          fileNames,
          resultDir.getCanonicalPath(),
          compilationMode,
          new CompilationOptions(specification));
      System.err.println("Should have failed R8/D8 compilation with an exception.");
      return;
    } else {
      executeCompilerUnderTest(
          compilerUnderTest,
          fileNames,
          resultDir.getCanonicalPath(),
          compilationMode,
          new CompilationOptions(specification));
    }

    if (!specification.skipRun
        && (ToolHelper.artSupported() || ToolHelper.dealsWithGoldenFiles())) {
      File originalFile;
      File processedFile;

      // Collect the generated dex files.
      File[] outputFiles =
          resultDir.listFiles((File file) -> file.getName().endsWith(".dex"));
      if (outputFiles.length == 1) {
        // Just run Art on classes.dex.
        processedFile = outputFiles[0];
      } else {
        // Run Art on JAR file with multiple dex files.
        processedFile
            = temp.getRoot().toPath().resolve(specification.name + ".jar").toFile();
        buildJar(outputFiles, processedFile);
      }

      File expectedFile = specification.resolveFile("expected.txt");
      String expected =
          com.google.common.io.Files.asCharSource(expectedFile, Charsets.UTF_8).read();
      if (specification.failsOnRun) {
        expectException(AssertionError.class);
      }

      ArtCommandBuilder builder = buildArtCommand(processedFile, specification, version);
      String output;
      try {
        output = ToolHelper.runArt(builder);
      } catch (AssertionError e) {
        addDexInformationToVerificationError(fileNames, processedFile,
            specification.resolveFile("classes.dex"), e);
        throw e;
      }
      if (specification.failsOnRun) {
        System.err.println("Should have failed run with art");
        return;
      }

      File checkCommand = specification.resolveFile("check");
      if (checkCommand.exists() && !ToolHelper.isWindows()) {
        // Run the Art test custom check command.
        File actualFile = temp.newFile();
        com.google.common.io.Files.asByteSink(actualFile).write(output.getBytes(Charsets.UTF_8));
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(
            specification.resolveFile("check").toString(), expectedFile.toString(),
            actualFile.toString());
        ProcessResult result = ToolHelper.runProcess(processBuilder);
        if (result.exitCode != 0 && !specification.failsWithArtOutput) {
          System.err.println("ERROR: check script failed. Building comparison of dex files");
          failWithDexDiff(specification.resolveFile("classes.dex"), processedFile);
        }
      } else {
        if (!expected.equals(output)) {
          // The expected.txt in the Android repository might not match what our version of Art
          // produces.
          originalFile = specification.resolveFile(specification.name + ".jar");
          if (specification.failsWithArtOriginalOnly) {
            expectException(AssertionError.class);
          }
          builder = buildArtCommand(originalFile, specification, version);
          expected = ToolHelper.runArt(builder);
          if (specification.failsWithArtOriginalOnly) {
            System.err.println("Original file should have failed run with art");
            return;
          }
        }
        if (specification.failsWithArtOutput) {
          expectException(ComparisonFailure.class);
        }
        if (!specification.outputMayDiffer) {
          assertEquals(expected, output);
        }
      }
    }
  }

  private void expectException(Class<? extends Throwable> exception) {
    thrown.expect(exception);
    expectedException = true;
  }

  private void failWithDexDiff(File originalFile, File processedFile) throws IOException {
    CodeInspector inspectOriginal =
        new CodeInspector(originalFile.toPath().toAbsolutePath());
    CodeInspector inspectProcessed =
        new CodeInspector(processedFile.toPath().toAbsolutePath());
    StringBuilder builderOriginal = new StringBuilder();
    StringBuilder builderProcessed = new StringBuilder();
    inspectOriginal.forAllClasses((clazz) -> builderOriginal.append(clazz.dumpMethods()));
    inspectProcessed.forAllClasses((clazz) -> builderProcessed.append(clazz.dumpMethods()));
    assertEquals(builderOriginal.toString(), builderProcessed.toString());
    fail();
  }

  private void addDexInformationToVerificationError(
      Collection<String> inputFiles, File processedFile, File referenceFile,
      AssertionError verificationError) {
    List<ComparisonFailure> errors;
    try {
      // Parse all the verification errors.
      CodeInspector processed = new CodeInspector(processedFile.toPath());
      CodeInspector original = DEX_COMPARE_WITH_DEX_REFERENCE_ON_FAILURE
          ? new CodeInspector(referenceFile.toPath())
          : new CodeInspector(inputFiles.stream().map(Paths::get).collect(Collectors.toList()));
      List<ArtErrorInfo> errorInfo = ArtErrorParser.parse(verificationError.getMessage());
      errors = ListUtils.map(errorInfo, (error) ->
          new ComparisonFailure(
              error.getMessage(),
              "ORIGINAL\n" + error.dump(original, false) + "\nEND ORIGINAL",
              "PROCESSED\n" + error.dump(processed, true) + "\nEND PROCESSED"));
    } catch (Throwable e) {
      System.err.println("Failed to add extra dex information to the verification error:");
      e.printStackTrace();
      throw verificationError;
    }

    // If we failed to annotate anything, rethrow the original exception.
    if (errors.isEmpty()) {
      throw verificationError;
    }

    // Otherwise, we print each error and throw the last one, since Intellij only supports nice
    // comparison-diff if thrown and we can only throw one :-(
    System.err.println(verificationError.getMessage());
    for (ComparisonFailure error : errors.subList(0, errors.size() - 1)) {
      System.err.println(error.toString());
    }
    throw errors.get(errors.size() - 1);
  }

  // TODO(zerny): Refactor tests to output jar files directly and eliminate this method.
  private static void buildJar(File[] files, File jarFile) throws IOException {
    try (JarOutputStream target = new JarOutputStream(new FileOutputStream(jarFile))) {
      for (File file : files) {
        // Only use the file name in the JAR entry (classes.dex, classes2.dex, ...)
        JarEntry entry = new JarEntry(file.getName());
        entry.setTime(file.lastModified());
        target.putNextEntry(entry);
        Files.copy(file.toPath(), target);
        target.closeEntry();
      }
    }
  }
}
