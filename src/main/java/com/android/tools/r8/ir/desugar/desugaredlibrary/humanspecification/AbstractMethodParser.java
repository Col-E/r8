// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/** Parse methods of the form: modifiers* returnType holder#name(arg0, ..., argN) */
public abstract class AbstractMethodParser {

  private static final String SEPARATORS = "\\s+|,\\s+|#|\\(|\\)";

  private static final Map<String, Integer> modifiers =
      ImmutableMap.<String, Integer>builder()
          .put("public", Constants.ACC_PUBLIC)
          .put("private", Constants.ACC_PRIVATE)
          .put("protected", Constants.ACC_PROTECTED)
          .put("final", Constants.ACC_FINAL)
          .put("abstract", Constants.ACC_ABSTRACT)
          .put("static", Constants.ACC_STATIC)
          .build();

  final DexItemFactory factory;

  protected AbstractMethodParser(DexItemFactory factory) {
    this.factory = factory;
  }

  // TODO(b/218755060): It would be nice to avoid the split regexp and use a nextToken()
  //  method instead, then add a TraversalContinuation.
  public void parseMethod(String signature) {
    String[] tokens = signature.split(SEPARATORS);
    if (tokens.length < 3) {
      throw new CompilationError("Desugared library: cannot parse method " + signature);
    }
    methodStart();
    int first = parseModifiers(tokens);
    returnType(stringTypeToDexType(tokens[first]));
    holderType(stringTypeToDexType(tokens[first + 1]));
    methodName(factory.createString(tokens[first + 1 + 1]));
    for (int i = first + 3; i < tokens.length; i++) {
      argType(stringTypeToDexType(tokens[i]));
    }
    methodEnd();
  }

  private DexType stringTypeToDexType(String stringType) {
    return factory.createType(DescriptorUtils.javaTypeToDescriptor(stringType));
  }

  private int parseModifiers(String[] split) {
    int index = 0;
    while (modifiers.containsKey(split[index])) {
      modifier(modifiers.get(split[index]));
      index++;
    }
    return index;
  }

  protected abstract void methodStart();

  protected abstract void methodEnd();

  protected abstract void returnType(DexType type);

  protected abstract void argType(DexType type);

  protected abstract void modifier(int access);

  protected abstract void holderType(DexType type);

  protected abstract void methodName(DexString name);
}
