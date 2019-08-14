// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.OptionalBool;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysis;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteShrinker;
import com.android.tools.r8.ir.analysis.proto.ProtoReferences;
import com.android.tools.r8.ir.analysis.proto.RawMessageInfoDecoder;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.shaking.KeepReason;
import java.util.IdentityHashMap;
import java.util.Map;

// TODO(b/112437944): Handle cycles in the graph + add a test that fails with the current
//  implementation. The current caching mechanism is unsafe, because we may mark a message as not
//  containing a map/required field in presence of cycles, although it does.

// TODO(b/112437944): Handle extensions in the map/required field detection + add a test that fails
//  with the current implementation. If there is a field whose type is an extension, then we should
//  look if any of the applicable extensions could contain a map/required field.

// TODO(b/112437944): Handle incomplete information about extensions + add a test that fails with
//  the current implementation. If there are some extensions that cannot be resolved, then we should
//  keep fields that could reach extensions to be conservative.
public class ProtoEnqueuerExtension extends EnqueuerAnalysis {

  private final AppView<?> appView;
  private final RawMessageInfoDecoder decoder;
  private final ProtoFieldTypeFactory factory;
  private final ProtoReferences references;

  // Mapping for the set of proto message that have already become live.
  private final Map<DexType, ProtoMessageInfo> liveProtos = new IdentityHashMap<>();

  // Mapping for additional proto messages that have not yet become live. If there is a proto that
  // has become live, then its schema may refer to another proto message that has not yet become
  // live. In that case, we still need to decode the schema of the not-yet-live proto message,
  // because we need to check if it has a required/map field.
  private final Map<DexType, ProtoMessageInfo> seenButNotLiveProtos = new IdentityHashMap<>();

  // To cache whether a proto message contains a map/required field directly or indirectly.
  private final Map<ProtoMessageInfo, OptionalBool> reachesMapOrRequiredFieldFromMessageCache =
      new IdentityHashMap<>();

  public ProtoEnqueuerExtension(AppView<?> appView) {
    this.appView = appView;
    this.decoder = appView.protoShrinker().decoder;
    this.factory = appView.protoShrinker().factory;
    this.references = appView.protoShrinker().references;
  }

  /**
   * When a dynamicMethod() of a proto message becomes live, then build the corresponding {@link
   * ProtoMessageInfo} object, and create a mapping from the holder to it.
   */
  @Override
  public void processNewlyLiveMethod(DexEncodedMethod encodedMethod) {
    if (!references.isDynamicMethod(encodedMethod)) {
      return;
    }

    DexType holder = encodedMethod.method.holder;
    if (seenButNotLiveProtos.containsKey(holder)) {
      // The proto is now live instead of dead.
      liveProtos.put(holder, seenButNotLiveProtos.remove(holder));
      return;
    }

    // Since this dynamicMethod() only becomes live once, and it has just become live, it must be
    // the case that the proto is not already live.
    assert !liveProtos.containsKey(holder);
    createProtoMessageInfoFromDynamicMethod(encodedMethod, liveProtos);
  }

  private void createProtoMessageInfoFromDynamicMethod(
      DexEncodedMethod dynamicMethod, Map<DexType, ProtoMessageInfo> protos) {
    DexType holder = dynamicMethod.method.holder;
    assert !protos.containsKey(holder);

    DexClass context = appView.definitionFor(holder);
    if (context == null || !context.isProgramClass()) {
      // TODO(b/112437944): What if a proto message references a proto message on the classpath or
      //  library path? We should treat them as having a map/required field to be conservative.
      assert false; // Should generally not happen.
      return;
    }

    IRCode code = dynamicMethod.buildIR(appView, context.origin);
    InvokeMethod newMessageInfoInvoke =
        GeneratedMessageLiteShrinker.getNewMessageInfoInvoke(code, references);
    ProtoMessageInfo protoMessageInfo =
        newMessageInfoInvoke != null ? decoder.run(context, newMessageInfoInvoke) : null;
    protos.put(holder, protoMessageInfo);
  }

  @Override
  public void notifyFixpoint(Enqueuer enqueuer, EnqueuerWorklist worklist) {
    // TODO(b/112437944): We only need to check if a given field can reach a map/required field
    //  once. Maybe maintain a map `newlyLiveProtos` that store the set of proto messages that have
    //  become live since the last intermediate fixpoint.

    // TODO(b/112437944): We only need to visit the subset of protos in `liveProtos` that has at
    //  least one field that is not yet live. Maybe split `liveProtos` into `partiallyLiveProtos`
    //  and `fullyLiveProtos`.
    for (ProtoMessageInfo protoMessageInfo : liveProtos.values()) {
      if (protoMessageInfo == null || !protoMessageInfo.hasFields()) {
        continue;
      }

      for (ProtoFieldInfo protoFieldInfo : protoMessageInfo.getFields()) {
        DexField valueStorage = protoFieldInfo.getValueStorage(protoMessageInfo);
        DexEncodedField encodedValueStorage = appView.appInfo().resolveField(valueStorage);
        if (encodedValueStorage == null) {
          continue;
        }

        DexClass clazz = appView.definitionFor(encodedValueStorage.field.holder);
        if (clazz == null || !clazz.isProgramClass()) {
          assert false; // Should generally not happen.
          continue;
        }

        DexEncodedMethod dynamicMethod = clazz.lookupVirtualMethod(references::isDynamicMethod);
        if (dynamicMethod == null) {
          assert false; // Should generally not happen.
          continue;
        }

        boolean encodedValueStorageIsLive;
        if (enqueuer.isFieldLive(encodedValueStorage)) {
          // Mark the field as both read and written, since it is used reflectively.
          enqueuer.registerFieldAccess(encodedValueStorage.field, dynamicMethod);
          encodedValueStorageIsLive = true;
        } else if (reachesMapOrRequiredField(protoFieldInfo)) {
          enqueuer.registerFieldAccess(encodedValueStorage.field, dynamicMethod);
          worklist.enqueueMarkReachableFieldAction(
              encodedValueStorage.field, KeepReason.reflectiveUseIn(dynamicMethod));
          encodedValueStorageIsLive = true;
        } else {
          encodedValueStorageIsLive = false;
        }

        DexField newlyLiveField = null;
        if (encodedValueStorageIsLive) {
          // For one-of fields, mark the corresponding one-of-case field as live, and for proto2
          // singular fields, mark the corresponding hazzer-bit field as live.
          if (protoFieldInfo.getType().isOneOf()) {
            newlyLiveField = protoFieldInfo.getOneOfCaseField(protoMessageInfo);
          } else if (protoFieldInfo.hasHazzerBitField(protoMessageInfo)) {
            newlyLiveField = protoFieldInfo.getHazzerBitField(protoMessageInfo);
          }
        } else {
          // For one-of fields, mark the one-of field as live if the one-of-case field is live, and
          // for proto2 singular fields, mark the field as live if the corresponding hazzer-bit
          // field is live.
          if (protoFieldInfo.getType().isOneOf()) {
            DexField oneOfCaseField = protoFieldInfo.getOneOfCaseField(protoMessageInfo);
            DexEncodedField encodedOneOfCaseField = appView.appInfo().resolveField(oneOfCaseField);
            if (encodedOneOfCaseField != null && enqueuer.isFieldLive(encodedOneOfCaseField)) {
              newlyLiveField = encodedValueStorage.field;
            }
          } else if (protoFieldInfo.hasHazzerBitField(protoMessageInfo)) {
            DexField hazzerBitField = protoFieldInfo.getHazzerBitField(protoMessageInfo);
            DexEncodedField encodedHazzerBitField = appView.appInfo().resolveField(hazzerBitField);
            if (encodedHazzerBitField != null && enqueuer.isFieldLive(encodedHazzerBitField)) {
              newlyLiveField = encodedValueStorage.field;
            }
          }
        }

        if (newlyLiveField != null) {
          if (enqueuer.registerFieldAccess(newlyLiveField, dynamicMethod)) {
            worklist.enqueueMarkReachableFieldAction(
                newlyLiveField, KeepReason.reflectiveUseIn(dynamicMethod));
          }
        }
      }
    }
  }

  /**
   * Traverses the proto schema graph.
   *
   * @return true if this proto field contains a map/required field directly or indirectly.
   */
  private boolean reachesMapOrRequiredField(ProtoFieldInfo protoFieldInfo) {
    ProtoFieldType protoFieldType = protoFieldInfo.getType();

    // If it is a map/required field, return true.
    if (protoFieldType.isMap() || protoFieldType.isRequired()) {
      return true;
    }

    // Otherwise, check if the type of the field may contain a map/required field.
    DexType baseMessageType = protoFieldInfo.getBaseMessageType(factory);
    if (baseMessageType != null) {
      ProtoMessageInfo protoMessageInfo = getOrCreateProtoMessageInfo(baseMessageType);
      assert protoMessageInfo != null;
      return reachesMapOrRequiredField(protoMessageInfo);
    }
    return false;
  }

  /**
   * Traverses the proto schema graph.
   *
   * @return true if this proto message contains a map/required field directly or indirectly.
   */
  private boolean reachesMapOrRequiredField(ProtoMessageInfo protoMessageInfo) {
    if (!protoMessageInfo.hasFields()) {
      return false;
    }
    OptionalBool cache =
        reachesMapOrRequiredFieldFromMessageCache.getOrDefault(
            protoMessageInfo, OptionalBool.unknown());
    if (!cache.isUnknown()) {
      return cache.isTrue();
    }

    // To guard against infinite recursion, we set the cache for this message to false, although
    // we may later find out that this message actually contains a map/required field.
    reachesMapOrRequiredFieldFromMessageCache.put(protoMessageInfo, OptionalBool.of(false));

    // Check if any of the fields contains a map/required field.
    for (ProtoFieldInfo protoFieldInfo : protoMessageInfo.getFields()) {
      if (reachesMapOrRequiredField(protoFieldInfo)) {
        reachesMapOrRequiredFieldFromMessageCache.put(protoMessageInfo, OptionalBool.of(true));
        return true;
      }
    }

    return false;
  }

  private ProtoMessageInfo getOrCreateProtoMessageInfo(DexType type) {
    if (liveProtos.containsKey(type)) {
      return liveProtos.get(type);
    }

    if (seenButNotLiveProtos.containsKey(type)) {
      return seenButNotLiveProtos.get(type);
    }

    DexClass clazz = appView.definitionFor(type);
    if (clazz == null || !clazz.isProgramClass()) {
      seenButNotLiveProtos.put(type, null);
      return null;
    }

    DexEncodedMethod dynamicMethod = clazz.lookupVirtualMethod(references::isDynamicMethod);
    if (dynamicMethod == null) {
      seenButNotLiveProtos.put(type, null);
      return null;
    }

    createProtoMessageInfoFromDynamicMethod(dynamicMethod, seenButNotLiveProtos);

    return seenButNotLiveProtos.get(type);
  }
}
