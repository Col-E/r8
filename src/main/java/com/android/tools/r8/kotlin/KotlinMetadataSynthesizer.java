// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.addKotlinPrefix;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToInternalName;
import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.List;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;

class KotlinMetadataSynthesizer {
  static KmType toKmType(String descriptor) {
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(descriptorToInternalName(descriptor));
    return kmType;
  }

  static KmType toRenamedKmType(
      DexType type, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    // E.g., [Ljava/lang/String; -> Lkotlin/Array;
    if (type.isArrayType()) {
      return toKmType(addKotlinPrefix("Array;"));
    }
    // E.g., void -> Lkotlin/Unit;
    if (appView.dexItemFactory().kotlin.knownTypeConversion.containsKey(type)) {
      KmType kmType = new KmType(flagsOf());
      DexType convertedType = appView.dexItemFactory().kotlin.knownTypeConversion.get(type);
      assert convertedType != null;
      kmType.visitClass(descriptorToInternalName(convertedType.toDescriptorString()));
      return kmType;
    }
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return null;
    }
    // For library or classpath class, synthesize @Metadata always.
    // For a program class, make sure it is live.
    if (!appView.appInfo().isNonProgramTypeOrLiveProgramType(type)) {
      return null;
    }
    DexType renamedType = lens.lookupType(type, appView.dexItemFactory());
    // For library or classpath class, we should not have renamed it.
    assert clazz.isProgramClass() || renamedType == type
        : type.toSourceString() + " -> " + renamedType.toSourceString();
    // TODO(b/70169921): Mysterious, why attempts to properly set flags bothers kotlinc?
    //   and/or why wiping out flags works for KmType but not KmFunction?!
    KmType kmType = new KmType(flagsOf());
    kmType.visitClass(descriptorToInternalName(renamedType.toDescriptorString()));
    return kmType;
  }

  static KmFunction toRenamedKmFunction(
      DexMethod method, AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    DexEncodedMethod encodedMethod = appView.definitionFor(method);
    if (encodedMethod == null) {
      return null;
    }
    // For library overrides, synthesize @Metadata always.
    // For regular methods, make sure it is live.
    if (!encodedMethod.isLibraryMethodOverride().isTrue()
        && !appView.appInfo().liveMethods.contains(method)) {
      return null;
    }
    DexMethod renamedMethod = lens.lookupMethod(method, appView.dexItemFactory());
    // For a library method override, we should not have renamed it.
    assert !encodedMethod.isLibraryMethodOverride().isTrue() || renamedMethod == method
        : method.toSourceString() + " -> " + renamedMethod.toSourceString();
    // TODO(b/70169921): Consult kotlinx.metadata.Flag.Function for kind (e.g., suspend).
    KmFunction kmFunction =
        new KmFunction(encodedMethod.accessFlags.getAsKotlinFlags(), renamedMethod.name.toString());
    KmType kmReturnType = toRenamedKmType(method.proto.returnType, appView, lens);
    assert kmReturnType != null;
    kmFunction.setReturnType(kmReturnType);
    List<KmValueParameter> parameters = kmFunction.getValueParameters();
    for (int i = 0; i < method.proto.parameters.values.length; i++) {
      DexType paramType = method.proto.parameters.values[i];
      DebugLocalInfo debugLocalInfo = encodedMethod.getParameterInfo().get(i);
      String parameterName =
          debugLocalInfo != null ? debugLocalInfo.name.toString() : ("p" + i);
      // TODO(b/70169921): Consult kotlinx.metadata.Flag.ValueParameter.
      KmValueParameter kmValueParameter = new KmValueParameter(flagsOf(), parameterName);
      KmType kmParamType = toRenamedKmType(paramType, appView, lens);
      assert kmParamType != null;
      kmValueParameter.setType(kmParamType);
      parameters.add(kmValueParameter);
    }
    return kmFunction;
  }
}
