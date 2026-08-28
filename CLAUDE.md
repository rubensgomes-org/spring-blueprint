# Claude Instructions

## General Principles
 
- Prefer clarity to cleverness.

## Workflow Process

- Plan before implementing significant changes.
- Small fixes may be implemented directly.
- MUST get user approval before executing the plan.
- Store all plans in `misc/tasks` folder.
- Use checkbox format in task files (- [ ] syntax).
- Work incrementally, checking off tasks as completed.
- Communicate with high-level summaries only.
- Add Review section when wrapping up work

## Coding Principles

- Apply the SOLID & Clean Code principles.
- Keep functions small (<20 lines ideal, max 80 lines).
- Use descriptive names, avoid abbreviations.
- Avoid globals and static state.
- Never swallow exceptions.
- Avoid deep nesting, use early returns.
- No magic numbers, use named constants.
- Prefer immutability and pure functions.
- Follow the DRY "Don't Repeat Yourself" principle.
- Follow the KISS "Keep It Simple Stupid" principle.
- Write meaningful logging with context
- Prefer modifying existing files when appropriate.
- Create new files to improve maintainability or when required by the design.

## Documentation Standards

- Prefer self-documenting code over comments.
- Write comments only when they explain intent, business rules, or non-obvious behavior.
- Do not add comments that merely restate the code.
- Add and keep documentation that describes a design pattern.
- Keep documentation concise and focused.
- Favor clear naming over explanatory comments.
- Remove redundant comments when modifying code.

## Java Language Specific Guidelines

- Constructor injection only for dependencies.
- Avoid field injection.
- Use records for immutable DTOs.
- Add Javadoc for packages, classes, interfaces, methods, variables.
- Do not generate Javadoc for private members.
- Do not generate Javadoc for simple getters, setters, constructors, or obvious methods.
- Lombok where it is already adopted by the project.
- Lombok @Slf4j for logging.
- Java 11+ features (var, records, streams).
- Use Optional for nullable returns instead of null.
- Streams and Lambdas for collections.
- Enums for fixed constants.
- Use try-with-resources for resource management.
- Avoid wildcard imports.
- Use `Objects.requireNonNull()` for null checks.
