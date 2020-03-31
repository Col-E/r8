// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMemberInfo.EMPTY_TYPE_PARAM_INFO;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmMethodSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizerUtils.populateKmTypeFromSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizerUtils.toClassifier;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToKotlinClassifier;
import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKmType;
import static kotlinx.metadata.Flag.Property.IS_VAR;
import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.kotlin.KotlinMemberInfo.KotlinFunctionInfo;
import com.android.tools.r8.kotlin.KotlinMemberInfo.KotlinPropertyInfo;
import com.android.tools.r8.kotlin.KotlinMetadataSynthesizerUtils.AddKotlinAnyType;
import com.android.tools.r8.kotlin.KotlinMetadataSynthesizerUtils.KmVisitorOption;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmClassifier;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.KmVariance;
import kotlinx.metadata.jvm.JvmExtensionsKt;

class KotlinMetadataSynthesizer {

  final AppView<AppInfoWithLiveness> appView;
  final NamingLens lens;
  private final List<KmTypeParameter> classTypeParameters;

  public KotlinMetadataSynthesizer(
      AppView<AppInfoWithLiveness> appView, NamingLens lens, KotlinInfo<?> kotlinInfo) {
    this.appView = appView;
    this.lens = lens;
    this.classTypeParameters = kotlinInfo.getTypeParameters();
  }

  static boolean isExtension(KmFunction kmFunction) {
    return kmFunction.getReceiverParameterType() != null;
  }

  static boolean isExtension(KmProperty kmProperty) {
    return kmProperty.getReceiverParameterType() != null;
  }

  static KmType toKmType(String descriptor) {
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(descriptorToKotlinClassifier(descriptor));
    return kmType;
  }

  private DexType toRenamedType(DexType type) {
    // For library or classpath class, synthesize @Metadata always.
    // For a program class, make sure it is live.
    if (!appView.appInfo().isNonProgramTypeOrLiveProgramType(type)) {
      return null;
    }
    DexType renamedType = lens.lookupType(type, appView.dexItemFactory());
    // For library or classpath class, we should not have renamed it.
    DexClass clazz = appView.definitionFor(type);
    assert clazz == null || clazz.isProgramClass() || renamedType == type
        : type.toSourceString() + " -> " + renamedType.toSourceString();
    return renamedType;
  }

  String toRenamedBinaryName(DexType type) {
    DexType renamedType = toRenamedType(type);
    if (renamedType == null) {
      return null;
    }
    return getBinaryNameFromDescriptor(renamedType.toDescriptorString());
  }

  String toRenamedClassifier(DexType type) {
    type = type.isClassType() ? toRenamedType(type) : type;
    if (type == null) {
      return null;
    }
    return toClassifier(type, appView.dexItemFactory());
  }

  // TODO(b/148654451): Canonicalization?
  KmType toRenamedKmType(
      DexType type,
      TypeSignature typeSignature,
      KotlinTypeInfo originalKotlinTypeInfo,
      List<KmTypeParameter> typeParameters) {
    if (originalKotlinTypeInfo != null && originalKotlinTypeInfo.isTypeAlias()) {
      KmType kmType = new KmType(flagsOf());
      kmType.visitTypeAlias(originalKotlinTypeInfo.asTypeAlias().getName());
      return kmType;
    }
    return toRenamedKmTypeWithClassifier(
        type, originalKotlinTypeInfo, typeSignature, typeParameters);
  }

  private KmType toRenamedKmTypeWithClassifierForFieldSignature(
      KotlinTypeInfo originalTypeInfo,
      FieldTypeSignature fieldSignature,
      List<KmTypeParameter> typeParameters) {
    Box<KmType> kmTypeBox = new Box<>();
    populateKmTypeFromSignature(
        fieldSignature,
        originalTypeInfo,
        (kmVisitorOption) -> {
          assert kmVisitorOption == KmVisitorOption.VISIT_NEW;
          KmType value = new KmType(flagsOf());
          kmTypeBox.set(value);
          return value;
        },
        typeParameters,
        appView.dexItemFactory(),
        AddKotlinAnyType.ADD);
    return kmTypeBox.get();
  }

  private KmType toRenamedKmTypeWithClassifier(
      DexType type,
      KotlinTypeInfo originalTypeInfo,
      TypeSignature typeSignature,
      List<KmTypeParameter> typeParameters) {
    if (typeSignature != null && typeSignature.isFieldTypeSignature()) {
      KmType renamedKmType =
          toRenamedKmTypeWithClassifierForFieldSignature(
              originalTypeInfo, typeSignature.asFieldTypeSignature(), typeParameters);
      if (renamedKmType != null) {
        return renamedKmType;
      }
    }
    String classifier = toRenamedClassifier(type);
    if (classifier == null) {
      return null;
    }
    // Seems like no flags for KmType are ever set, thus passing in flagsOf() seems ok.
    KmType renamedKmType = new KmType(flagsOf());
    renamedKmType.visitClass(classifier);
    // TODO(b/151194164): Can be generalized too, like ArrayTypeSignature.Converter ?
    // E.g., java.lang.String[] -> KmType(kotlin/Array, KmTypeProjection(OUT, kotlin/String))
    if (type.isArrayType() && !type.isPrimitiveArrayType()) {
      DexType elementType = type.toArrayElementType(appView.dexItemFactory());
      KmType argumentType = toRenamedKmTypeWithClassifier(elementType, null, null, typeParameters);
      KmVariance variance =
          originalTypeInfo != null && originalTypeInfo.isObjectArray()
              ? originalTypeInfo.getArguments().get(0).variance
              : KmVariance.INVARIANT;
      renamedKmType.getArguments().add(new KmTypeProjection(variance, argumentType));
    }
    return renamedKmType;
  }

  KmConstructor toRenamedKmConstructor(DexClass clazz, DexEncodedMethod method) {
    // Make sure it is an instance initializer and live.
    if (!method.isInstanceInitializer()
        || !appView.appInfo().liveMethods.contains(method.method)) {
      return null;
    }
    // Take access flags from metadata.
    KotlinFunctionInfo kotlinFunctionInfo = method.getKotlinMemberInfo().asFunctionInfo();
    int flags;
    List<KotlinTypeParameterInfo> originalTypeParameterInfo;
    if (kotlinFunctionInfo != null) {
      flags = kotlinFunctionInfo.flags;
      originalTypeParameterInfo = kotlinFunctionInfo.kotlinTypeParameterInfo;
    } else {
      flags = method.accessFlags.getAsKotlinFlags();
      originalTypeParameterInfo = EMPTY_TYPE_PARAM_INFO;
    }
    KmConstructor kmConstructor = new KmConstructor(flags);
    JvmExtensionsKt.setSignature(kmConstructor, toJvmMethodSignature(method.method));
    MethodTypeSignature signature = GenericSignature.Parser.toMethodTypeSignature(method, appView);
    List<KmTypeParameter> typeParameters =
        convertFormalTypeParameters(
            originalTypeParameterInfo,
            signature.getFormalTypeParameters(),
            kmTypeParameter -> {
              assert false : "KmConstructor cannot have additional type parameters";
            });
    List<KmValueParameter> parameters = kmConstructor.getValueParameters();
    if (!populateKmValueParameters(method, signature, parameters, typeParameters)) {
      return null;
    }
    // For inner, non-static classes, the type-parameter for the receiver should not have a
    // value-parameter:
    // val myInner : OuterNestedInner = nested.Inner(1)
    // Will have value-parameters for the constructor:
    // #  constructors: KmConstructor[
    // #    KmConstructor{
    // #      flags: 6,
    // #      valueParameters: KmValueParameter[
    // #        KmValueParameter{
    // #          flags: 0,
    // #          name: x,
    // #          type: KmType{
    // #            flags: 0,
    // #            classifier: Class(name=kotlin/Int),
    // #            arguments: KmTypeProjection[],
    // #            abbreviatedType: null,
    // #            outerType: null,
    // #            raw: false,
    // #            annotations: KmAnnotion[],
    // #          },
    // #          varargElementType: null,
    // #        }
    // #      ],
    // #     signature: <init>(Lcom/android/tools/r8/kotlin/metadata/typealias_lib/Outer$Nested;I)V,
    // #    }
    // #  ],
    // A bit weird since the signature obviously have two value-parameters.
    List<InnerClassAttribute> innerClasses = clazz.getInnerClasses();
    if (!parameters.isEmpty() && !innerClasses.isEmpty()) {
      DexType immediateOuterType = null;
      for (InnerClassAttribute innerClass : innerClasses) {
        if (innerClass.getInner() == clazz.type) {
          immediateOuterType = innerClass.getOuter();
          break;
        }
      }
      if (immediateOuterType != null) {
        String classifier = toRenamedClassifier(immediateOuterType);
        KmType potentialReceiver = parameters.get(0).getType();
        if (potentialReceiver != null
            && potentialReceiver.classifier instanceof KmClassifier.Class
            && ((KmClassifier.Class) potentialReceiver.classifier).getName().equals(classifier)) {
          parameters.remove(0);
        }
      }
    }
    return kmConstructor;
  }

  KmFunction toRenamedKmFunction(DexEncodedMethod method) {
    // For library overrides, synthesize @Metadata always.
    // For regular methods, make sure it is live or pinned.
    if (!method.isLibraryMethodOverride().isTrue()
        && !appView.appInfo().isPinned(method.method)
        && !appView.appInfo().liveMethods.contains(method.method)) {
      return null;
    }
    DexMethod renamedMethod = lens.lookupMethod(method.method, appView.dexItemFactory());
    // For a library method override, we should not have renamed it.
    assert !method.isLibraryMethodOverride().isTrue() || renamedMethod.name == method.method.name
        : method.toSourceString() + " -> " + renamedMethod.toSourceString();
    // TODO(b/151194869): Should we keep kotlin-specific flags only while synthesizing the base
    //  value from general JVM flags?
    KotlinFunctionInfo kotlinMemberInfo = method.getKotlinMemberInfo().asFunctionInfo();
    assert kotlinMemberInfo != null;
    int flag =
        appView.appInfo().isPinned(method.method)
            ? kotlinMemberInfo.flags
            : method.accessFlags.getAsKotlinFlags();
    KmFunction kmFunction = new KmFunction(flag, renamedMethod.name.toString());
    JvmExtensionsKt.setSignature(kmFunction, toJvmMethodSignature(renamedMethod));

    // TODO(b/129925954): Should this be integrated as part of DexDefinition instead of parsing it
    //  on demand? That may require utils to map internal encoding of signature back to
    //  corresponding backend definitions, like DexAnnotation (DEX) or Signature attribute (CF).
    MethodTypeSignature signature = GenericSignature.Parser.toMethodTypeSignature(method, appView);
    List<KmTypeParameter> methodTypeParameters = kmFunction.getTypeParameters();
    DexProto proto = method.method.proto;
    DexType returnType = proto.returnType;
    TypeSignature returnSignature = signature.returnType().typeSignature();
    List<KmTypeParameter> allTypeParameters =
        convertFormalTypeParameters(
            kotlinMemberInfo.kotlinTypeParameterInfo,
            signature.getFormalTypeParameters(),
            methodTypeParameters::add);
    KmType kmReturnType =
        toRenamedKmType(
            returnType, returnSignature, kotlinMemberInfo.returnType, allTypeParameters);
    if (kmReturnType == null) {
      return null;
    }
    kmFunction.setReturnType(kmReturnType);
    if (method.isKotlinExtensionFunction()) {
      assert proto.parameters.values.length > 0 : method.method.toSourceString();
      DexType receiverType = proto.parameters.values[0];
      TypeSignature receiverSignature = signature.getParameterTypeSignature(0);
      KmType kmReceiverType =
          toRenamedKmType(
              receiverType,
              receiverSignature,
              kotlinMemberInfo.receiverParameterType,
              allTypeParameters);
      if (kmReceiverType == null) {
        return null;
      }
      kmFunction.setReceiverParameterType(kmReceiverType);
    }

    if (!populateKmValueParameters(
        method, signature, kmFunction.getValueParameters(), allTypeParameters)) {
      return null;
    }
    return kmFunction;
  }

  private List<KmTypeParameter> convertFormalTypeParameters(
      List<KotlinTypeParameterInfo> originalTypeParameterInfo,
      List<FormalTypeParameter> parameters,
      Consumer<KmTypeParameter> addedFromParameters) {
    return KotlinMetadataSynthesizerUtils.convertFormalTypeParameters(
        classTypeParameters,
        originalTypeParameterInfo,
        parameters,
        appView.dexItemFactory(),
        addedFromParameters);
  }

  private boolean populateKmValueParameters(
      DexEncodedMethod method,
      MethodTypeSignature signature,
      List<KmValueParameter> parameters,
      List<KmTypeParameter> typeParameters) {
    KotlinFunctionInfo kotlinFunctionInfo = method.getKotlinMemberInfo().asFunctionInfo();
    if (kotlinFunctionInfo == null) {
      return false;
    }
    boolean isExtension = method.isKotlinExtensionFunction();
    for (int i = isExtension ? 1 : 0; i < method.method.proto.parameters.values.length; i++) {
      DexType parameterType = method.method.proto.parameters.values[i];
      DebugLocalInfo debugLocalInfo = method.getParameterInfo().get(i);
      String parameterName = debugLocalInfo != null ? debugLocalInfo.name.toString() : ("p" + i);
      KotlinValueParameterInfo valueParameterInfo =
          kotlinFunctionInfo.getValueParameterInfo(isExtension ? i - 1 : i);
      TypeSignature parameterTypeSignature = signature.getParameterTypeSignature(i);
      KmValueParameter kmValueParameter =
          toRewrittenKmValueParameter(
              valueParameterInfo,
              parameterType,
              parameterTypeSignature,
              parameterName,
              typeParameters);
      if (kmValueParameter == null) {
        return false;
      }
      parameters.add(kmValueParameter);
    }
    return true;
  }

  private KmValueParameter toRewrittenKmValueParameter(
      KotlinValueParameterInfo valueParameterInfo,
      DexType parameterType,
      TypeSignature parameterTypeSignature,
      String candidateParameterName,
      List<KmTypeParameter> typeParameters) {
    int flag = valueParameterInfo != null ? valueParameterInfo.flag : flagsOf();
    String name = valueParameterInfo != null ? valueParameterInfo.name : candidateParameterName;
    KmValueParameter kmValueParameter = new KmValueParameter(flag, name);
    KotlinTypeInfo originalKmTypeInfo = valueParameterInfo != null ? valueParameterInfo.type : null;
    KmType kmParamType =
        toRenamedKmType(parameterType, parameterTypeSignature, originalKmTypeInfo, typeParameters);
    if (kmParamType == null) {
      return null;
    }
    kmValueParameter.setType(kmParamType);
    if (valueParameterInfo != null) {
      JvmExtensionsKt.getAnnotations(kmParamType).addAll(valueParameterInfo.annotations);
    }
    if (valueParameterInfo != null && valueParameterInfo.isVararg) {
      // TODO(b/152389234): Test for arrays in varargs.
      if (!parameterType.isArrayType()) {
        return null;
      }
      // vararg x: T gets translated to x: Array<out T>
      DexType elementType = parameterType.toArrayElementType(appView.dexItemFactory());
      TypeSignature elementSignature =
          parameterTypeSignature != null
              ? parameterTypeSignature.toArrayElementTypeSignature(appView) : null;
      KmType kmElementType = toRenamedKmType(elementType, elementSignature, null, typeParameters);
      if (kmElementType == null) {
        return null;
      }
      kmValueParameter.setVarargElementType(kmElementType);
    }

    return kmValueParameter;
  }

  /**
   * A group of a field, and methods that correspond to a Kotlin property.
   *
   * <p>
   * va(l|r) prop: T
   *
   * is converted to a backing field with signature `T prop` if necessary. If the property is a pure
   * computation, e.g., return `this` or access to the status of other properties, a field is not
   * needed for the property.
   * <p>
   * Depending on property visibility, getter is defined with signature `T getProp()` (or `isProp`
   * for boolean property).
   * <p>
   * If it's editable, i.e., declared as `var`, setter is defined with signature `void setProp(T)`.
   * <p>
   * Yet another addition, `void prop$annotations()`, seems(?) to be used to store associated
   * annotations.
   *
   * <p>
   * Currently, it's unclear how users can selectively keep each. Assuming every piece is kept by
   * more general rules, such as keeping non-private members, we expect to find those as a whole.
   * When rewriting a corresponding property, each information is used to update corresponding part,
   * e.g., field to update the return type of the property, getter to update jvmMethodSignature of
   * getter, and so on.
   */
  static class KmPropertyGroup {

    final int flags;
    final String name;
    final DexEncodedField field;
    private final DexEncodedMethod getter;
    private final KotlinPropertyInfo getterInfo;
    private final DexEncodedMethod setter;
    private final KotlinPropertyInfo setterInfo;
    final DexEncodedMethod annotations;
    final boolean isExtension;
    private final List<KmTypeParameter> classTypeParameters;

    private KmPropertyGroup(
        int flags,
        String name,
        DexEncodedField field,
        DexEncodedMethod getter,
        KotlinPropertyInfo getterInfo,
        DexEncodedMethod setter,
        KotlinPropertyInfo setterInfo,
        DexEncodedMethod annotations,
        boolean isExtension,
        List<KmTypeParameter> classTypeParameters) {
      this.flags = flags;
      this.name = name;
      this.field = field;
      this.getter = getter;
      this.getterInfo = getterInfo;
      this.setter = setter;
      this.setterInfo = setterInfo;
      this.annotations = annotations;
      this.isExtension = isExtension;
      this.classTypeParameters = classTypeParameters;
    }

    static Builder builder(int flags, String name, List<KmTypeParameter> classTypeParameters) {
      return new Builder(flags, name, classTypeParameters);
    }

    static class Builder {

      private final int flags;
      private final String name;
      private DexEncodedField field;
      private DexEncodedMethod getter;
      private KotlinPropertyInfo getterInfo;
      private DexEncodedMethod setter;
      private KotlinPropertyInfo setterInfo;
      private DexEncodedMethod annotations;
      private List<KmTypeParameter> classTypeParameters;

      private boolean isExtensionGetter;
      private boolean isExtensionSetter;
      private boolean isExtensionAnnotations;

      private Builder(int flags, String name, List<KmTypeParameter> classTypeParameters) {
        this.flags = flags;
        this.name = name;
        this.classTypeParameters = classTypeParameters;
      }

      Builder foundBackingField(DexEncodedField field) {
        this.field = field;
        return this;
      }

      Builder foundGetter(DexEncodedMethod getter, KotlinPropertyInfo propertyInfo) {
        this.getter = getter;
        this.getterInfo = propertyInfo;
        return this;
      }

      Builder foundSetter(DexEncodedMethod setter, KotlinPropertyInfo propertyInfo) {
        this.setter = setter;
        this.setterInfo = propertyInfo;
        return this;
      }

      Builder foundAnnotations(DexEncodedMethod annotations) {
        this.annotations = annotations;
        return this;
      }

      Builder isExtensionGetter() {
        this.isExtensionGetter = true;
        return this;
      }

      Builder isExtensionSetter() {
        this.isExtensionSetter = true;
        return this;
      }

      Builder isExtensionAnnotations() {
        this.isExtensionAnnotations = true;
        return this;
      }

      KmPropertyGroup build() {
        boolean isExtension = isExtensionGetter || isExtensionSetter || isExtensionAnnotations;
        // If this is an extension property, everything should be an extension.
        if (isExtension) {
          if (getter != null && !isExtensionGetter) {
            return null;
          }
          if (setter != null && !isExtensionSetter) {
            return null;
          }
          if (annotations != null && !isExtensionAnnotations) {
            return null;
          }
        }
        return new KmPropertyGroup(
            flags,
            name,
            field,
            getter,
            getterInfo,
            setter,
            setterInfo,
            annotations,
            isExtension,
            classTypeParameters);
      }
    }

    private String setFieldForProperty(
        KmProperty kmProperty,
        KmType defaultPropertyType,
        AppView<AppInfoWithLiveness> appView,
        NamingLens lens,
        KotlinMetadataSynthesizer synthesizer) {
      DexField renamedField = lens.lookupField(field.field, appView.dexItemFactory());
      if (kmProperty.getReturnType() == defaultPropertyType) {
        KmType kmPropertyType =
            synthesizer.toRenamedKmType(field.field.type, null, null, this.classTypeParameters);
        if (kmPropertyType != null) {
          kmProperty.setReturnType(kmPropertyType);
        }
      }
      JvmExtensionsKt.setFieldSignature(kmProperty, toJvmFieldSignature(renamedField));
      return appView.appInfo().isPinned(field.field) ? null : renamedField.name.toString();
    }

    private boolean setReceiverParameterTypeForExtensionProperty(
        KmProperty kmProperty,
        DexEncodedMethod method,
        AppView<AppInfoWithLiveness> appView,
        KotlinMetadataSynthesizer synthesizer) {
      if (!isExtension) {
        return true;
      }
      MethodTypeSignature signature =
          GenericSignature.Parser.toMethodTypeSignature(method, appView);
      // TODO(b/152599446): Update with type parameters for the receiver.
      List<KmTypeParameter> typeParameters =
          KotlinMetadataSynthesizerUtils.convertFormalTypeParameters(
              classTypeParameters,
              KotlinMemberInfo.EMPTY_TYPE_PARAM_INFO,
              signature.getFormalTypeParameters(),
              appView.dexItemFactory(),
              kmTypeParameter -> {});
      DexType receiverType = method.method.proto.parameters.values[0];
      TypeSignature receiverSignature = signature.getParameterTypeSignature(0);
      KmType kmReceiverType =
          synthesizer.toRenamedKmType(receiverType, receiverSignature, null, typeParameters);
      if (kmProperty.getReceiverParameterType() != null) {
        // If the receiver type for the extension property is set already make sure it's consistent.
        return getDescriptorFromKmType(kmReceiverType)
            .equals(getDescriptorFromKmType(kmProperty.getReceiverParameterType()));
      }
      kmProperty.setReceiverParameterType(kmReceiverType);
      return true;
    }

    private String setGetterForProperty(
        KmProperty kmProperty,
        KmType defaultPropertyType,
        AppView<AppInfoWithLiveness> appView,
        NamingLens lens,
        KotlinMetadataSynthesizer synthesizer) {
      if (checkGetterCriteria() == GetterSetterCriteria.NOT_AVAILABLE) {
        // Property without getter.
        // Even though a getter does not exist, `kotlinc` still set getter flags and use them to
        // determine when to direct field access v.s. getter calls for property resolution.
        if (field != null) {
          kmProperty.setGetterFlags(field.accessFlags.getAsKotlinFlags());
        }
        return kmProperty.getName();
      }
      assert checkGetterCriteria() == GetterSetterCriteria.MET;
      assert getter != null;
      DexProto proto = getter.method.proto;
      assert proto.parameters.size() == (isExtension ? 1 : 0)
          : "checkGetterCriteria: " + this.toString();
      MethodTypeSignature signature =
          GenericSignature.Parser.toMethodTypeSignature(getter, appView);
      // TODO(b/152599446): Update with type parameters for the getter.
      List<KmTypeParameter> typeParameters =
          KotlinMetadataSynthesizerUtils.convertFormalTypeParameters(
              this.classTypeParameters,
              ImmutableList.of(),
              signature.getFormalTypeParameters(),
              appView.dexItemFactory(),
              kmTypeParameter -> {});
      DexType returnType = proto.returnType;
      TypeSignature returnSignature = signature.returnType().typeSignature();
      KmType kmPropertyType =
          synthesizer.toRenamedKmType(
              returnType, returnSignature, getterInfo.returnType, typeParameters);
      if (kmProperty.getReturnType() == defaultPropertyType) {
        // The property type is not set yet.
        kmProperty.setReturnType(kmPropertyType);
      } else if (!getDescriptorFromKmType(kmPropertyType)
          .equals(getDescriptorFromKmType(kmProperty.getReturnType()))) {
        // If property type is set already (via backing field), make sure it's consistent.
        return null;
      }
      DexMethod renamedGetter = lens.lookupMethod(getter.method, appView.dexItemFactory());
      kmProperty.setGetterFlags(getter.accessFlags.getAsKotlinFlags());
      JvmExtensionsKt.setGetterSignature(kmProperty, toJvmMethodSignature(renamedGetter));
      return appView.appInfo().isPinned(getter.method) ? null : renamedGetter.name.toString();
    }

    private String setSetterForProperty(
        KmProperty kmProperty,
        KmType defaultPropertyType,
        AppView<AppInfoWithLiveness> appView,
        NamingLens lens,
        KotlinMetadataSynthesizer synthesizer) {
      GetterSetterCriteria criteria = checkSetterCriteria();
      if (criteria == GetterSetterCriteria.VIOLATE) {
        return kmProperty.getName();
      }
      if (criteria == GetterSetterCriteria.NOT_AVAILABLE) {
        if (field != null && IS_VAR.invoke(flags)) {
          // Editable property without setter.
          // Even though a setter does not exist, `kotlinc` still set setter flags and use them to
          // determine when to direct field access v.s. setter calls for property resolution.
          kmProperty.setSetterFlags(field.accessFlags.getAsKotlinFlags());
        }
        return kmProperty.getName();
      }
      assert criteria == GetterSetterCriteria.MET;
      assert setter != null;
      DexProto proto = setter.method.proto;
      assert proto.parameters.size() == (isExtension ? 2 : 1)
          : "checkSetterCriteria: " + this.toString();
      MethodTypeSignature signature =
          GenericSignature.Parser.toMethodTypeSignature(setter, appView);
      // TODO(b/152599446): Update with type parameters for the setter.
      List<KmTypeParameter> typeParameters =
          KotlinMetadataSynthesizerUtils.convertFormalTypeParameters(
              classTypeParameters,
              ImmutableList.of(),
              signature.getFormalTypeParameters(),
              appView.dexItemFactory(),
              kmTypeParameter -> {});
      int valueIndex = isExtension ? 1 : 0;
      DexType valueType = proto.parameters.values[valueIndex];
      TypeSignature valueSignature = signature.getParameterTypeSignature(valueIndex);
      KmType kmPropertyType =
          synthesizer.toRenamedKmType(
              valueType, valueSignature, setterInfo.returnType, typeParameters);
      if (kmProperty.getReturnType() == defaultPropertyType) {
        // The property type is not set yet.
        kmProperty.setReturnType(kmPropertyType);
      } else {
        // If property type is set already make sure it's consistent.
        if (!getDescriptorFromKmType(kmPropertyType)
            .equals(getDescriptorFromKmType(kmProperty.getReturnType()))) {
          return null;
        }
      }
      assert setter.getKotlinMemberInfo().isPropertyInfo();
      KotlinValueParameterInfo valueParameterInfo =
          setter.getKotlinMemberInfo().asPropertyInfo().valueParameterInfo;
      KmValueParameter kmValueParameter =
          synthesizer.toRewrittenKmValueParameter(
              valueParameterInfo, valueType, valueSignature, "value", typeParameters);
      if (kmValueParameter != null) {
        kmProperty.setSetterParameter(kmValueParameter);
      }
      DexMethod renamedSetter = lens.lookupMethod(setter.method, appView.dexItemFactory());
      kmProperty.setSetterFlags(setterInfo.setterFlags);
      JvmExtensionsKt.setSetterSignature(kmProperty, toJvmMethodSignature(renamedSetter));
      return appView.appInfo().isPinned(setter.method) ? null : renamedSetter.name.toString();
    }

    KmProperty toRenamedKmProperty(KotlinMetadataSynthesizer synthesizer) {
      AppView<AppInfoWithLiveness> appView = synthesizer.appView;
      NamingLens lens = synthesizer.lens;
      KmProperty kmProperty = new KmProperty(flags, name, flagsOf(), flagsOf());

      // Set default values
      KmType defaultPropertyType = new KmType(flagsOf());
      kmProperty.setReturnType(defaultPropertyType);

      String renamedPropertyName = name;
      if (getter != null) {
        if (checkGetterCriteria() == GetterSetterCriteria.VIOLATE) {
          return null;
        }
        if (!setReceiverParameterTypeForExtensionProperty(
            kmProperty, getter, appView, synthesizer)) {
          return null;
        }
        renamedPropertyName =
            setGetterForProperty(kmProperty, defaultPropertyType, appView, lens, synthesizer);
      }
      if (setter != null) {
        if (checkSetterCriteria() == GetterSetterCriteria.VIOLATE) {
          return null;
        }
        if (!setReceiverParameterTypeForExtensionProperty(
            kmProperty, setter, appView, synthesizer)) {
          return null;
        }
        String renamedName =
            setSetterForProperty(kmProperty, defaultPropertyType, appView, lens, synthesizer);
        if (renamedPropertyName != null) {
          renamedPropertyName = renamedName;
        }
      }
      // Setting the property type from the field has to be done after the getter, otherwise we
      // may potentially loose type-argument information.
      if (field != null) {
        String renamedName =
            setFieldForProperty(kmProperty, defaultPropertyType, appView, lens, synthesizer);
        if (renamedPropertyName != null) {
          renamedPropertyName = renamedName;
        }
      }
      // Rename the property name if and only if none of participating members is pinned, and
      // any of them is indeed renamed (to a new name).
      if (renamedPropertyName != null) {
        kmProperty.setName(renamedPropertyName);
      }
      return kmProperty;
    }

    enum GetterSetterCriteria {
      NOT_AVAILABLE,
      MET,
      VIOLATE
    }

    // Getter should look like:
    //   1) T getProp(); for regular property, or
    //   2) T getProp(R); for extension property, where
    // T is a property type, and R is a receiver type.
    private GetterSetterCriteria checkGetterCriteria() {
      if (getter == null) {
        return GetterSetterCriteria.NOT_AVAILABLE;
      }
      // Property type will be checked against a backing field type if any.
      // And if they're different, we won't synthesize KmProperty for that.
      // Checking parameter size.
      if (isExtension) {
        return getter.method.proto.parameters.size() == 1
            ? GetterSetterCriteria.MET : GetterSetterCriteria.VIOLATE;
      } else {
        return getter.method.proto.parameters.size() == 0
            ? GetterSetterCriteria.MET : GetterSetterCriteria.VIOLATE;
      }
    }

    // Setter should look like:
    //   1) void setProp(T); for regular property, or
    //   2) void setProp(R, T); for extension property, where
    // T is a property type, and R is a receiver type.
    private GetterSetterCriteria checkSetterCriteria() {
      if (setter == null) {
        return GetterSetterCriteria.NOT_AVAILABLE;
      }
      if (!setter.method.proto.returnType.isVoidType()) {
        return GetterSetterCriteria.VIOLATE;
      }
      // Property type will be checked against a backing field type if any.
      // And if they're different, we won't synthesize KmProperty for that.
      // Plus, receiver type will be checked, too, against a getter.
      if (isExtension) {
        return setter.method.proto.parameters.size() == 2
            ? GetterSetterCriteria.MET : GetterSetterCriteria.VIOLATE;
      } else {
        return setter.method.proto.parameters.size() == 1
            ? GetterSetterCriteria.MET : GetterSetterCriteria.VIOLATE;
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("KmPropertyGroup {").append(System.lineSeparator());
      builder.append("  name: ").append(name).append(System.lineSeparator());
      builder.append("  isExtension: ").append(isExtension).append(System.lineSeparator());
      if (field != null) {
        builder.append("  field: ")
            .append(field.toSourceString())
            .append(System.lineSeparator());
      }
      if (getter != null) {
        builder
            .append("  getter: ")
            .append(getter.toSourceString())
            .append(System.lineSeparator());
      }
      if (setter != null) {
        builder
            .append("  setter: ")
            .append(setter.toSourceString())
            .append(System.lineSeparator());
      }
      if (annotations != null) {
        builder
            .append("  annotations: ")
            .append(annotations.toSourceString())
            .append(System.lineSeparator());
      }
      builder.append("}");
      return builder.toString();
    }
  }
}
