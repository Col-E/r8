// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class SignatureMappingInformation extends MappingInformation {

  private static final String SIGNATURE_KEY = "signature";

  SignatureMappingInformation(int lineNumber) {
    super(lineNumber);
  }

  @Override
  public boolean isSignatureMappingInformation() {
    return true;
  }

  @Override
  public SignatureMappingInformation asSignatureMappingInformation() {
    return this;
  }

  public abstract Signature getSignature();

  public abstract Signature apply(
      Signature originalSignature, String renamedName, DiagnosticsHandler diagnosticsHandler);

  JsonObject serializeMethodSignature(JsonObject object, MethodSignature signature) {
    JsonArray signatureArr = new JsonArray();
    signatureArr.add(signature.type);
    signatureArr.add(signature.name);
    for (String parameter : signature.parameters) {
      signatureArr.add(parameter);
    }
    object.add(SIGNATURE_KEY, signatureArr);
    return object;
  }

  static MethodSignature getMethodSignature(
      JsonObject object, String id, DiagnosticsHandler diagnosticsHandler, int lineNumber) {
    JsonElement signatureElement =
        getJsonElementFromObject(object, diagnosticsHandler, lineNumber, SIGNATURE_KEY, id);
    if (signatureElement == null || !signatureElement.isJsonArray()) {
      return null;
    }
    // Signature will be [returnType, name, param1, param2, ...].
    JsonArray signature = signatureElement.getAsJsonArray();
    String[] parameters = new String[signature.size() - 2];
    for (int i = 2; i < signature.size(); i++) {
      parameters[i - 2] = signature.get(i).getAsString();
    }
    return new MethodSignature(
        signature.get(1).getAsString(), signature.get(0).getAsString(), parameters);
  }
}
