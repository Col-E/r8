// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.DexValue.DexValueAnnotation;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueMethod;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformer;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DexAnnotation extends DexItem implements StructuralItem<DexAnnotation> {

  public enum AnnotatedKind {
    FIELD,
    METHOD,
    TYPE,
    PARAMETER;

    public static AnnotatedKind from(DexDefinition definition) {
      return from(definition.getReference());
    }

    public static AnnotatedKind from(ProgramDefinition definition) {
      return from(definition.getReference());
    }

    public static AnnotatedKind from(DexReference reference) {
      return reference.apply(type -> TYPE, field -> FIELD, method -> METHOD);
    }

    public boolean isParameter() {
      return this == PARAMETER;
    }
  }

  public static final DexAnnotation[] EMPTY_ARRAY = {};
  public static final int VISIBILITY_BUILD = 0x00;
  public static final int VISIBILITY_RUNTIME = 0x01;
  public static final int VISIBILITY_SYSTEM = 0x02;
  public final int visibility;
  public final DexEncodedAnnotation annotation;

  private static void specify(StructuralSpecification<DexAnnotation, ?> spec) {
    spec.withItem(a -> a.annotation).withInt(a -> a.visibility);
  }

  public DexAnnotation(int visibility, DexEncodedAnnotation annotation) {
    this.visibility = visibility;
    this.annotation = annotation;
  }

  @Override
  public DexAnnotation self() {
    return this;
  }

  @Override
  public StructuralMapping<DexAnnotation> getStructuralMapping() {
    return DexAnnotation::specify;
  }

  public DexType getAnnotationType() {
    return annotation.type;
  }

  @Override
  public int hashCode() {
    return visibility + annotation.hashCode() * 3;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof DexAnnotation) {
      DexAnnotation o = (DexAnnotation) other;
      return (visibility == o.visibility) && annotation.equals(o.annotation);
    }
    return false;
  }

  @Override
  public String toString() {
    return visibility + " " + annotation;
  }

  public int getVisibility() {
    return visibility;
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    annotation.collectIndexedItems(appView, indexedItems);
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    mixedItems.add(this);
  }

  public static boolean retainCompileTimeAnnotation(DexType annotation, InternalOptions options) {
    if (options.retainCompileTimeAnnotations) {
      return true;
    }
    if (annotation == options.itemFactory.annotationSynthesizedClass
        || annotation
            .getDescriptor()
            .startsWith(options.itemFactory.dalvikAnnotationOptimizationPrefix)) {
      return true;
    }
    if (options.processCovariantReturnTypeAnnotations) {
      // @CovariantReturnType annotations are processed by CovariantReturnTypeAnnotationTransformer,
      // they thus need to be read here and will then be removed as part of the processing.
      return CovariantReturnTypeAnnotationTransformer.isCovariantReturnTypeAnnotation(
          annotation, options.itemFactory);
    }
    return false;
  }

  public static DexAnnotation createEnclosingClassAnnotation(DexType enclosingClass,
      DexItemFactory factory) {
    return createSystemValueAnnotation(factory.annotationEnclosingClass, factory,
        new DexValueType(enclosingClass));
  }

  public static DexType getEnclosingClassFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    DexValue value = getSystemValueAnnotationValue(factory.annotationEnclosingClass, annotation);
    if (value == null) {
      return null;
    }
    return value.asDexValueType().value;
  }

  public static DexAnnotation createEnclosingMethodAnnotation(DexMethod enclosingMethod,
      DexItemFactory factory) {
    return createSystemValueAnnotation(factory.annotationEnclosingMethod, factory,
        new DexValueMethod(enclosingMethod));
  }

  public static DexMethod getEnclosingMethodFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    DexValue value = getSystemValueAnnotationValue(factory.annotationEnclosingMethod, annotation);
    if (value == null) {
      return null;
    }
    return value.asDexValueMethod().value;
  }

  public static boolean isEnclosingClassAnnotation(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationEnclosingClass;
  }

  public static boolean isEnclosingMethodAnnotation(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationEnclosingMethod;
  }

  public static boolean isInnerClassAnnotation(DexAnnotation annotation, DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationInnerClass;
  }

  public static boolean isMemberClassesAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationMemberClasses;
  }

  public static boolean isNestHostAnnotation(DexAnnotation annotation, DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationNestHost;
  }

  public static boolean isNestMembersAnnotation(DexAnnotation annotation, DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationNestMembers;
  }

  public static boolean isPermittedSubclassesAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationPermittedSubclasses;
  }

  public static DexAnnotation createInnerClassAnnotation(
      DexString clazz, int access, DexItemFactory factory) {
    return new DexAnnotation(
        VISIBILITY_SYSTEM,
        new DexEncodedAnnotation(
            factory.annotationInnerClass,
            new DexAnnotationElement[] {
              new DexAnnotationElement(
                  factory.createString("accessFlags"), DexValueInt.create(access)),
              new DexAnnotationElement(
                  factory.createString("name"),
                  (clazz == null) ? DexValueNull.NULL : new DexValueString(clazz))
            }));
  }

  public static Pair<DexString, Integer> getInnerClassFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    assert isInnerClassAnnotation(annotation, factory);
    DexAnnotationElement[] elements = annotation.annotation.elements;
    Pair<DexString, Integer> result = new Pair<>();
    for (DexAnnotationElement element : elements) {
      if (element.name == factory.createString("name")) {
        if (element.value.isDexValueString()) {
          result.setFirst(element.value.asDexValueString().getValue());
        }
      } else {
        assert element.name == factory.createString("accessFlags");
        result.setSecond(element.value.asDexValueInt().getValue());
      }
    }
    return result;
  }

  public static DexAnnotation createMemberClassesAnnotation(List<DexType> classes,
      DexItemFactory factory) {
    DexValue[] values = new DexValue[classes.size()];
    for (int i = 0; i < classes.size(); i++) {
      values[i] = new DexValueType(classes.get(i));
    }
    return createSystemValueAnnotation(factory.annotationMemberClasses, factory,
        new DexValueArray(values));
  }

  public static List<DexType> getMemberClassesFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    DexValue value = getSystemValueAnnotationValue(factory.annotationMemberClasses, annotation);
    if (value == null) {
      return null;
    }
    DexValueArray membersArray = value.asDexValueArray();
    List<DexType> types = new ArrayList<>(membersArray.getValues().length);
    for (DexValue elementValue : membersArray.getValues()) {
      types.add(elementValue.asDexValueType().value);
    }
    return types;
  }

  public static DexType getNestHostFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    DexValue value = getSystemValueAnnotationValue(factory.annotationNestHost, annotation);
    if (value == null) {
      return null;
    }
    return value.asDexValueType().getValue();
  }

  private static List<DexType> getTypesFromAnnotation(
      DexType annotationType, DexAnnotation annotation) {
    DexValue value = getSystemValueAnnotationValue(annotationType, annotation);
    if (value == null) {
      return null;
    }
    DexValueArray membersArray = value.asDexValueArray();
    List<DexType> types = new ArrayList<>(membersArray.getValues().length);
    for (DexValue elementValue : membersArray.getValues()) {
      types.add(elementValue.asDexValueType().value);
    }
    return types;
  }

  public static List<DexType> getNestMembersFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    return getTypesFromAnnotation(factory.annotationNestMembers, annotation);
  }

  public static List<DexType> getPermittedSubclassesFromAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    return getTypesFromAnnotation(factory.annotationPermittedSubclasses, annotation);
  }

  public static DexAnnotation createSourceDebugExtensionAnnotation(DexValue value,
      DexItemFactory factory) {
    return new DexAnnotation(VISIBILITY_SYSTEM,
        new DexEncodedAnnotation(factory.annotationSourceDebugExtension,
            new DexAnnotationElement[] {
              new DexAnnotationElement(factory.createString("value"), value)
            }));
  }

  public static DexAnnotation createMethodParametersAnnotation(DexValue[] names,
      DexValue[] accessFlags, DexItemFactory factory) {
    assert names.length == accessFlags.length;
    return new DexAnnotation(VISIBILITY_SYSTEM,
        new DexEncodedAnnotation(factory.annotationMethodParameters,
            new DexAnnotationElement[]{
                new DexAnnotationElement(
                    factory.createString("names"),
                    new DexValueArray(names)),
                new DexAnnotationElement(
                    factory.createString("accessFlags"),
                    new DexValueArray(accessFlags))
            }));
  }

  public static DexAnnotation createAnnotationDefaultAnnotation(DexType type,
      List<DexAnnotationElement> defaults, DexItemFactory factory) {
    return createSystemValueAnnotation(factory.annotationDefault, factory,
        new DexValueAnnotation(
            new DexEncodedAnnotation(type,
                defaults.toArray(DexAnnotationElement.EMPTY_ARRAY)))
    );
  }

  public static DexAnnotation createSignatureAnnotation(String signature, DexItemFactory factory) {
    return createSystemValueAnnotation(factory.annotationSignature, factory,
        compressSignature(signature, factory));
  }

  public static DexAnnotation createNestHostAnnotation(
      NestHostClassAttribute host, DexItemFactory factory) {
    return createSystemValueAnnotation(
        factory.annotationNestHost, factory, new DexValue.DexValueType(host.getNestHost()));
  }

  public static DexAnnotation createNestMembersAnnotation(
      List<NestMemberClassAttribute> members, DexItemFactory factory) {
    List<DexValueType> list = new ArrayList<>(members.size());
    for (NestMemberClassAttribute member : members) {
      list.add(new DexValue.DexValueType(member.getNestMember()));
    }
    return createSystemValueAnnotation(
        factory.annotationNestMembers,
        factory,
        new DexValue.DexValueArray(list.toArray(DexValue.EMPTY_ARRAY)));
  }

  public static DexAnnotation createPermittedSubclassesAnnotation(
      List<PermittedSubclassAttribute> permittedSubclasses, DexItemFactory factory) {
    List<DexValueType> list = new ArrayList<>(permittedSubclasses.size());
    for (PermittedSubclassAttribute permittedSubclass : permittedSubclasses) {
      list.add(new DexValue.DexValueType(permittedSubclass.getPermittedSubclass()));
    }
    return createSystemValueAnnotation(
        factory.annotationPermittedSubclasses,
        factory,
        new DexValue.DexValueArray(list.toArray(DexValue.EMPTY_ARRAY)));
  }

  public static String getSignature(DexAnnotation signatureAnnotation) {
    DexValueArray elements = signatureAnnotation.annotation.elements[0].value.asDexValueArray();
    StringBuilder signature = new StringBuilder();
    for (DexValue element : elements.getValues()) {
      signature.append(element.asDexValueString().value.toString());
    }
    return signature.toString();
  }

  public static String getSignature(DexAnnotationSet signatureAnnotations, DexItemFactory factory) {
    DexAnnotation signature = signatureAnnotations.getFirstMatching(factory.annotationSignature);
    return signature == null ? null : getSignature(signature);
  }

  public static DexAnnotation createThrowsAnnotation(DexValue[] exceptions,
      DexItemFactory factory) {
    return createSystemValueAnnotation(factory.annotationThrows, factory,
        new DexValueArray(exceptions));
  }

  private static DexAnnotation createSystemValueAnnotation(DexType type, DexItemFactory factory,
      DexValue value) {
    return new DexAnnotation(VISIBILITY_SYSTEM,
        new DexEncodedAnnotation(type, new DexAnnotationElement[]{
            new DexAnnotationElement(factory.createString("value"), value)
        }));
  }

  private static DexValue getSystemValueAnnotationValue(DexType type, DexAnnotation annotation) {
    assert annotation.visibility == VISIBILITY_SYSTEM;
    assert annotation.annotation.type == type;
    return annotation.annotation.elements.length == 0
        ? null
        : annotation.annotation.elements[0].value;
  }

  public static boolean isThrowingAnnotation(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationThrows;
  }

  public static boolean isSignatureAnnotation(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationSignature;

  }

  public static boolean isAnnotationDefaultAnnotation(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationDefault;
  }

  public static boolean isJavaLangRetentionAnnotation(
      DexAnnotation annotation, DexItemFactory factory) {
    return annotation.getAnnotationType() == factory.retentionType;
  }

  public static boolean isSourceDebugExtension(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationSourceDebugExtension;
  }

  public static boolean isParameterNameAnnotation(DexAnnotation annotation,
      DexItemFactory factory) {
    return annotation.annotation.type == factory.annotationMethodParameters;
  }

  /**
   * As a simple heuristic for compressing a signature by splitting on fully qualified class names
   * and make them individual part. All other parts of the signature are simply grouped and separate
   * the names.
   * For examples, "()Ljava/lang/List<Lfoo/bar/Baz;>;" splits into:
   * <pre>
   *   ["()", "Ljava/lang/List<", "Lfoo/bar/Baz;", ">;"]
   * </pre>
   */
  private static DexValue compressSignature(String signature, DexItemFactory factory) {
    final int length = signature.length();
    List<DexValue> parts = new ArrayList<>();

    for (int at = 0; at < length; /*at*/) {
      char c = signature.charAt(at);
      int endAt = at + 1;
      if (c == 'L') {
        // Scan to ';' or '<' and consume them.
        while (endAt < length) {
          c = signature.charAt(endAt);
          if (c == ';' || c == '<') {
            endAt++;
            break;
          }
          endAt++;
        }
      } else {
        // Scan to 'L' without consuming it.
        while (endAt < length) {
          c = signature.charAt(endAt);
          if (c == 'L') {
            break;
          }
          endAt++;
        }
      }

      parts.add(toDexValue(signature.substring(at, endAt), factory));
      at = endAt;
    }

    return new DexValueArray(parts.toArray(DexValue.EMPTY_ARRAY));
  }

  private static DexValue toDexValue(String string, DexItemFactory factory) {
    return new DexValueString(factory.createString(string));
  }

  public static DexAnnotation createAnnotationSynthesizedClass(
      SyntheticKind kind, DexItemFactory dexItemFactory) {
    DexString versionHash =
        dexItemFactory.createString(dexItemFactory.getSyntheticNaming().getVersionHash());
    DexAnnotationElement kindElement =
        new DexAnnotationElement(dexItemFactory.kindString, DexValueInt.create(kind.getId()));
    DexAnnotationElement versionHashElement =
        new DexAnnotationElement(dexItemFactory.versionHashString, new DexValueString(versionHash));
    DexAnnotationElement[] elements = new DexAnnotationElement[] {kindElement, versionHashElement};
    return new DexAnnotation(
        VISIBILITY_BUILD,
        new DexEncodedAnnotation(dexItemFactory.annotationSynthesizedClass, elements));
  }

  public static boolean hasSynthesizedClassAnnotation(
      DexAnnotationSet annotations, DexItemFactory factory, SyntheticItems synthetics) {
    return getSynthesizedClassAnnotationInfo(annotations, factory, synthetics) != null;
  }

  public static SyntheticKind getSynthesizedClassAnnotationInfo(
      DexAnnotationSet annotations, DexItemFactory factory, SyntheticItems synthetics) {
    if (annotations.size() != 1) {
      return null;
    }
    DexAnnotation annotation = annotations.annotations[0];
    if (annotation.annotation.type != factory.annotationSynthesizedClass) {
      return null;
    }
    int length = annotation.annotation.elements.length;
    if (length != 2) {
      return null;
    }
    assert factory.kindString.isLessThan(factory.versionHashString);
    DexAnnotationElement kindElement = annotation.annotation.elements[0];
    DexAnnotationElement versionHashElement = annotation.annotation.elements[1];
    if (kindElement.name != factory.kindString) {
      return null;
    }
    if (!kindElement.value.isDexValueInt()) {
      return null;
    }
    if (versionHashElement.name != factory.versionHashString) {
      return null;
    }
    if (!versionHashElement.value.isDexValueString()) {
      return null;
    }
    String currentVersionHash = synthetics.getNaming().getVersionHash();
    String syntheticVersionHash = versionHashElement.value.asDexValueString().getValue().toString();
    if (!currentVersionHash.equals(syntheticVersionHash)) {
      return null;
    }
    SyntheticKind kind =
        synthetics.getNaming().fromId(kindElement.value.asDexValueInt().getValue());
    return kind;
  }

  public DexAnnotation rewrite(Function<DexEncodedAnnotation, DexEncodedAnnotation> rewriter) {
    DexEncodedAnnotation rewritten = rewriter.apply(annotation);
    if (rewritten == annotation) {
      return this;
    }
    if (rewritten == null) {
      return null;
    }
    return new DexAnnotation(visibility, rewritten);
  }
}
