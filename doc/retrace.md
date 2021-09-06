# R8, Retrace and map file versioning

Programs compiled by R8 are not structurally the same as the input. For example,
names, invokes and line numbers can all change when compiling with R8. The
correspondence between the input and the output is recorded in a mapping file.
The mapping file can be used with retrace to recover the original stack trace
from the stack traces produced by running the R8 compiled program. More
information about R8 and mapping files can be found
[here](https://developer.android.com/studio/build/shrink-code#decode-stack-trace).

## Additional information appended as comments to the file

The format for additional information is encoded as comments with json formatted
data. The data has an ID to disambiguate it from other information.

### Version

The version information states what version the content of the mapping file is
using.

The format of the version information is:
```
# {"id":"com.android.tools.r8.mapping","version":"1.0"}
```
Here `id` must be `com.android.tools.r8.mapping` and `version` is the version of
the mapping file.

The version information applies to content of the file following the position of
the version entry until either the end of the file or another version entry is
present.

If no version information is present, the content is assumed to have version
zero and no additional mapping information except [Source File](#source-file).

When interpreting the mapping file, any additional mapping information
pertaining to a later version should be ignored. In other words, treated as a
normal comment.

Retracing tools supporting this versioning scheme should issue a warning when
given mapping files with versions higher than the version supported by the tool.

### Source File

The source file information states what source file a class originated from.

The source file information must be placed directly below the class mapping it
pertains to. The format of the source file information is:
```
some.package.Class -> foo.a:
# {"id":"sourceFile","fileName":"R8Test.kt"}
```
Here `id` must be the string `sourceFile` and `fileName` is the source file name
as a string value (in this example `R8Test.kt`).

Note that the `id` of the source file information is unqualified. It is the only
allowed unqualified identifier as it was introduced prior to the versioning
scheme.

### Synthesized (Introduced at version 1.0)

The synthesized information states what parts of the compiled output program are
synthesized by the compiler. A retracing tool should use the synthesized
information to strip out synthesized method frames from retraced stacks.

The synthesized information must be placed directly below the class, field or
method mapping it pertains to. The format of the synthesized information is:
```
# {'id':'com.android.tools.r8.synthesized'}
```
Here `id` must be `com.android.tools.r8.synthesized`. There is no other content.

A class mapping would be:
```
some.package.SomeSynthesizedClass -> x.y.z:
# {'id':'com.android.tools.r8.synthesized'}
```
This specifies that the class `x.y.z` has been synthesized by the compiler and
thus it did not exist in the original input program.

Notice that the left-hand side and right-hand side, here
`some.package.SomeSynthesizedClass` and `x.y.z` respectively, could be any class
name. It is likely that the mapping for synthetics is the identity, but a useful
name can be placed here if possible which legacy retrace tools would use when
retracing.

A field mapping would be:
```
some.package.Class -> x.y.z:
  int someField -> a
  # {'id':'com.android.tools.r8.synthesized'}
```
This specifies that the field `x.y.z.a` has been synthesized by the compiler. As
for classes, since the field is not part of the original input program, the
left- and right-hand side names could be anything. Note that a field can be
synthesized without the class being synthesized.

A method mapping would be:
```
some.package.SomeSynthesizedClass -> x.y.z:
  void someMethod() -> a
  # {'id':'com.android.tools.r8.synthesized'}
```
This specifies that the method `x.y.z.a()` has been synthesized by the compiler.
As for classes, since the method is not part of the original input program, the
left- and right-hand side names could be anything. Note that a method can be
synthesized without the class being synthesized.

For inline frames a mapping would be:
```
some.package.Class -> foo.a:
    4:4:void example.Foo.lambda$main$0():225 -> a
    4:4:void run(example.Foo):2 -> a
    # {'id':'com.android.tools.r8.synthesized'}
    5:5:void example.Foo.lambda$main$1():228 -> a
    5:5:void run(example.Foo):4 -> a
    # {'id':'com.android.tools.r8.synthesized'} <-- redundant
```
This specifies that line 4 in the method `foo.a.a` is in a method that has
been synthesized by the compiler. Since the method is either synthesized or not
any extra synthesized comments will have no effect.

Synthesized information should never be placed on inlined frames:
```
some.package.Class -> foo.a:
4:4:void example.Foo.syntheticThatIsInlined():225 -> a
# {'id':'com.android.tools.r8.synthesized'}
4:4:void run(example.Foo):2 -> a
```
In the above, the mapping information suggests that the inline frame
`example.Foo.syntheticThatIsInlined` should be marked as `synthesized`. However,
since that method was not part of the input program it should not be in the
output mapping information at all.

