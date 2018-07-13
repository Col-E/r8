// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.dexinspector;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import java.util.Iterator;
import java.util.function.Predicate;

public class FoundMethodSubject extends MethodSubject {

  private DexInspector dexInspector;
  private final FoundClassSubject clazz;
  private final DexEncodedMethod dexMethod;

  public FoundMethodSubject(
      DexInspector dexInspector, DexEncodedMethod encoded, FoundClassSubject clazz) {
    this.dexInspector = dexInspector;
    this.clazz = clazz;
    this.dexMethod = encoded;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    return clazz.naming != null && !getFinalSignature().name.equals(getOriginalSignature().name);
  }

  @Override
  public boolean isPublic() {
    return dexMethod.accessFlags.isPublic();
  }

  @Override
  public boolean isStatic() {
    return dexMethod.accessFlags.isStatic();
  }

  @Override
  public boolean isFinal() {
    return dexMethod.accessFlags.isFinal();
  }

  @Override
  public boolean isAbstract() {
    return dexMethod.accessFlags.isAbstract();
  }

  @Override
  public boolean isBridge() {
    return dexMethod.accessFlags.isBridge();
  }

  @Override
  public boolean isInstanceInitializer() {
    return dexMethod.isInstanceInitializer();
  }

  @Override
  public boolean isClassInitializer() {
    return dexMethod.isClassInitializer();
  }

  @Override
  public DexEncodedMethod getMethod() {
    return dexMethod;
  }

  @Override
  public MethodSignature getOriginalSignature() {
    MethodSignature signature = getFinalSignature();
    if (clazz.naming == null) {
      return signature;
    }

    // Map the parameters and return type to original names. This is needed as the in the
    // Proguard map the names on the left side are the original names. E.g.
    //
    //   X -> a
    //     X method(X) -> a
    //
    // whereas the final signature is for X.a is "a (a)"
    String[] OriginalParameters = new String[signature.parameters.length];
    for (int i = 0; i < OriginalParameters.length; i++) {
      String obfuscated = signature.parameters[i];
      String original = dexInspector.originalToObfuscatedMapping.inverse().get(obfuscated);
      OriginalParameters[i] = original != null ? original : obfuscated;
    }
    String obfuscatedReturnType = signature.type;
    String originalReturnType =
        dexInspector.originalToObfuscatedMapping.inverse().get(obfuscatedReturnType);
    String returnType = originalReturnType != null ? originalReturnType : obfuscatedReturnType;

    MethodSignature lookupSignature =
        new MethodSignature(signature.name, returnType, OriginalParameters);

    MemberNaming memberNaming = clazz.naming.lookup(lookupSignature);
    return memberNaming != null ? (MethodSignature) memberNaming.getOriginalSignature() : signature;
  }

  @Override
  public MethodSignature getFinalSignature() {
    return MethodSignature.fromDexMethod(dexMethod.method);
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return dexInspector.getOriginalSignatureAttribute(
        dexMethod.annotations, GenericSignatureParser::parseMethodSignature);
  }

  @Override
  public String getFinalSignatureAttribute() {
    return dexInspector.getFinalSignatureAttribute(dexMethod.annotations);
  }

  @Override
  public Iterator<InstructionSubject> iterateInstructions() {
    return dexInspector.createInstructionIterator(this);
  }

  @Override
  public <T extends InstructionSubject> Iterator<T> iterateInstructions(
      Predicate<InstructionSubject> filter) {
    return new FilteredInstructionIterator<>(dexInspector, this, filter);
  }

  @Override
  public String toString() {
    return dexMethod.toSourceString();
  }
}
