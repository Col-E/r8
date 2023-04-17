// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PartitionFileNameInformation extends ReferentialMappingInformation {

  private final Map<String, String> typeNameToFileNameMapping;

  public static final String ID = "partitionSourceFiles";
  static final String FILE_NAME_MAPPINGS_KEY = "fileNameMappings";

  private PartitionFileNameInformation(Map<String, String> typeNameToFileNameMapping) {
    this.typeNameToFileNameMapping = typeNameToFileNameMapping;
  }

  @Override
  public String getId() {
    return ID;
  }

  public Map<String, String> getTypeNameToFileNameMapping() {
    return typeNameToFileNameMapping;
  }

  @Override
  public boolean isPartitionFileNameInformation() {
    return true;
  }

  @Override
  public PartitionFileNameInformation asPartitionFileNameInformation() {
    return this;
  }

  @Override
  public MappingInformation compose(MappingInformation existing) throws MappingComposeException {
    throw new MappingComposeException("Unable to compose " + ID);
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isPartitionFileNameInformation();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    JsonObject map = new JsonObject();
    typeNameToFileNameMapping.forEach(map::addProperty);
    object.add(FILE_NAME_MAPPINGS_KEY, map);
    return object.toString();
  }

  public static void deserialize(JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    JsonObject mappingsObject = object.getAsJsonObject(FILE_NAME_MAPPINGS_KEY);
    Builder builder = builder();
    mappingsObject
        .entrySet()
        .forEach(
            entry ->
                builder.addClassToFileNameMapping(entry.getKey(), entry.getValue().getAsString()));
    onMappingInfo.accept(builder.build());
  }

  public static class Builder {

    private final Map<String, String> typeNameToFileNameMapping = new HashMap<>();

    public Builder addClassToFileNameMapping(String typeName, String fileName) {
      typeNameToFileNameMapping.put(typeName, fileName);
      return this;
    }

    public boolean isEmpty() {
      return typeNameToFileNameMapping.isEmpty();
    }

    public PartitionFileNameInformation build() {
      return new PartitionFileNameInformation(typeNameToFileNameMapping);
    }
  }
}
