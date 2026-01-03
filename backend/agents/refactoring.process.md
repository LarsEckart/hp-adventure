# Production Code Refactoring Process

STARTER_CHARACTER = 🟣

Analyse our code base, find a code smell, and apply an appropriate refactoring from the list below.
Review git log about previous refactorings we've done.

## Steps

Confirm the code we want to change has tests.

For each refactor:
1. Ensure all tests pass.
2. Choose and perform the simplest possible refactoring (one at a time).
3. Ensure all tests pass after the change.
4. Commit each successful refactor with the message format: `- r <refactoring>` (quotes must include the `- r`).
5. Provide a status update after each refactor.

If a refactor fails three times or no further refactoring is found, pause and check with the user.

## Bad Smells in Code

They might indicate refactoring opportunities:

- Mysterious Name
- Duplicated Code
- Long Function
- Long Parameter List
- Global Data
- Mutable Data
- Divergent Change
- Shotgun Surgery
- Feature Envy
- Data Clumps
- Primitive Obsession
- Repeated Switches
- Loops
- Temporary Field
- Message Chains
- Middle Man
- Insider Trading
- Large Class
- Alternative Classes with Different Interfaces
- Data Class
- Refused Bequest
- Comments

## Refactorings

### Quick Wins (do first)

- Extract Function
- Inline Function
- Extract Variable
- Inline Variable
- Change Function Declaration
- Encapsulate Variable
- Rename Variable
- Introduce Parameter Object
- Combine Functions into Class
- Combine Functions into Transform
- Split Phase

### Encapsulation (reducing coupling, increasing cohesion)

- Encapsulate Record
- Encapsulate Collection
- Replace Primitive with Object
- Replace Temp with Query
- Extract Class
- Inline Class
- Hide Delegate
- Remove Middle Man
- Substitute Algorithm

### Moving Features (improving location of responsibilities)

- Move Function
- Move Field
- Move Statements into Function
- Move Statements to Callers
- Replace Inline Code with Function Call
- Slide Statements
- Split Loop
- Replace Loop with Pipeline
- Remove Dead Code

### Organizing Data (improving data structures)

- Split Variable
- Rename Field
- Replace Derived Variable with Query
- Change Reference to Value
- Change Value to Reference
- Replace Magic Literal

### Simplifying Conditional Logic (improving readability)

- Decompose Conditional
- Consolidate Conditional Expression
- Replace Nested Conditional with Guard Clauses
- Replace Conditional with Polymorphism
- Introduce Special Case
- Introduce Assertion
- Replace Control Flag with Break

### Refactoring APIs (improving method signatures and interactions)

- Separate Query from Modifier
- Parameterize Function
- Remove Flag Argument
- Preserve Whole Object
- Replace Parameter with Query
- Replace Query with Parameter
- Remove Setting Method
- Replace Constructor with Factory Function
- Replace Function with Command
- Replace Command with Function
- Return Modified Value
- Replace Error Code with Exception
- Replace Exception with Precheck

### Dealing with Inheritance (improving class hierarchies)

- Pull Up Method
- Pull Up Field
- Pull Up Constructor Body
- Push Down Method
- Push Down Field
- Replace Type Code with Subclasses
- Remove Subclass
- Extract Superclass
- Collapse Hierarchy
- Replace Subclass with Delegate
- Replace Superclass with Delegate
