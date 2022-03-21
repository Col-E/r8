// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.Ordered;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SyntheticNaming {

  private static class KindGenerator {
    private int nextId = 1;
    private List<SyntheticKind> kinds = new ArrayList<>();

    private int getNextId() {
      return nextId++;
    }

    private SyntheticKind register(SyntheticKind kind) {
      kinds.add(kind);
      return kind;
    }

    SyntheticKind forSingleMethod(String descriptor) {
      return register(new SyntheticMethodKind(getNextId(), descriptor));
    }

    SyntheticKind forInstanceClass(String descriptor) {
      return register(new SyntheticClassKind(getNextId(), descriptor));
    }

    SyntheticKind forFixedClass(String descriptor) {
      return register(new SyntheticFixedClassKind(getNextId(), descriptor, false, false));
    }

    SyntheticKind forGlobalClass() {
      return register(new SyntheticFixedClassKind(getNextId(), "", true, true));
    }

    SyntheticKind forGlobalClasspathClass() {
      return register(new SyntheticFixedClassKind(getNextId(), "", false, false));
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

    public static final SyntheticKind ENUM_UNBOXING_LOCAL_UTILITY_CLASS,
        ENUM_UNBOXING_SHARED_UTILITY_CLASS,
        RECORD_TAG,
        COMPANION_CLASS,
        EMULATED_INTERFACE_CLASS,
        RETARGET_CLASS,
        RETARGET_STUB,
        RETARGET_INTERFACE,
        WRAPPER,
        VIVIFIED_WRAPPER,
        LAMBDA,
        INIT_TYPE_ARGUMENT,
        HORIZONTAL_INIT_TYPE_ARGUMENT_1,
        HORIZONTAL_INIT_TYPE_ARGUMENT_2,
        HORIZONTAL_INIT_TYPE_ARGUMENT_3,
        NON_FIXED_INIT_TYPE_ARGUMENT,
        ENUM_UNBOXING_CHECK_NOT_ZERO_METHOD,
        RECORD_HELPER,
        BACKPORT,
        BACKPORT_WITH_FORWARDING,
        STATIC_INTERFACE_CALL,
        TO_STRING_IF_NOT_NULL,
        THROW_CCE_IF_NOT_NULL,
        THROW_IAE,
        THROW_ICCE,
        THROW_NSME,
        TWR_CLOSE_RESOURCE,
        SERVICE_LOADER,
        OUTLINE,
        API_CONVERSION,
        API_CONVERSION_PARAMETERS,
        EMULATED_INTERFACE_MARKER_CLASS,
        CONST_DYNAMIC,
        ENUM_CONVERSION,
        ARRAY_CONVERSION,
        API_MODEL_OUTLINE,
        API_MODEL_STUB;

    private static final List<SyntheticKind> ALL_KINDS;

    static {
      KindGenerator generator = new KindGenerator();
      // Global synthetics.
      RECORD_TAG = generator.forGlobalClass();
      API_MODEL_STUB = generator.forGlobalClass();

      // Classpath only synthetics in the global type namespace.
      RETARGET_STUB = generator.forGlobalClasspathClass();
      EMULATED_INTERFACE_MARKER_CLASS = generator.forGlobalClasspathClass();

      // Fixed suffix synthetics. Each has a hygienic prefix type.
      ENUM_UNBOXING_LOCAL_UTILITY_CLASS = generator.forFixedClass("$EnumUnboxingLocalUtility");
      ENUM_UNBOXING_SHARED_UTILITY_CLASS = generator.forFixedClass("$EnumUnboxingSharedUtility");
      COMPANION_CLASS = generator.forFixedClass("$-CC");
      EMULATED_INTERFACE_CLASS = generator.forFixedClass("$-EL");
      RETARGET_CLASS = generator.forFixedClass("RetargetClass");
      RETARGET_INTERFACE = generator.forFixedClass("RetargetInterface");
      WRAPPER = generator.forFixedClass("$Wrapper");
      VIVIFIED_WRAPPER = generator.forFixedClass("$VivifiedWrapper");
      INIT_TYPE_ARGUMENT = generator.forFixedClass("-IA");
      HORIZONTAL_INIT_TYPE_ARGUMENT_1 = generator.forFixedClass(SYNTHETIC_CLASS_SEPARATOR + "IA$1");
      HORIZONTAL_INIT_TYPE_ARGUMENT_2 = generator.forFixedClass(SYNTHETIC_CLASS_SEPARATOR + "IA$2");
      HORIZONTAL_INIT_TYPE_ARGUMENT_3 = generator.forFixedClass(SYNTHETIC_CLASS_SEPARATOR + "IA$3");
      ENUM_CONVERSION = generator.forFixedClass("$EnumConversion");

      // Locally generated synthetic classes.
      LAMBDA = generator.forInstanceClass("Lambda");
      NON_FIXED_INIT_TYPE_ARGUMENT = generator.forInstanceClass("$IA");
      CONST_DYNAMIC = generator.forInstanceClass("$Condy");

      // Method synthetics.
      ENUM_UNBOXING_CHECK_NOT_ZERO_METHOD = generator.forSingleMethod("CheckNotZero");
      RECORD_HELPER = generator.forSingleMethod("Record");
      BACKPORT = generator.forSingleMethod("Backport");
      BACKPORT_WITH_FORWARDING = generator.forSingleMethod("BackportWithForwarding");
      STATIC_INTERFACE_CALL = generator.forSingleMethod("StaticInterfaceCall");
      TO_STRING_IF_NOT_NULL = generator.forSingleMethod("ToStringIfNotNull");
      THROW_CCE_IF_NOT_NULL = generator.forSingleMethod("ThrowCCEIfNotNull");
      THROW_IAE = generator.forSingleMethod("ThrowIAE");
      THROW_ICCE = generator.forSingleMethod("ThrowICCE");
      THROW_NSME = generator.forSingleMethod("ThrowNSME");
      TWR_CLOSE_RESOURCE = generator.forSingleMethod("TwrCloseResource");
      SERVICE_LOADER = generator.forSingleMethod("ServiceLoad");
      OUTLINE = generator.forSingleMethod("Outline");
      API_CONVERSION = generator.forSingleMethod("APIConversion");
      API_CONVERSION_PARAMETERS = generator.forSingleMethod("APIConversionParameters");
      ARRAY_CONVERSION = generator.forSingleMethod("$ArrayConversion");
      API_MODEL_OUTLINE = generator.forSingleMethod("ApiModelOutline");

      ALL_KINDS = generator.getAllKinds();
    }

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

    public static Collection<SyntheticKind> values() {
      return ALL_KINDS;
    }

    public static SyntheticKind fromId(int id) {
      if (0 < id && id <= ALL_KINDS.size()) {
        return ALL_KINDS.get(id - 1);
      }
      assert false;
      return null;
    }

    public int getId() {
      return id;
    }

    public String getDescriptor() {
      return descriptor;
    }

    public abstract boolean isShareable();

    public abstract boolean isSingleSyntheticMethod();

    public abstract boolean isFixedSuffixSynthetic();

    public abstract boolean isGlobal();

    public abstract boolean isMayOverridesNonProgramType();

    public abstract boolean allowSyntheticContext();
  }

  private static class SyntheticMethodKind extends SyntheticKind {

    public SyntheticMethodKind(int id, String descriptor) {
      super(id, descriptor);
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

    @Override
    public boolean allowSyntheticContext() {
      return false;
    }
  }

  private static class SyntheticClassKind extends SyntheticKind {

    public SyntheticClassKind(int id, String descriptor) {
      super(id, descriptor);
    }

    @Override
    public boolean isShareable() {
      if (this == NON_FIXED_INIT_TYPE_ARGUMENT) {
        // TODO(b/214901256): Sharing of synthetic classes may lead to duplicate method errors.
        return false;
      }
      return true;
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
    public boolean allowSyntheticContext() {
      return false;
    }
  }

  private static class SyntheticFixedClassKind extends SyntheticClassKind {
    private final boolean mayOverridesNonProgramType;
    private final boolean allowSyntheticContext;

    private SyntheticFixedClassKind(
        int id,
        String descriptor,
        boolean mayOverridesNonProgramType,
        boolean allowSyntheticContext) {
      super(id, descriptor);
      this.mayOverridesNonProgramType = mayOverridesNonProgramType;
      this.allowSyntheticContext = allowSyntheticContext;
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
    public boolean allowSyntheticContext() {
      return allowSyntheticContext;
    }
  }

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
  private static final String EXTERNAL_SYNTHETIC_CLASS_SEPARATOR =
      SYNTHETIC_CLASS_SEPARATOR + "ExternalSynthetic";
  /** Method name when generating synthetic methods in a class. */
  static final String INTERNAL_SYNTHETIC_METHOD_NAME = "m";

  static String getPrefixForExternalSyntheticType(SyntheticKind kind, DexType type) {
    String binaryName = type.toBinaryName();
    int index =
        binaryName.lastIndexOf(
            kind.isFixedSuffixSynthetic() ? kind.descriptor : SYNTHETIC_CLASS_SEPARATOR);
    if (index < 0) {
      throw new Unreachable("Unexpected failure to compute an synthetic prefix");
    }
    return binaryName.substring(0, index);
  }

  static DexType createFixedType(
      SyntheticKind kind, SynthesizingContext context, DexItemFactory factory) {
    assert kind.isFixedSuffixSynthetic();
    return createType("", kind, context.getSynthesizingContextType(), "", factory);
  }

  static DexType createInternalType(
      SyntheticKind kind, SynthesizingContext context, String id, DexItemFactory factory) {
    assert !kind.isFixedSuffixSynthetic();
    return createType(
        INTERNAL_SYNTHETIC_CLASS_SEPARATOR,
        kind,
        context.getSynthesizingContextType(),
        id,
        factory);
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

  private static String createDescriptor(
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
    assert !typeBinaryNameOrDescriptor.contains(INTERNAL_SYNTHETIC_CLASS_SEPARATOR);
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
