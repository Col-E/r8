// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.PositionalMappingInformation;
import com.google.gson.JsonObject;
import java.util.function.Consumer;

public class UnknownJsonMappingInformation extends PositionalMappingInformation {

  private final String id;
  private final String payload;

  public UnknownJsonMappingInformation(String id, String payload) {
    this.id = id;
    this.payload = payload;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getPayload() {
    return payload;
  }

  @Override
  public String serialize() {
    throw new Unreachable("We should not at this point serialize unknown information");
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return true;
  }

  @Override
  public boolean isUnknownJsonMappingInformation() {
    return true;
  }

  @Override
  public UnknownJsonMappingInformation asUnknownJsonMappingInformation() {
    return this;
  }

  @Override
  public MappingInformation compose(MappingInformation existing) throws MappingComposeException {
    throw new MappingComposeException("Unable to compose unknown json mapping information");
  }

  public static void deserialize(
      String id, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    onMappingInfo.accept(new UnknownJsonMappingInformation(id, object.toString()));
  }
}
