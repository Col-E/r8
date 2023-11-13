// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SyntheticNaming {

  private KindGenerator generator = new KindGenerator();

  // Global synthetics.
  public final SyntheticKind RECORD_TAG = generator.forGlobalClass();
  public final SyntheticKind API_MODEL_STUB = generator.forGlobalClass();
  public final SyntheticKind METHOD_HANDLES_LOOKUP = generator.forGlobalClass();
  public final SyntheticKind VAR_HANDLE = generator.forGlobalClass();

  // Classpath only synthetics in the global type namespace.
  public final SyntheticKind GENERIC_API_CONVERSION_STUB = generator.forGlobalClasspathClass();
  public final SyntheticKind RETARGET_STUB = generator.forGlobalClasspathClass();
  public final SyntheticKind EMULATED_INTERFACE_MARKER_CLASS = generator.forGlobalClasspathClass();

  // Fixed suffix synthetics. Each has a hygienic prefix type.
  public final SyntheticKind ENUM_UNBOXING_LOCAL_UTILITY_CLASS =
      generator.forFixedClass("$EnumUnboxingLocalUtility");
  public final SyntheticKind ENUM_UNBOXING_SHARED_UTILITY_CLASS =
      generator.forFixedClass("$EnumUnboxingSharedUtility");
  public final SyntheticKind COMPANION_CLASS = generator.forFixedClass(COMPANION_CLASS_SUFFIX);
  public final SyntheticKind EMULATED_INTERFACE_CLASS =
      generator.forFixedClass(InterfaceDesugaringForTesting.EMULATED_INTERFACE_CLASS_SUFFIX);
  public final SyntheticKind RETARGET_CLASS = generator.forFixedClass("RetargetClass");
  public final SyntheticKind RETARGET_INTERFACE = generator.forFixedClass("RetargetInterface");
  public final SyntheticKind WRAPPER = generator.forFixedClass("$Wrapper");
  public final SyntheticKind VIVIFIED_WRAPPER = generator.forFixedClass("$VivifiedWrapper");
  public final SyntheticKind INIT_TYPE_ARGUMENT = generator.forFixedClass("-IA");
  public final SyntheticKind HORIZONTAL_INIT_TYPE_ARGUMENT_1 =
      generator.forFixedClass(SYNTHETIC_CLASS_SEPARATOR + "IA$1");
  public final SyntheticKind HORIZONTAL_INIT_TYPE_ARGUMENT_2 =
      generator.forFixedClass(SYNTHETIC_CLASS_SEPARATOR + "IA$2");
  public final SyntheticKind HORIZONTAL_INIT_TYPE_ARGUMENT_3 =
      generator.forFixedClass(SYNTHETIC_CLASS_SEPARATOR + "IA$3");
  public final SyntheticKind ENUM_CONVERSION = generator.forFixedClass("$EnumConversion");

  // Locally generated synthetic classes.
  public final SyntheticKind LAMBDA = generator.forInstanceClass("Lambda");
  public final SyntheticKind THREAD_LOCAL = generator.forInstanceClass("ThreadLocal");

  // Merging not permitted since this could defeat the purpose of the synthetic class.
  public final SyntheticKind SHARED_SUPER_CLASS =
      generator.forNonSharableInstanceClass("SharedSuper");

  // TODO(b/214901256): Sharing of synthetic classes may lead to duplicate method errors.
  public final SyntheticKind NON_FIXED_INIT_TYPE_ARGUMENT =
      generator.forNonSharableInstanceClass("$IA");
  public final SyntheticKind CONST_DYNAMIC = generator.forNonSharableInstanceClass("$Condy");

  // Method synthetics.
  public final SyntheticKind ENUM_UNBOXING_CHECK_NOT_ZERO_METHOD =
      generator.forSingleMethodWithGlobalMerging("CheckNotZero");
  public final SyntheticKind RECORD_HELPER = generator.forSingleMethodWithGlobalMerging("Record");
  public final SyntheticKind BACKPORT = generator.forSingleMethodWithGlobalMerging("Backport");
  public final SyntheticKind BACKPORT_WITH_FORWARDING =
      generator.forSingleMethod("BackportWithForwarding");
  public final SyntheticKind STATIC_INTERFACE_CALL =
      generator.forSingleMethod("StaticInterfaceCall");
  public final SyntheticKind TO_STRING_IF_NOT_NULL =
      generator.forSingleMethodWithGlobalMerging("ToStringIfNotNull");
  public final SyntheticKind THROW_CCE_IF_NOT_NULL =
      generator.forSingleMethodWithGlobalMerging("ThrowCCEIfNotNull");
  public final SyntheticKind THROW_IAE = generator.forSingleMethodWithGlobalMerging("ThrowIAE");
  public final SyntheticKind THROW_ICCE = generator.forSingleMethodWithGlobalMerging("ThrowICCE");
  public final SyntheticKind THROW_NSME = generator.forSingleMethodWithGlobalMerging("ThrowNSME");
  public final SyntheticKind THROW_RTE = generator.forSingleMethodWithGlobalMerging("ThrowRTE");
  public final SyntheticKind TWR_CLOSE_RESOURCE =
      generator.forSingleMethodWithGlobalMerging("TwrCloseResource");
  public final SyntheticKind SERVICE_LOADER =
      generator.forSingleMethodWithGlobalMerging("ServiceLoad");
  public final SyntheticKind OUTLINE = generator.forSingleMethod("Outline");
  public final SyntheticKind COVARIANT_OUTLINE = generator.forSingleMethod("CovariantOutline");
  public final SyntheticKind API_CONVERSION = generator.forSingleMethod("APIConversion");
  public final SyntheticKind API_CONVERSION_PARAMETERS =
      generator.forSingleMethod("APIConversionParameters");
  public final SyntheticKind COLLECTION_CONVERSION =
      generator.forSingleMethod("$CollectionConversion");
  public final SyntheticKind API_MODEL_OUTLINE =
      generator.forSingleMethodWithGlobalMerging("ApiModelOutline");
  public final SyntheticKind API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING =
      generator.forSingleMethod("ApiModelOutline");
  public final SyntheticKind DESUGARED_LIBRARY_BRIDGE =
      generator.forSingleMethod("DesugaredLibraryBridge");

  private final List<SyntheticKind> ALL_KINDS;
  private String lazyVersionHash = null;

  public SyntheticNaming() {
    ALL_KINDS = generator.getAllKinds();
    generator = null;
  }

  public String getVersionHash() {
    if (lazyVersionHash == null) {
      computeVersionHash();
    }
    return lazyVersionHash;
  }

  private void computeVersionHash() {
    Hasher hasher = Hashing.sha256().newHasher();
    hasher.putString(Version.getVersionString(), StandardCharsets.UTF_8);
    for (SyntheticKind kind : ALL_KINDS) {
      kind.hash(hasher);
    }
    lazyVersionHash = hasher.hash().toString();
  }

  public Collection<SyntheticKind> kinds() {
    return ALL_KINDS;
  }

  public SyntheticKind fromId(int id) {
    if (0 < id && id <= ALL_KINDS.size()) {
      return ALL_KINDS.get(id - 1);
    }
    return null;
  }

  private static class KindGenerator {
    private int nextId = 1;
    private List<SyntheticKind> kinds = new ArrayList<>();

    private SyntheticKind register(SyntheticKind kind) {
      kinds.add(kind);
      if (kinds.size() != kind.getId()) {
        throw new Unreachable("Invalid synthetic kind id: " + kind.getId());
      }
      return kind;
    }

    private int getNextId() {
      return nextId++;
    }

    SyntheticKind forSingleMethod(String descriptor) {
      return register(new SyntheticMethodKind(getNextId(), descriptor, false));
    }

    SyntheticKind forSingleMethodWithGlobalMerging(String descriptor) {
      return register(new SyntheticMethodKind(getNextId(), descriptor, true));
    }

    // TODO(b/214901256): Remove once fixed.
    SyntheticKind forNonSharableInstanceClass(String descriptor) {
      return register(new SyntheticClassKind(getNextId(), descriptor, false));
    }

    SyntheticKind forInstanceClass(String descriptor) {
      return register(new SyntheticClassKind(getNextId(), descriptor, true));
    }

    SyntheticKind forFixedClass(String descriptor) {
      assert !descriptor.isEmpty();
      return register(new SyntheticFixedClassKind(getNextId(), descriptor, false));
    }

    SyntheticKind forGlobalClass() {
      return register(new SyntheticFixedClassKind(getNextId(), "", true));
    }

    SyntheticKind forGlobalClasspathClass() {
      return register(new SyntheticFixedClassKind(getNextId(), "", false));
    }

    List<SyntheticKind> getAllKinds() {
      List<SyntheticKind> kinds = this.kinds;
      this.kinds = null;
      return kinds;
    }
  }

  /**
   * Enumeration of all kinds of synthetic items.
   *
   * <p>The synthetic kinds are used to provide hinting about what a synthetic item represents. The
   * kinds must *not* be used be the compiler and are only meant for "debugging". The compiler and
   * its test may use the kind information as part of asserting properties of the compiler. The kind
   * will be put into any non-minified synthetic name and thus the kind "descriptor" must be a
   * distinct for each kind.
   */
  public abstract static class SyntheticKind implements Ordered<SyntheticKind> {

    private final int id;
    private final String descriptor;

    SyntheticKind(int id, String descriptor) {
      this.id = id;
      this.descriptor = descriptor;
    }

    @Override
    public int compareTo(SyntheticKind other) {
      return Integer.compare(id, other.getId());
    }

    @Override
    public int hashCode() {
      return id;
    }

    @Override
    public boolean equals(Object o) {
      return Equatable.equalsImpl(this, o);
    }

    public int getId() {
      return id;
    }

    public String getDescriptor() {
      return descriptor;
    }

    public boolean isSyntheticMethodKind() {
      return false;
    }

    public SyntheticMethodKind asSyntheticMethodKind() {
      return null;
    }

    public abstract boolean isShareable();

    public abstract boolean isSingleSyntheticMethod();

    public abstract boolean isFixedSuffixSynthetic();

    public abstract boolean isGlobal();

    public abstract boolean isMayOverridesNonProgramType();

    public final void hash(Hasher hasher) {
      hasher.putInt(getId());
      hasher.putString(getDescriptor(), StandardCharsets.UTF_8);
      internalHash(hasher);
    }

    public abstract void internalHash(Hasher hasher);
  }

  public static class SyntheticMethodKind extends SyntheticKind {

    private final boolean allowGlobalMerging;

    public SyntheticMethodKind(int id, String descriptor, boolean allowGlobalMerging) {
      super(id, descriptor);
      this.allowGlobalMerging = allowGlobalMerging;
    }

    @Override
    public boolean isShareable() {
      // Single methods may always be shared.
      return true;
    }

    @Override
    public boolean isSingleSyntheticMethod() {
      return true;
    }

    @Override
    public boolean isFixedSuffixSynthetic() {
      return false;
    }

    @Override
    public boolean isGlobal() {
      return false;
    }

    @Override
    public boolean isMayOverridesNonProgramType() {
      return false;
    }

    public boolean isAllowGlobalMerging() {
      return allowGlobalMerging;
    }

    @Override
    public boolean isSyntheticMethodKind() {
      return true;
    }

    @Override
    public SyntheticMethodKind asSyntheticMethodKind() {
      return this;
    }

    @Override
    public void internalHash(Hasher hasher) {
      hasher.putString("method", StandardCharsets.UTF_8);
    }
  }

  private static class SyntheticClassKind extends SyntheticKind {

    // TODO(b/214901256): Remove once fixed.
    private final boolean sharable;

    public SyntheticClassKind(int id, String descriptor, boolean sharable) {
      super(id, descriptor);
      this.sharable = sharable;
    }

    @Override
    public boolean isShareable() {
      return sharable;
    }

    @Override
    public final boolean isSingleSyntheticMethod() {
      return false;
    }

    @Override
    public boolean isFixedSuffixSynthetic() {
      return false;
    }

    @Override
    public boolean isGlobal() {
      return false;
    }

    @Override
    public boolean isMayOverridesNonProgramType() {
      return false;
    }

    @Override
    public void internalHash(Hasher hasher) {
      hasher.putString("class", StandardCharsets.UTF_8);
      hasher.putBoolean(sharable);
    }
  }

  private static class SyntheticFixedClassKind extends SyntheticClassKind {
    private final boolean mayOverridesNonProgramType;

    private SyntheticFixedClassKind(int id, String descriptor, boolean mayOverridesNonProgramType) {
      super(id, descriptor, false);
      this.mayOverridesNonProgramType = mayOverridesNonProgramType;
    }

    @Override
    public boolean isShareable() {
      return false;
    }

    @Override
    public boolean isFixedSuffixSynthetic() {
      return true;
    }

    @Override
    public boolean isGlobal() {
      return getDescriptor().isEmpty();
    }

    @Override
    public boolean isMayOverridesNonProgramType() {
      return mayOverridesNonProgramType;
    }

    @Override
    public void internalHash(Hasher hasher) {
      hasher.putString(isGlobal() ? "global" : "fixed", StandardCharsets.UTF_8);
      hasher.putBoolean(mayOverridesNonProgramType);
    }
  }

  public static final String COMPANION_CLASS_SUFFIX = "$-CC";
  private static final String SYNTHETIC_CLASS_SEPARATOR = "$$";
  /**
   * The internal synthetic class separator is only used for representing synthetic items during
   * compilation. In particular, this separator must never be used to write synthetic classes to the
   * final compilation result.
   */
  private static final String INTERNAL_SYNTHETIC_CLASS_SEPARATOR =
      SYNTHETIC_CLASS_SEPARATOR + "InternalSynthetic";
  /**
   * The external synthetic class separator is used when writing classes. It may appear in types
   * during compilation as the output of a compilation may be the input to another.
   */
  public static final String EXTERNAL_SYNTHETIC_CLASS_SEPARATOR =
      SYNTHETIC_CLASS_SEPARATOR + "ExternalSynthetic";
  /** Method name when generating synthetic methods in a class. */
  static final String INTERNAL_SYNTHETIC_METHOD_NAME = "m";

  static String getPrefixForExternalSyntheticType(SyntheticKind kind, DexType type) {
    String binaryName = type.toBinaryName();
    if (kind.isGlobal()) {
      return binaryName;
    }
    int index =
        binaryName.lastIndexOf(
            kind.isFixedSuffixSynthetic() ? kind.descriptor : SYNTHETIC_CLASS_SEPARATOR);
    if (index < 0) {
      throw new Unreachable("Unexpected failure to compute a synthetic prefix for " + binaryName);
    }
    return binaryName.substring(0, index);
  }

  static String getOuterContextFromExternalSyntheticType(SyntheticKind kind, DexType type) {
    assert !kind.isGlobal();
    String binaryName = type.toBinaryName();
    int index =
        binaryName.indexOf(
            kind.isFixedSuffixSynthetic() ? kind.descriptor : EXTERNAL_SYNTHETIC_CLASS_SEPARATOR);
    if (index < 0) {
      throw new Unreachable(
          "Unexpected failure to determine the context of synthetic class: " + binaryName);
    }
    return binaryName.substring(0, index);
  }

  static DexType createFixedType(
      SyntheticKind kind, SynthesizingContext context, DexItemFactory factory) {
    assert kind.isFixedSuffixSynthetic();
    return createType("", kind, context.getSynthesizingContextType(), "", factory);
  }

  static DexType createInternalType(
      SyntheticKind kind, SynthesizingContext context, String id, AppView<?> appView) {
    assert !kind.isFixedSuffixSynthetic();
    return createType(
        INTERNAL_SYNTHETIC_CLASS_SEPARATOR,
        kind,
        context.getSynthesizingInputContext(appView.options().intermediate),
        id,
        appView.dexItemFactory());
  }

  static DexType createExternalType(
      SyntheticKind kind, String externalSyntheticTypePrefix, String id, DexItemFactory factory) {
    assert kind.isFixedSuffixSynthetic() == id.isEmpty();
    return createType(
        kind.isFixedSuffixSynthetic() ? "" : EXTERNAL_SYNTHETIC_CLASS_SEPARATOR,
        kind,
        externalSyntheticTypePrefix,
        id,
        factory);
  }

  private static DexType createType(
      String separator, SyntheticKind kind, DexType context, String id, DexItemFactory factory) {
    return factory.createType(createDescriptor(separator, kind, context.getInternalName(), id));
  }

  private static DexType createType(
      String separator,
      SyntheticKind kind,
      String externalSyntheticTypePrefix,
      String id,
      DexItemFactory factory) {
    return factory.createType(createDescriptor(separator, kind, externalSyntheticTypePrefix, id));
  }

  public static String createDescriptor(
      String separator, SyntheticKind kind, String externalSyntheticTypePrefix, String id) {
    return DescriptorUtils.getDescriptorFromClassBinaryName(
        externalSyntheticTypePrefix + separator + kind.descriptor + id);
  }

  public static boolean verifyNotInternalSynthetic(DexType type) {
    return verifyNotInternalSynthetic(type.toDescriptorString());
  }

  public static boolean verifyNotInternalSynthetic(ClassReference reference) {
    return verifyNotInternalSynthetic(reference.getDescriptor());
  }

  public static boolean verifyNotInternalSynthetic(String typeBinaryNameOrDescriptor) {
    assert !typeBinaryNameOrDescriptor.contains(INTERNAL_SYNTHETIC_CLASS_SEPARATOR)
        : typeBinaryNameOrDescriptor;
    return true;
  }

  // Visible via package protection in SyntheticItemsTestUtils.

  enum Phase {
    INTERNAL,
    EXTERNAL
  }

  static String getPhaseSeparator(Phase phase) {
    assert phase != null;
    return phase == Phase.INTERNAL
        ? INTERNAL_SYNTHETIC_CLASS_SEPARATOR
        : EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
  }

  static ClassReference makeSyntheticReferenceForTest(
      ClassReference context, SyntheticKind kind, String id) {
    return Reference.classFromDescriptor(
        createDescriptor(EXTERNAL_SYNTHETIC_CLASS_SEPARATOR, kind, context.getBinaryName(), id));
  }

  static boolean isSynthetic(ClassReference clazz, Phase phase, SyntheticKind kind) {
    String typeName = clazz.getTypeName();
    if (kind.isFixedSuffixSynthetic()) {
      assert phase == null;
      return clazz.getBinaryName().endsWith(kind.descriptor);
    }
    String separator = getPhaseSeparator(phase);
    int i = typeName.lastIndexOf(separator);
    return i >= 0 && checkMatchFrom(kind, typeName, i, separator, phase == Phase.EXTERNAL);
  }

  private static boolean checkMatchFrom(
      SyntheticKind kind,
      String name,
      int i,
      String externalSyntheticClassSeparator,
      boolean checkIntSuffix) {
    int end = i + externalSyntheticClassSeparator.length() + kind.descriptor.length();
    if (end >= name.length()) {
      return false;
    }
    String prefix = name.substring(i, end);
    return prefix.equals(externalSyntheticClassSeparator + kind.descriptor)
        && (!checkIntSuffix || isInt(name.substring(end)));
  }

  private static boolean isInt(String str) {
    if (str.isEmpty()) {
      return false;
    }
    if ('0' == str.charAt(0)) {
      return str.length() == 1;
    }
    for (int i = 0; i < str.length(); i++) {
      if (!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
