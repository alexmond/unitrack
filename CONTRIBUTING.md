# Contributing to UniTrack

Thanks for your interest in improving UniTrack!

## License of contributions

UniTrack is **open-core** (see [LICENSE](LICENSE)): the server (`unitrack-web`, `unitrack-it`)
is **AGPL-3.0-only**, and the published libraries (`unitrack-core`, `unitrack-cli`,
`unitrack-maven-plugin`) are **Apache-2.0**. Your contribution is licensed under the license of
the module you change.

## Sign-off (DCO)

Every commit must be signed off under the [Developer Certificate of Origin](https://developercertificate.org/) —
add a `Signed-off-by` trailer with `git commit -s`. This certifies you wrote the change (or have
the right to submit it) under the project's license.

## Contributor License grant (CLA)

By submitting a contribution you also grant Alexander Mondshain (the maintainer) a perpetual,
worldwide, non-exclusive, royalty-free license to use, reproduce, modify, and **relicense** your
contribution, including under a **commercial license**, in addition to the module's open-source
license. This preserves the project's ability to dual-license (e.g. a commercial edition of the
AGPL server) while keeping the open-core fully open. You retain copyright to your contribution.

Put a one-line acknowledgement in your PR description: *"I agree to the DCO and the CONTRIBUTING
CLA grant."*

## SPDX headers

New source files should carry an SPDX identifier matching their module:

- server (`unitrack-web`, `unitrack-it`): `// SPDX-License-Identifier: AGPL-3.0-only`
- libraries (`unitrack-core`, `unitrack-cli`, `unitrack-maven-plugin`): `// SPDX-License-Identifier: Apache-2.0`

> TODO: backfill SPDX headers across existing sources (best done via a license-header Maven
> plugin so spring-javaformat stays happy) — tracked as a follow-up, not required per-PR yet.

## Building

See [CLAUDE.md](CLAUDE.md) / the README. In short: `scripts/dev-verify.sh` must be green
(format, Checkstyle, PMD, JaCoCo, tests) before a PR.
