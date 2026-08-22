## Global Workflow Rules

**Workflow Process:**
- MUST plan first before implementation
- Get user approval on plan
- Store all plans in `misc/tasks` folder
- Use checkbox format in task files (- [ ] syntax)
- Work incrementally, checking off tasks as completed
- Communicate with high-level summaries only
- Add Review section when wrapping up work
- MUST check off checkboxes as tasks complete

**Coding Principles:**
- Clarity over cleverness
- SOLID & Clean Code principles
- Functions <20 lines ideal, max 60 lines
- Descriptive names, no abbreviations
- Document all public code
- Avoid globals and static state
- Never swallow exceptions
- Avoid deep nesting, use early returns
- No magic numbers, use named constants
- Prefer immutability and pure functions
- Follow DRY principle
- Meaningful logging with context

**Critical Constraints:**
- NEVER create files unless absolutely necessary
- ALWAYS prefer editing existing files
- NEVER proactively create documentation files
- Do only what's asked, nothing more

## Java Language Specific Guidelines

- Constructor injection for dependencies
- Records for DTOs
- Always add Javadoc for packages, classes, interfaces, methods, variables
- Use Lombok annotations
- Use Lombok @Slf4j for logging
- Java 11+ features (var, records, streams)
- Optional for nullable returns instead of null
- Streams and Lambdas for collections
- Enums for fixed constants 
- Try-with-resources for resource management
- Avoid wildcard imports 
- Use `Objects.requireNonNull()` for null checks
