@Value.Style(
    // @formatter:off
    typeAbstract = { "*" },
    get = { "is*", "get*" },
    with = "with*",
    defaults = @Value.Immutable(builder = false, prehash = true)
    // @formatter:on
)
package mb.nabl2.constraints;

import org.immutables.value.Value;