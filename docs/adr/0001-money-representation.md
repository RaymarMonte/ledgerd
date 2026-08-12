# ADR 0001: Money representation

A decision has to be made as to how money should be represented in the system, what type it should be.

## Decision
Money will be represented in integer minor units. For example, a value of 10.99 USD will be expressed as 1099. 

### Rejected option: Floating point
In floating point values, base 10 fractional values that have no finite representation in binary get rounded, leading to an erroneous value. During additive operations, this can get worse when 2 rounded values produce a sum that is then also rounded. This can cause the ledger to stop summing to zero at some point, which is not ideal.


### Rejected option: BigDecimal
BigDecimal doesn't have the rounding issues that float have and is actually safe to use. Integer minor units was chosen over it due to the overall simplicity that it brings. BigDecimal keeps track of its own scale which introduces complexity that can lead to ambiguous comparisons.

It does have some benefit over integer, mainly that it's easy to read and maps directly to currency values, unlike doing minor integer units and having to manually manage scale.

## Notes
- Integer does have rounding issues for division operations. At the time of writing this document, division is not yet on focus and concrete decisions are not yet made on how to deal with those, but the guiding principle for that would definitely be that the ledger should sum to zero.