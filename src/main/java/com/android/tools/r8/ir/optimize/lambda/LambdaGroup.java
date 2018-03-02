// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.lambda.CodeProcessor.Strategy;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// Represents a group of lambda classes which potentially can be represented
// by the same lambda _group_ class. Each lambda class inside the group is
// assigned an integer id.
public abstract class LambdaGroup {
  public final LambdaGroupId id;

  // Lambda group class name. Is intended to be stable and uniques.
  // In current implementation is generated in following way:
  //       <optional-package>.-$$LambdaGroup$<HASH>
  // with HASH generated based on names of the lambda class names
  // of lambdas included in the group.
  private DexType classType;

  // Maps lambda classes belonging to the group into the index inside the
  // group. Note usage of linked hash map to keep insertion ordering stable.
  //
  // WARNING: access to this map is NOT synchronized and must be performed in
  //          thread-safe context.
  private final Map<DexType, LambdaInfo> lambdas = new LinkedHashMap<>();

  public static class LambdaInfo {
    public int id;
    public final DexProgramClass clazz;

    LambdaInfo(int id, DexProgramClass clazz) {
      this.id = id;
      this.clazz = clazz;
    }
  }

  public LambdaGroup(LambdaGroupId id) {
    this.id = id;
  }

  public final DexType getGroupClassType() {
    assert classType != null;
    return classType;
  }

  public final List<LambdaInfo> lambdas() {
    return Lists.newArrayList(lambdas.values());
  }

  public final boolean shouldAddToMainDex(AppInfo appInfo) {
    // We add the group class to main index if any of the
    // lambda classes it replaces is added to main index.
    for (DexType type : lambdas.keySet()) {
      if (appInfo.isInMainDexList(type)) {
        return true;
      }
    }
    return false;
  }

  public final boolean containsLambda(DexType lambda) {
    return lambdas.containsKey(lambda);
  }

  public final int lambdaId(DexType lambda) {
    assert lambdas.containsKey(lambda);
    return lambdas.get(lambda).id;
  }

  protected final DexEncodedField[] lambdaCaptureFields(DexType lambda) {
    assert lambdas.containsKey(lambda);
    return lambdas.get(lambda).clazz.instanceFields();
  }

  // Contains less than 2 elements?
  final boolean isTrivial() {
    return lambdas.size() < 2;
  }

  final void add(DexProgramClass lambda) {
    assert !lambdas.containsKey(lambda.type);
    lambdas.put(lambda.type, new LambdaInfo(lambdas.size(), lambda));
  }

  final void remove(DexType lambda) {
    assert lambdas.containsKey(lambda);
    lambdas.remove(lambda);
  }

  final void compact() {
    int lastUsed = -1;
    int lastSeen = -1;
    for (Entry<DexType, LambdaInfo> entry : lambdas.entrySet()) {
      Integer index = entry.getValue().id;
      assert lastUsed <= lastSeen && lastSeen < index;
      lastUsed++;
      lastSeen = index;
      if (lastUsed < index) {
        entry.getValue().id = lastUsed;
      }
    }
  }

  public abstract Strategy getCodeStrategy();

  public abstract ThrowingConsumer<DexClass, LambdaStructureError> lambdaClassValidator(
      Kotlin kotlin, AppInfoWithSubtyping appInfo);

  // Package for a lambda group class to be created in.
  protected abstract String getTypePackage();

  public final DexProgramClass synthesizeClass(DexItemFactory factory) {
    assert classType == null;
    List<LambdaInfo> lambdas = Lists.newArrayList(this.lambdas.values());
    classType = factory.createType(
        "L" + getTypePackage() + "-$$LambdaGroup$" + createHash(lambdas) + ";");
    // We need to register new subtype manually  the newly introduced type
    // does not have 'hierarchyLevel' set, but it is actually needed during
    // synthetic class methods processing.
    factory.kotlin.functional.lambdaType.addDirectSubtype(classType);
    return getBuilder(factory).synthesizeClass();
  }

  protected abstract LambdaGroupClassBuilder getBuilder(DexItemFactory factory);

  private String createHash(List<LambdaInfo> lambdas) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bytes);

      // We will generate SHA-1 hash of the list of lambda classes represented in the group.
      for (LambdaInfo lambda : lambdas) {
        DexString descriptor = lambda.clazz.type.descriptor;
        out.writeInt(descriptor.size); // To avoid same-prefix problem
        out.write(descriptor.content);
      }
      out.close();

      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update(bytes.toByteArray());
      return BaseEncoding.base64Url().omitPadding().encode(digest.digest());
    } catch (NoSuchAlgorithmException | IOException ex) {
      throw new Unreachable("Cannot get SHA-1 message digest");
    }
  }

  public static class LambdaStructureError extends Exception {
    public LambdaStructureError(String cause) {
      super(cause);
    }
  }
}
