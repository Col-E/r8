// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.utils.ChainableStringConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComposingBuilder {

  private final Map<String, ComposingClassBuilder> mapping = new HashMap<>();

  public void compose(ClassNamingForNameMapper classMapping) throws MappingComposeException {
    String originalName = classMapping.originalName;
    ComposingClassBuilder composingClassBuilder = mapping.get(originalName);
    String renamedName = classMapping.renamedName;
    if (composingClassBuilder == null) {
      composingClassBuilder = new ComposingClassBuilder(originalName, renamedName);
    } else {
      composingClassBuilder.setRenamedName(renamedName);
      mapping.remove(originalName);
    }
    ComposingClassBuilder previousMapping = mapping.put(renamedName, composingClassBuilder);
    if (previousMapping != null) {
      throw new MappingComposeException(
          "Duplicate class mapping. Both '"
              + previousMapping.getOriginalName()
              + "' and '"
              + originalName
              + "' maps to '"
              + renamedName
              + "'.");
    }
    composingClassBuilder.compose(classMapping);
  }

  @Override
  public String toString() {
    List<ComposingClassBuilder> classBuilders = new ArrayList<>(mapping.values());
    classBuilders.sort(Comparator.comparing(ComposingClassBuilder::getOriginalName));
    StringBuilder sb = new StringBuilder();
    ChainableStringConsumer wrap = ChainableStringConsumer.wrap(sb::append);
    for (ComposingClassBuilder classBuilder : classBuilders) {
      classBuilder.write(wrap);
    }
    return sb.toString();
  }

  public static class ComposingClassBuilder {

    private static final String INDENTATION = "    ";

    private final String originalName;
    private String renamedName;
    private final Map<String, List<MemberNaming>> fieldMembers = new HashMap<>();

    private ComposingClassBuilder(String originalName, String renamedName) {
      this.originalName = originalName;
      this.renamedName = renamedName;
    }

    public void setRenamedName(String renamedName) {
      this.renamedName = renamedName;
    }

    public String getOriginalName() {
      return originalName;
    }

    public String getRenamedName() {
      return renamedName;
    }

    public void compose(ClassNamingForNameMapper mapper) throws MappingComposeException {
      mapper.forAllFieldNaming(
          fieldNaming -> {
            List<MemberNaming> memberNamings = fieldMembers.get(fieldNaming.getOriginalName());
            if (memberNamings == null) {
              fieldMembers
                  .computeIfAbsent(fieldNaming.getRenamedName(), ignoreArgument(ArrayList::new))
                  .add(fieldNaming);
              return;
            }
            // There is no right-hand side of field mappings thus if we have seen an existing
            // mapping we cannot compose the type. For fields we check that the original type is the
            // same or we throw an error since we cannot guarantee a proper composition.
            for (int i = 0; i < memberNamings.size(); i++) {
              MemberNaming memberNaming = memberNamings.get(i);
              assert memberNaming.getRenamedName().equals(fieldNaming.getOriginalName());
              if (memberNaming.renamedSignature.equals(fieldNaming.getOriginalSignature())) {
                memberNamings.set(
                    i,
                    new MemberNaming(
                        memberNaming.getOriginalSignature(), fieldNaming.getRenamedName()));
                return;
              }
            }
            throw new MappingComposeException(
                "Unable to compose field naming '"
                    + fieldNaming
                    + "' since the original type has changed.");
          });
    }

    public void write(ChainableStringConsumer consumer) {
      consumer.accept(originalName).accept(" -> ").accept(renamedName).accept(":\n");
      // TODO(b/241763080): Support mapping information.
      writeFieldNames(consumer);
      // TODO(b/241763080): Support function mappings.
    }

    private void writeFieldNames(ChainableStringConsumer consumer) {
      ArrayList<MemberNaming> fieldNamings = new ArrayList<>();
      for (List<MemberNaming> namingsForKey : fieldMembers.values()) {
        fieldNamings.addAll(namingsForKey);
      }
      fieldNamings.sort(Comparator.comparing(MemberNaming::getOriginalName));
      fieldNamings.forEach(
          naming -> consumer.accept(INDENTATION).accept(naming.toString()).accept("\n"));
    }
  }
}
