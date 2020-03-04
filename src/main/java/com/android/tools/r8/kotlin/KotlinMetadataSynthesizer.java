// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.NAME;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmMethodSignature;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToKotlinClassifier;
import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKmType;
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
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeProjection;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.KmVariance;
import kotlinx.metadata.jvm.JvmExtensionsKt;

class KotlinMetadataSynthesizer {

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

  static DexType toRenamedType(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
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

  static String toRenamedBinaryName(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    DexType renamedType = toRenamedType(type, appView, lens);
    if (renamedType == null) {
      return null;
    }
    return getBinaryNameFromDescriptor(renamedType.toDescriptorString());
  }

  static String toRenamedClassifier(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    // E.g., V -> kotlin/Unit, J -> kotlin/Long, [J -> kotlin/LongArray
    if (appView.dexItemFactory().kotlin.knownTypeConversion.containsKey(type)) {
      DexType convertedType = appView.dexItemFactory().kotlin.knownTypeConversion.get(type);
      assert convertedType != null;
      return descriptorToKotlinClassifier(convertedType.toDescriptorString());
    }
    // E.g., [Ljava/lang/String; -> kotlin/Array
    if (type.isArrayType()) {
      return NAME + "/Array";
    }
    DexType renamedType = toRenamedType(type, appView, lens);
    if (renamedType == null) {
      return null;
    }
    return descriptorToKotlinClassifier(renamedType.toDescriptorString());
  }

  // TODO(b/148654451): Canonicalization?
  static KmType toRenamedKmType(
      DexType type,
      TypeSignature typeSignature,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    if (typeSignature != null
        && typeSignature.isFieldTypeSignature()
        && typeSignature.asFieldTypeSignature().isClassTypeSignature()
        && typeSignature.asFieldTypeSignature().asClassTypeSignature().type() == type) {
      return toRenamedKmType(
          typeSignature.asFieldTypeSignature().asClassTypeSignature(), appView, lens);
    }

    String classifier = toRenamedClassifier(type, appView, lens);
    if (classifier == null) {
      return null;
    }
    // TODO(b/70169921): Mysterious, why attempts to properly set flags bothers kotlinc?
    //   and/or why wiping out flags works for KmType but not KmFunction?!
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(classifier);
    // TODO(b/70169921): Can be generalized too, like ArrayTypeSignature.Converter ?
    // E.g., java.lang.String[] -> KmType(kotlin/Array, KmTypeProjection(OUT, kotlin/String))
    if (type.isArrayType() && !type.isPrimitiveArrayType()) {
      DexType elementType = type.toArrayElementType(appView.dexItemFactory());
      KmType argumentType = toRenamedKmType(elementType, null, appView, lens);
      kmType.getArguments().add(new KmTypeProjection(KmVariance.OUT, argumentType));
    }
    return kmType;
  }

  static KmType toRenamedKmType(
      ClassTypeSignature classTypeSignature,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return classTypeSignature.convert(
        new ClassTypeSignatureToRenamedKmTypeConverter(appView, lens));
  }

  static class ClassTypeSignatureToRenamedKmTypeConverter
      implements ClassTypeSignature.Converter<KmType> {

    private final AppView<AppInfoWithLiveness> appView;
    private final NamingLens lens;

    ClassTypeSignatureToRenamedKmTypeConverter(
        AppView<AppInfoWithLiveness> appView, NamingLens lens) {
      this.appView = appView;
      this.lens = lens;
    }

    @Override
    public KmType init() {
      return new KmType(flagsOf());
    }

    @Override
    public KmType visitType(DexType type, KmType result) {
      if (result == null) {
        return result;
      }
      String classifier = toRenamedClassifier(type, appView, lens);
      if (classifier == null) {
        return null;
      }
      result.visitClass(classifier);
      return result;
    }

    @Override
    public KmType visitTypeArgument(FieldTypeSignature typeArgument, KmType result) {
      if (result == null) {
        return result;
      }
      if (typeArgument.isClassTypeSignature()) {
        KmType argumentType = typeArgument.asClassTypeSignature().convert(this);
        result.getArguments().add(new KmTypeProjection(KmVariance.INVARIANT, argumentType));
      }
      // TODO(b/70169921): for TypeVariableSignature, there is KmType::visitTypeParameter.
      return result;
    }

    @Override
    public KmType visitInnerTypeSignature(ClassTypeSignature innerTypeSignature, KmType result) {
      // Do nothing
      return result;
    }
  }

  private static KmType setRenamedKmType(
      DexType type,
      TypeSignature signature,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens,
      Consumer<KmType> consumer) {
    KmType renamedKmType = toRenamedKmType(type, signature, appView, lens);
    if (renamedKmType != null) {
      consumer.accept(renamedKmType);
    }
    return renamedKmType;
  }

  static KmConstructor toRenamedKmConstructor(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    // Make sure it is an instance initializer and live.
    if (!method.isInstanceInitializer()
        || !appView.appInfo().liveMethods.contains(method.method)) {
      return null;
    }
    KmConstructor kmConstructor = new KmConstructor(method.accessFlags.getAsKotlinFlags());
    JvmExtensionsKt.setSignature(kmConstructor, toJvmMethodSignature(method.method));
    MethodTypeSignature signature = GenericSignature.Parser.toMethodTypeSignature(method, appView);
    List<KmValueParameter> parameters = kmConstructor.getValueParameters();
    if (!populateKmValueParameters(parameters, method, signature, appView, lens)) {
      return null;
    }
    return kmConstructor;
  }

  static KmFunction toRenamedKmFunction(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
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
    // TODO(b/70169921): Should we keep kotlin-specific flags only while synthesizing the base
    //  value from general JVM flags?
    int flag =
        appView.appInfo().isPinned(method.method) && method.getKotlinMemberInfo() != null
            ? method.getKotlinMemberInfo().flag
            : method.accessFlags.getAsKotlinFlags();
    KmFunction kmFunction = new KmFunction(flag, renamedMethod.name.toString());
    JvmExtensionsKt.setSignature(kmFunction, toJvmMethodSignature(renamedMethod));

    // TODO(b/129925954): Should this be integrated as part of DexDefinition instead of parsing it
    //  on demand? That may require utils to map internal encoding of signature back to
    //  corresponding backend definitions, like DexAnnotation (DEX) or Signature attribute (CF).
    MethodTypeSignature signature = GenericSignature.Parser.toMethodTypeSignature(method, appView);

    DexProto proto = method.method.proto;
    DexType returnType = proto.returnType;
    TypeSignature returnSignature = signature.returnType().typeSignature();
    KmType kmReturnType = setRenamedKmType(
        returnType, returnSignature, appView, lens, kmFunction::setReturnType);
    if (kmReturnType == null) {
      return null;
    }

    if (method.isKotlinExtensionFunction()) {
      assert proto.parameters.values.length > 0
          : method.method.toSourceString();
      DexType receiverType = proto.parameters.values[0];
      TypeSignature receiverSignature = signature.getParameterTypeSignature(0);
      KmType kmReceiverType = setRenamedKmType(
          receiverType, receiverSignature, appView, lens, kmFunction::setReceiverParameterType);
      if (kmReceiverType == null) {
        return null;
      }
    }

    List<KmValueParameter> parameters = kmFunction.getValueParameters();
    if (!populateKmValueParameters(parameters, method, signature, appView, lens)) {
      return null;
    }
    return kmFunction;
  }

  private static boolean populateKmValueParameters(
      List<KmValueParameter> parameters,
      DexEncodedMethod method,
      MethodTypeSignature signature,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    boolean isExtension = method.isKotlinExtensionFunction();
    for (int i = isExtension ? 1 : 0; i < method.method.proto.parameters.values.length; i++) {
      DexType parameterType = method.method.proto.parameters.values[i];
      DebugLocalInfo debugLocalInfo = method.getParameterInfo().get(i);
      String parameterName = debugLocalInfo != null ? debugLocalInfo.name.toString() : ("p" + i);
      KotlinValueParameterInfo valueParameterInfo =
          method.getKotlinMemberInfo().getValueParameterInfo(isExtension ? i - 1 : i);
      TypeSignature parameterTypeSignature = signature.getParameterTypeSignature(i);
      KmValueParameter kmValueParameter =
          toRewrittenKmValueParameter(
              valueParameterInfo,
              parameterType,
              parameterTypeSignature,
              parameterName,
              appView,
              lens);
      if (kmValueParameter == null) {
        return false;
      }
      parameters.add(kmValueParameter);
    }
    return true;
  }

  private static KmValueParameter toRewrittenKmValueParameter(
      KotlinValueParameterInfo valueParameterInfo,
      DexType parameterType,
      TypeSignature parameterTypeSignature,
      String candidateParameterName,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    int flag = valueParameterInfo != null ? valueParameterInfo.flag : flagsOf();
    String name = valueParameterInfo != null ? valueParameterInfo.name : candidateParameterName;
    KmValueParameter kmValueParameter = new KmValueParameter(flag, name);
    KmType kmParamType = setRenamedKmType(
        parameterType, parameterTypeSignature, appView, lens, kmValueParameter::setType);
    if (kmParamType == null) {
      return null;
    }
    if (valueParameterInfo != null) {
      JvmExtensionsKt.getAnnotations(kmParamType).addAll(valueParameterInfo.annotations);
    }

    if (valueParameterInfo != null && valueParameterInfo.isVararg) {
      if (!parameterType.isArrayType()) {
        return null;
      }
      DexType elementType = parameterType.toArrayElementType(appView.dexItemFactory());
      TypeSignature elementSignature =
          parameterTypeSignature != null
              ? parameterTypeSignature.toArrayElementTypeSignature(appView) : null;
      KmType kmElementType = setRenamedKmType(
          elementType, elementSignature, appView, lens, kmValueParameter::setVarargElementType);
      if (kmElementType == null) {
        return null;
      }
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
    final int flag;
    final String name;
    final DexEncodedField field;
    final DexEncodedMethod getter;
    final DexEncodedMethod setter;
    final DexEncodedMethod annotations;
    final boolean isExtension;

    private KmPropertyGroup(
        int flag,
        String name,
        DexEncodedField field,
        DexEncodedMethod getter,
        DexEncodedMethod setter,
        DexEncodedMethod annotations,
        boolean isExtension) {
      this.flag = flag;
      this.name = name;
      this.field = field;
      this.getter = getter;
      this.setter = setter;
      this.annotations = annotations;
      this.isExtension = isExtension;
    }

    static Builder builder(int flag, String name) {
      return new Builder(flag, name);
    }

    static class Builder {
      private final int flag;
      private final String name;
      private DexEncodedField field;
      private DexEncodedMethod getter;
      private DexEncodedMethod setter;
      private DexEncodedMethod annotations;

      private boolean isExtensionGetter;
      private boolean isExtensionSetter;
      private boolean isExtensionAnnotations;

      private Builder(int flag, String name) {
        this.flag = flag;
        this.name = name;
      }

      Builder foundBackingField(DexEncodedField field) {
        this.field = field;
        return this;
      }

      Builder foundGetter(DexEncodedMethod getter) {
        this.getter = getter;
        return this;
      }

      Builder foundSetter(DexEncodedMethod setter) {
        this.setter = setter;
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
        return new KmPropertyGroup(flag, name, field, getter, setter, annotations, isExtension);
      }
    }

    KmProperty toRenamedKmProperty(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
      KmProperty kmProperty = new KmProperty(flag, name, flagsOf(), flagsOf());
      KmType kmPropertyType = null;
      KmType kmReceiverType = null;

      // A flag to indicate we can rename the property name. This will become false if any member
      // is pinned. Then, we conservatively assume that users want the property to be pinned too.
      // That is, we won't rename the property even though some other members could be renamed.
      boolean canChangePropertyName = true;
      // A candidate property name. Not overwritten by the following members, hence the order of
      // preference: a backing field, getter, and setter.
      String renamedPropertyName = name;
      if (field != null) {
        if (appView.appInfo().isPinned(field.field)) {
          canChangePropertyName = false;
        }
        DexField renamedField = lens.lookupField(field.field, appView.dexItemFactory());
        if (canChangePropertyName && renamedField.name != field.field.name) {
          renamedPropertyName = renamedField.name.toString();
        }
        FieldTypeSignature signature =
            GenericSignature.Parser.toFieldTypeSignature(field, appView);
        kmPropertyType =
            setRenamedKmType(field.field.type, signature, appView, lens, kmProperty::setReturnType);
        JvmExtensionsKt.setFieldSignature(kmProperty, toJvmFieldSignature(renamedField));
      }

      GetterSetterCriteria criteria = checkGetterCriteria();
      if (criteria == GetterSetterCriteria.VIOLATE) {
        return null;
      }

      if (criteria == GetterSetterCriteria.MET) {
        assert getter != null && getter.method.proto.parameters.size() == (isExtension ? 1 : 0)
            : "checkGetterCriteria: " + this.toString();
        MethodTypeSignature signature =
            GenericSignature.Parser.toMethodTypeSignature(getter, appView);
        if (isExtension) {
          TypeSignature receiverSignature = signature.getParameterTypeSignature(0);
          kmReceiverType =
              setRenamedKmType(
                  getter.method.proto.parameters.values[0],
                  receiverSignature,
                  appView,
                  lens,
                  kmProperty::setReceiverParameterType);
        }

        DexType returnType = getter.method.proto.returnType;
        TypeSignature returnSignature = signature.returnType().typeSignature();
        if (kmPropertyType == null) {
          // The property type is not set yet.
          kmPropertyType = setRenamedKmType(
              returnType, returnSignature, appView, lens, kmProperty::setReturnType);
        } else {
          // If property type is set already (via backing field), make sure it's consistent.
          KmType kmPropertyTypeFromGetter =
              toRenamedKmType(returnType, returnSignature, appView, lens);
          if (!getDescriptorFromKmType(kmPropertyType)
              .equals(getDescriptorFromKmType(kmPropertyTypeFromGetter))) {
            return null;
          }
        }
        if (appView.appInfo().isPinned(getter.method)) {
          canChangePropertyName = false;
        }
        DexMethod renamedGetter = lens.lookupMethod(getter.method, appView.dexItemFactory());
        if (canChangePropertyName
            && renamedGetter.name != getter.method.name
            && renamedPropertyName.equals(name)) {
          renamedPropertyName = renamedGetter.name.toString();
        }
        kmProperty.setGetterFlags(getter.accessFlags.getAsKotlinFlags());
        JvmExtensionsKt.setGetterSignature(kmProperty, toJvmMethodSignature(renamedGetter));
      }

      criteria = checkSetterCriteria();
      if (criteria == GetterSetterCriteria.VIOLATE) {
        return null;
      }

      if (criteria == GetterSetterCriteria.MET) {
        assert setter != null && setter.method.proto.parameters.size() == (isExtension ? 2 : 1)
            : "checkSetterCriteria: " + this.toString();
        MethodTypeSignature signature =
            GenericSignature.Parser.toMethodTypeSignature(setter, appView);
        if (isExtension) {
          DexType receiverType = setter.method.proto.parameters.values[0];
          TypeSignature receiverSignature = signature.getParameterTypeSignature(0);
          if (kmReceiverType == null) {
            kmReceiverType =
                setRenamedKmType(
                    receiverType,
                    receiverSignature,
                    appView,
                    lens,
                    kmProperty::setReceiverParameterType);
          } else {
            // If the receiver type for the extension property is set already (via getter),
            // make sure it's consistent.
            KmType kmReceiverTypeFromSetter =
                toRenamedKmType(receiverType, receiverSignature, appView, lens);
            if (!getDescriptorFromKmType(kmReceiverType)
                .equals(getDescriptorFromKmType(kmReceiverTypeFromSetter))) {
              return null;
            }
          }
        }

        int valueIndex = isExtension ? 1 : 0;
        DexType valueType = setter.method.proto.parameters.values[valueIndex];
        TypeSignature valueSignature = signature.getParameterTypeSignature(valueIndex);
        if (kmPropertyType == null) {
          // The property type is not set yet.
          kmPropertyType =
              setRenamedKmType(valueType, valueSignature, appView, lens, kmProperty::setReturnType);
        } else {
          // If property type is set already (via either backing field or getter),
          // make sure it's consistent.
          KmType kmPropertyTypeFromSetter =
              toRenamedKmType(valueType, valueSignature, appView, lens);
          if (!getDescriptorFromKmType(kmPropertyType)
              .equals(getDescriptorFromKmType(kmPropertyTypeFromSetter))) {
            return null;
          }
        }
        KotlinValueParameterInfo valueParameterInfo =
            setter.getKotlinMemberInfo().getValueParameterInfo(valueIndex);
        KmValueParameter kmValueParameter = toRewrittenKmValueParameter(
            valueParameterInfo, valueType, valueSignature, "value", appView, lens);
        if (kmValueParameter != null) {
          kmProperty.setSetterParameter(kmValueParameter);
        }
        if (appView.appInfo().isPinned(setter.method)) {
          canChangePropertyName = false;
        }
        DexMethod renamedSetter = lens.lookupMethod(setter.method, appView.dexItemFactory());
        if (canChangePropertyName
            && renamedSetter.name != setter.method.name
            && renamedPropertyName.equals(name)) {
          renamedPropertyName = renamedSetter.name.toString();
        }
        kmProperty.setSetterFlags(setter.accessFlags.getAsKotlinFlags());
        JvmExtensionsKt.setSetterSignature(kmProperty, toJvmMethodSignature(renamedSetter));
      }

      // If the property type remains null at the end, bail out to synthesize this property.
      if (kmPropertyType == null) {
        return null;
      }
      // For extension property, if the receiver type remains null at the end, bail out too.
      if (isExtension && kmReceiverType == null) {
        return null;
      }
      // Rename the property name if and only if none of participating members is pinned, and
      // any of them is indeed renamed (to a new name).
      if (canChangePropertyName && !renamedPropertyName.equals(name)) {
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
