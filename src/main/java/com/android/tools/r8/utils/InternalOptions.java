// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.IROrdering.IdentityIROrdering;
import com.android.tools.r8.utils.IROrdering.NondeterministicIROrdering;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class InternalOptions {

  // Set to true to run compilation in a single thread and without randomly shuffling the input.
  // This makes life easier when running R8 in a debugger.
  public static final boolean DETERMINISTIC_DEBUGGING = false;

  public enum LineNumberOptimization {
    OFF,
    ON,
    IDENTITY_MAPPING
  }

  public final DexItemFactory itemFactory;
  public final ProguardConfiguration proguardConfiguration;
  public final Reporter reporter;

  // TODO(zerny): Make this private-final once we have full program-consumer support.
  public ProgramConsumer programConsumer = null;

  public final List<DataResourceProvider> dataResourceProviders = new ArrayList<>();
  public DataResourceConsumer dataResourceConsumer;

  // Constructor for testing and/or other utilities.
  public InternalOptions() {
    reporter = new Reporter(new DefaultDiagnosticsHandler());
    itemFactory = new DexItemFactory();
    proguardConfiguration = ProguardConfiguration.defaultConfiguration(itemFactory, reporter);
  }

  // Constructor for D8.
  public InternalOptions(DexItemFactory factory, Reporter reporter) {
    assert reporter != null;
    assert factory != null;
    this.reporter = reporter;
    itemFactory = factory;
    proguardConfiguration = ProguardConfiguration.defaultConfiguration(itemFactory, reporter);
  }

  // Constructor for R8.
  public InternalOptions(ProguardConfiguration proguardConfiguration, Reporter reporter) {
    assert reporter != null;
    assert proguardConfiguration != null;
    this.reporter = reporter;
    this.proguardConfiguration = proguardConfiguration;
    itemFactory = proguardConfiguration.getDexItemFactory();
    // -dontoptimize disables optimizations by flipping related flags.
    if (!proguardConfiguration.isOptimizing()) {
      enableVerticalClassMerging = false;
      enableDevirtualization = false;
      enableNonNullTracking = false;
      enableInlining = false;
      enableClassInlining = false;
      enableClassStaticizer = false;
      enableSwitchMapRemoval = false;
      outline.enabled = false;
      enableValuePropagation = false;
    }
  }

  public boolean printTimes = System.getProperty("com.android.tools.r8.printtimes") != null;

  // Flag to toggle if DEX code objects should pass-through without IR processing.
  public boolean passthroughDexCode = false;

  // Optimization-related flags. These should conform to -dontoptimize.
  public boolean enableVerticalClassMerging = true;
  public boolean enableDevirtualization = true;
  public boolean enableNonNullTracking = true;
  public boolean enableInlining =
      !Version.isDev() || System.getProperty("com.android.tools.r8.disableinlining") == null;
  public boolean enableClassInlining = true;
  public boolean enableClassStaticizer = true;
  public int classInliningInstructionLimit = 50;
  public int inliningInstructionLimit = 5;
  public boolean enableSwitchMapRemoval = true;
  public final OutlineOptions outline = new OutlineOptions();
  public boolean enableValuePropagation = true;

  // Number of threads to use while processing the dex files.
  public int numberOfThreads = DETERMINISTIC_DEBUGGING ? 1 : ThreadUtils.NOT_SPECIFIED;
  // Print smali disassembly.
  public boolean useSmaliSyntax = false;
  // Verbose output.
  public boolean verbose = false;
  // Silencing output.
  public boolean quiet = false;
  // Throw exception if there is a warning about invalid debug info.
  public boolean invalidDebugInfoFatal = false;

  // Hidden marker for classes.dex
  private boolean hasMarker = false;
  private Marker marker;

  public boolean hasMarker() {
    return hasMarker;
  }

  public void setMarker(Marker marker) {
    this.hasMarker = true;
    this.marker = marker;
  }

  public Marker getMarker() {
    assert hasMarker();
    return marker;
  }

  public boolean isGeneratingDex() {
    return isGeneratingDexIndexed() || isGeneratingDexFilePerClassFile();
  }

  public boolean isGeneratingDexIndexed() {
    return programConsumer instanceof DexIndexedConsumer;
  }

  public boolean isGeneratingDexFilePerClassFile() {
    return programConsumer instanceof DexFilePerClassFileConsumer;
  }

  public boolean isGeneratingClassFiles() {
    return programConsumer instanceof ClassFileConsumer;
  }

  public DexIndexedConsumer getDexIndexedConsumer() {
    return (DexIndexedConsumer) programConsumer;
  }

  public DexFilePerClassFileConsumer getDexFilePerClassFileConsumer() {
    return (DexFilePerClassFileConsumer) programConsumer;
  }

  public ClassFileConsumer getClassFileConsumer() {
    return (ClassFileConsumer) programConsumer;
  }

  public void signalFinishedToConsumers() {
    if (programConsumer != null) {
      programConsumer.finished(reporter);
      if (dataResourceConsumer != null) {
        dataResourceConsumer.finished(reporter);
      }
    }
  }

  public List<String> methodsFilter = ImmutableList.of();
  public int minApiLevel = AndroidApiLevel.getDefault().getLevel();
  // Skipping min_api check and compiling an intermediate result intended for later merging.
  // Intermediate builds also emits or update synthesized classes mapping.
  public boolean intermediate = false;
  public List<String> logArgumentsFilter = ImmutableList.of();

  // Flag to turn on/off lambda class merging in R8.
  public boolean enableLambdaMerging = false;
  // Flag to turn on/off desugaring in D8/R8.
  public boolean enableDesugaring = true;
  // Defines interface method rewriter behavior.
  public OffOrAuto interfaceMethodDesugaring = OffOrAuto.Auto;
  // Defines try-with-resources rewriter behavior.
  public OffOrAuto tryWithResourcesDesugaring = OffOrAuto.Auto;
  // Flag to turn on/off processing of @dalvik.annotation.codegen.CovariantReturnType and
  // @dalvik.annotation.codegen.CovariantReturnType$CovariantReturnTypes.
  public boolean processCovariantReturnTypeAnnotations = true;

  // Whether or not to check for valid multi-dex builds.
  //
  // For min-api levels that did not support native multi-dex the user should provide a main dex
  // list. However, DX, didn't check that this was the case. Therefore, for CompatDX we have a flag
  // to disable the check that the build makes sense for multi-dexing.
  public boolean enableMainDexListCheck = true;

  public boolean enableTreeShaking = true;

  public boolean printCfg = false;
  public String printCfgFile;
  public boolean ignoreMissingClasses = false;
  // EXPERIMENTAL flag to get behaviour as close to Proguard as possible.
  public boolean forceProguardCompatibility = false;
  public boolean enableMinification = true;
  public boolean disableAssertions = true;
  public boolean debugKeepRules = false;
  // Read input classes into CfCode format (instead of JarCode).
  public boolean enableCfFrontend = false;
  // Don't convert Code objects to IRCode.
  public boolean skipIR = false;

  public boolean debug = false;
  public final TestingOptions testing = new TestingOptions();

  public ImmutableList<ProguardConfigurationRule> mainDexKeepRules = ImmutableList.of();
  public boolean minimalMainDex;
  /**
   * Enable usage of InheritanceClassInDexDistributor for multidex legacy builds.
   * This allows distribution of classes to minimize DexOpt LinearAlloc usage by minimizing linking
   * errors during DexOpt and controlling the load of classes with linking issues.
   * This has the consequence of making minimal main dex not absolutely minimal regarding runtime
   * execution constraints because it's adding classes in the main dex to satisfy also DexOpt
   * constraints.
   */
  public boolean enableInheritanceClassInDexDistributor = true;

  public LineNumberOptimization lineNumberOptimization = LineNumberOptimization.ON;

  public static class InvalidParameterAnnotationInfo {

    final DexMethod method;
    final int expectedParameterCount;
    final int actualParameterCount;

    public InvalidParameterAnnotationInfo(
        DexMethod method, int expectedParameterCount, int actualParameterCount) {
      this.method = method;
      this.expectedParameterCount = expectedParameterCount;
      this.actualParameterCount = actualParameterCount;
    }
  }

  private static class TypeVersionPair {

    final int version;
    final DexType type;

    public TypeVersionPair(int version, DexType type) {
      this.version = version;
      this.type = type;
    }
  }

  private final Map<Origin, List<TypeVersionPair>> missingEnclosingMembers = new HashMap<>();

  private final Map<Origin, List<InvalidParameterAnnotationInfo>> warningInvalidParameterAnnotations
      = new HashMap<>();

  private final Map<Origin, List<Pair<DexEncodedMethod, String>>> warningInvalidDebugInfo
      = new HashMap<>();

  // Don't read code from dex files. Used to extract non-code information from vdex files where
  // the code contains unsupported byte codes.
  public boolean skipReadingDexCode = false;

  // If null, no main-dex list needs to be computed.
  // If non null it must be and passed to the consumer.
  public StringConsumer mainDexListConsumer = null;

  // If null, no proguad map needs to be computed.
  // If non null it must be and passed to the consumer.
  public StringConsumer proguardMapConsumer = null;

  // If null, no proguad seeds info needs to be computed.
  // If non null it must be and passed to the consumer.
  public StringConsumer proguardSeedsConsumer = null;

  // If null, no usage information needs to be computed.
  // If non-null, it must be and is passed to the consumer.
  public StringConsumer usageInformationConsumer = null;

  // If null, no configuration information needs to be printed.
  // If non-null, configuration must be passed to the consumer.
  public StringConsumer configurationConsumer = null;

  public Path proguardCompatibilityRulesOutput = null;

  public static boolean assertionsEnabled() {
    boolean assertionsEnabled = false;
    assert assertionsEnabled = true; // Intentional side-effect.
    return assertionsEnabled;
  }

  public void warningMissingEnclosingMember(DexType clazz, Origin origin, int version) {
    TypeVersionPair pair = new TypeVersionPair(version, clazz);
    synchronized (missingEnclosingMembers) {
      missingEnclosingMembers.computeIfAbsent(origin, k -> new ArrayList<>()).add(pair);
    }
  }

  public void warningInvalidParameterAnnotations(
      DexMethod method, Origin origin, int expected, int actual) {
    InvalidParameterAnnotationInfo info =
        new InvalidParameterAnnotationInfo(method, expected, actual);
    synchronized (warningInvalidParameterAnnotations) {
      warningInvalidParameterAnnotations.computeIfAbsent(origin, k -> new ArrayList<>()).add(info);
    }
  }

  public void warningInvalidDebugInfo(
      DexEncodedMethod method, Origin origin, InvalidDebugInfoException e) {
    if (invalidDebugInfoFatal) {
      throw new CompilationError("Fatal warning: Invalid debug info", e);
    }
    synchronized (warningInvalidDebugInfo) {
      warningInvalidDebugInfo.computeIfAbsent(
          origin, k -> new ArrayList<>()).add(new Pair<>(method, e.getMessage()));
    }
  }

  public boolean printWarnings() {
    boolean printed = false;
    boolean printOutdatedToolchain = false;
    if (warningInvalidParameterAnnotations.size() > 0) {
      // TODO(b/67626202): Add a regression test with a program that hits this issue.
      reporter.info(
          new StringDiagnostic(
              "Invalid parameter counts in MethodParameter attributes. "
                  + "This is likely due to Proguard having removed a parameter."));
      for (Origin origin : new TreeSet<>(warningInvalidParameterAnnotations.keySet())) {
        StringBuilder builder =
            new StringBuilder("Methods with invalid MethodParameter attributes:");
        for (InvalidParameterAnnotationInfo info : warningInvalidParameterAnnotations.get(origin)) {
          builder
              .append("\n  ")
              .append(info.method)
              .append(" expected count: ")
              .append(info.expectedParameterCount)
              .append(" actual count: ")
              .append(info.actualParameterCount);
        }
        reporter.info(new StringDiagnostic(builder.toString(), origin));
      }
      printed = true;
    }
    if (warningInvalidDebugInfo.size() > 0) {
      int count = 0;
      for (List<Pair<DexEncodedMethod, String>> methods : warningInvalidDebugInfo.values()) {
        count += methods.size();
      }
      reporter.info(
          new StringDiagnostic(
              "Stripped invalid locals information from "
                  + count
                  + (count == 1 ? " method." : " methods.")));
      for (Origin origin : new TreeSet<>(warningInvalidDebugInfo.keySet())) {
        StringBuilder builder = new StringBuilder("Methods with invalid locals information:");
        for (Pair<DexEncodedMethod, String> method : warningInvalidDebugInfo.get(origin)) {
          builder.append("\n  ").append(method.getFirst().toSourceString());
          builder.append("\n  ").append(method.getSecond());
        }
        reporter.info(new StringDiagnostic(builder.toString(), origin));
      }
      printed = true;
      printOutdatedToolchain = true;
    }
    if (missingEnclosingMembers.size() > 0) {
      reporter.info(
          new StringDiagnostic(
              "InnerClass annotations are missing corresponding EnclosingMember annotations."
                  + " Such InnerClass annotations are ignored."));
      for (Origin origin : new TreeSet<>(missingEnclosingMembers.keySet())) {
        StringBuilder builder = new StringBuilder("Classes with missing enclosing members: ");
        boolean first = true;
        for (TypeVersionPair pair : missingEnclosingMembers.get(origin)) {
          if (first) {
            first = false;
          } else {
            builder.append(", ");
          }
          builder.append(pair.type);
          printOutdatedToolchain |= pair.version < 49;
        }
        reporter.info(new StringDiagnostic(builder.toString(), origin));
      }
      printed = true;
    }
    if (printOutdatedToolchain) {
      reporter.info(
          new StringDiagnostic(
              "Some warnings are typically a sign of using an outdated Java toolchain."
                  + " To fix, recompile the source with an updated toolchain."));
    }
    return printed;
  }

  public boolean hasMethodsFilter() {
    return methodsFilter.size() > 0;
  }

  public boolean methodMatchesFilter(DexEncodedMethod method) {
    // Not specifying a filter matches all methods.
    if (!hasMethodsFilter()) {
      return true;
    }
    // Currently the filter is simple string equality on the qualified name.
    String qualifiedName = method.qualifiedName();
    return methodsFilter.indexOf(qualifiedName) >= 0;
  }

  public boolean methodMatchesLogArgumentsFilter(DexEncodedMethod method) {
    // Not specifying a filter matches no methods.
    if (logArgumentsFilter.size() == 0) {
      return false;
    }
    // Currently the filter is simple string equality on the qualified name.
    String qualifiedName = method.qualifiedName();
    return logArgumentsFilter.indexOf(qualifiedName) >= 0;
  }

  public enum PackageObfuscationMode {
    // General package obfuscation.
    NONE,
    // Repackaging all classes into the single user-given (or top-level) package.
    REPACKAGE,
    // Repackaging all packages into the single user-given (or top-level) package.
    FLATTEN
  }

  public static class OutlineOptions {

    public static final String CLASS_NAME = "r8.GeneratedOutlineSupport";
    public static final String METHOD_PREFIX = "outline";

    public boolean enabled = true;
    public int minSize = 3;
    public int maxSize = 99;
    public int threshold = 20;
  }

  public static class TestingOptions {

    public IROrdering irOrdering =
        InternalOptions.assertionsEnabled() && !InternalOptions.DETERMINISTIC_DEBUGGING
            ? NondeterministicIROrdering.getInstance()
            : IdentityIROrdering.getInstance();

    public boolean alwaysUsePessimisticRegisterAllocation = false;
    public boolean invertConditionals = false;
    public boolean placeExceptionalBlocksLast = false;
    public boolean dontCreateMarkerInD8 = false;
    public boolean forceJumboStringProcessing = false;
    public boolean nondeterministicCycleElimination = false;
    public Set<Inliner.Reason> validInliningReasons = null;
    public boolean suppressExperimentalCfBackendWarning = false;
  }

  public boolean canUseInvokePolymorphicOnVarHandle() {
    return hasMinApi(AndroidApiLevel.P);
  }

  public boolean canUseInvokePolymorphic() {
    return hasMinApi(AndroidApiLevel.O);
  }

  public boolean canUseConstantMethodHandle() {
    return hasMinApi(AndroidApiLevel.P);
  }

  private boolean hasMinApi(AndroidApiLevel level) {
    return isGeneratingClassFiles() || minApiLevel >= level.getLevel();
  }

  public boolean canUseConstantMethodType() {
    return hasMinApi(AndroidApiLevel.P);
  }

  public boolean canUseInvokeCustom() {
    return hasMinApi(AndroidApiLevel.O);
  }

  public boolean canUseDefaultAndStaticInterfaceMethods() {
    return hasMinApi(AndroidApiLevel.N);
  }

  public boolean canLeaveStaticInterfaceMethodInvokes() {
    return hasMinApi(AndroidApiLevel.L);
  }

  public boolean canUseTwrCloseResourceMethod() {
    return hasMinApi(AndroidApiLevel.K);
  }

  public boolean canUsePrivateInterfaceMethods() {
    return hasMinApi(AndroidApiLevel.N);
  }

  public boolean canUseMultidex() {
    return intermediate || hasMinApi(AndroidApiLevel.L);
  }

  public boolean canUseLongCompareAndObjectsNonNull() {
    return hasMinApi(AndroidApiLevel.K);
  }

  public boolean canUseSuppressedExceptions() {
    return hasMinApi(AndroidApiLevel.K);
  }

  // APIs for accessing parameter names annotations are not available before Android O, thus does
  // not emit them to avoid wasting space in Dex files because runtimes before Android O will ignore
  // them.
  public boolean canUseParameterNameAnnotations() {
    return hasMinApi(AndroidApiLevel.O);
  }

  // Dalvik x86-atom backend had a bug that made it crash on filled-new-array instructions for
  // arrays of objects. This is unfortunate, since this never hits arm devices, but we have
  // to disallow filled-new-array of objects for dalvik until kitkat. The buggy code was
  // removed during the jelly-bean release cycle and is not there from kitkat.
  //
  // Buggy code that accidentally call code that only works on primitives arrays.
  //
  // https://android.googlesource.com/platform/dalvik/+/ics-mr0/vm/mterp/out/InterpAsm-x86-atom.S#25106
  public boolean canUseFilledNewArrayOfObjects() {
    return hasMinApi(AndroidApiLevel.K);
  }

  // Art had a bug (b/68761724) for Android N and O in the arm32 interpreter
  // where an aget-wide instruction using the same register for the array
  // and the first register of the result could lead to the wrong exception
  // being thrown on out of bounds.
  public boolean canUseSameArrayAndResultRegisterInArrayGetWide() {
    return minApiLevel > AndroidApiLevel.O_MR1.getLevel();
  }

  // Some Lollipop versions of Art found in the wild perform invalid bounds
  // check elimination. There is a fast path of loops and a slow path.
  // The bailout to the slow path is performed too early and therefore
  // the array-index variable might not be defined in the slow path code leading
  // to use of undefined registers as indices into arrays. The result
  // is ArrayIndexOutOfBounds exceptions.
  //
  // In an attempt to help these Art VMs, all single-width constants are initialized and not moved.
  //
  // There is no guarantee that this works, but it does make the problem
  // disappear on the one known instance of this problem.
  //
  // See b/69364976 and b/77996377.
  public boolean canHaveBoundsCheckEliminationBug() {
    return minApiLevel < AndroidApiLevel.M.getLevel();
  }

  // MediaTek JIT compilers for KitKat phones did not implement the not
  // instruction as it was not generated by DX. Therefore, apps containing
  // not instructions would crash if the code was JIT compiled. Therefore,
  // we can only use not instructions if we are targeting Art-based
  // phones.
  public boolean canUseNotInstruction() {
    return hasMinApi(AndroidApiLevel.L);
  }

  // Art before M has a verifier bug where the type of the contents of the receiver register is
  // assumed to not change. If the receiver register is reused for something else the verifier
  // will fail and the code will not run.
  public boolean canHaveThisTypeVerifierBug() {
    return minApiLevel < AndroidApiLevel.M.getLevel();
  }

  // The dalvik jit had a bug where the long operations add, sub, or, xor and and would write
  // the first part of the result long before reading the second part of the input longs.
  public boolean canHaveOverlappingLongRegisterBug() {
    return minApiLevel < AndroidApiLevel.L.getLevel();
  }

  // Some dalvik versions found in the wild perform invalid JIT compilation of cmp-long
  // instructions where the result register overlaps with the input registers.
  // See b/74084493.
  //
  // The same dalvik versions also have a bug where the JIT compilation of code such as:
  //
  // void method(long l) {
  //  if (l < 0) throw new RuntimeException("less than");
  //  if (l == 0) throw new RuntimeException("equal");
  // }
  //
  // Will enter the case for l==0 even when l is non-zero. The code generated for this is of
  // the form:
  //
  // 0:   0x00: ConstWide16         v0, 0x0000000000000000 (0)
  // 1:   0x02: CmpLong             v2, v4, v0
  // 2:   0x04: IfLtz               v2, 0x0c (+8)
  // 3:   0x06: IfNez               v2, 0x0a (+4)
  //
  // However, the jit apparently clobbers the input register in the IfLtz instruction. Therefore,
  // for dalvik VMs we have to instead generate the following code:
  //
  // 0:   0x00: ConstWide16         v0, 0x0000000000000000 (0)
  // 1:   0x02: CmpLong             v2, v4, v0
  // 2:   0x04: IfLtz               v2, 0x0e (+10)
  // 3:   0x06: CmpLong             v2, v4, v0
  // 4:   0x08: IfNez               v2, 0x0c (+4)
  //
  // See b/75408029.
  public boolean canHaveCmpLongBug() {
    return minApiLevel < AndroidApiLevel.L.getLevel();
  }

  // Some Lollipop VMs incorrectly optimize code with mul2addr instructions. In particular,
  // the following hash code method produces wrong results after optimizations:
  //
  //    0:   0x00: IgetObject          v0, v3, Field java.lang.Class MultiClassKey.first
  //    1:   0x02: InvokeVirtual       { v0 } Ljava/lang/Object;->hashCode()I
  //    2:   0x05: MoveResult          v0
  //    3:   0x06: Const16             v1, 0x001f (31)
  //    4:   0x08: MulInt2Addr         v1, v0
  //    5:   0x09: IgetObject          v2, v3, Field java.lang.Class MultiClassKey.second
  //    6:   0x0b: InvokeVirtual       { v2 } Ljava/lang/Object;->hashCode()I
  //    7:   0x0e: MoveResult          v2
  //    8:   0x0f: AddInt2Addr         v1, v2
  //    9:   0x10: Return              v1
  //
  // It seems that the issue is the MulInt2Addr instructions. Avoiding that, the VM computes
  // hash codes correctly also after optimizations.
  //
  // This issue has only been observed on a Verizon Ellipsis 8 tablet. See b/76115465.
  public boolean canHaveMul2AddrBug() {
    return minApiLevel < AndroidApiLevel.M.getLevel();
  }

  // Some Marshmallow VMs create an incorrect doubly-linked list of instructions. When the VM
  // attempts to create a fixup for a Cortex 53 long add/sub issue, it may diverge due to the cyclic
  // list.
  //
  // See b/77842465.
  public boolean canHaveDex2OatLinkedListBug() {
    return minApiLevel < AndroidApiLevel.N.getLevel();
  }

  // dex2oat on Marshmallow VMs does aggressive inlining which can eat up all the memory on
  // devices for self-recursive methods.
  //
  // See b/111960171
  public boolean canHaveDex2OatInliningIssue() {
    return minApiLevel < AndroidApiLevel.N.getLevel();
  }

  // Art 7.0.0 and later Art JIT may perform an invalid optimization if a string new-instance does
  // not flow directly to the init call.
  //
  // See b/78493232 and b/80118070.
  public boolean canHaveArtStringNewInitBug() {
    return minApiLevel < AndroidApiLevel.Q.getLevel();
  }

  // Dalvik tracing JIT may perform invalid optimizations when int/float values are converted to
  // double and used in arithmetic operations.
  //
  // See b/77496850.
  public boolean canHaveNumberConversionRegisterAllocationBug() {
    return minApiLevel < AndroidApiLevel.L.getLevel();
  }

  // Some Lollipop mediatek VMs have a peculiar bug where the inliner crashes if there is a
  // simple constructor that just forwards its arguments to the super constructor. Strangely,
  // this happens only for specific signatures: so far the only reproduction we have is for
  // a constructor accepting two doubles and one object.
  //
  // To workaround this we insert a materializing const instruction before the super init
  // call. Having a temporary register seems to disable the buggy optimizations.
  //
  // See b/68378480.
  public boolean canHaveForwardingInitInliningBug() {
    return minApiLevel < AndroidApiLevel.M.getLevel();
  }
}
