// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.codeinspector.KmTypeSubject.getDescriptorFromKmType;

import com.android.tools.r8.references.Reference;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmFunctionExtensionVisitor;
import kotlinx.metadata.KmFunctionVisitor;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmTypeAlias;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;

public interface FoundKmDeclarationContainerSubject extends KmDeclarationContainerSubject {

  CodeInspector codeInspector();
  KmDeclarationContainer getKmDeclarationContainer();

  @Override
  default List<String> getParameterTypeDescriptorsInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .flatMap(kmFunction ->
            kmFunction.getValueParameters().stream()
                .map(kmValueParameter -> getDescriptorFromKmType(kmValueParameter.getType()))
                .filter(Objects::nonNull))
        .collect(Collectors.toList());
  }

  @Override
  default List<String> getReturnTypeDescriptorsInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .map(kmFunction -> getDescriptorFromKmType(kmFunction.getReturnType()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  default List<String> getReturnTypeDescriptorsInProperties() {
    return getKmDeclarationContainer().getProperties().stream()
        .map(kmProperty -> getDescriptorFromKmType(kmProperty.getReturnType()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  // TODO(b/145824437): This is a dup of KotlinMetadataJvmExtensionUtils$KmFunctionProcessor
  class KmFunctionProcessor {
    // Custom name via @JvmName("..."). Otherwise, null.
    JvmMethodSignature signature = null;

    KmFunctionProcessor(KmFunction kmFunction) {
      kmFunction.accept(new KmFunctionVisitor() {
        @Override
        public KmFunctionExtensionVisitor visitExtensions(KmExtensionType type) {
          if (type != JvmFunctionExtensionVisitor.TYPE) {
            return null;
          }
          return new JvmFunctionExtensionVisitor() {
            @Override
            public void visit(JvmMethodSignature desc) {
              assert signature == null : signature.asString();
              signature = desc;
            }
          };
        }
      });
      // We don't check Kotlin types in tests, but be aware of the relocation issue.
      // See b/70169921#comment57 for more details.
    }
  }

  // TODO(b/151194869): Search both original and renamed names.
  default KmFunctionSubject kmFunctionOrExtensionWithUniqueName(String name, boolean isExtension) {
    KmFunction foundFunction = null;
    for (KmFunction kmFunction : getKmDeclarationContainer().getFunctions()) {
      if (KmFunctionSubject.isExtension(kmFunction) != isExtension) {
        continue;
      }
      if (kmFunction.getName().equals(name)) {
        foundFunction = kmFunction;
        break;
      }
      KmFunctionProcessor kmFunctionProcessor = new KmFunctionProcessor(kmFunction);
      if (kmFunctionProcessor.signature != null
          && kmFunctionProcessor.signature.getName().equals(name)) {
        foundFunction = kmFunction;
        break;
      }
    }
    if (foundFunction != null) {
      return new FoundKmFunctionSubject(codeInspector(), foundFunction);
    }
    return new AbsentKmFunctionSubject();
  }

  @Override
  default KmFunctionSubject kmFunctionWithUniqueName(String name) {
    return kmFunctionOrExtensionWithUniqueName(name, false);
  }

  @Override
  default KmFunctionSubject kmFunctionExtensionWithUniqueName(String name) {
    return kmFunctionOrExtensionWithUniqueName(name, true);
  }

  @Override
  default List<KmFunctionSubject> getFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .map(kmFunction -> new FoundKmFunctionSubject(codeInspector(), kmFunction))
        .collect(Collectors.toList());
  }

  default ClassSubject getClassSubjectFromDescriptor(String descriptor) {
    return codeInspector().clazz(Reference.classFromDescriptor(descriptor));
  }

  default ClassSubject getClassSubjectFromKmType(KmType kmType) {
    String descriptor = getDescriptorFromKmType(kmType);
    if (descriptor == null) {
      return new AbsentClassSubject(codeInspector(), Reference.classFromDescriptor("Lnot_found;"));
    }
    return getClassSubjectFromDescriptor(descriptor);
  }

  @Override
  default List<ClassSubject> getParameterTypesInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .flatMap(kmFunction ->
            kmFunction.getValueParameters().stream()
                .map(kmValueParameter -> getClassSubjectFromKmType(kmValueParameter.getType()))
                .filter(ClassSubject::isPresent))
        .collect(Collectors.toList());
  }

  @Override
  default List<ClassSubject> getReturnTypesInFunctions() {
    return getKmDeclarationContainer().getFunctions().stream()
        .map(kmFunction -> getClassSubjectFromKmType(kmFunction.getReturnType()))
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }

  // TODO(b/145824437): This is a dup of KotlinMetadataJvmExtensionUtils$KmPropertyProcessor
  class KmPropertyProcessor {
    JvmFieldSignature fieldSignature = null;
    // Custom getter via @get:JvmName("..."). Otherwise, null.
    JvmMethodSignature getterSignature = null;
    // Custom getter via @set:JvmName("..."). Otherwise, null.
    JvmMethodSignature setterSignature = null;

    KmPropertyProcessor(KmProperty kmProperty) {
      kmProperty.accept(new KmPropertyVisitor() {
        @Override
        public KmPropertyExtensionVisitor visitExtensions(KmExtensionType type) {
          if (type != JvmPropertyExtensionVisitor.TYPE) {
            return null;
          }
          return new JvmPropertyExtensionVisitor() {
            @Override
            public void visit(
                int flags,
                JvmFieldSignature fieldDesc,
                JvmMethodSignature getterDesc,
                JvmMethodSignature setterDesc) {
              assert fieldSignature == null : fieldSignature.asString();
              fieldSignature = fieldDesc;
              assert getterSignature == null : getterSignature.asString();
              getterSignature = getterDesc;
              assert setterSignature == null : setterSignature.asString();
              setterSignature = setterDesc;
            }
          };
        }
      });
      // We don't check Kotlin types in tests, but be aware of the relocation issue.
      // See b/70169921#comment57 for more details.
    }
  }

  default KmPropertySubject kmPropertyOrExtensionWithUniqueName(String name, boolean isExtension) {
    KmProperty foundProperty = null;
    for (KmProperty kmProperty : getKmDeclarationContainer().getProperties()) {
      if (KmPropertySubject.isExtension(kmProperty) != isExtension) {
        continue;
      }
      if (kmProperty.getName().equals(name)) {
        foundProperty = kmProperty;
        break;
      }
      KmPropertyProcessor kmPropertyProcessor = new KmPropertyProcessor(kmProperty);
      if (kmPropertyProcessor.fieldSignature != null
          && kmPropertyProcessor.fieldSignature.getName().equals(name)) {
        foundProperty = kmProperty;
        break;
      }
      if (kmPropertyProcessor.getterSignature != null
          && kmPropertyProcessor.getterSignature.getName().equals(name)) {
        foundProperty = kmProperty;
        break;
      }
      if (kmPropertyProcessor.setterSignature != null
          && kmPropertyProcessor.setterSignature.getName().equals(name)) {
        foundProperty = kmProperty;
        break;
      }
    }
    if (foundProperty != null) {
      return new FoundKmPropertySubject(codeInspector(), foundProperty);
    }
    return new AbsentKmPropertySubject();
  }

  @Override
  default KmPropertySubject kmPropertyWithUniqueName(String name) {
    return kmPropertyOrExtensionWithUniqueName(name, false);
  }

  @Override
  default KmPropertySubject kmPropertyExtensionWithUniqueName(String name) {
    return kmPropertyOrExtensionWithUniqueName(name, true);
  }

  @Override
  default List<KmPropertySubject> getProperties() {
    return getKmDeclarationContainer().getProperties().stream()
        .map(kmProperty -> new FoundKmPropertySubject(codeInspector(), kmProperty))
        .collect(Collectors.toList());
  }

  @Override
  default List<ClassSubject> getReturnTypesInProperties() {
    return getKmDeclarationContainer().getProperties().stream()
        .map(kmProperty -> getClassSubjectFromKmType(kmProperty.getReturnType()))
        .filter(ClassSubject::isPresent)
        .collect(Collectors.toList());
  }

  @Override
  default List<KmTypeAliasSubject> getTypeAliases() {
    CodeInspector inspector = codeInspector();
    return getKmDeclarationContainer().getTypeAliases().stream()
        .map(typeAlias -> new FoundKmTypeAliasSubject(inspector, typeAlias))
        .collect(Collectors.toList());
  }

  @Override
  default KmTypeAliasSubject kmTypeAliasWithUniqueName(String name) {
    for (KmTypeAlias typeAlias : getKmDeclarationContainer().getTypeAliases()) {
      if (typeAlias.getName().equals(name)) {
        return new FoundKmTypeAliasSubject(codeInspector(), typeAlias);
      }
    }
    return new AbsentKmTypeAliasSubject();
  }
}
