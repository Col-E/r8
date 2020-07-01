// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import static com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics.invalidParameterInformationObject;
import static com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics.invalidValueForObjectWithId;
import static com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics.tooManyEntriesForParameterInformation;
import static com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics.tooManyInformationalParameters;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The MethodSignatureChangedInformation structure adds extra information regarding the mapped
 * method signature that is otherwise not available in the existing proguard mapping format. The
 * JSON-structure is as follows:
 *
 * <pre>
 *   {
 *     "id": "argumentsChanged",
 *     "signature": { methodSignature },
 *     "returnType": "java.lang.String",
 *     "receiver": false,
 *     "params": [
 *       [1], // <-- parameter with original index 1 (starting index based on receiver) is removed.
 *       [2, Foo] // <-- parameter with index 2 has type Foo
 *     ]
 *   }
 * </pre>
 */
public class MethodSignatureChangedInformation extends SignatureMappingInformation {

  private ParameterInformation[] argumentInfos;
  private final boolean receiver;
  private final String returnType;
  private final MethodSignature signature;

  public static final String ID = "methodSignatureChanged";
  private static final String RETURN_TYPE_KEY = "returnType";
  private static final String PARAMS_KEY = "params";
  private static final String RECEIVER_KEY = "receiver";

  @Override
  public String serialize() {
    JsonObject result = new JsonObject();
    serializeMethodSignature(result, signature);
    result.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    result.add(RECEIVER_KEY, new JsonPrimitive(receiver));
    result.add(RETURN_TYPE_KEY, new JsonPrimitive(returnType));
    JsonArray arguments = new JsonArray();
    for (ParameterInformation argInfo : argumentInfos) {
      arguments.add(argInfo.serialize());
    }
    result.add(PARAMS_KEY, arguments);
    return result.toString();
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isMethodSignatureChangedInformation();
  }

  @Override
  public Signature getSignature() {
    return signature;
  }

  @Override
  public Signature apply(
      Signature originalSignature, String renamedName, DiagnosticsHandler diagnosticsHandler) {
    if (originalSignature == null || !originalSignature.isMethodSignature()) {
      assert false : "Should only call apply for method signature";
      return originalSignature;
    }
    MethodSignature signature = originalSignature.asMethodSignature();
    String type = signature.type;
    String[] parameters = signature.parameters;
    int numberOfArgumentsRemoved = getNumberOfArgumentsRemoved();
    if (numberOfArgumentsRemoved > parameters.length) {
      // The mapping information is not up to date with the current signature.
      diagnosticsHandler.warning(tooManyInformationalParameters(getLineNumber()));
      return new MethodSignature(renamedName, type, parameters);
    }
    String[] newParameters = new String[parameters.length - numberOfArgumentsRemoved];
    int insertIndex = 0;
    for (int i = 0; i < parameters.length; i++) {
      ParameterInformation argInfo = getParameterInformation(i);
      if (argInfo != null && argInfo.getType() == null) {
        // Argument has been removed.
      } else {
        if (insertIndex >= newParameters.length) {
          // The mapping information is not up to date with the current signature.
          diagnosticsHandler.warning(tooManyInformationalParameters(getLineNumber()));
          return new MethodSignature(renamedName, type, parameters);
        } else if (argInfo == null) {
          // Unchanged, take current parameter.
          newParameters[insertIndex++] = parameters[i];
        } else {
          newParameters[insertIndex++] = argInfo.getType();
        }
      }
    }
    assert insertIndex == newParameters.length;
    return new MethodSignature(renamedName, getReturnType(), newParameters);
  }

  @Override
  public boolean isMethodSignatureChangedInformation() {
    return true;
  }

  public int getNumberOfArgumentsRemoved() {
    int removedCount = 0;
    for (ParameterInformation argInfo : argumentInfos) {
      if (argInfo.type == null) {
        removedCount++;
      }
    }
    return removedCount;
  }

  public boolean hasReceiver() {
    return receiver;
  }

  public String getReturnType() {
    return returnType;
  }

  public ParameterInformation getParameterInformation(int index) {
    int subtractIndex = receiver ? 1 : 0;
    for (int i = 0; i < argumentInfos.length; i++) {
      if (argumentInfos[i].index - subtractIndex == index) {
        return argumentInfos[i];
      }
    }
    return null;
  }

  @Override
  public MethodSignatureChangedInformation asMethodSignatureChangedInformation() {
    return this;
  }

  private MethodSignatureChangedInformation(
      MethodSignature signature,
      String returnType,
      boolean hasReceiver,
      ParameterInformation[] argumentInfos,
      int lineNumber) {
    super(lineNumber);
    this.signature = signature;
    this.argumentInfos = argumentInfos;
    this.returnType = returnType;
    this.receiver = hasReceiver;
  }

  public static MappingInformation build(
      JsonObject object, DiagnosticsHandler diagnosticsHandler, int lineNumber) {
    try {
      JsonElement returnTypeElement =
          getJsonElementFromObject(object, diagnosticsHandler, lineNumber, RETURN_TYPE_KEY, ID);
      JsonElement receiverElement =
          getJsonElementFromObject(object, diagnosticsHandler, lineNumber, RECEIVER_KEY, ID);
      JsonElement argsElement =
          getJsonElementFromObject(object, diagnosticsHandler, lineNumber, PARAMS_KEY, ID);
      MethodSignature signature = getMethodSignature(object, ID, diagnosticsHandler, lineNumber);
      if (signature == null
          || returnTypeElement == null
          || receiverElement == null
          || argsElement == null) {
        return null;
      }
      JsonArray argumentsArray = argsElement.getAsJsonArray();
      if (argumentsArray == null) {
        return null;
      }
      ParameterInformation[] args = new ParameterInformation[argumentsArray.size()];
      for (int i = 0; i < argumentsArray.size(); i++) {
        args[i] =
            ParameterInformation.fromJsonArray(
                argumentsArray.get(i).getAsJsonArray(), diagnosticsHandler, lineNumber);
      }
      return new MethodSignatureChangedInformation(
          signature,
          returnTypeElement.getAsString(),
          receiverElement.getAsBoolean(),
          args,
          lineNumber);
    } catch (UnsupportedOperationException | IllegalStateException ignored) {
      diagnosticsHandler.info(invalidValueForObjectWithId(lineNumber, MAPPING_ID_KEY, ID));
      return null;
    }
  }

  public static class ParameterInformation {
    private final int index;
    private final String type;

    public int getIndex() {
      return index;
    }

    public String getType() {
      return type;
    }

    private ParameterInformation(int index, String type) {
      this.index = index;
      this.type = type;
    }

    static ParameterInformation fromJsonArray(
        JsonArray argumentInfo, DiagnosticsHandler diagnosticsHandler, int lineNumber) {
      assert argumentInfo != null;
      try {
        if (argumentInfo.size() > 2) {
          diagnosticsHandler.info(tooManyEntriesForParameterInformation(lineNumber));
          return null;
        }
        int index = argumentInfo.get(0).getAsInt();
        if (argumentInfo.size() == 1) {
          // This is a removed argument - no type information
          return new ParameterInformation(index, null);
        } else {
          return new ParameterInformation(index, argumentInfo.get(1).getAsString());
        }
      } catch (UnsupportedOperationException | IllegalStateException ignored) {
        diagnosticsHandler.info(invalidParameterInformationObject(lineNumber));
        return null;
      }
    }

    public static ParameterInformation buildRemovedParameterInformation(int index) {
      return new ParameterInformation(index, null);
    }

    public static ParameterInformation buildChangedParameterInformation(int index, String type) {
      return new ParameterInformation(index, type);
    }

    JsonArray serialize() {
      JsonArray serializedArray = new JsonArray();
      serializedArray.add(index);
      if (type != null) {
        serializedArray.add(type);
      }
      return serializedArray;
    }
  }
}
