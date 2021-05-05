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
    RECORD_TAG("", false, true, true),
    COMPANION_CLASS("-CC", false, true),
    EMULATED_INTERFACE_CLASS("-EL", false, true),
    LAMBDA("Lambda", false),
    INIT_TYPE_ARGUMENT("-IA", false, true),
    HORIZONTAL_INIT_TYPE_ARGUMENT_1(SYNTHETIC_CLASS_SEPARATOR + "IA$1", false, true),
    HORIZONTAL_INIT_TYPE_ARGUMENT_2(SYNTHETIC_CLASS_SEPARATOR + "IA$2", false, true),
    HORIZONTAL_INIT_TYPE_ARGUMENT_3(SYNTHETIC_CLASS_SEPARATOR + "IA$3", false, true),
    // Method synthetics.
    RECORD_HELPER("Record", true),
    BACKPORT("Backport", true),
    STATIC_INTERFACE_CALL("StaticInterfaceCall", true),
    TO_STRING_IF_NOT_NULL("ToStringIfNotNull", true),
    THROW_CCE_IF_NOT_NULL("ThrowCCEIfNotNull", true),
    THROW_IAE("ThrowIAE", true),
    THROW_ICCE("ThrowICCE", true),
    THROW_NSME("ThrowNSME", true),
    TWR_CLOSE_RESOURCE("TwrCloseResource", true),
    SERVICE_LOADER("ServiceLoad", true),
    OUTLINE("Outline", true);

    public final String descriptor;
    public final boolean isSingleSyntheticMethod;
    public final boolean isFixedSuffixSynthetic;
    public final boolean mayOverridesNonProgramType;

    SyntheticKind(String descriptor, boolean isSingleSyntheticMethod) {
      this(descriptor, isSingleSyntheticMethod, false);
    }

    SyntheticKind(
        String descriptor, boolean isSingleSyntheticMethod, boolean isFixedSuffixSynthetic) {
      this(descriptor, isSingleSyntheticMethod, isFixedSuffixSynthetic, false);
    }

    SyntheticKind(
        String descriptor,
        boolean isSingleSyntheticMethod,
        boolean isFixedSuffixSynthetic,
        boolean mayOverridesNonProgramType) {
      this.descriptor = descriptor;
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
  /** Method prefix when generating synthetic methods in a class. */
  static final String INTERNAL_SYNTHETIC_METHOD_PREFIX = "m";

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

  static boolean isSynthetic(ClassReference clazz, Phase phase, SyntheticKind kind) {
    String typeName = clazz.getTypeName();
    if (kind.isFixedSuffixSynthetic) {
      assert phase == null;
      return clazz.getBinaryName().endsWith(kind.descriptor);
    }
    String separator = getPhaseSeparator(phase);
    int i = typeName.indexOf(separator);
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
