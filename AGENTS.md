# AGENTS.md — family-rules-server

This file provides guidance for agentic coding agents working in this repository.

---

## Project Overview

**family-rules-server** is a Kotlin + Spring Boot server built with Gradle (Kotlin DSL). It exposes a REST API for family device management backed by Google Cloud Firestore. The frontend is a static HTML/JS/Handlebars UI served by Spring.

---

## Build, Lint, and Test Commands

All commands use the Gradle wrapper (`./gradlew`). There is no npm, TypeScript, or Node.js toolchain.

| Task | Command |
|---|---|
| Build (including tests) | `./gradlew build` |
| Build (skip tests) | `./gradlew build -x test` |
| Run all tests | `./gradlew test` |
| Run a single test class | `./gradlew test --tests "pl.zarajczyk.familyrules.V2ReportControllerIntegrationSpec"` |
| Run a single test by name | `./gradlew test --tests "pl.zarajczyk.familyrules.V2ReportControllerIntegrationSpec.should accept report"` |
| Run app locally | `./gradlew bootRun` |
| Build fat JAR | `./gradlew bootJar` |
| Clean | `./gradlew clean` |

There is no dedicated lint command. The project uses `kotlin.code.style=official` (JetBrains official style) configured in `gradle.properties`, but no ktlint or detekt is set up.

Integration tests require Docker to be running (Testcontainers spins up a Firestore emulator container).

---

## Architecture

The project follows **hexagonal architecture (Ports & Adapters)**:

```
src/main/kotlin/pl/zarajczyk/familyrules/
├── domain/          # Business logic + port interfaces — no framework dependencies
│   └── port/        # Repository interfaces (what domain needs from infrastructure)
├── adapter/
│   └── firestore/   # Concrete Firestore implementations of port interfaces
├── api/v2/          # Device-facing REST API (stateless, API key auth)
├── gui/bff/         # Browser-Facing API (session auth)
├── configuration/   # Spring configuration, security
└── migrations/      # Standalone migration tool
```

**Key rule:** The domain layer must never import from `adapter/` or `api/`. Adapters depend on domain types; the domain is pure.

---

## Code Style Guidelines

### Kotlin Style
- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- 4-space indentation, no tabs.
- Opening braces on the same line as the declaration.
- Trailing commas on multi-line argument lists are fine.

### Imports
- Use wildcard imports for same-package classes: `import pl.zarajczyk.familyrules.domain.*`
- Use specific imports for third-party and standard library types.
- Prefer `kotlinx.datetime` over `java.time` for date/time (except where Java interop is required).

### Naming Conventions
| Construct | Convention | Example |
|---|---|---|
| Classes, interfaces | PascalCase | `DevicesService`, `AppGroupRepository` |
| Domain entity interfaces | PascalCase (noun) | `Device`, `User`, `AppGroup` |
| Concrete entity implementations | `RefBased<Concept>` | `RefBasedDevice`, `RefBasedUser` |
| Firestore ref wrappers | `Firestore<Concept>Ref` | `FirestoreDeviceRef` |
| Repository ports | `<Concept>Repository` | `DevicesRepository` |
| Firestore adapters | `Firestore<Concept>Repository` | `FirestoreDevicesRepository` |
| DTO classes | `<Concept>Dto` | `DeviceDetailsDto`, `WeeklyScheduleDto` |
| Enum constants | SCREAMING_SNAKE_CASE | `ACTIVE`, `ADMIN`, `PARENT` |
| Functions, properties | camelCase | `setupNewDevice`, `deviceId` |
| Type aliases | PascalCase | `typealias DeviceId = UUID` |
| Test classes | `<Subject>(Integration\|Unit)Spec` | `V2ReportControllerIntegrationSpec` |

### Types and Data Modeling
- Use `data class` for DTOs and value objects.
- Use `interface` for domain entities (not concrete classes directly); back them with `RefBased*` implementations.
- Annotate Firestore-serialized DTOs with `@Serializable` (kotlinx.serialization).
- Use `typealias` to give semantic meaning to primitive types: `typealias DeviceId = UUID`.
- Use the `ValueUpdate<T>` wrapper pattern for partial-update DTOs (null means "leave unchanged"):
  ```kotlin
  data class DeviceDetailsUpdateDto(
      val deviceName: ValueUpdate<String> = leaveUnchanged(),
      val schedule: ValueUpdate<WeeklyScheduleDto> = leaveUnchanged(),
  )
  ```

### Error Handling
- Define domain-specific exceptions extending `RuntimeException`:
  ```kotlin
  class UserNotFoundException(username: String) : RuntimeException("User $username not found")
  ```
- Use Kotlin's anonymous catch (`_`) to discard the exception object when it is not needed:
  ```kotlin
  } catch (_: UserNotFoundException) {
      RegisterInstanceResponse(RegisterInstanceStatus.INVALID_PASSWORD)
  }
  ```
- The `GlobalExceptionHandler` (`@ControllerAdvice`) handles HTTP-layer exceptions and maps them to `ErrorResponse(error, message, timestamp)`. Add new mappings there rather than in individual controllers.
- Map domain exceptions to `ResponseStatusException` in BFF controllers:
  ```kotlin
  } catch (_: AppGroupNotFoundException) {
      throw ResponseStatusException(HttpStatus.FORBIDDEN)
  }
  ```
- Security errors use `BadCredentialsException` subclasses (`MissingHeaderException`, `UnauthorizedException`).

### Spring Conventions
- Use constructor injection; avoid field injection (`@Autowired` on fields) in new code.
- Mark beans with `@Service`, `@Component`, `@Repository` as appropriate.
- Do not pass `Ref` objects above the service layer — services should resolve refs to domain objects immediately. The existing `@Deprecated("avoid refs on a Service level")` annotation on affected methods signals this intent.
- Use `@Lazy(false)` to override lazy initialization when a bean's `@PostConstruct` must run at startup.

---

## Test Conventions

### Framework Stack
- **Kotest** `FunSpec` style as the primary test framework.
- **MockK** for mocking.
- **Testcontainers** (`FirestoreEmulatorContainer`) for integration tests — Docker must be running.
- **Spring MockMvc** for HTTP-layer assertions.
- JUnit 5 is the runner platform (`useJUnitPlatform()`).

### Test Structure
All test files live in `src/test/kotlin/pl/zarajczyk/familyrules/` (flat, no sub-packages).

**Integration test template:**
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Testcontainers
class MyControllerIntegrationSpec : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired private lateinit var mockMvc: MockMvc

    companion object {
        @Container @JvmStatic
        val firestoreContainer = FirestoreEmulatorContainer("gcr.io/...")

        @JvmStatic @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            firestoreContainer.start()
            registry.add("firestore.emulator-host") { firestoreContainer.emulatorEndpoint }
        }
    }

    init {
        beforeSpec { /* create test user */ }
        afterSpec { /* delete test user */ }
        beforeTest { /* reset state */ }

        test("should do something") {
            // Given
            // When
            // Then
        }
    }
}
```

**Unit test template (no Spring context):**
```kotlin
class MyServiceUnitSpec : FunSpec({
    test("should behave correctly") {
        // Given
        // When
        // Then
    }
})
```

### Assertions (Kotest)
```kotlin
result shouldBe expectedValue
result shouldNotBe null
result.shouldNotBeNull()
collection shouldHaveSize 3
collection shouldContain "item"
```

### Test Isolation
- Integration tests create a unique user per spec using `System.currentTimeMillis()` in the name, then tear it down in `afterSpec`. Do not mock Firestore; use the real emulator.
- Use `// Given`, `// When`, `// Then` comments inside `test { }` blocks.

---

## Firestore Data Access
- All Firestore access goes through adapter classes in `adapter/firestore/`. Never access Firestore directly from domain or API layers.
- Use the `getStringOrThrow`, `getLongOrThrow`, `getBooleanOrThrow` extension functions from `FirestoreUtils.kt` (they throw `FieldNotFoundException` instead of returning null).
- JSON blobs stored in Firestore fields are serialized with `kotlinx.serialization` (`Json.encodeToString` / `Json.decodeFromString`).
- `SchedulePacker` implements sparse schedule storage — ACTIVE periods are stripped on save and reconstructed on load.

---

## Security
There are three ordered `SecurityFilterChain` beans:
1. `/api/v1/**` — all permitted (legacy).
2. `/api/v2/**` — stateless, `ApiV2KeyAuthFilter` (Basic Auth with `deviceId:token`).
3. Everything else — form login + remember-me; BFF endpoints require authentication.

Passwords are stored as SHA-256 hashes. Use the `String.sha256()` extension from `utils.kt`.
