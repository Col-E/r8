// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.function.Consumer;

public abstract class ResidualSignatureMappingInformation extends ReferentialMappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_2;
  public static final String ID = "com.android.tools.r8.residualsignature";
  public static final String SIGNATURE_KEY = "signature";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    object.add(SIGNATURE_KEY, new JsonPrimitive(serializeInternal()));
    return object.toString();
  }

  protected abstract String serializeInternal();

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }

  public static void deserialize(
      MapVersion version, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      JsonElement signature = object.get(SIGNATURE_KEY);
      if (signature == null) {
        throw new CompilationError("Expected '" + SIGNATURE_KEY + "' to be present: " + object);
      }
      String signatureString = signature.getAsString();
      if (signatureString.contains("(")) {
        onMappingInfo.accept(
            ResidualMethodSignatureMappingInformation.deserialize(signatureString));
      } else {
        onMappingInfo.accept(ResidualFieldSignatureMappingInformation.deserialize(signatureString));
      }
    }
  }

  public abstract boolean isValid();

  @Override
  public boolean isResidualSignatureMappingInformation() {
    return true;
  }

  @Override
  public ResidualSignatureMappingInformation asResidualSignatureMappingInformation() {
    return this;
  }

  public static class ResidualMethodSignatureMappingInformation
      extends ResidualSignatureMappingInformation {

    private static final ResidualMethodSignatureMappingInformation INVALID_METHOD_SIGNATURE =
        new ResidualMethodSignatureMappingInformation(new String[0], "LINVALID;");

    private final String returnType;
    private final String[] parameters;

    private ResidualMethodSignatureMappingInformation(String[] parameters, String returnType) {
      assert DescriptorUtils.isDescriptor(returnType)
          || DescriptorUtils.isVoidDescriptor(returnType);
      this.returnType = returnType;
      this.parameters = parameters;
    }

    public static ResidualMethodSignatureMappingInformation fromDexMethod(DexMethod method) {
      String[] parameters =
          ArrayUtils.mapToStringArray(method.getParameters().values, DexType::toDescriptorString);
      return new ResidualMethodSignatureMappingInformation(
          parameters, method.getReturnType().toDescriptorString());
    }

    @Override
    protected String serializeInternal() {
      return StringUtils.join("", Arrays.asList(parameters), BraceType.PARENS) + returnType;
    }

    @Override
    public boolean isValid() {
      return this != INVALID_METHOD_SIGNATURE;
    }

    public static ResidualMethodSignatureMappingInformation deserialize(String signature) {
      String[] argumentTypeDescriptors = DescriptorUtils.getArgumentTypeDescriptors(signature);
      String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(signature);
      boolean isValid =
          DescriptorUtils.isDescriptor(returnTypeDescriptor)
              || DescriptorUtils.isVoidDescriptor(returnTypeDescriptor);
      for (String argumentTypeDescriptor : argumentTypeDescriptors) {
        isValid &= DescriptorUtils.isDescriptor(argumentTypeDescriptor);
      }
      return isValid
          ? new ResidualMethodSignatureMappingInformation(
              argumentTypeDescriptors, returnTypeDescriptor)
          : INVALID_METHOD_SIGNATURE;
    }

    public String getReturnType() {
      return returnType;
    }

    public String[] getParameters() {
      return parameters;
    }

    @Override
    public boolean isResidualMethodSignatureMappingInformation() {
      return true;
    }

    @Override
    public ResidualMethodSignatureMappingInformation asResidualMethodSignatureMappingInformation() {
      return this;
    }

    @Override
    public MappingInformation compose(MappingInformation existing) {
      // Always take the newest residual mapping
      assert existing.isResidualMethodSignatureMappingInformation();
      return this;
    }

    @Override
    public boolean allowOther(MappingInformation information) {
      if (!information.isResidualMethodSignatureMappingInformation()) {
        return true;
      }
      ResidualMethodSignatureMappingInformation other =
          information.asResidualMethodSignatureMappingInformation();
      return returnType.equals(other.returnType) && Arrays.equals(parameters, other.parameters);
    }
  }

  public static class ResidualFieldSignatureMappingInformation
      extends ResidualSignatureMappingInformation {

    private static final ResidualFieldSignatureMappingInformation INVALID_FIELD_SIGNATURE =
        new ResidualFieldSignatureMappingInformation("LINVALID;");

    private final String type;

    private ResidualFieldSignatureMappingInformation(String type) {
      assert DescriptorUtils.isDescriptor(type);
      this.type = type;
    }

    public static ResidualFieldSignatureMappingInformation fromDexField(DexField field) {
      return new ResidualFieldSignatureMappingInformation(field.getType().toDescriptorString());
    }

    public String getType() {
      return type;
    }

    @Override
    protected String serializeInternal() {
      return type;
    }

    @Override
    public boolean isValid() {
      return this != INVALID_FIELD_SIGNATURE;
    }

    public static ResidualFieldSignatureMappingInformation deserialize(String signature) {
      return DescriptorUtils.isDescriptor(signature)
          ? new ResidualFieldSignatureMappingInformation(signature)
          : INVALID_FIELD_SIGNATURE;
    }

    @Override
    public boolean isResidualFieldSignatureMappingInformation() {
      return true;
    }

    @Override
    public ResidualFieldSignatureMappingInformation asResidualFieldSignatureMappingInformation() {
      return this;
    }

    @Override
    public MappingInformation compose(MappingInformation existing) {
      // Always take the newest residual mapping.
      assert existing.isResidualFieldSignatureMappingInformation();
      return this;
    }

    @Override
    public boolean allowOther(MappingInformation information) {
      return !information.isResidualFieldSignatureMappingInformation()
          || type.equals(information.asResidualFieldSignatureMappingInformation().getType());
    }
  }
}
