package io.silvicky.item.backrooms;

/**
 * The two coordinate fields this mod deals with.
 *
 * <p>This enum exists because the distinction used to live entirely in one person's memory, which is how
 * positions ended up being transformed twice: once at the packet boundary and again inside a server-side
 * handler that had already been given server coordinates.
 *
 * <p><b>The contract.</b>
 * <ul>
 *   <li>{@link #S_FIELD} is the world the server simulates. Every position inside the server - entity
 *       positions, chunk positions handed to worldgen, block lookups, collision AABBs, the keys of
 *       {@code s2cMap}/{@code c2sMap} - is S-field.</li>
 *   <li>{@link #C_FIELD} is the view the client renders. It exists only on the wire and inside the chunk
 *       tracking remap. A C-field position must never be used for a world lookup.</li>
 * </ul>
 *
 * <p><b>The rule.</b> Conversion happens at exactly one boundary per direction: client to server on inbound
 * packets, and server to client on outbound ones. Nothing that runs after a packet has been decoded may
 * convert again, because the position it holds is already S-field.
 *
 * <p><b>Grep markers.</b> Every transform site in this mod is tagged with one of these, so the convention
 * can be reviewed without relying on anyone's memory:
 * <ul>
 *   <li>{@code [S]} - server world coordinates. Do <em>not</em> transform it again.</li>
 *   <li>{@code [C]} - client view coordinates.</li>
 *   <li>{@code [S>C]} - converts server to client.</li>
 *   <li>{@code [C>S]} - converts client to server.</li>
 * </ul>
 * Grep {@code "[S>C]"}, {@code "[C>S]"}, {@code "[S]"} and {@code "[C]"} to audit the whole surface. The
 * only correct places for a conversion are a {@code [C>S]} on an inbound packet and an {@code [S>C]} on
 * an outbound one. <b>A conversion applied to a value tagged {@code [S]} is a double transform.</b>
 *
 * <p>The practical test for any site: <i>could this value have arrived on a packet?</i> If yes, it may still
 * be C-field. If it came from the server's own state - an entity, a chunk map, a BlockPos the server
 * computed - it is S-field and converting it again is a double transform.
 */
public enum CoordinateSpace
{
    /** Server world coordinates. What everything on the server works in. */
    S_FIELD,
    /** Client view coordinates. Only on the wire and inside the chunk tracking remap. */
    C_FIELD
}
