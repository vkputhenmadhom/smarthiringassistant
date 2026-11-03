# Job Analyzer Service - Java 21 Modernization Plan

## Phase 1: Virtual Threads & Async Patterns
- [ ] Enhanced JobAnalyzerService.callAiSkillsExtraction() with virtual threads
- [ ] Add async/virtual thread support for AI integration calls

## Phase 2: Text Blocks
- [ ] Replace multi-line strings with text blocks
- [ ] Convert error messages and documentation

## Phase 3: Enhanced Switch Expressions
- [ ] Convert titleBase() to switch expressions
- [ ] Convert locationFactor() to switch expressions  
- [ ] Convert normalizeSkill() to switch expressions
- [ ] Add pattern matching in error handling

## Phase 4: Records with Validation
- [ ] Add compact constructors to SalaryEstimateRequest
- [ ] Validate experienceYears >= 0
- [ ] Validate required fields

## Phase 5: Type Inference
- [ ] Expand var keyword usage
- [ ] Stream operations and collections

## Phase 6: Pattern Matching
- [ ] Use instanceof pattern matching in readInteger()
- [ ] Use pattern matching in exception handling
- [ ] Use pattern matching in map extractions

## Phase 7: Code Quality
- [ ] Reduce Lombok dependency where Records can replace
- [ ] Use Java 21 Collections APIs
- [ ] Ensure no warnings or errors

