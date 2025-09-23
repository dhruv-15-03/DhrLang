# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]
_No unreleased changes yet_

## [1.0.0] - 2025-09-23

### Added
- Generics support with type parameter substitution and strong diagnostics across fields and methods.
- Multi-dimensional arrays: parsing, type checking, evaluation, and tests (creation, indexing, bounds, and type rules).
- Implicit field access in instance methods: unqualified identifiers resolve to fields when no local/param matches, with access control and generic substitution.
- Compile-time checks for static field initializers:
  - STATIC_FORWARD_REFERENCE: same-class static forward reads are rejected.
  - STATIC_INIT_CYCLE: cycles among same-class static field initializers are rejected.
- Expanded diagnostics documentation (ERROR_CODES.md) and a Diagnostics Quick Guide in README.

### Changed
- SPEC.md updated to reflect generics substitution, multi-dimensional arrays, and implicit field access; clarified scoping rules for static vs instance contexts.
- README.md updated with new feature status and examples.

### Fixed
- Regression in undefined-variable hint preserved for static contexts while enabling implicit field access in instance methods.
- Improved static dependency analysis to catch forward references inside nested expressions and multi-dimensional initializers.

