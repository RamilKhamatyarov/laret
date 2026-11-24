# Managed Sidecar Manifest Format

The managed manifest API is under development. It does not yet install or launch
managed plugins through the CLI. Legacy URL-plus-checksum plugins are unchanged.

`ManagedPluginManifestReader.readReviewed(path)` reads a local, regular TOML file
of at most 1 MiB. Supplying it is an explicit caller trust decision: Laret does
not authenticate a publisher or fetch a remote checksum to establish trust.
Review the document and its artifact hashes before supplying it.

```toml
schema_version = 1
name = "viewer"
version = "1.0.0"
revision = "review-42"

[[artifacts]]
os = "linux"
arch = "x86_64"
url = "https://example.test/releases/1.0.0/viewer.tar.gz"
# Replace with the reviewed artifact's actual 64-hex SHA-256; not an installable hash.
sha256 = "REPLACE_WITH_REVIEWED_SHA256"
kind = "TAR_GZ"
shape = "DIRECTORY"
lifecycle = "FOREGROUND"

[artifacts.start]
executable = "bin/viewer"
arguments = []

[artifacts.isolation]
config_root = "config"
data_root = "data"

[artifacts.isolation.environment]
VIEWER_CONFIG = "{config}"
VIEWER_DATA = "{data}"
```

Supported OS identifiers are `linux`, `macos`, and `windows`; architecture
identifiers are `x86_64` and `aarch64`. Selection requires an exact reviewed
version and target. There is no latest-release fallback or guessed emulation.
Artifact kinds are `BINARY`, `ZIP`, and `TAR_GZ`. Archives require `DIRECTORY`;
`SINGLE_FILE` preserves the legacy executable shape. All URLs require HTTPS.
Unknown fields, schema versions, duplicate targets, or missing fields fail closed.

Commands declare an executable path and literal argument array, never a shell
command string. Paths use portable forward-slash relative names without traversal,
drive letters, empty segments, or backslashes. Shell entry points are rejected.
Argument punctuation remains literal; it must never be evaluated by a shell.

Configuration and data roots are distinct relative names inside an instance's
future managed state area, not arbitrary absolute deletion targets. Child-only
environment settings must reference both `{config}` and `{data}`. `{instance}`
is also available. Reserved `LARET_*` environment overrides are rejected.
These declarations do not prove filesystem or process ownership. Installation
and lifecycle code must establish that evidence before permitting deletion.

Persistent tools declare `lifecycle = "PERSISTENT"` and additionally supply:

```toml
[artifacts.stop]
identity = "viewer-{instance}"
timeout_seconds = 10

[artifacts.stop.command]
executable = "bin/viewer"
arguments = ["server", "stop"]
```

The timeout is a finite 1-300 seconds. Foreground-only tools must not declare a
stop contract. An identity template is not by itself a process ownership probe.

`ManagedPluginMetadataCodec` round-trips version-1 installation metadata with
the reviewed manifest snapshot, selected target, installation timestamp, instance
identity, strict integrity policy, and a complete path-to-SHA-256 inventory.
Digests in the installed inventory must be lowercase, with no case-colliding
paths. Start and stop executables must appear in that inventory. No metadata
permits accepting self-updated hashes. The existing four-field `PluginMetadata`
format is unchanged and is not automatically upgraded.

No third-party release, including Herdr, is certified by this format example.

## Archive Admission

`PluginArchiveExtractor` accepts ZIP and plain USTAR tar.gz payloads in a fresh
staging directory. It returns file digests for the installed inventory. The caller
must verify the pinned artifact digest before calling it; this low-level utility
does not make trust decisions or publish installations.

Expanded content is limited to 1 GiB and 10,000 entries. The extractor rejects
links, special files, traversal, absolute/drive paths, duplicate destinations,
case-colliding path components, and nonportable Windows filenames. ZIP64,
multi-volume/encrypted ZIP, and TAR extensions such as PAX/GNU long names are
unsupported and must not trigger a shell-tool fallback. Failed extraction removes
only its newly created staging directory; existing directories are never adopted.
