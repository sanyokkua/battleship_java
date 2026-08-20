# Phase 1 Contract Design Index

These files define how feature 001 will build the authoritative root `contracts/` product. They are
planning artifacts, not a second OpenAPI source and not inputs for backend/frontend generation.

- [openapi-design.md](openapi-design.md): canonical-source graph, exact wire conventions, security,
  revisions, SSE, compatibility, and file ownership.
- [operation-matrix.md](operation-matrix.md): all 11 operations with success, body, header, cookie,
  security, cache, and failure behavior.
- [schema-catalog.md](schema-catalog.md): reusable schema names, fields, unions, conditional presence,
  validation, privacy, and arithmetic invariants.
- [problem-catalog.md](problem-catalog.md): closed v1 problem codes, statuses, structured reason and
  recovery fields, and non-enumeration rules.
- [validation-matrix.md](validation-matrix.md): requirements/stories/edges/success criteria/checklist
  concerns mapped to root artifacts, future work packages, and proofs.

The implementation source of truth will be `contracts/openapi/openapi.yaml` and its referenced files.
If this design inventory and the active spec differ, the active spec controls and the design must be
corrected before implementation.
