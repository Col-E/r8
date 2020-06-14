// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmAnnotation;
import kotlinx.metadata.KmAnnotationArgument;
import kotlinx.metadata.KmAnnotationArgument.AnnotationValue;
import kotlinx.metadata.KmAnnotationArgument.ArrayValue;
import kotlinx.metadata.KmAnnotationArgument.EnumValue;
import kotlinx.metadata.KmAnnotationArgument.KClassValue;

abstract class KotlinAnnotationArgumentInfo implements EnqueuerMetadataTraceable {

  private static final Map<String, KotlinAnnotationArgumentInfo> EMPTY_ARGUMENTS =
      ImmutableMap.of();

  abstract KmAnnotationArgument<?> rewrite(AppView<?> appView, NamingLens namingLens);

  private static KotlinAnnotationArgumentInfo createArgument(
      KmAnnotationArgument<?> arg, DexItemFactory factory) {
    if (arg instanceof KClassValue) {
      return KotlinAnnotationClassValueInfo.create((KClassValue) arg, factory);
    } else if (arg instanceof EnumValue) {
      return KotlinAnnotationEnumValueInfo.create((EnumValue) arg, factory);
    } else if (arg instanceof AnnotationValue) {
      return KotlinAnnotationAnnotationValueInfo.create((AnnotationValue) arg, factory);
    } else if (arg instanceof ArrayValue) {
      return KotlinAnnotationArrayValueInfo.create((ArrayValue) arg, factory);
    } else {
      return KotlinAnnotationPrimitiveArgumentInfo.create(arg);
    }
  }

  static Map<String, KotlinAnnotationArgumentInfo> create(
      Map<String, KmAnnotationArgument<?>> arguments, DexItemFactory factory) {
    if (arguments.isEmpty()) {
      return EMPTY_ARGUMENTS;
    }
    LinkedHashMap<String, KotlinAnnotationArgumentInfo> modeled = new LinkedHashMap<>();
    arguments.forEach((key, arg) -> modeled.put(key, createArgument(arg, factory)));
    return modeled;
  }

  private static class KotlinAnnotationClassValueInfo extends KotlinAnnotationArgumentInfo {

    private final KotlinTypeReference value;

    private KotlinAnnotationClassValueInfo(KotlinTypeReference value) {
      this.value = value;
    }

    private static KotlinAnnotationClassValueInfo create(KClassValue arg, DexItemFactory factory) {
      return new KotlinAnnotationClassValueInfo(
          KotlinTypeReference.fromBinaryName(arg.getValue(), factory));
    }

    @Override
    public void trace(DexDefinitionSupplier definitionSupplier) {
      value.trace(definitionSupplier);
    }

    @Override
    KmAnnotationArgument<?> rewrite(AppView<?> appView, NamingLens namingLens) {
      return new KClassValue(
          value.toRenamedBinaryNameOrDefault(appView, namingLens, ClassClassifiers.anyName));
    }
  }

  private static class KotlinAnnotationEnumValueInfo extends KotlinAnnotationArgumentInfo {

    private final KotlinTypeReference enumClassName;
    private final String enumEntryName;

    private KotlinAnnotationEnumValueInfo(KotlinTypeReference enumClassName, String enumEntryName) {
      this.enumClassName = enumClassName;
      this.enumEntryName = enumEntryName;
    }

    private static KotlinAnnotationEnumValueInfo create(EnumValue arg, DexItemFactory factory) {
      return new KotlinAnnotationEnumValueInfo(
          KotlinTypeReference.fromBinaryName(arg.getEnumClassName(), factory),
          arg.getEnumEntryName());
    }

    @Override
    public void trace(DexDefinitionSupplier definitionSupplier) {
      enumClassName.trace(definitionSupplier);
    }

    @Override
    KmAnnotationArgument<?> rewrite(AppView<?> appView, NamingLens namingLens) {
      return new EnumValue(
          enumClassName.toRenamedBinaryNameOrDefault(appView, namingLens, ClassClassifiers.anyName),
          enumEntryName);
    }
  }

  private static class KotlinAnnotationAnnotationValueInfo extends KotlinAnnotationArgumentInfo {

    private final KotlinAnnotationInfo value;

    private KotlinAnnotationAnnotationValueInfo(KotlinAnnotationInfo value) {
      this.value = value;
    }

    private static KotlinAnnotationAnnotationValueInfo create(
        AnnotationValue arg, DexItemFactory factory) {
      return new KotlinAnnotationAnnotationValueInfo(
          KotlinAnnotationInfo.create(arg.getValue(), factory));
    }

    @Override
    public void trace(DexDefinitionSupplier definitionSupplier) {
      value.trace(definitionSupplier);
    }

    @Override
    KmAnnotationArgument<?> rewrite(AppView<?> appView, NamingLens namingLens) {
      Box<KmAnnotation> rewrittenAnnotation = new Box<>();
      value.rewrite(rewrittenAnnotation::set, appView, namingLens);
      if (rewrittenAnnotation.isSet()) {
        return new AnnotationValue(rewrittenAnnotation.get());
      }
      return null;
    }
  }

  private static class KotlinAnnotationArrayValueInfo extends KotlinAnnotationArgumentInfo {

    private static final KotlinAnnotationArrayValueInfo EMPTY =
        new KotlinAnnotationArrayValueInfo(ImmutableList.of());

    private final List<KotlinAnnotationArgumentInfo> value;

    private KotlinAnnotationArrayValueInfo(List<KotlinAnnotationArgumentInfo> value) {
      this.value = value;
    }

    private static KotlinAnnotationArrayValueInfo create(ArrayValue arg, DexItemFactory factory) {
      if (arg.getValue().isEmpty()) {
        return EMPTY;
      }
      ImmutableList.Builder<KotlinAnnotationArgumentInfo> builder = ImmutableList.builder();
      for (KmAnnotationArgument<?> argument : arg.getValue()) {
        builder.add(createArgument(argument, factory));
      }
      return new KotlinAnnotationArrayValueInfo(builder.build());
    }

    @Override
    public void trace(DexDefinitionSupplier definitionSupplier) {
      for (KotlinAnnotationArgumentInfo kotlinAnnotationArgumentInfo : value) {
        kotlinAnnotationArgumentInfo.trace(definitionSupplier);
      }
    }

    @Override
    KmAnnotationArgument<?> rewrite(AppView<?> appView, NamingLens namingLens) {
      List<KmAnnotationArgument<?>> rewrittenArguments = new ArrayList<>();
      for (KotlinAnnotationArgumentInfo kotlinAnnotationArgumentInfo : value) {
        KmAnnotationArgument<?> rewrittenArg =
            kotlinAnnotationArgumentInfo.rewrite(appView, namingLens);
        if (rewrittenArg != null) {
          rewrittenArguments.add(rewrittenArg);
        }
      }
      return new ArrayValue(rewrittenArguments);
    }
  }

  private static class KotlinAnnotationPrimitiveArgumentInfo extends KotlinAnnotationArgumentInfo {

    private final KmAnnotationArgument<?> argument;

    private KotlinAnnotationPrimitiveArgumentInfo(KmAnnotationArgument<?> argument) {
      this.argument = argument;
    }

    private static KotlinAnnotationPrimitiveArgumentInfo create(KmAnnotationArgument<?> argument) {
      return new KotlinAnnotationPrimitiveArgumentInfo(argument);
    }

    @Override
    public void trace(DexDefinitionSupplier definitionSupplier) {
      // Nothing to trace
    }

    @Override
    KmAnnotationArgument<?> rewrite(AppView<?> appView, NamingLens namingLens) {
      return argument;
    }
  }
}
