# Compatibility

## Requirements

- Scala 3.3 or newer. Releases are built on the 3.3 LTS line, so they work on every later Scala 3 version.
- JVM, Scala.js 1.x and Scala Native 0.5.

## Versioning

CaseComplete follows [early semantic versioning](https://www.scala-lang.org/blog/2021/02/16/preventing-version-conflicts-with-versionscheme.html).
Since 1.0.0, binary compatibility is kept within a major version.
CI checks every change against the previous release with [MiMa](https://github.com/lightbend-labs/mima).

The checks happen at compile time, so the wording of compile errors may change between minor versions.
What compiles and what doesn't stays the same.

## License

[MIT](https://github.com/stivens/CaseComplete/blob/main/LICENSE).
