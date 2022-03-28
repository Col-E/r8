# Description of the human desugared library configuration file

## Version

The field `configuration_format_version` encodes a versioning number internal to
R8/D8 in the form of an unsigned integer. It allows R8/D8 to know if the file
given is supported. If the number if greater or equal to 100, the file is
encoded using the human flags (by opposition to the legacy flags). Human flags
are not shipped to external users. Human flags can be converted to machine flags
which are shipped to external users. Users internal to Google are allowed to use
directly human flags if we can easily update the file without backward
compatibility issues.

The field `identifier` is the maven-coordinated id for the desugared library
configuration file.

## Required compilation API level

The field `required_compilation_api_level` encodes the minimal Android API level
required for the desugared library to be compiled correctly. If the API of
library used for compilation of the library or a program using the library is
lower than this level, one has to upgrade the SDK version used to be able to use
desugared libraries.

## Synthesize prefix

The field `synthesized_library_classes_package_prefix` is used both to prefix
type names of synthetic classes created during the L8 compilation and for some
of the rewritings.

## Library callbacks

The field `support_all_callbacks_from_library` is set if D8/R8 should generate
extra callbacks, i.e., methods that may be called from specific library
implementations into the program. Setting it to false may lead to invalid
behavior if the library effectively use one of the callbacks, but reduces code
size.

## Common, library and program flags

The fields `common_flags`, `library_flags` and `program_flags` include the set
of rewriting flags required for respectively rewriting both the library and the
program, only the library or only the program.

The flags are in a list, where each list entry specifies up to which min API
level the set of flags should be applied. During compilation, R8/D8 adds up all
the required flags for the min API level specified at compilation.

The following subsections describe each rewriting flag.

### Flag rewrite_prefix

`prefix: rewrittenPrefix`
D8/R8 identifies any class type matching the prefix, and rewrite such types with
the new prefix. Types not present as class types are not rewritten. Implicitly,
all synthetic types derived from the matching type are also rewritten (lambdas
and backports in the class, etc.).

Example:
`foo.: f$.`
A class present with the type foo.Foo will generate a rewrite rule:
foo.Foo -> f$.Foo. A type present foo.Bar, which is not the type of any class,
will not generate any rewrite rule.

### Flag rewrite_derived_prefix

`prefix: { fromPrefix: toPrefix }`
D8/R8 identifies any class type matching the prefix, and rewrite the type with
the fromPrefix to the type with the toPrefix. This can be useful to generate
rewrite rules from types not present in the input.

Example:
`foo.: { f$.: foo. }`
A class present with the type foo.Foo will generate a rewrite rule:
f$.Foo -> foo.Foo.

### Flag retarget_method

`methodToRetarget: retargetType`
D8/R8 identifies all invokes which method resolve to the methodToRetarget, and
rewrite it to an invoke to the same method but with the retargetType as holder.
If the method is virtual, this converts the invoke to an invoke-static and adds
the receiver type as the first parameter.

The retargeting is valid for static methods, private methods and methods
effectively final (methods with the final keyword, methods on final classes,
which do not override any other method).

When using the flag, the method, if virtual, is considered as effectively final.
For retargeting of virtual methods that can be overridden, see
retarget_method_with_emulated_dispatch.

Example:
`Foo Bar#foo(Zorg): DesugarBar`
Any invoke which method resolves into the method with return type Foo, name foo,
parameter Zorg on the holder Bar, is rewritten to an invoke-static to the same
method on DesugarBar. If the method is not static, the rewritten method takes an
extra first parameter of type Bar.

### Flag retarget_method_with_emulated_dispatch

`methodToRetarget: retargetType`
Essentially the same as retarget_method, but for non effectively final virtual
method. The flag fails the compilation if the methodToRetarget is static. D8/R8
generates an emulated dispatch scheme so that the method can be retargeted, but
the virtual dispatch is still valid and will correctly call the overrides if
present.

### Flag amend_library_method

`modifiers method`
For the retarget_method and retarget_method_with_emulated_dispatch flags to
work, resolution has to find the method to retarget to. In some cases, the
method is missing because it's not present on the required compilation level
Android SDK, or because the method is private.

This flag amends the library to introduce the method, so resolution can find it
and retarget it correctly.

### Flag dont_retarget

`type`
In classes with such type, invokes are not retargeted with the retarget_method
and the retarget_method_with_emulated_dispatch flag. In addition, forwarding
methods required for retarget_method_with_emulated_dispatch are not introduced
in such classes.

### Flag emulate_interface

`libraryInterface: desugaredLibraryInterface`
D8/R8 assume the libraryInterface is already in the library, but without the
default and static methods present on it. It generates a companion class holding
the code for the default and static methods, and a dispatch class which hold the
code to support emulated dispatch for the default methods.

### Flag dont_rewrite

`methodNotToRewrite`
D8/R8 ignroes the methods present here from the emulated interface.

### Flag wrapper_conversion

`type`
Generate wrappers for the given type, including methods from the type and all
its super types and interface types. In addition, analyse all invokes resolving
into the library. If the invoke includes the type as return or parameter type,
automatically surround the library call with conversion code using wrappers. The
sequence of instructions with the conversions and the library invoke is outlined
and shared if possible.

### Flag wrapper_conversion_excluding

`type: [methods]`
Similar to wrapper_conversion, generate wrappers for the given type but ignore
the methods listed. This can be used for methods not accessing fields or private
methods, either to reduce code size or to work around final methods.

### Flag custom_conversion

`type: conversionType`
Similar to wrapper_conversion, but instead of generating wrappers, rely on hand
written conversions present on conversionType. The conversions methods must be
of the form:
Type convert(RewrittenType)
RewrittenType convert(Type)

## Extra keep rules

The last field is `extra_keep_rules`, it includes keep rules that are appended
by L8 when shrinking the desugared library. It includes keep rules related to
reflection inside the desugared library, related to enum to have EnumSet working
and to keep the j$ prefix.

## Copyright

Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file for
details. All rights reserved. Use of this source code is governed by a BSD-style
license that can be found in the LICENSE file.