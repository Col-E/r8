// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.function.Consumer;

public abstract class ResidualSignatureMappingInformation extends MappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_EXPERIMENTAL;
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

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isResidualSignatureMappingInformation();
  }

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

  public static class ResidualMethodSignatureMappingInformation
      extends ResidualSignatureMappingInformation {

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

    public static ResidualMethodSignatureMappingInformation deserialize(String signature) {
      return new ResidualMethodSignatureMappingInformation(
          DescriptorUtils.getArgumentTypeDescriptors(signature),
          DescriptorUtils.getReturnTypeDescriptor(signature));
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
  }

  public static class ResidualFieldSignatureMappingInformation
      extends ResidualSignatureMappingInformation {

    private final String type;

    private ResidualFieldSignatureMappingInformation(String type) {
      assert DescriptorUtils.isDescriptor(type);
      this.type = type;
    }

    public static ResidualFieldSignatureMappingInformation fromDexField(DexField field) {
      return new ResidualFieldSignatureMappingInformation(field.getType().toDescriptorString());
    }

    @Override
    protected String serializeInternal() {
      return type;
    }

    public static ResidualFieldSignatureMappingInformation deserialize(String signature) {
      return new ResidualFieldSignatureMappingInformation(signature);
    }

    @Override
    public boolean isResidualFieldSignatureMappingInformation() {
      return true;
    }

    @Override
    public ResidualFieldSignatureMappingInformation asResidualFieldSignatureMappingInformation() {
      return this;
    }
  }
}
