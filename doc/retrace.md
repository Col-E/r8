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

- Version 1.0 was introduced by R8 in version 3.1.21

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

### RewriteFrame (Introduced at version 2.0)

The RewriteFrame information informs the retrace tool that when retracing a
frame it should rewrite it. The mapping information has the form:

```
  # { id: 'com.android.tools.r8.rewriteFrame', "
      conditions: ['throws(<exceptionDescriptor>)'],
      actions: ['removeInnerFrames(<count>)'] }
```

The format is to specify conditions for when the rule should be applied and then
describe the actions to take. The following conditions exist:

- `throws(<exceptionDescriptor>)`: Will be true if the thrown exception above is
`<exceptionDescriptor>`

Conditions can be combined by adding more items to the list. The semantics of
having more elements in the list is that the conditions are AND'ed together. To
achieve OR one should duplicate the information.

Actions describe what should happen to the retraced frames if the condition
holds. Multiple specified actions will be applied from left to right. The
following actions exist:

- `removeInnerFrames(<count>)`: Will remove the number of frames starting with
inner most frame. It is an error to specify a count higher than all frames.

An example could be to remove an inlined frame if a null-pointer-exception is
thrown:

```
some.Class -> a:
  4:4:void other.Class.inlinee():23:23 -> a
  4:4:void caller(other.Class):7 -> a\n"
  # { id: 'com.android.tools.r8.rewriteFrame', "
      conditions: ['throws(Ljava/lang/NullPointerException;)'],
      actions: ['removeInnerFrames(1)'] }
```

When retracing:
```
Exception in thread "main" java.lang.NullPointerException: ...
  at a.a(:4)
```

It will normally retrace to:
```
Exception in thread "main" java.lang.NullPointerException: ...
  at other.Class.inlinee(Class.java:23)
  at some.Class.caller(Class.java:7)
```

Amending the last mapping with the above inline information instructs the
retracer to discard frames above, resulting in the retrace result:
```
Exception in thread "main" java.lang.NullPointerException: ...
  at some.Class.caller(Class.java:7)
```

The `rewriteFrame` information will only be applied if the line that is being
retraced is directly under the exception line.

### Outline (Introduced at version 2.0)

The outline information can be used by compilers to specify that a method is an
outline. It has the following format:

```
# { 'id':'com.android.tools.r8.outline' }
```

When a retracer retraces a frame that has the outline mapping information it
should carry the reported position to the next frame and use the
`outlineCallsite` to obtain the correct position.

### Outline Call Site (Introduced at version 2.0)

A position in an outline can correspond to multiple different positions
depending on the context. The information can be stored in the mapping file with
the following format:

```
# { 'id':'com.android.tools.r8.outlineCallsite',
    'positions': {
        'outline_pos_1': callsite_pos_1,
        'outline_pos_2': callsite_pos_2,
         ...
     }
  }
```

The retracer should when seeing the `outline` information carry the line number
to the next frame. The position should be rewritten by using the positions map
before using the resulting position for further retracing. Here is an example:

```
# { id: 'com.android.tools.r8.mapping', version: '2.0' }
outline.Class -> a:
  1:2:int outline() -> a
# { 'id':'com.android.tools.r8.outline' }
some.Class -> b:
  1:1:void foo.bar.Baz.qux():42:42 -> s
  4:4:int outlineCaller(int):98:98 -> s
  5:5:int outlineCaller(int):100:100 -> s
  27:27:int outlineCaller(int):0:0 -> s
# { 'id':'com.android.tools.r8.outlineCallsite',
    'positions': { '1': 4, '2': 5 } }
```

Retracing the following stack trace lines:

```
  at a.a(:1)
  at b.s(:27)
```

Should first retrace the first line and see it is an `outline` and then use
the `outlineCallsite` for `b.s` at position `27` to map the read position `1` to
position `4` and then use that to find the actual mapping, resulting in the
retraced stack:

```
  at some.Class.outlineCaller(Class.java:98)
```

It should be such that for all stack traces, if a retracer ever see an outline
the next obfuscated line should contain `outlineCallSite` information.

### Catch all range for methods with a single unique position

If only a single position is needed for retracing a method correctly one can
skip emitting the position and rely on retrace to retrace correctly. To ensure
compatibility R8 emits a catch-all range `0:65535` as such:

```
0:65535:void foo():33:33 -> a
```

It does not matter if the mapping is an inline frame. Catch all ranges should
never be used for overloads.