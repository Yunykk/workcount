# Contributing

WorkCount is intentionally small. Contributions should keep it simple, local-first, and easy to understand.

## Development

1. Fork the repository.
2. Create a branch from `main`.
3. Make a focused change.
4. Run tests:

```bash
./gradlew test
```

5. Open a pull request with a short explanation of the change.

## Guidelines

- Keep UI text in English.
- Prefer clear Kotlin over clever abstractions.
- Keep user data local unless a feature explicitly requires otherwise.
- Add tests for domain logic and data behavior when changing calculations, exports, or persistence.
- Do not commit generated build output, IDE files, local SDK paths, or signing keys.
