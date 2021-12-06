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
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;

public class SyntheticNaming {

  /**
   * Enumeration of all kinds of synthetic items.
   *
   * <p>The synthetic kinds are used to provide hinting about what a synthetic item represents. The
   * kinds must *not* be used be the compiler and are only meant for "debugging". The compiler and
   * its test may use the kind information as part of asserting properties of the compiler. The kind
   * will be put into any non-minified synthetic name and thus the kind "descriptor" must be a
   * distinct for each kind.
   */
  public enum SyntheticKind {
    // Class synthetics.
    ENUM_UNBOXING_LOCAL_UTILITY_CLASS("$EnumUnboxingLocalUtility", 24, false, true),
    ENUM_UNBOXING_SHARED_UTILITY_CLASS("$EnumUnboxingSharedUtility", 25, false, true),
    RECORD_TAG("", 1, false, true, true),
    COMPANION_CLASS("$-CC", 2, false, true),
    EMULATED_INTERFACE_CLASS("$-EL", 3, false, true),
    RETARGET_CLASS("RetargetClass", 20, false, true),
    RETARGET_INTERFACE("RetargetInterface", 21, false, true),
    WRAPPER("$Wrapper", 22, false, true),
    VIVIFIED_WRAPPER("$VivifiedWrapper", 23, false, true),
    LAMBDA("Lambda", 4, false),
    INIT_TYPE_ARGUMENT("-IA", 5, false, true),
    HORIZONTAL_INIT_TYPE_ARGUMENT_1(SYNTHETIC_CLASS_SEPARATOR + "IA$1", 6, false, true),
    HORIZONTAL_INIT_TYPE_ARGUMENT_2(SYNTHETIC_CLASS_SEPARATOR + "IA$2", 7, false, true),
    HORIZONTAL_INIT_TYPE_ARGUMENT_3(SYNTHETIC_CLASS_SEPARATOR + "IA$3", 8, false, true),
    // Method synthetics.
    ENUM_UNBOXING_CHECK_NOT_ZERO_METHOD("CheckNotZero", 27, true),
    RECORD_HELPER("Record", 9, true),
    BACKPORT("Backport", 10, true),
    STATIC_INTERFACE_CALL("StaticInterfaceCall", 11, true),
    TO_STRING_IF_NOT_NULL("ToStringIfNotNull", 12, true),
    THROW_CCE_IF_NOT_NULL("ThrowCCEIfNotNull", 13, true),
    THROW_IAE("ThrowIAE", 14, true),
    THROW_ICCE("ThrowICCE", 15, true),
    THROW_NSME("ThrowNSME", 16, true),
    TWR_CLOSE_RESOURCE("TwrCloseResource", 17, true),
    SERVICE_LOADER("ServiceLoad", 18, true),
    OUTLINE("Outline", 19, true),
    API_CONVERSION("APIConversion", 26, true),
    API_CONVERSION_PARAMETERS("APIConversionParameters", 28, true),
    EMULATED_INTERFACE_MARKER_CLASS("", 29, false, true, true),
    CONST_DYNAMIC("$Condy", 30, false),
    ENUM_CONVERSION("$EnumConversion", 31, false, true),
    API_MODEL_OUTLINE("ApiModelOutline", 32, true, false, false);

    static {
      assert verifyNoOverlappingIds();
    }

    public final String descriptor;
    public final int id;
    public final boolean isSingleSyntheticMethod;
    public final boolean isFixedSuffixSynthetic;
    public final boolean mayOverridesNonProgramType;

    SyntheticKind(String descriptor, int id, boolean isSingleSyntheticMethod) {
      this(descriptor, id, isSingleSyntheticMethod, false);
    }

    SyntheticKind(
        String descriptor,
        int id,
        boolean isSingleSyntheticMethod,
        boolean isFixedSuffixSynthetic) {
      this(descriptor, id, isSingleSyntheticMethod, isFixedSuffixSynthetic, false);
    }

    SyntheticKind(
        String descriptor,
        int id,
        boolean isSingleSyntheticMethod,
        boolean isFixedSuffixSynthetic,
        boolean mayOverridesNonProgramType) {
      this.descriptor = descriptor;
      this.id = id;
      this.isSingleSyntheticMethod = isSingleSyntheticMethod;
      this.isFixedSuffixSynthetic = isFixedSuffixSynthetic;
      this.mayOverridesNonProgramType = mayOverridesNonProgramType;
    }

    public boolean allowSyntheticContext() {
      return this == RECORD_TAG;
    }

    public static SyntheticKind fromDescriptor(String descriptor) {
      for (SyntheticKind kind : values()) {
        if (kind.descriptor.equals(descriptor)) {
          return kind;
        }
      }
      return null;
    }

    public static SyntheticKind fromId(int id) {
      for (SyntheticKind kind : values()) {
        if (kind.id == id) {
          return kind;
        }
      }
      return null;
    }

    private static boolean verifyNoOverlappingIds() {
      Int2ReferenceMap<SyntheticKind> idToKind = new Int2ReferenceOpenHashMap<>();
      for (SyntheticKind kind : values()) {
        SyntheticKind kindWithSameId = idToKind.put(kind.id, kind);
        assert kindWithSameId == null
            : "Synthetic kind " + idToKind + " has same id as " + kindWithSameId;
      }
      return true;
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
            kind.isFixedSuffixSynthetic ? kind.descriptor : SYNTHETIC_CLASS_SEPARATOR);
    if (index < 0) {
      throw new Unreachable("Unexpected failure to compute an synthetic prefix");
    }
    return binaryName.substring(0, index);
  }

  public static DexType createFixedType(
      SyntheticKind kind, SynthesizingContext context, DexItemFactory factory) {
    assert kind.isFixedSuffixSynthetic;
    return createType("", kind, context.getSynthesizingContextType(), "", factory);
  }

  static DexType createInternalType(
      SyntheticKind kind, SynthesizingContext context, String id, DexItemFactory factory) {
    assert !kind.isFixedSuffixSynthetic;
    return createType(
        INTERNAL_SYNTHETIC_CLASS_SEPARATOR,
        kind,
        context.getSynthesizingContextType(),
        id,
        factory);
  }

  static DexType createExternalType(
      SyntheticKind kind, String externalSyntheticTypePrefix, String id, DexItemFactory factory) {
    assert kind.isFixedSuffixSynthetic == id.isEmpty();
    return createType(
        kind.isFixedSuffixSynthetic ? "" : EXTERNAL_SYNTHETIC_CLASS_SEPARATOR,
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

  public static boolean isInternalStaticInterfaceCall(ClassReference reference) {
    return SyntheticNaming.isSynthetic(
        reference, Phase.INTERNAL, SyntheticKind.STATIC_INTERFACE_CALL);
  }

  static boolean isSynthetic(ClassReference clazz, Phase phase, SyntheticKind kind) {
    String typeName = clazz.getTypeName();
    if (kind.isFixedSuffixSynthetic) {
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
