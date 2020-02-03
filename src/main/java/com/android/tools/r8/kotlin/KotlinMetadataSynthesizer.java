// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmMethodSignature;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromKmType;
import static com.android.tools.r8.kotlin.Kotlin.NAME;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToKotlinClassifier;
import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;
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

  static String toRenamedClassifier(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    // E.g., [Ljava/lang/String; -> kotlin/Array
    if (type.isArrayType()) {
      return NAME + "/Array";
    }
    // E.g., void -> kotlin/Unit
    if (appView.dexItemFactory().kotlin.knownTypeConversion.containsKey(type)) {
      DexType convertedType = appView.dexItemFactory().kotlin.knownTypeConversion.get(type);
      assert convertedType != null;
      return descriptorToKotlinClassifier(convertedType.toDescriptorString());
    }
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
    return descriptorToKotlinClassifier(renamedType.toDescriptorString());
  }

  static KmType toRenamedKmType(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    String classifier = toRenamedClassifier(type, appView, lens);
    if (classifier == null) {
      return null;
    }
    // TODO(b/70169921): Mysterious, why attempts to properly set flags bothers kotlinc?
    //   and/or why wiping out flags works for KmType but not KmFunction?!
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(classifier);
    return kmType;
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
    List<KmValueParameter> parameters = kmConstructor.getValueParameters();
    if (!populateKmValueParameters(parameters, method, appView, lens, false)) {
      return null;
    }
    return kmConstructor;
  }

  static KmFunction toRenamedKmFunction(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return toRenamedKmFunctionHelper(method, appView, lens, false);
  }

  static KmFunction toRenamedKmFunctionAsExtension(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens) {
    return toRenamedKmFunctionHelper(method, appView, lens, true);
  }

  private static KmFunction toRenamedKmFunctionHelper(
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens,
      boolean isExtension) {
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
    JvmExtensionsKt.setSignature(kmFunction, toJvmMethodSignature(method.method));
    KmType kmReturnType = toRenamedKmType(method.method.proto.returnType, appView, lens);
    if (kmReturnType == null) {
      return null;
    }
    kmFunction.setReturnType(kmReturnType);
    if (isExtension) {
      assert method.method.proto.parameters.values.length > 0
          : method.method.toSourceString();
      KmType kmReceiverType =
          toRenamedKmType(method.method.proto.parameters.values[0], appView, lens);
      if (kmReceiverType == null) {
        return null;
      }
      kmFunction.setReceiverParameterType(kmReceiverType);
    }
    List<KmValueParameter> parameters = kmFunction.getValueParameters();
    if (!populateKmValueParameters(parameters, method, appView, lens, isExtension)) {
      return null;
    }
    return kmFunction;
  }

  private static boolean populateKmValueParameters(
      List<KmValueParameter> parameters,
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens lens,
      boolean isExtension) {
    for (int i = isExtension ? 1 : 0; i < method.method.proto.parameters.values.length; i++) {
      DexType paramType = method.method.proto.parameters.values[i];
      DebugLocalInfo debugLocalInfo = method.getParameterInfo().get(i);
      String parameterName = debugLocalInfo != null ? debugLocalInfo.name.toString() : ("p" + i);
      // TODO(b/70169921): Consult kotlinx.metadata.Flag.ValueParameter.
      KmValueParameter kmValueParameter = new KmValueParameter(flagsOf(), parameterName);
      KmType kmParamType = toRenamedKmType(paramType, appView, lens);
      if (kmParamType == null) {
        return false;
      }
      kmValueParameter.setType(kmParamType);
      parameters.add(kmValueParameter);
    }
    return true;
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

      if (field != null) {
        DexField renamedField = lens.lookupField(field.field, appView.dexItemFactory());
        if (renamedField == null) {
          // Bail out if we can't find a renamed backing field.
          return null;
        }
        kmProperty.setName(renamedField.name.toString());
        kmPropertyType = toRenamedKmType(field.field.type, appView, lens);
        if (kmPropertyType != null) {
          kmProperty.setReturnType(kmPropertyType);
        }
        JvmExtensionsKt.setFieldSignature(kmProperty, toJvmFieldSignature(field.field));
      }

      GetterSetterCriteria criteria = checkGetterCriteria();
      if (criteria == GetterSetterCriteria.VIOLATE) {
        return null;
      }

      if (criteria == GetterSetterCriteria.MET) {
        assert getter != null
            : "checkGetterCriteria: " + this.toString();
        if (isExtension) {
          assert getter.method.proto.parameters.size() == 1
              : "checkGetterCriteria: " + this.toString();
          kmReceiverType = toRenamedKmType(getter.method.proto.parameters.values[0], appView, lens);
          if (kmReceiverType != null) {
            kmProperty.setReceiverParameterType(kmReceiverType);
          }
        }
        if (kmPropertyType == null) {
          // The property type is not set yet.
          kmPropertyType = toRenamedKmType(getter.method.proto.returnType, appView, lens);
          if (kmPropertyType != null) {
            kmProperty.setReturnType(kmPropertyType);
          }
        } else {
          // If property type is set already (via backing field), make sure it's consistent.
          KmType kmPropertyTypeFromGetter =
              toRenamedKmType(getter.method.proto.returnType, appView, lens);
          if (!getDescriptorFromKmType(kmPropertyType)
              .equals(getDescriptorFromKmType(kmPropertyTypeFromGetter))) {
            return null;
          }
        }
        kmProperty.setGetterFlags(getter.accessFlags.getAsKotlinFlags());
        JvmExtensionsKt.setGetterSignature(kmProperty, toJvmMethodSignature(getter.method));
      }

      criteria = checkSetterCriteria();
      if (criteria == GetterSetterCriteria.VIOLATE) {
        return null;
      }

      if (criteria == GetterSetterCriteria.MET) {
        assert setter != null && setter.method.proto.parameters.size() >= 1
            : "checkSetterCriteria: " + this.toString();
        if (isExtension) {
          assert setter.method.proto.parameters.size() == 2
              : "checkSetterCriteria: " + this.toString();
          if (kmReceiverType == null) {
            kmReceiverType =
                toRenamedKmType(setter.method.proto.parameters.values[0], appView, lens);
            if (kmReceiverType != null) {
              kmProperty.setReceiverParameterType(kmReceiverType);
            }
          } else {
            // If the receiver type for the extension property is set already (via getter),
            // make sure it's consistent.
            KmType kmReceiverTypeFromSetter =
                toRenamedKmType(setter.method.proto.parameters.values[0], appView, lens);
            if (!getDescriptorFromKmType(kmReceiverType)
                .equals(getDescriptorFromKmType(kmReceiverTypeFromSetter))) {
              return null;
            }
          }
        }
        int valueIndex = isExtension ? 1 : 0;
        if (kmPropertyType == null) {
          // The property type is not set yet.
          kmPropertyType =
              toRenamedKmType(setter.method.proto.parameters.values[valueIndex], appView, lens);
          if (kmPropertyType != null) {
            kmProperty.setReturnType(kmPropertyType);
          }
        } else {
          // If property type is set already (via either backing field or getter),
          // make sure it's consistent.
          KmType kmPropertyTypeFromSetter =
              toRenamedKmType(setter.method.proto.parameters.values[valueIndex], appView, lens);
          if (!getDescriptorFromKmType(kmPropertyType)
              .equals(getDescriptorFromKmType(kmPropertyTypeFromSetter))) {
            return null;
          }
        }
        kmProperty.setSetterFlags(setter.accessFlags.getAsKotlinFlags());
        JvmExtensionsKt.setSetterSignature(kmProperty, toJvmMethodSignature(setter.method));
      }

      // If the property type remains null at the end, bail out to synthesize this property.
      if (kmPropertyType == null) {
        return null;
      }
      // For extension property, if the receiver type remains null at the end, bail out too.
      if (isExtension && kmReceiverType == null) {
        return null;
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
