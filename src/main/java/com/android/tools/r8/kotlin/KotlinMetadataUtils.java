// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.errors.InvalidDescriptorException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;
import kotlinx.metadata.jvm.KotlinClassHeader;

public class KotlinMetadataUtils {

  public static final NoKotlinInfo NO_KOTLIN_INFO = new NoKotlinInfo("NO_KOTLIN_INFO");
  public static final NoKotlinInfo INVALID_KOTLIN_INFO = new NoKotlinInfo("INVALID_KOTLIN_INFO");

  private static class NoKotlinInfo
      implements KotlinClassLevelInfo, KotlinFieldLevelInfo, KotlinMethodLevelInfo {

    private final String name;

    private NoKotlinInfo(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public KotlinClassHeader rewrite(
        DexClass clazz, AppView<AppInfoWithLiveness> appView, NamingLens namingLens) {
      throw new Unreachable("Should never be called");
    }
  }

  static JvmFieldSignature toJvmFieldSignature(DexField field) {
    return new JvmFieldSignature(field.name.toString(), field.type.toDescriptorString());
  }

  static JvmMethodSignature toJvmMethodSignature(DexMethod method) {
    StringBuilder descBuilder = new StringBuilder();
    descBuilder.append("(");
    for (DexType argType : method.proto.parameters.values) {
      descBuilder.append(argType.toDescriptorString());
    }
    descBuilder.append(")");
    descBuilder.append(method.proto.returnType.toDescriptorString());
    return new JvmMethodSignature(method.name.toString(), descBuilder.toString());
  }

  static class KmPropertyProcessor {
    private JvmFieldSignature fieldSignature = null;
    // Custom getter via @get:JvmName("..."). Otherwise, null.
    private JvmMethodSignature getterSignature = null;
    // Custom getter via @set:JvmName("..."). Otherwise, null.
    private JvmMethodSignature setterSignature = null;

    KmPropertyProcessor(KmProperty kmProperty) {
      kmProperty.accept(
          new KmPropertyVisitor() {
            @Override
            public KmPropertyExtensionVisitor visitExtensions(KmExtensionType type) {
              if (type != JvmPropertyExtensionVisitor.TYPE) {
                return null;
              }
              return new JvmPropertyExtensionVisitor() {
                @Override
                public void visit(
                    int flags,
                    JvmFieldSignature fieldDesc,
                    JvmMethodSignature getterDesc,
                    JvmMethodSignature setterDesc) {
                  assert fieldSignature == null : fieldSignature.asString();
                  fieldSignature = fieldDesc;
                  assert getterSignature == null : getterSignature.asString();
                  getterSignature = getterDesc;
                  assert setterSignature == null : setterSignature.asString();
                  setterSignature = setterDesc;
                }
              };
            }
          });
    }

    JvmFieldSignature fieldSignature() {
      return fieldSignature;
    }

    JvmMethodSignature getterSignature() {
      return getterSignature;
    }

    JvmMethodSignature setterSignature() {
      return setterSignature;
    }
  }

  static boolean isValidMethodDescriptor(String methodDescriptor) {
    try {
      String[] argDescriptors = DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor);
      for (String argDescriptor : argDescriptors) {
        if (argDescriptor.charAt(0) == 'L' && !DescriptorUtils.isClassDescriptor(argDescriptor)) {
          return false;
        }
      }
      return true;
    } catch (InvalidDescriptorException e) {
      return false;
    }
  }

  static String toRenamedDescriptorOrDefault(
      DexType type,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens,
      String defaultValue) {
    if (appView.appInfo().wasPruned(type)) {
      return defaultValue;
    }
    DexString descriptor = namingLens.lookupDescriptor(type);
    if (descriptor != null) {
      return descriptor.toString();
    }
    return defaultValue;
  }

  static String kotlinNameFromDescriptor(DexString descriptor) {
    return DescriptorUtils.getBinaryNameFromDescriptor(descriptor.toString());
  }

  static DexType referenceTypeFromBinaryName(
      String binaryName, DexDefinitionSupplier definitionSupplier) {
    return referenceTypeFromDescriptor(
        DescriptorUtils.getDescriptorFromClassBinaryName(binaryName), definitionSupplier);
  }

  static DexType referenceTypeFromDescriptor(
      String descriptor, DexDefinitionSupplier definitionSupplier) {
    DexType type = definitionSupplier.dexItemFactory().createType(descriptor);
    // Lookup the definition, ignoring the result. This populates the sets in the Enqueuer.
    if (type.isClassType()) {
      definitionSupplier.definitionFor(type);
    }
    return type;
  }

  public static boolean isKeepingKotlinMetadataInRules(InternalOptions options) {
    // We will only process kotlin.Metadata annotations if a rule for keeping it is
    // explicitly specified. A rule is only applied to program classes, therefore, if the
    // kotlin stdlib is passed on classpath or library we will not mark the type. Therefore,
    // we fall back to a simple scan through all rules to check if the type is kept directly.
    ProguardConfiguration proguardConfiguration = options.getProguardConfiguration();
    if (proguardConfiguration != null && proguardConfiguration.getRules() != null) {
      for (ProguardConfigurationRule rule : proguardConfiguration.getRules()) {
        if (KotlinMetadataUtils.isKotlinMetadataKeepRule(rule, options.itemFactory)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isKotlinMetadataKeepRule(
      ProguardConfigurationRule rule, DexItemFactory factory) {
    if (rule.isProguardKeepRule()) {
      ProguardKeepRule proguardKeepRule = rule.asProguardKeepRule();
      return proguardKeepRule.getType() == ProguardKeepRuleType.KEEP
          && !proguardKeepRule.getModifiers().allowsShrinking
          && !proguardKeepRule.getModifiers().allowsObfuscation
          && proguardKeepRule.getClassNames().matches(factory.kotlinMetadataType);
    }
    return false;
  }
}
