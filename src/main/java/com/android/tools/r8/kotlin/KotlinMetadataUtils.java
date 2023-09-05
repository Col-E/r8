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
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.TriFunction;
import java.util.List;
import java.util.function.Consumer;
import kotlin.Metadata;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinMetadataUtils {

  private static final NoKotlinInfo NO_KOTLIN_INFO = new NoKotlinInfo("NO_KOTLIN_INFO");
  private static final NoKotlinInfo INVALID_KOTLIN_INFO = new NoKotlinInfo("INVALID_KOTLIN_INFO");

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
    public Pair<Metadata, Boolean> rewrite(DexClass clazz, AppView<?> appView) {
      throw new Unreachable("Should never be called");
    }

    @Override
    public String getPackageName() {
      throw new Unreachable("Should never be called");
    }

    @Override
    public int[] getMetadataVersion() {
      throw new Unreachable("Should never be called");
    }

    @Override
    public boolean isNoKotlinInformation() {
      return true;
    }

    @Override
    public void trace(DexDefinitionSupplier definitionSupplier) {
      // No information needed to trace.
    }
  }

  public static NoKotlinInfo getNoKotlinInfo() {
    return NO_KOTLIN_INFO;
  }

  public static NoKotlinInfo getInvalidKotlinInfo() {
    return INVALID_KOTLIN_INFO;
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

  static JvmMethodSignature toDefaultJvmMethodSignature(
      JvmMethodSignature methodSignature, int intArguments) {
    return new JvmMethodSignature(
        methodSignature.getName() + "$default",
        methodSignature.getDesc().replace(")", "I".repeat(intArguments) + "Ljava/lang/Object;)"));
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
            @SuppressWarnings("ReferenceEquality")
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

  public static boolean mayProcessKotlinMetadata(AppView<?> appView) {
    // This can run before we have determined the pinned items, because we may need to load the
    // stack-map table on input. This is therefore a conservative guess on kotlin.Metadata is kept.
    DexClass kotlinMetadata =
        appView
            .appInfo()
            .definitionForWithoutExistenceAssert(appView.dexItemFactory().kotlinMetadataType);
    if (kotlinMetadata == null || kotlinMetadata.isNotProgramClass()) {
      return true;
    }
    ProguardConfiguration proguardConfiguration = appView.options().getProguardConfiguration();
    if (proguardConfiguration != null && proguardConfiguration.getRules() != null) {
      for (ProguardConfigurationRule rule : proguardConfiguration.getRules()) {
        if (KotlinMetadataUtils.canBeKotlinMetadataKeepRule(rule, appView.options().itemFactory)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean canBeKotlinMetadataKeepRule(
      ProguardConfigurationRule rule, DexItemFactory factory) {
    if (rule.isProguardIfRule()) {
      // For if rules, we simply assume that the precondition can become true.
      return canBeKotlinMetadataKeepRule(rule.asProguardIfRule().getSubsequentRule(), factory);
    }
    if (!rule.isProguardKeepRule()) {
      return false;
    }
    ProguardKeepRule proguardKeepRule = rule.asProguardKeepRule();
    // -keepclassmembers will not in itself keep a class alive.
    if (proguardKeepRule.getType() == ProguardKeepRuleType.KEEP_CLASS_MEMBERS) {
      return false;
    }
    // If the rule allows shrinking, it will not require us to keep the class.
    if (proguardKeepRule.getModifiers().allowsShrinking) {
      return false;
    }
    // Check if the type is matched
    return proguardKeepRule.getClassNames().matches(factory.kotlinMetadataType);
  }

  static String getKotlinClassName(DexClass clazz, String descriptor) {
    InnerClassAttribute innerClassAttribute = clazz.getInnerClassAttributeForThisClass();
    if (innerClassAttribute != null && innerClassAttribute.getOuter() != null) {
      return DescriptorUtils.descriptorToKotlinClassifier(descriptor);
    } else if (clazz.isLocalClass() || clazz.isAnonymousClass()) {
      return getKotlinLocalOrAnonymousNameFromDescriptor(descriptor, true);
    } else {
      // We no longer have an innerclass relationship to maintain and we therefore return a binary
      // name.
      return DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
    }
  }

  static String getKotlinLocalOrAnonymousNameFromDescriptor(
      String descriptor, boolean isLocalOrAnonymous) {
    // For local or anonymous classes, the classifier is prefixed with '.' and inner classes
    // are separated with '$'.
    if (isLocalOrAnonymous) {
      return "." + DescriptorUtils.getBinaryNameFromDescriptor(descriptor);
    }
    return DescriptorUtils.descriptorToKotlinClassifier(descriptor);
  }

  static int[] getCompatibleKotlinInfo() {
    return KotlinClassMetadata.COMPATIBLE_METADATA_VERSION;
  }

  static <TKm> TKm consume(TKm tKm, Consumer<TKm> consumer) {
    consumer.accept(tKm);
    return tKm;
  }

  static <TInfo, TKm> boolean rewriteIfNotNull(
      AppView<?> appView,
      TInfo info,
      Consumer<TKm> newTConsumer,
      TriFunction<TInfo, Consumer<TKm>, AppView<?>, Boolean> rewrite) {
    return info != null ? rewrite.apply(info, newTConsumer, appView) : false;
  }

  static <TInfo, TKm> boolean rewriteList(
      AppView<?> appView,
      List<TInfo> ts,
      List<TKm> newTs,
      TriFunction<TInfo, Consumer<TKm>, AppView<?>, Boolean> rewrite) {
    assert newTs.isEmpty();
    return rewriteList(appView, ts, newTs::add, rewrite);
  }

  static <TInfo, TKm> boolean rewriteList(
      AppView<?> appView,
      List<TInfo> ts,
      Consumer<TKm> newTConsumer,
      TriFunction<TInfo, Consumer<TKm>, AppView<?>, Boolean> rewrite) {
    boolean rewritten = false;
    for (TInfo t : ts) {
      rewritten |= rewrite.apply(t, newTConsumer, appView);
    }
    return rewritten;
  }
}
