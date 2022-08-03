// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;

/** Parse fields of the form: modifiers* fieldType holder#fieldName */
public abstract class AbstractFieldParser extends AbstractMemberParser {

  protected AbstractFieldParser(DexItemFactory factory) {
    super(factory);
  }

  // TODO(b/218755060): It would be nice to avoid the split regexp and use a nextToken()
  //  method instead, then add a TraversalContinuation.
  public void parseField(String signature) {
    String[] tokens = signature.split(SEPARATORS);
    if (tokens.length < 3) {
      throw new CompilationError("Desugared library: cannot parse field " + signature);
    }
    fieldStart();
    int first = parseModifiers(tokens);
    fieldType(stringTypeToDexType(tokens[first]));
    holderType(stringTypeToDexType(tokens[first + 1]));
    fieldName(factory.createString(tokens[first + 1 + 1]));
    fieldEnd();
  }

  protected abstract void fieldStart();

  protected abstract void fieldEnd();

  protected abstract void fieldType(DexType type);

  protected abstract void holderType(DexType type);

  protected abstract void fieldName(DexString name);
}
